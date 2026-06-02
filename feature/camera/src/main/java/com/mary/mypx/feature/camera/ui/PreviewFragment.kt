package com.mary.mypx.feature.camera.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.mary.mypx.domain.model.FilterType
import com.mary.mypx.feature.camera.R
import com.mary.mypx.feature.camera.databinding.FragmentPreviewBinding
import com.mary.mypx.feature.camera.viewmodel.CameraViewModel
import com.mary.mypx.sdk.FilterType as SdkFilterType
import kotlinx.coroutines.launch
import java.io.InputStream

/**
 * 预览Fragment - 照片预览和编辑页面的UI控制器
 * 
 * 【功能说明】
 * 显示拍摄的照片，支持：
 * 1. 查看照片
 * 2. 对比原图和编辑后的图片（按住Compare按钮）
 * 3. 多种编辑工具：预设、色彩调节、人像美化、裁剪旋转、换天空、衣物美化
 * 4. 保存照片
 * 5. 更多选项：照片信息、帮助反馈、预设管理
 * 
 * 【页面流程】
 * 相机页面 → 预览页面 → 编辑/保存 → 返回相机页面
 * 
 * 【数据传递】
 * 从相机页面接收照片URI，通过Navigation的arguments传递：
 * ```kotlin
 * val bundle = Bundle().apply {
 *     putString("photoUriString", uriString)
 * }
 * findNavController().navigate(R.id.action_camera_to_preview, bundle)
 * ```
 */
class PreviewFragment : Fragment() {
    
    companion object {
        private const val TAG = "PreviewFragment"
    }
    
    // ==================== 视图绑定 ====================
    
    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!
    
    // ==================== ViewModel ====================
    
    /**
     * 相机视图模型 - 与CameraFragment共享同一个ViewModel
     * 
     * 【为什么共享ViewModel？】
     * 使用activityViewModels()或同一个ViewModelStoreOwner，
     * 可以在多个Fragment之间共享数据。
     * 但这里使用viewModels()，每个Fragment有自己的ViewModel实例。
     */
    private val viewModel: CameraViewModel by viewModels()
    
    // ==================== 图像数据 ====================
    
    /**
     * 原始图像 - 拍照时的原始图像
     * 
     * 用于滤镜处理的输入，以及在取消滤镜时恢复原图
     */
    private var originalBitmap: Bitmap? = null
    
    /**
     * 处理后的图像 - 应用滤镜后的图像
     * 
     * 用于显示和保存
     */
    private var processedBitmap: Bitmap? = null
    
    /**
     * 照片URI字符串 - 从相机页面传递过来的照片URI
     * 
     * 用于加载照片图像
     */
    private var photoUriString: String? = null
    
    // ==================== 生命周期方法 ====================
    
    /**
     * 创建视图 - 初始化视图绑定
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    /**
     * 视图创建完成 - 初始化UI和加载图像
     * 
     * 【功能说明】
     * 1. 获取从相机页面传递的照片URI
     * 2. 加载照片图像
     * 3. 设置UI交互
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 获取照片URI字符串
        photoUriString = arguments?.getString("photoUriString")
        // 加载图像
        photoUriString?.let { uriString ->
            val uri = Uri.parse(uriString)
            loadImage(uri)
        }
        
        // 设置UI交互
        setupUI()
    }
    
    // ==================== 图像加载 ====================
    
    /**
     * 加载图像 - 从URI加载图像到Bitmap
     * 
     * 【功能说明】
     * 1. 通过ContentResolver打开URI对应的输入流
     * 2. 使用BitmapFactory解码为Bitmap
     * 3. 显示图像
     * 4. 应用当前选择的滤镜
     * 
     * 【参数说明】
     * @param uri 照片的URI
     * 
     * 【错误处理】
     * 如果加载失败，显示错误提示
     */
    private fun loadImage(uri: Uri) {
        try {
            // 打开URI对应的输入流
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
            // 解码为Bitmap
            originalBitmap = BitmapFactory.decodeStream(inputStream)
            // 关闭输入流
            inputStream?.close()
            
            // 显示图像
            originalBitmap?.let {
                binding.imagePreview.setImageBitmap(it)
                
                // 应用当前选择的滤镜
                applyFilter(viewModel.currentFilter.value)
            }
        } catch (e: Exception) {
            // 加载失败，显示错误提示
            Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ==================== UI设置 ====================
    
    /**
     * 设置UI - 配置所有UI组件的交互
     * 
     * 【功能说明】
     * 1. 返回按钮：返回相机页面
     * 2. 应用按钮：保存照片
     * 3. 更多按钮：显示弹出菜单
     * 4. 对比按钮：按住显示原图，松开显示编辑后
     * 5. 工具栏：各个编辑工具的点击事件
     */
    private fun setupUI() {
        // 返回按钮点击事件
        binding.buttonBack.setOnClickListener {
            findNavController().popBackStack()
        }
        
        // 应用按钮点击事件 - 保存照片
        binding.buttonApply.setOnClickListener {
            applyAndSave()
        }
        
        // 更多按钮点击事件 - 显示弹出菜单
        binding.buttonMore.setOnClickListener { view ->
            showPopupMenu(view)
        }
        
        // 对比按钮 - 按住显示原图（无水印），松开显示编辑后的图片（有水印）
        binding.compareButton.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // 按住时显示原图，隐藏水印
                    originalBitmap?.let {
                        binding.imagePreview.setImageBitmap(it)
                    }
                    binding.watermarkOverlay.visibility = View.GONE
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    // 松开时显示编辑后的图片，显示水印
                    val editedBitmap = processedBitmap ?: originalBitmap
                    editedBitmap?.let {
                        binding.imagePreview.setImageBitmap(it)
                    }
                    binding.watermarkOverlay.visibility = View.VISIBLE
                    true
                }
                else -> false
            }
        }
        
