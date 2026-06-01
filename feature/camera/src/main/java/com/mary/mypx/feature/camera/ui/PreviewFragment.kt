package com.mary.mypx.feature.camera.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.mary.mypx.domain.model.FilterType
import com.mary.mypx.feature.camera.databinding.FragmentPreviewBinding
import com.mary.mypx.feature.camera.viewmodel.CameraViewModel
import com.mary.mypx.sdk.FilterType as SdkFilterType
import kotlinx.coroutines.launch
import java.io.InputStream

class PreviewFragment : Fragment() {
    
    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: CameraViewModel by viewModels()
    
    private var originalBitmap: Bitmap? = null
    private var processedBitmap: Bitmap? = null
    private var photoUriString: String? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        photoUriString = arguments?.getString("photoUriString")
        photoUriString?.let { uriString ->
            val uri = Uri.parse(uriString)
            loadImage(uri)
        }
        
        setupUI()
    }
    
    private fun loadImage(uri: Uri) {
        try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
            originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            originalBitmap?.let {
                binding.imagePreview.setImageBitmap(it)
                
                // Apply current filter
                applyFilter(viewModel.currentFilter.value)
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupUI() {
        binding.buttonSave.setOnClickListener {
            processedBitmap?.let { bitmap ->
                savePhoto(bitmap)
            } ?: originalBitmap?.let { bitmap ->
                savePhoto(bitmap)
            }
        }
        
        binding.buttonShare.setOnClickListener {
            // Implement share functionality
            Toast.makeText(requireContext(), "Share functionality", Toast.LENGTH_SHORT).show()
        }
        
        binding.buttonBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }
    
    private fun applyFilter(filterType: FilterType) {
        originalBitmap?.let { bitmap ->
            val imageProcessor = viewModel.getImageProcessor()
            val performanceMonitor = viewModel.getPerformanceMonitor()
            
            performanceMonitor.startInference()
            
            lifecycleScope.launch {
                val sdkFilterType = viewModel.getSdkFilterType(filterType)
                processedBitmap = imageProcessor.process(bitmap, sdkFilterType)
                performanceMonitor.endInference()
                
                processedBitmap?.let {
                    binding.imagePreview.setImageBitmap(it)
                    
                    // Show processing time
                    val inferenceTime = performanceMonitor.getLastInferenceTime()
                    binding.textProcessingTime.text = "Processing: ${inferenceTime}ms"
                    binding.textProcessingTime.visibility = View.VISIBLE
                }
            }
        }
    }
    
    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
        return outputStream.toByteArray()
    }
    
    private fun savePhoto(bitmap: Bitmap) {
        // Convert Bitmap to ByteArray
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
        val imageData = outputStream.toByteArray()
        
        // Save using ViewModel
        lifecycleScope.launch {
            val result = viewModel.savePhoto(imageData)
            result.fold(
                onSuccess = {
                    Toast.makeText(requireContext(), "Photo saved", Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    Toast.makeText(requireContext(), "Failed to save photo: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
