package com.mary.mypx.sdk

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 相机管理器 - 封装CameraX API的核心类
 * 
 * 【SDK设计原则】
 * 这个类是camera-sdk模块的核心，它：
 * 1. 封装复杂的CameraX API，提供简单的接口
 * 2. 管理相机的生命周期
 * 3. 处理相机的配置和状态
 * 
 * 【为什么封装CameraX？】
 * CameraX虽然比Camera2简单，但仍有学习成本：
 * 1. 需要理解Provider、UseCase等概念
 * 2. 需要处理生命周期绑定
 * 3. 需要管理多个UseCase（预览、拍照、分析）
 * 封装后，调用者只需要调用简单的方法即可。
 * 
 * 【单线程执行器】
 * 使用单线程执行器处理相机操作，好处是：
 * 1. 避免并发问题：相机操作不适合并发
 * 2. 保证顺序：操作按顺序执行
 * 3. 简化逻辑：不需要处理锁和同步
 * 
 * @param context Android上下文，用于获取系统服务和资源
 */
class CameraManager(private val context: Context) {
    
    // ==================== 相机核心组件 ====================
    
    /**
     * 相机提供者 - 管理相机实例的创建和生命周期
     * 
     * ProcessCameraProvider是CameraX的核心类，它：
     * 1. 提供相机实例
     * 2. 管理相机生命周期
     * 3. 处理UseCase的绑定
     */
    private var cameraProvider: ProcessCameraProvider? = null
    
    /**
     * 图像捕获器 - 用于拍照
     * 
     * ImageCapture是拍照的UseCase，支持：
     * 1. 拍照并保存到文件
     * 2. 拍照并返回Bitmap
     * 3. 闪光灯控制
     */
    private var imageCapture: ImageCapture? = null
    
    /**
     * 图像分析器 - 用于实时分析相机画面
     * 
     * ImageAnalysis是图像分析的UseCase，用于：
     * 1. 实时获取相机画面
     * 2. 应用AI滤镜
     * 3. 性能监控
     */
    private var imageAnalysis: ImageAnalysis? = null
    
    /**
     * 相机实例 - 代表当前绑定的相机
     */
    private var camera: androidx.camera.core.Camera? = null
    
    /**
     * 预览器 - 用于显示相机画面
     * 
     * Preview是预览的UseCase，将相机画面显示到PreviewView上
     */
    private var preview: Preview? = null
    
    // ==================== 执行器和配置 ====================
    
    /**
     * 相机执行器 - 在后台线程处理相机操作
     * 
     * 使用单线程执行器的原因：
     * 1. 相机操作不适合并发
     * 2. 图像分析需要按顺序处理
     * 3. 避免线程安全问题
     */
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    /**
     * 当前摄像头朝向 - 前置或后置
     * 
     * 默认使用后置摄像头
     */
    private var currentLensFacing = CameraSelector.LENS_FACING_BACK
    
    /**
     * 当前闪光灯模式 - 开启或关闭
     * 
     * 默认关闭闪光灯
     */
    private var currentFlashMode = ImageCapture.FLASH_MODE_OFF
    
    // ==================== 公开方法 ====================
    
    /**
     * 开始相机预览 - 启动相机并显示画面
     * 
     * 【功能说明】
     * 初始化相机，绑定所有UseCase（预览、拍照、分析），开始显示实时画面。
     * 这是相机操作的入口方法，调用后相机开始工作。
     * 
     * 【参数说明】
     * @param lifecycleOwner 生命周期所有者（通常是Activity或Fragment）
     *                       CameraX会自动管理相机的生命周期：
     *                       - 当Activity暂停时，相机自动暂停
     *                       - 当Activity销毁时，相机自动释放
     * @param previewView 预览视图，用于显示相机画面
     *                    这是一个自定义的SurfaceView，专门用于显示相机预览
     * @param onImageAnalyzed 图像分析回调（可选）
     *                        每收到一帧画面就会调用这个回调
     *                        用于实时AI处理或性能监控
     * 
     * 【执行流程】
     * 1. 获取CameraProvider实例（异步）
     * 2. 创建所有UseCase（预览、拍照、分析）
     * 3. 绑定UseCase到生命周期
     * 4. 开始相机预览
     * 
     * 【为什么是异步的？】
     * CameraProvider.getInstance()是异步操作，因为：
     * 1. 相机硬件初始化需要时间
     * 2. 需要检查相机权限
     * 3. 需要选择合适的相机设备
     */
    fun startPreview(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onImageAnalyzed: ((androidx.camera.core.ImageProxy) -> Unit)? = null
    ) {
        // 获取CameraProvider实例
        // 这是一个异步操作，通过addListener监听完成事件
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        // 监听初始化完成事件
        // 当CameraProvider准备好后，执行相机绑定
        cameraProviderFuture.addListener({
            // 获取CameraProvider实例
            cameraProvider = cameraProviderFuture.get()
            
            // ==================== 创建预览UseCase ====================
            preview = Preview.Builder()
                .build()
                .also {
                    // 将预览连接到PreviewView
                    // 这样相机画面就会显示在屏幕上
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            
            // ==================== 创建拍照UseCase ====================
            imageCapture = ImageCapture.Builder()
                // 使用最高质量模式（而不是最快速度模式）
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                // 设置闪光灯模式
                .setFlashMode(currentFlashMode)
                .build()
            
            // ==================== 创建图像分析UseCase ====================
            imageAnalysis = ImageAnalysis.Builder()
                // 设置分析分辨率为720p
                // 分辨率越低，处理速度越快
                .setTargetResolution(Size(1280, 720))
                // 背压策略：只保留最新的帧
                // 如果处理速度跟不上，丢弃旧帧，保证实时性
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    // 设置图像分析器
                    // 使用相机执行器在后台线程处理
                    onImageAnalyzed?.let { callback ->
                        analysis.setAnalyzer(cameraExecutor, callback)
                    }
                }
            
            // ==================== 选择摄像头 ====================
            val cameraSelector = CameraSelector.Builder()
                // 要求使用指定朝向的摄像头
                .requireLensFacing(currentLensFacing)
                .build()
            
            // ==================== 绑定所有UseCase ====================
            try {
                // 先解绑所有之前的UseCase
                // 这是必要的，否则会报错
                cameraProvider?.unbindAll()
                
                // 将所有UseCase绑定到生命周期
                // CameraX会自动管理它们的生命周期
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,      // 生命周期所有者
                    cameraSelector,      // 摄像头选择器
                    preview,             // 预览UseCase
                    imageCapture,        // 拍照UseCase
                    imageAnalysis        // 分析UseCase
                )
            } catch (e: Exception) {
                // 绑定失败，打印错误信息
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context)) // 在主线程执行回调
    }
    
