package com.mary.mypx.feature.camera.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.mary.mypx.domain.model.FilterType
import com.mary.mypx.feature.camera.R
import com.mary.mypx.feature.camera.databinding.FragmentPreviewBinding
import com.mary.mypx.feature.camera.viewmodel.PreviewViewModel
import com.mary.mypx.sdk.FilterType as SdkFilterType
import kotlinx.coroutines.launch
import java.io.InputStream

/**
 * 预览Fragment - 照片预览和编辑页面的UI控制器
 *
 * 【Clean Architecture 实现】
 * - Fragment 只负责 UI 显示和用户交互
 * - 业务逻辑全部委托给 ViewModel
 * - 不直接调用 Repository 或第三方库
 */
class PreviewFragment : Fragment() {

    companion object {
        private const val TAG = "PreviewFragment"
    }

    // ==================== 视图绑定 ====================

    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!

    // ==================== ViewModel ====================

    private val viewModel: PreviewViewModel by viewModels()

    // ==================== 状态 ====================

    /** 照片URI字符串 */
    private var photoUriString: String? = null

    // ==================== 生命周期方法 ====================

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

        hideStatusBar()

        photoUriString = arguments?.getString("photoUriString")
        photoUriString?.let { uriString ->
            val uri = Uri.parse(uriString)
            loadImage(uri)
        }

