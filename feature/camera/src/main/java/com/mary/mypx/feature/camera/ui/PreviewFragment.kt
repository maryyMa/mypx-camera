package com.mary.mypx.feature.camera.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
import com.mary.mypx.feature.camera.viewmodel.CameraViewModel
import com.mary.mypx.sdk.FilterType as SdkFilterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    
    // ==================== 色彩调节 ====================
    
    /** 色彩调节工具 */
    private lateinit var colorHelper: ColorAdjustmentHelper
    
    /** 是否正在色彩调节模式 */
    private var isColorAdjustMode = false
    
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
     * 1. 隐藏系统状态栏（全屏显示）
     * 2. 初始化 GPUImage
     * 3. 获取从相机页面传递的照片URI
     * 4. 加载照片图像
     * 5. 设置UI交互
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 隐藏系统状态栏
        hideStatusBar()
        
        // 初始化色彩调节工具
        colorHelper = ColorAdjustmentHelper(requireContext())
        
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
    
    /**
     * 隐藏系统状态栏
     */
    private fun hideStatusBar() {
        val window = requireActivity().window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.statusBars())
    }
    
    /**
     * 显示系统状态栏
     */
    private fun showStatusBar() {
        val window = requireActivity().window
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.show(WindowInsetsCompat.Type.statusBars())
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
        // 返回按钮点击事件 - 显示状态栏并返回
        binding.buttonBack.setOnClickListener {
            showStatusBar()
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
        
        // 色彩调节 - 进入色彩调节模式
        binding.toolColor.setOnClickListener {
            enterColorAdjustMode()
        }
        
        // 人像美化
        binding.toolBeauty.setOnClickListener {
            Toast.makeText(requireContext(), "Portrait Beauty", Toast.LENGTH_SHORT).show()
        }
        
        // 裁剪旋转 - 进入裁剪模式
        binding.toolCrop.setOnClickListener {
            enterCropMode()
        }
        
        // 换天空
        binding.toolSky.setOnClickListener {
            Toast.makeText(requireContext(), "Sky Replacement", Toast.LENGTH_SHORT).show()
        }
        
        // 衣物美化
        binding.toolClothes.setOnClickListener {
            Toast.makeText(requireContext(), "Clothing Beauty", Toast.LENGTH_SHORT).show()
        }
        
        // 上传到 GitHub
        binding.toolUpload.setOnClickListener {
            uploadToGitHub()
        }
        
        // 裁剪取消按钮
        binding.buttonCropCancel.setOnClickListener {
            exitCropMode(false)
        }
        
        // 裁剪确认按钮
        binding.buttonCropConfirm.setOnClickListener {
            exitCropMode(true)
        }
        
        // 色彩调节取消按钮
        binding.buttonColorCancel.setOnClickListener {
            exitColorAdjustMode(false)
        }
        
        // 色彩调节确认按钮
        binding.buttonColorConfirm.setOnClickListener {
            exitColorAdjustMode(true)
        }
        
        // 设置色彩调节滑块监听器
        setupColorSeekBars()
    }
    
    /**
     * 设置色彩调节滑块监听器
     */
    private fun setupColorSeekBars() {
        // 亮度滑块
        binding.seekbarBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    colorHelper.setBrightness(progress)
                    binding.textBrightnessValue.text = "${progress - 100}"
                    applyColorAdjustment()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 对比度滑块
        binding.seekbarContrast.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    colorHelper.setContrast(progress)
                    binding.textContrastValue.text = "${progress - 100}"
                    applyColorAdjustment()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 饱和度滑块
        binding.seekbarSaturation.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    colorHelper.setSaturation(progress)
                    binding.textSaturationValue.text = "${progress - 100}"
                    applyColorAdjustment()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    /**
     * 应用色彩调节到图片
     * 【注意】必须在主线程调用，因为 GPUImage 需要 GL 上下文
     */
    private fun applyColorAdjustment() {
        val sourceBitmap = originalBitmap ?: return
        
        // 在主线程处理图片（GPUImage 需要 GL 上下文）
        val result = colorHelper.applyFilter(sourceBitmap)
        processedBitmap = result
        binding.imagePreview.setImageBitmap(result)
    }
    
    /**
     * 进入裁剪模式
     * 
     * 【功能说明】
     * 1. 重置裁剪区域为图片的 4/5，居中显示
     * 2. 显示裁剪覆盖层
     * 3. 隐藏主工具栏，显示裁剪工具栏
     * 4. 隐藏水印和对比按钮
     */
    private fun enterCropMode() {
        // 显示裁剪覆盖层并重置裁剪区域
        binding.cropOverlay.visibility = View.VISIBLE
        binding.cropOverlay.resetCropRect()
        
        // 隐藏主工具栏，显示裁剪工具栏
        binding.mainToolbar.visibility = View.GONE
        binding.cropToolbar.visibility = View.VISIBLE
        
        // 隐藏水印和对比按钮
        binding.watermarkOverlay.visibility = View.GONE
        binding.compareButton.visibility = View.GONE
    }
    
    /**
     * 退出裁剪模式
     * 
     * @param applyCrop 是否应用裁剪
     */
    private fun exitCropMode(applyCrop: Boolean) {
        if (applyCrop) {
            // 应用裁剪
            applyCropToImage()
        }
        
        // 隐藏裁剪覆盖层
        binding.cropOverlay.visibility = View.GONE
        
        // 显示主工具栏，隐藏裁剪工具栏
        binding.mainToolbar.visibility = View.VISIBLE
        binding.cropToolbar.visibility = View.GONE
        
        // 显示水印和对比按钮
        binding.watermarkOverlay.visibility = View.VISIBLE
        binding.compareButton.visibility = View.VISIBLE
    }
    
    /**
     * 进入色彩调节模式
     * 
     * 【功能说明】
     * 1. 显示色彩调节工具栏
     * 2. 隐藏主工具栏
     * 3. 隐藏水印和对比按钮
     */
    private fun enterColorAdjustMode() {
        isColorAdjustMode = true
        
        // 隐藏主工具栏，显示色彩调节工具栏
        binding.mainToolbar.visibility = View.GONE
        binding.colorToolbar.visibility = View.VISIBLE
        
        // 隐藏水印和对比按钮
        binding.watermarkOverlay.visibility = View.GONE
        binding.compareButton.visibility = View.GONE
    }
    
    /**
     * 退出色彩调节模式
     * 
     * @param apply 是否应用色彩调节
     */
    private fun exitColorAdjustMode(apply: Boolean) {
        isColorAdjustMode = false
        
        if (!apply) {
            // 取消调节，恢复原始图片
            colorHelper.reset()
            originalBitmap?.let {
                processedBitmap = null
                binding.imagePreview.setImageBitmap(it)
            }
            // 重置滑块
            binding.seekbarBrightness.progress = 100
            binding.seekbarContrast.progress = 100
            binding.seekbarSaturation.progress = 100
            binding.textBrightnessValue.text = "0"
            binding.textContrastValue.text = "0"
            binding.textSaturationValue.text = "0"
        }
        
        // 显示主工具栏，隐藏色彩调节工具栏
        binding.mainToolbar.visibility = View.VISIBLE
        binding.colorToolbar.visibility = View.GONE
        
        // 显示水印和对比按钮
        binding.watermarkOverlay.visibility = View.VISIBLE
        binding.compareButton.visibility = View.VISIBLE
    }
    
    /**
     * 应用裁剪到图片
     * 
     * 【功能说明】
     * 1. 获取裁剪区域（相对坐标 0-1）
     * 2. 计算实际像素坐标
     * 3. 裁剪图片
     * 4. 更新显示
     */
    private fun applyCropToImage() {
        val currentBitmap = processedBitmap ?: originalBitmap ?: return
        val cropRect = binding.cropOverlay.getCropRect()
        
        // 计算实际像素坐标
        val x = (cropRect.left * currentBitmap.width).toInt()
        val y = (cropRect.top * currentBitmap.height).toInt()
        val width = ((cropRect.right - cropRect.left) * currentBitmap.width).toInt()
        val height = ((cropRect.bottom - cropRect.top) * currentBitmap.height).toInt()
        
        // 边界检查
        val safeX = x.coerceIn(0, currentBitmap.width - 1)
        val safeY = y.coerceIn(0, currentBitmap.height - 1)
        val safeWidth = width.coerceIn(1, currentBitmap.width - safeX)
        val safeHeight = height.coerceIn(1, currentBitmap.height - safeY)
        
        // 裁剪图片
        val croppedBitmap = Bitmap.createBitmap(currentBitmap, safeX, safeY, safeWidth, safeHeight)
        
        // 更新图片
        processedBitmap = croppedBitmap
        binding.imagePreview.setImageBitmap(croppedBitmap)
        
        Toast.makeText(requireContext(), "Image cropped", Toast.LENGTH_SHORT).show()
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
    
    // ==================== 保存功能 ====================
    
    /**
     * 保存照片 - 将照片保存到系统相册
     * 
     * 【功能说明】
     * 1. 获取当前显示的图片（处理后的或原始的）
     * 2. 使用 MediaStore API 保存到系统相册
     * 3. 显示保存结果
     * 
     * @param bitmap 要保存的Bitmap
     */
    private fun savePhoto(bitmap: Bitmap) {
        lifecycleScope.launch {
            val uri = saveToGallery(bitmap)
            if (uri != null) {
                Toast.makeText(requireContext(), "Photo saved to gallery", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to save photo", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 保存照片到系统相册
     * 
     * @param bitmap 要保存的 Bitmap
     * @return 照片的 content:// URI，失败返回 null
     */
    private suspend fun saveToGallery(bitmap: Bitmap): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "IMG_$timeStamp.jpg"
                
                val contentValues = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MyPx")
                    }
                }
                
                val uri = requireContext().contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                
                uri?.let { imageUri ->
                    requireContext().contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    }
                }
                
                uri
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save photo to gallery", e)
                null
            }
        }
    }
    
    // ==================== GitHub 上传 ====================
    
    /**
     * 上传图片到 GitHub
     * 
     * 【功能说明】
     * 1. 获取当前显示的图片（处理后的或原始的）
     * 2. 将 Bitmap 转换为字节数组
     * 3. 调用 GitHubUploadService 上传
     * 4. 显示上传结果
     */
    private fun uploadToGitHub() {
        val token = com.mary.mypx.feature.camera.BuildConfig.GITHUB_TOKEN
        if (token.isEmpty()) {
            Toast.makeText(requireContext(), "GitHub token not configured", Toast.LENGTH_LONG).show()
            return
        }
        
        // 获取当前显示的图片
        val bitmap = processedBitmap ?: originalBitmap
        if (bitmap == null) {
            Toast.makeText(requireContext(), "No image to upload", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 显示上传中提示
        val progressDialog = android.app.ProgressDialog(requireContext()).apply {
            setMessage("Uploading to GitHub...")
            setCancelable(false)
            show()
        }
        
        lifecycleScope.launch {
            try {
                // 将 Bitmap 转换为字节数组
                val result = withContext(Dispatchers.IO) {
                    try {
                        val outputStream = java.io.ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                        val imageBytes = outputStream.toByteArray()
                        // 上传到 GitHub
                        val service =
                            com.mary.mypx.feature.camera.network.GitHubUploadService(token)
                        service.uploadImage(imageBytes)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
                result.fold(
                    onSuccess = { url ->
                        Log.d(TAG, "Upload successful: $url")
                        Toast.makeText(
                            requireContext(),
                            "Upload successful!",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Upload failed", error)
                        Toast.makeText(
                            requireContext(),
                            "Upload failed: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Upload error", e)
                Toast.makeText(
                    requireContext(),
                    "Upload error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progressDialog.dismiss()
            }
        }
    }
    
    // ==================== 生命周期 ====================
    
    /**
     * 销毁视图 - 清理资源
     * 
     * 【功能说明】
     * 1. 显示系统状态栏
     * 2. 置空绑定，避免内存泄漏
     */
    override fun onDestroyView() {
        // 显示系统状态栏
        showStatusBar()
        
        super.onDestroyView()
        // 置空绑定，避免内存泄漏
        _binding = null
    }
}