    /**
     * 拍照 - 捕获当前相机画面
     * 
     * 【功能说明】
     * 触发相机拍摄一张照片，并保存到设备存储。
     * 保存后照片会出现在系统相册中。
     * 
     * 【参数说明】
     * @param onImageCaptured 拍照成功回调
     *                        返回ImageCapture.OutputFileResults，包含保存的URI
     * @param onError 拍照失败回调
     *                返回异常信息，用于错误处理
     * 
     * 【保存位置】
     * 照片保存到 Pictures/MyPx 目录下，文件名格式：
     * IMG_时间戳.jpg（如 IMG_1717234567890.jpg）
     */
    fun takePhoto(onImageCaptured: (ImageCapture.OutputFileResults) -> Unit, onError: (Exception) -> Unit) {
        // 检查imageCapture是否已初始化
        val imageCapture = imageCapture ?: return
        
        // 创建保存照片的元数据
        val contentValues = android.content.ContentValues().apply {
            // 文件名：IMG_时间戳.jpg
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
            // MIME类型：JPEG图像
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            // Android 10+使用RELATIVE_PATH指定保存位置
            if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.P) {
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyPx")
            }
        }
        
        // 创建输出选项
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()
        
        // 执行拍照
        imageCapture.takePicture(
            outputOptions,  // 输出选项
            ContextCompat.getMainExecutor(context),  // 在主线程执行回调
            object : ImageCapture.OnImageSavedCallback {
                // 拍照成功
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onImageCaptured(outputFileResults)
                }
                
                // 拍照失败
                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            }
        )
    }
    
    /**
     * 切换摄像头 - 在前置和后置摄像头之间切换
     * 
     * 【功能说明】
     * 切换当前使用的摄像头。调用后需要重新调用startPreview才能生效。
     * 
     * 【使用场景】
     * 用户点击切换摄像头按钮时调用
     */
    fun switchCamera() {
        // 切换摄像头朝向
        currentLensFacing = if (currentLensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT  // 切换到前置
        } else {
            CameraSelector.LENS_FACING_BACK   // 切换到后置
        }
    }
    
    /**
     * 切换闪光灯 - 开启或关闭闪光灯
     * 
     * 【功能说明】
     * 切换闪光灯的开关状态。只在使用后置摄像头时有效。
     * 
     * 【使用场景】
     * 用户点击闪光灯按钮时调用
     */
    fun toggleFlash() {
        // 切换闪光灯模式
        currentFlashMode = if (currentFlashMode == ImageCapture.FLASH_MODE_OFF) {
            ImageCapture.FLASH_MODE_ON   // 开启闪光灯
        } else {
            ImageCapture.FLASH_MODE_OFF  // 关闭闪光灯
        }
        // 更新imageCapture的闪光灯设置
        imageCapture?.flashMode = currentFlashMode
    }
    
    /**
     * 检查闪光灯是否开启
     * 
     * 【返回值】
     * true: 闪光灯开启
     * false: 闪光灯关闭
     */
    fun isFlashOn(): Boolean = currentFlashMode == ImageCapture.FLASH_MODE_ON
    
    /**
     * 检查是否使用前置摄像头
     * 
     * 【返回值】
     * true: 使用前置摄像头
     * false: 使用后置摄像头
     */
    fun isUsingFrontCamera(): Boolean = currentLensFacing == CameraSelector.LENS_FACING_FRONT
    
    /**
     * 释放资源 - 清理相机占用的资源
     * 
     * 【功能说明】
     * 释放相机占用的所有资源，包括：
     * 1. 关闭执行器
     * 2. 解绑所有UseCase
     * 3. 释放相机硬件
     * 
     * 【调用时机】
     * 在Activity或Fragment销毁时调用，避免内存泄漏
     */
    fun release() {
        // 关闭执行器
        cameraExecutor.shutdown()
        // 解绑所有UseCase
        cameraProvider?.unbindAll()
    }
}
