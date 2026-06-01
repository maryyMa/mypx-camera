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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 相机Fragment - 相机预览页面的UI控制器
 * 
 * 【MVVM架构中的角色】
 * Fragment是MVVM架构中的View层，它：
 * 1. 显示UI（相机预览、按钮、滤镜选择等）
 * 2. 接收用户交互（点击、滑动等）
 * 3. 观察ViewModel的状态变化并更新UI
 * 4. 不包含业务逻辑，只负责显示和交互
 * 
 * 【生命周期】
 * Fragment有严格的生命周期：
 * - onCreateView：创建视图
 * - onViewCreated：视图创建完成，初始化UI
 * - onResume：页面可见，开始相机预览
 * - onPause：页面不可见，暂停相机
 * - onDestroyView：销毁视图，清理资源
 * 
 * 【权限处理】
 * 相机需要CAMERA权限，使用新的ActivityResult API请求权限：
 * 1. 检查权限是否已授予
 * 2. 如果未授予，请求权限
 * 3. 处理权限请求结果
 * 
 * 【为什么使用ViewBinding？】
 * ViewBinding是类型安全的视图绑定：
 * 1. 编译时检查，避免空指针
 * 2. 比findViewById更高效
 * 3. 自动生成绑定类，减少样板代码
 */
class CameraFragment : Fragment() {
    
    // ==================== 视图绑定 ====================
    
    /**
     * 视图绑定 - 自动生成的绑定类
     * 
     * 【为什么用可空类型？】
     * 在onDestroyView后需要置空，避免内存泄漏
     * 
     * 【为什么用get()访问器？】
     * 使用非空的binding属性，避免每次使用都要判空
     */
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    
    // ==================== ViewModel ====================
    
    /**
     * 相机视图模型 - 使用by viewModels()委托
     * 
     * 【为什么用by viewModels()？】
     * 1. 延迟初始化：第一次访问时才创建
     * 2. 配置变更保持：旋转屏幕时不会重新创建
     * 3. 自动清理：ViewModel在Fragment销毁时自动清理
     */
    private val viewModel: CameraViewModel by viewModels()
    
    // ==================== 权限请求 ====================
    
    /**
     * 权限请求启动器 - 使用新的ActivityResult API
     * 
     * 【为什么用ActivityResultContracts？】
     * 旧的onRequestPermissionsResult已弃用，新的API：
     * 1. 类型安全
     * 2. 更简洁
     * 3. 支持更多类型的合约
     * 
     * 【回调说明】
     * isGranted: true=权限已授予, false=权限被拒绝
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限已授予，启动相机
            startCamera()
        } else {
            // 权限被拒绝，显示提示
            Toast.makeText(requireContext(), "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ==================== 生命周期方法 ====================
    
    /**
     * 创建视图 - 初始化视图绑定
     * 
     * 【功能说明】
     * 使用ViewBinding创建视图，替代传统的inflate方式
     * 
     * 【参数说明】
     * @param inflater 布局加载器
     * @param container 父容器
     * @param savedInstanceState 保存的实例状态
     * 
     * @return 创建的视图
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 使用ViewBinding创建视图
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    /**
     * 视图创建完成 - 初始化UI和观察者
     * 
     * 【功能说明】
     * 在视图创建完成后：
     * 1. 设置UI交互（按钮点击等）
     * 2. 检查相机权限
     * 3. 观察ViewModel的状态变化
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 设置UI交互
        setupUI()
        // 检查相机权限
        checkPermissions()
        // 观察ViewModel
        observeViewModel()
    }
    
    // ==================== UI设置 ====================
    
    /**
     * 设置UI - 配置所有UI组件的交互
     * 
     * 【功能说明】
     * 为所有按钮设置点击事件：
     * 1. 拍照按钮：触发拍照
     * 2. 闪光灯按钮：切换闪光灯
     * 3. 切换摄像头按钮：切换前后摄像头
     * 4. 滤镜选择按钮：选择不同的滤镜
     */
    private fun setupUI() {
        // 拍照按钮点击事件
        binding.buttonTakePhoto.setOnClickListener {
            viewModel.takePhoto()
        }
        
        // 闪光灯按钮点击事件
        binding.buttonFlash.setOnClickListener {
            viewModel.toggleFlash()
            updateFlashIcon()
        }
        
        // 切换摄像头按钮点击事件
        binding.buttonSwitchCamera.setOnClickListener {
            viewModel.switchCamera()
            // 重新启动相机（使用新的摄像头）
            startCamera()
        }
        
        // 滤镜选择按钮点击事件
        binding.chipNone.setOnClickListener { 
            viewModel.setFilter(FilterType.NONE)
            // 选择无滤镜时，隐藏滤镜预览，显示原始预览
            binding.filteredPreview.visibility = View.GONE
        }
        binding.chipBeauty.setOnClickListener { viewModel.setFilter(FilterType.BEAUTY) }
        binding.chipSuperRes.setOnClickListener { viewModel.setFilter(FilterType.SUPER_RESOLUTION) }
        binding.chipNight.setOnClickListener { viewModel.setFilter(FilterType.NIGHT_MODE) }
    }
    
