package com.mary.mypx.feature.camera.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.mary.mypx.domain.model.CameraState
import com.mary.mypx.domain.model.FilterType
import com.mary.mypx.feature.camera.R
import com.mary.mypx.feature.camera.databinding.FragmentCameraBinding
import com.mary.mypx.feature.camera.viewmodel.CameraViewModel
import com.mary.mypx.sdk.PerformanceMonitor
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CameraFragment : Fragment() {
    
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: CameraViewModel by viewModels()
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        checkPermissions()
        observeViewModel()
    }
    
    private fun setupUI() {
        binding.buttonTakePhoto.setOnClickListener {
            viewModel.takePhoto()
        }
        
        binding.buttonFlash.setOnClickListener {
            viewModel.toggleFlash()
            updateFlashIcon()
        }
        
        binding.buttonSwitchCamera.setOnClickListener {
            viewModel.switchCamera()
            // Restart camera with new lens facing
            startCamera()
        }
        
        binding.chipNone.setOnClickListener { viewModel.setFilter(FilterType.NONE) }
        binding.chipBeauty.setOnClickListener { viewModel.setFilter(FilterType.BEAUTY) }
        binding.chipSuperRes.setOnClickListener { viewModel.setFilter(FilterType.SUPER_RESOLUTION) }
        binding.chipNight.setOnClickListener { viewModel.setFilter(FilterType.NIGHT_MODE) }
    }
    
    private fun checkPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun startCamera() {
        val cameraManager = viewModel.getCameraManager()
        val imageProcessor = viewModel.getImageProcessor()
        val performanceMonitor = viewModel.getPerformanceMonitor()
        
        cameraManager.startPreview(
            lifecycleOwner = viewLifecycleOwner,
            previewView = binding.previewView,
            onImageAnalyzed = { imageProxy ->
                performanceMonitor.startFrame()
                
                // Process frame with current filter
                val currentFilter = viewModel.currentFilter.value
                if (currentFilter != FilterType.NONE) {
                    performanceMonitor.startInference()
                    
                    // Convert ImageProxy to Bitmap for processing
                    val bitmap = imageProxyToBitmap(imageProxy)
                    bitmap?.let {
                        lifecycleScope.launch {
                            val sdkFilterType = viewModel.getSdkFilterType(currentFilter)
                            val processedBitmap = imageProcessor.process(it, sdkFilterType)
                            // In a real app, you'd render this processed bitmap
                            // For now, we just measure the inference time
                            performanceMonitor.endInference()
                        }
                    }
                }
                
                imageProxy.close()
                
                // Update performance metrics
                viewModel.updatePerformanceMetrics()
            }
        )
        
        viewModel.setCameraState(CameraState.Preview)
    }
    
    private fun imageProxyToBitmap(imageProxy: androidx.camera.core.ImageProxy): Bitmap? {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.cameraState.collectLatest { state ->
                when (state) {
                    is CameraState.Initializing -> {
                        binding.previewView.visibility = View.GONE
                    }
                    is CameraState.Preview -> {
                        binding.previewView.visibility = View.VISIBLE
                    }
                    is CameraState.TakingPhoto -> {
                        // Show loading indicator if needed
                    }
                    is CameraState.Processing -> {
                        // Show processing indicator
                    }
                    is CameraState.Error -> {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        viewModel.photoTaken.observe(viewLifecycleOwner) { photo ->
            photo?.let {
                val bundle = Bundle().apply {
                    putString("photoUriString", it.uriString)
                }
                findNavController().navigate(R.id.action_camera_to_preview, bundle)
                viewModel.clearPhoto()
            }
        }
        
        viewModel.performanceMetrics.observe(viewLifecycleOwner) { metrics ->
            binding.textPerformance.text = String.format(
                "FPS: %.1f | Inference: %dms | Memory: %.1fMB",
                metrics.fps,
                metrics.inferenceTimeMs,
                metrics.memoryUsageMB
            )
        }
        
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }
    
    private fun updateFlashIcon() {
        val flashOn = viewModel.isFlashOn()
        binding.buttonFlash.setImageResource(
            if (flashOn) android.R.drawable.ic_menu_info_details
            else android.R.drawable.ic_menu_manage
        )
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