        // 工具栏点击事件
        setupToolbar()
    }
    
    /**
     * 设置工具栏点击事件
     * 
     * 【工具列表】
     * - 预设：选择预设滤镜
     * - 色彩调节：调整亮度、对比度、饱和度等
     * - 人像美化：磨皮、美白、瘦脸等
     * - 裁剪旋转：裁剪和旋转图片
     * - 换天空：替换天空背景
     * - 衣物美化：衣物颜色和样式调整
     */
    private fun setupToolbar() {
        // 预设
        binding.toolPresets.setOnClickListener {
            Toast.makeText(requireContext(), "Presets", Toast.LENGTH_SHORT).show()
        }
        
        // 色彩调节
        binding.toolColor.setOnClickListener {
            Toast.makeText(requireContext(), "Color Adjustment", Toast.LENGTH_SHORT).show()
        }
        
        // 人像美化
        binding.toolBeauty.setOnClickListener {
            Toast.makeText(requireContext(), "Portrait Beauty", Toast.LENGTH_SHORT).show()
        }
        
        // 裁剪旋转
        binding.toolCrop.setOnClickListener {
            Toast.makeText(requireContext(), "Crop & Rotate", Toast.LENGTH_SHORT).show()
        }
        
        // 换天空
        binding.toolSky.setOnClickListener {
            Toast.makeText(requireContext(), "Sky Replacement", Toast.LENGTH_SHORT).show()
        }
        
        // 衣物美化
        binding.toolClothes.setOnClickListener {
            Toast.makeText(requireContext(), "Clothing Beauty", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 显示弹出菜单
     * 
     * 【菜单选项】
     * - 照片信息：显示照片的详细信息
     * - 帮助与反馈：显示帮助信息
     * - 刷新预设：重新加载预设
     * - 保存预设：保存当前设置为预设
     * - 导入预设：从文件导入预设
     * 
     * @param anchor 菜单锚点视图
     */
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
                    showHelpFeedback()
                    true
                }
                R.id.menu_refresh_presets -> {
                    refreshPresets()
                    true
                }
                R.id.menu_save_presets -> {
                    savePresets()
                    true
                }
                R.id.menu_import_presets -> {
                    importPresets()
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }
    
    /**
     * 显示照片信息
     * 
     * 【显示内容】
     * - 图片尺寸（宽x高）
     * - 文件大小
     * - URI路径
     */
    private fun showPhotoInfo() {
        val bitmap = originalBitmap
        if (bitmap != null) {
            val info = buildString {
                appendLine("尺寸: ${bitmap.width} x ${bitmap.height}")
                appendLine("URI: $photoUriString")
            }
            Toast.makeText(requireContext(), info, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(requireContext(), "No photo loaded", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 显示帮助与反馈
     */
    private fun showHelpFeedback() {
        Toast.makeText(requireContext(), "Help & Feedback", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 刷新预设
     * 
     * 【功能说明】
     * 重新加载默认预设配置
     */
    private fun refreshPresets() {
        Toast.makeText(requireContext(), "Presets refreshed", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 保存预设
     * 
     * 【功能说明】
     * 将当前滤镜设置保存为预设
     */
    private fun savePresets() {
        Toast.makeText(requireContext(), "Presets saved", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 导入预设
     * 
     * 【功能说明】
     * 从文件导入预设配置
     */
    private fun importPresets() {
        Toast.makeText(requireContext(), "Import presets", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 应用滤镜并保存
     * 
     * 【执行流程】
     * 1. 获取当前处理后的图像
     * 2. 保存到设备存储
     * 3. 显示保存结果
     * 4. 返回相机页面
     */
    private fun applyAndSave() {
        val bitmapToSave = processedBitmap ?: originalBitmap
        if (bitmapToSave != null) {
            savePhoto(bitmapToSave)
        } else {
            Toast.makeText(requireContext(), "No photo to save", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ==================== 滤镜处理 ====================
    
    /**
     * 应用滤镜 - 对图像应用指定的滤镜效果
     * 
     * 【功能说明】
     * 1. 获取图像处理器
     * 2. 开始推理计时
     * 3. 在协程中执行滤镜处理
     * 4. 显示处理后的图像
     * 5. 显示处理耗时
     * 
     * 【参数说明】
     * @param filterType 要应用的滤镜类型
     * 
     * 【性能考虑】
     * 使用协程在后台线程处理，避免阻塞主线程
     */
    private fun applyFilter(filterType: FilterType) {
        originalBitmap?.let { bitmap ->
            // 获取SDK组件
            val imageProcessor = viewModel.getImageProcessor()
            val performanceMonitor = viewModel.getPerformanceMonitor()
            
            // 开始推理计时
            performanceMonitor.startInference()
            
            // 在协程中执行滤镜处理
            lifecycleScope.launch {
                // 转换滤镜类型
                val sdkFilterType = viewModel.getSdkFilterType(filterType)
                // 应用滤镜
                processedBitmap = imageProcessor.process(bitmap, sdkFilterType)
                // 结束推理计时
                performanceMonitor.endInference()
                
                // 显示处理后的图像
                processedBitmap?.let {
                    binding.imagePreview.setImageBitmap(it)
                    
                    // 显示处理耗时
                    val inferenceTime = performanceMonitor.getLastInferenceTime()
                    binding.textProcessingTime.text = "Processing: ${inferenceTime}ms"
                    binding.textProcessingTime.visibility = View.VISIBLE
                }
            }
        }
    }
    
    // ==================== 图像转换 ====================
    
    /**
     * 将Bitmap转换为ByteArray
     * 
     * 【功能说明】
     * 将Bitmap格式转换为JPEG格式的字节数组，
     * 用于保存到存储或传输。
     * 
     * 【参数说明】
     * @param bitmap 要转换的Bitmap
     * @return JPEG格式的字节数组
     * 
     * 【压缩质量】
     * 使用90%的质量，平衡文件大小和图像质量
     */
    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
        return outputStream.toByteArray()
    }
    
    // ==================== 保存功能 ====================
    
    /**
     * 保存照片 - 将照片保存到设备存储
     * 
     * 【功能说明】
     * 1. 将Bitmap转换为ByteArray
     * 2. 调用ViewModel保存照片
     * 3. 显示保存结果
     * 
     * 【参数说明】
     * @param bitmap 要保存的Bitmap
     * 
     * 【保存流程】
     * Bitmap → ByteArray → ViewModel → Repository → 存储
     */
    private fun savePhoto(bitmap: Bitmap) {
        // 将Bitmap转换为ByteArray
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
        val imageData = outputStream.toByteArray()
        
        // 在协程中执行保存操作
        lifecycleScope.launch {
            // 调用ViewModel保存照片
            val result = viewModel.savePhoto(imageData)
            result.fold(
                onSuccess = {
                    // 保存成功，显示成功提示
                    Toast.makeText(requireContext(), "Photo saved", Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    // 保存失败，显示错误提示
                    Toast.makeText(requireContext(), "Failed to save photo: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
    
    // ==================== 生命周期 ====================
    
    /**
     * 销毁视图 - 清理资源
     * 
     * 【功能说明】
     * 在视图销毁时清理资源，避免内存泄漏
     */
    override fun onDestroyView() {
        super.onDestroyView()
        // 置空绑定，避免内存泄漏
        _binding = null
    }
}