    // ==================== 权限处理 ====================
    
    /**
     * 检查相机权限 - 检查并请求相机权限
     * 
     * 【功能说明】
     * 1. 检查CAMERA权限是否已授予
     * 2. 如果已授予，直接启动相机
     * 3. 如果未授予，请求权限
     * 
     * 【权限处理流程】
     * 检查权限 → 已授予 → 启动相机
     * 检查权限 → 未授予 → 请求权限 → 用户选择 → 启动相机/显示错误
     */
    private fun checkPermissions() {
        when {
            // 检查权限是否已授予
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // 权限已授予，启动相机
                startCamera()
            }
            else -> {
                // 请求权限
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    // ==================== 相机控制 ====================
    
    /**
     * 启动相机 - 初始化并开始相机预览
     * 
     * 【功能说明】
     * 1. 获取SDK组件（相机管理器、图像处理器、性能监控器）
     * 2. 启动相机预览
     * 3. 设置图像分析回调
     * 4. 更新相机状态
     * 
     * 【图像分析回调】
     * 每收到一帧画面就会调用这个回调，用于：
     * 1. 性能监控（FPS）
     * 2. AI处理（如果选择了滤镜）
     * 3. 推理耗时统计
     */
    /**
     * 启动相机 - 初始化并开始相机预览
     * 
     * 【功能说明】
     * 1. 获取SDK组件（相机管理器、图像处理器、性能监控器）
     * 2. 启动相机预览
     * 3. 设置图像分析回调，实现实时滤镜效果
     * 4. 更新相机状态
     * 
     * 【滤镜显示原理】
     * 
     * 方案1（当前实现）：覆盖层方案
     * ┌─────────────────────────────────────┐
     * │           PreviewView               │ ← 原始相机画面（底层）
     * │  ┌─────────────────────────────────┐│
     * │  │         ImageView               ││ ← 滤镜处理后的画面（上层）
     * │  │    (filtered_preview)           ││
     * │  └─────────────────────────────────┘│
     * └─────────────────────────────────────┘
     * 
     * 工作流程：
     * 1. ImageAnalysis 每秒分析 30 帧画面
     * 2. 每帧都经过滤镜处理
     * 3. 处理后的图像显示在 ImageView 上
     * 4. ImageView 覆盖在 PreviewView 上方
     * 5. 用户看到的是滤镜处理后的效果
     * 
     * 【性能优化】
     * 1. 只在选择滤镜时才进行处理
     * 2. 使用协程避免阻塞主线程
     * 3. 在后台线程执行图像处理
     * 4. 使用 postValue 避免线程问题
     * 
     * 【图像分析回调】
     * 每收到一帧画面就会调用这个回调，用于：
     * 1. 性能监控（FPS）
     * 2. AI处理（如果选择了滤镜）
     * 3. 推理耗时统计
     */
    private fun startCamera() {
        // 获取SDK组件
        val cameraManager = viewModel.getCameraManager()
        val imageProcessor = viewModel.getImageProcessor()
        val performanceMonitor = viewModel.getPerformanceMonitor()
        
        // 启动相机预览
        cameraManager.startPreview(
            lifecycleOwner = viewLifecycleOwner,
            previewView = binding.previewView,
            onImageAnalyzed = { imageProxy ->
                // 开始新帧的性能监控
                performanceMonitor.startFrame()
                
                // 获取当前选择的滤镜
                val currentFilter = viewModel.currentFilter.value
                
                // 【核心逻辑】根据是否选择滤镜，切换显示模式
                if (currentFilter != FilterType.NONE) {
                    // ==================== 选择了滤镜：显示滤镜处理后的图像 ====================
                    
                    // 开始推理计时
                    performanceMonitor.startInference()
                    
                    // 将ImageProxy转换为Bitmap
                    // ImageProxy是CameraX的图像格式，需要转换为Bitmap才能处理
                    val bitmap = imageProxyToBitmap(imageProxy)
                    bitmap?.let {
                        // 在协程中执行AI处理
                        // 使用lifecycleScope确保协程在Fragment销毁时自动取消
                        lifecycleScope.launch {
                            // 转换滤镜类型：Domain层 → SDK层
                            val sdkFilterType = viewModel.getSdkFilterType(currentFilter)
                            
                            // 【关键】应用滤镜处理
                            // 这里会调用TFLiteImageProcessor的process方法
                            // 对每个像素进行亮度、对比度等调整
                            val processedBitmap = imageProcessor.process(it, sdkFilterType)
                            
                            // 结束推理计时
                            performanceMonitor.endInference()
                            
                            // 【关键】在主线程更新UI，显示滤镜处理后的图像
                            // withContext(Dispatchers.Main) 确保在主线程执行UI操作
                            withContext(Dispatchers.Main) {
                                // 显示滤镜预览层
                                binding.filteredPreview.visibility = View.VISIBLE
                                // 设置滤镜处理后的图像
                                // 这样用户就能看到美颜/夜景增强等效果
                                binding.filteredPreview.setImageBitmap(processedBitmap)
                            }
                        }
                    }
                } else {
                    // ==================== 没有选择滤镜：显示原始相机画面 ====================
                    
                    // 在主线程更新UI
                    lifecycleScope.launch(Dispatchers.Main) {
                        // 隐藏滤镜预览层，显示原始预览
                        binding.filteredPreview.visibility = View.GONE
                    }
                }
                
                // 关闭ImageProxy，释放资源
                // 这是必须的，否则会导致内存泄漏
                imageProxy.close()
                
                // 更新性能指标
                viewModel.updatePerformanceMetrics()
            }
        )
        
        // 设置相机状态为预览
        viewModel.setCameraState(CameraState.Preview)
    }
    
    /**
     * 将ImageProxy转换为Bitmap
     * 
     * 【功能说明】
     * 将CameraX的ImageProxy格式转换为Android的Bitmap格式，
     * 以便进行图像处理。
     * 
     * 【参数说明】
     * @param imageProxy CameraX的图像代理
     * @return 转换后的Bitmap，如果转换失败返回null
     * 
     * 【转换原理】
     * 1. 获取图像的第一个平面（YUV格式的Y平面）
     * 2. 将缓冲区数据复制到字节数组
     * 3. 使用BitmapFactory解码为Bitmap
     */
    private fun imageProxyToBitmap(imageProxy: androidx.camera.core.ImageProxy): Bitmap? {
        // 获取图像的第一个平面
        val buffer = imageProxy.planes[0].buffer
        // 创建字节数组
        val bytes = ByteArray(buffer.remaining())
        // 复制数据
        buffer.get(bytes)
        // 解码为Bitmap
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    
    // ==================== 状态观察 ====================
    
    /**
     * 观察ViewModel - 监听状态变化并更新UI
     * 
     * 【功能说明】
     * 观察ViewModel中的各种状态，当状态变化时更新UI：
     * 1. 相机状态：控制预览的显示/隐藏
     * 2. 拍照结果：跳转到预览页面
     * 3. 性能指标：显示性能监控信息
     * 4. 错误信息：显示错误提示
     */
    private fun observeViewModel() {
        // 观察相机状态
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.cameraState.collectLatest { state ->
                when (state) {
                    is CameraState.Initializing -> {
                        // 初始化中，隐藏预览
                        binding.previewView.visibility = View.GONE
                    }
                    is CameraState.Preview -> {
                        // 预览中，显示预览
                        binding.previewView.visibility = View.VISIBLE
                    }
                    is CameraState.TakingPhoto -> {
                        // 拍照中，可以显示加载动画
                    }
                    is CameraState.Processing -> {
                        // 处理中，可以显示进度条
                    }
                    is CameraState.Error -> {
                        // 错误，显示错误提示
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        // 观察拍照结果
        viewModel.photoTaken.observe(viewLifecycleOwner) { photo ->
            photo?.let {
                // 创建导航参数
                val bundle = Bundle().apply {
                    putString("photoUriString", it.uriString)
                }
                // 导航到预览页面
                findNavController().navigate(R.id.action_camera_to_preview, bundle)
                // 清除拍照结果，避免重复处理
                viewModel.clearPhoto()
            }
        }
        
        // 观察性能指标
        viewModel.performanceMetrics.observe(viewLifecycleOwner) { metrics ->
            // 显示性能监控信息
            binding.textPerformance.text = String.format(
                "FPS: %.1f | Inference: %dms | Memory: %.1fMB",
                metrics.fps,
                metrics.inferenceTimeMs,
                metrics.memoryUsageMB
            )
        }
        
        // 观察错误信息
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                // 显示错误提示
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                // 清除错误信息
                viewModel.clearError()
            }
        }
    }
    
    // ==================== UI更新 ====================
    
    /**
     * 更新闪光灯图标 - 根据闪光灯状态更新图标
     * 
     * 【功能说明】
     * 根据闪光灯的开关状态，显示不同的图标
     */
    private fun updateFlashIcon() {
        val flashOn = viewModel.isFlashOn()
        binding.buttonFlash.setImageResource(
            if (flashOn) android.R.drawable.ic_menu_info_details
            else android.R.drawable.ic_menu_manage
        )
    }
    
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