        setupUI()
        observeViewModel()
    }

    private fun hideStatusBar() {
        val window = requireActivity().window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.statusBars())
    }

    private fun showStatusBar() {
        val window = requireActivity().window
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.show(WindowInsetsCompat.Type.statusBars())
    }

    // ==================== 图像加载 ====================

    private fun loadImage(uri: Uri) {
        try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            bitmap?.let {
                binding.imagePreview.setImageBitmap(it)
                viewModel.setOriginalImage(it)
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 观察 ViewModel ====================

    /**
     * 观察 ViewModel 状态变化
     *
     * 【Clean Architecture 数据流】
     * ViewModel 更新 StateFlow → Fragment 观察并更新 UI
     */
    private fun observeViewModel() {
        // 观察编辑后的图片
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.editedImageData.collect { data ->
                data?.let {
                    val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                    binding.imagePreview.setImageBitmap(bitmap)
                }
            }
        }

        // 观察加载状态
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                // 可以显示/隐藏加载指示器
            }
        }

        // 观察错误信息
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }
            }
        }

        // 观察成功信息
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.success.collect { success ->
                success?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    viewModel.clearSuccess()
                }
            }
        }
    }

    // ==================== UI设置 ====================

    private fun setupUI() {
        binding.buttonBack.setOnClickListener {
            showStatusBar()
            findNavController().popBackStack()
        }

        binding.buttonApply.setOnClickListener {
            viewModel.saveToGallery()
        }

        binding.buttonMore.setOnClickListener { view ->
            showPopupMenu(view)
        }

        binding.compareButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val original = viewModel.getOriginalBitmap()
                    original?.let { binding.imagePreview.setImageBitmap(it) }
                    binding.watermarkOverlay.visibility = View.GONE
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val edited = viewModel.getEditedBitmap()
                    edited?.let { binding.imagePreview.setImageBitmap(it) }
                    binding.watermarkOverlay.visibility = View.VISIBLE
                    true
                }
                else -> false
            }
        }

        setupToolbar()
    }

    private fun setupToolbar() {
        binding.toolPresets.setOnClickListener {
            Toast.makeText(requireContext(), "Presets", Toast.LENGTH_SHORT).show()
        }

        binding.toolColor.setOnClickListener {
            enterColorAdjustMode()
        }

        binding.toolBeauty.setOnClickListener {
            Toast.makeText(requireContext(), "Portrait Beauty", Toast.LENGTH_SHORT).show()
        }

        binding.toolCrop.setOnClickListener {
            enterCropMode()
        }

        binding.toolSky.setOnClickListener {
            Toast.makeText(requireContext(), "Sky Replacement", Toast.LENGTH_SHORT).show()
        }

        binding.toolClothes.setOnClickListener {
            Toast.makeText(requireContext(), "Clothing Beauty", Toast.LENGTH_SHORT).show()
        }

        binding.toolUpload.setOnClickListener {
            viewModel.uploadToGitHub()
        }

        binding.buttonCropCancel.setOnClickListener {
            exitCropMode(false)
        }

        binding.buttonCropConfirm.setOnClickListener {
            exitCropMode(true)
        }

        binding.buttonColorCancel.setOnClickListener {
            exitColorAdjustMode(false)
        }

        binding.buttonColorConfirm.setOnClickListener {
            exitColorAdjustMode(true)
        }

        setupColorSeekBars()
    }

    private fun setupColorSeekBars() {
        binding.seekbarBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress - 100
                binding.textBrightnessValue.text = "$value"
                if (fromUser) applyColorAdjustments()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekbarContrast.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress - 100
                binding.textContrastValue.text = "$value"
                if (fromUser) applyColorAdjustments()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekbarSaturation.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress - 100
                binding.textSaturationValue.text = "$value"
                if (fromUser) applyColorAdjustments()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun applyColorAdjustments() {
        val brightness = binding.seekbarBrightness.progress - 100
        val contrast = binding.seekbarContrast.progress - 100
        val saturation = binding.seekbarSaturation.progress - 100
        viewModel.adjustColors(brightness, contrast, saturation)
    }

    // ==================== 裁剪模式 ====================

    private fun enterCropMode() {
        binding.cropOverlay.visibility = View.VISIBLE
        binding.cropOverlay.resetCropRect()
        binding.mainToolbar.visibility = View.GONE
        binding.cropToolbar.visibility = View.VISIBLE
        binding.watermarkOverlay.visibility = View.GONE
        binding.compareButton.visibility = View.GONE
    }

    private fun exitCropMode(applyCrop: Boolean) {
        if (applyCrop) {
            // 获取相对坐标（0-1）
            val cropRect = binding.cropOverlay.getCropRect()

            // 获取当前图片尺寸，转换为绝对像素坐标
            val bitmap = viewModel.getEditedBitmap()
            if (bitmap != null) {
                val x = (cropRect.left * bitmap.width).toInt()
                val y = (cropRect.top * bitmap.height).toInt()
                val width = (cropRect.width() * bitmap.width).toInt()
                val height = (cropRect.height() * bitmap.height).toInt()
                viewModel.cropImage(x, y, width, height)
            }
        }

        binding.cropOverlay.visibility = View.GONE
        binding.mainToolbar.visibility = View.VISIBLE
        binding.cropToolbar.visibility = View.GONE
        binding.watermarkOverlay.visibility = View.VISIBLE
        binding.compareButton.visibility = View.VISIBLE
    }

    // ==================== 色彩调节模式 ====================

    private fun enterColorAdjustMode() {
        binding.mainToolbar.visibility = View.GONE
        binding.colorToolbar.visibility = View.VISIBLE
        binding.watermarkOverlay.visibility = View.GONE
        binding.compareButton.visibility = View.GONE
    }

    private fun exitColorAdjustMode(apply: Boolean) {
        if (!apply) {
            viewModel.resetToOriginal()
            binding.seekbarBrightness.progress = 100
            binding.seekbarContrast.progress = 100
            binding.seekbarSaturation.progress = 100
            binding.textBrightnessValue.text = "0"
            binding.textContrastValue.text = "0"
            binding.textSaturationValue.text = "0"
        }

        binding.mainToolbar.visibility = View.VISIBLE
        binding.colorToolbar.visibility = View.GONE
        binding.watermarkOverlay.visibility = View.VISIBLE
        binding.compareButton.visibility = View.VISIBLE
    }

    // ==================== 弹出菜单 ====================

    private fun showPopupMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_preview, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_photo_info -> {
                    showPhotoInfo()
                    true
                }
                R.id.menu_help_feedback -> {
                    Toast.makeText(requireContext(), "Help & Feedback", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun showPhotoInfo() {
        val bitmap = viewModel.getOriginalBitmap()
        if (bitmap != null) {
            val info = "尺寸: ${bitmap.width} x ${bitmap.height}\nURI: $photoUriString"
            Toast.makeText(requireContext(), info, Toast.LENGTH_LONG).show()
        }
    }

    // ==================== 生命周期 ====================

    override fun onDestroyView() {
        showStatusBar()
        super.onDestroyView()
        _binding = null
    }
}
