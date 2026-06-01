package com.mary.mypx.feature.camera.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mary.mypx.domain.model.CameraState
import com.mary.mypx.domain.model.FilterType
import com.mary.mypx.domain.model.Photo
import com.mary.mypx.domain.repository.CameraRepository
import com.mary.mypx.domain.repository.PerformanceMetrics
import com.mary.mypx.sdk.CameraManager
import com.mary.mypx.sdk.ImageProcessor
import com.mary.mypx.sdk.PerformanceMonitor
import com.mary.mypx.sdk.TFLiteImageProcessor
import com.mary.mypx.sdk.FilterType as SdkFilterType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 相机视图模型 - 管理相机页面的状态和业务逻辑
 * 
 * 【MVVM架构中的角色】
 * ViewModel是MVVM架构的核心组件，它：
 * 1. 管理UI状态（相机状态、滤镜选择等）
 * 2. 处理用户交互（拍照、切换摄像头等）
 * 3. 协调SDK和Domain层完成业务逻辑
 * 4. 在配置变更（如旋转屏幕）时保持状态
 * 
 * 【为什么继承AndroidViewModel？】
 * 继承AndroidViewModel而不是ViewModel，因为：
 * 1. 需要Application上下文来初始化SDK组件
 * 2. CameraManager、ImageProcessor等需要Context
 * 3. AndroidViewModel持有Application引用，生命周期与应用一致
 * 
 * 【状态管理】
 * 使用StateFlow和LiveData管理状态：
 * - StateFlow：用于可观察的状态（如相机状态、当前滤镜）
 * - LiveData：用于一次性事件（如拍照结果、错误信息）
 * 
 * 【单向数据流】
 * 遵循单向数据流原则：
 * - 用户操作 → ViewModel → SDK/Domain → 更新状态 → UI自动更新
 * - UI不直接修改状态，只通过ViewModel的方法
 * 
 * @param application Android应用上下文
 */
class CameraViewModel(application: Application) : AndroidViewModel(application) {
    
    // ==================== SDK组件 ====================
    
    /**
     * 相机管理器 - 封装CameraX API
     * 
     * 负责：
     * 1. 相机预览
     * 2. 拍照
     * 3. 前后置切换
     * 4. 闪光灯控制
     */
    private val cameraManager = CameraManager(application)
    
    /**
     * 图像处理器 - AI图像处理
     * 
     * 负责：
     * 1. 美颜滤镜
     * 2. 超分辨率
     * 3. 夜景增强
     */
    private val imageProcessor: ImageProcessor = TFLiteImageProcessor(application)
    
    /**
     * 性能监控器 - 监控应用性能
     * 
     * 负责：
     * 1. 帧率监控
     * 2. 推理耗时监控
     * 3. 内存使用监控
     */
    private val performanceMonitor = PerformanceMonitor()
    
    // ==================== 状态管理 ====================
    
    /**
     * 相机状态 - 使用StateFlow管理
     * 
     * 【为什么用StateFlow而不是LiveData？】
     * 1. StateFlow是协程友好的，可以在协程中收集
     * 2. StateFlow有初始值，不会为null
     * 3. StateFlow支持distinctUntilChanged，避免重复更新
     * 
     * 【状态变化流程】
     * Initializing → Preview → TakingPhoto → Preview（循环）
     * 任何状态都可能 → Error
     */
    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Initializing)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()
    
    /**
     * 当前选择的滤镜 - 使用StateFlow管理
     * 
     * 默认为NONE（无滤镜），用户可以在UI上选择不同的滤镜
     */
    private val _currentFilter = MutableStateFlow(FilterType.NONE)
    val currentFilter: StateFlow<FilterType> = _currentFilter.asStateFlow()
    
    /**
     * 拍照结果 - 使用LiveData管理
     * 
     * 【为什么用LiveData而不是StateFlow？】
     * 1. 拍照是一次性事件，不应该在配置变更后重新触发
     * 2. LiveData自动处理生命周期，避免内存泄漏
     * 3. 使用observeOnce可以确保只处理一次
     */
    private val _photoTaken = MutableLiveData<Photo?>()
    val photoTaken: LiveData<Photo?> = _photoTaken
    
    /**
     * 性能指标 - 使用LiveData管理
     * 
     * 定期更新，用于在UI上显示性能监控信息
     */
    private val _performanceMetrics = MutableLiveData<PerformanceMetrics>()
    val performanceMetrics: LiveData<PerformanceMetrics> = _performanceMetrics
    
    /**
     * 错误信息 - 使用LiveData管理
     * 
     * 当发生错误时设置，UI显示后应该清除
     */
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    // ==================== 状态缓存 ====================
    
    /**
     * 闪光灯状态缓存 - 避免频繁查询SDK
     */
    private var isFlashOn = false
    
    /**
     * 前置摄像头状态缓存 - 避免频繁查询SDK
     */
    private var isFrontCamera = false
    
    // ==================== 公开方法 ====================
    
    /**
     * 获取相机管理器 - 供Fragment调用
     * 
     * 【为什么暴露SDK组件？】
     * 为了简化代码，直接暴露SDK组件给Fragment使用。
     * 在更严格的架构中，应该通过UseCase封装。
     */
    fun getCameraManager() = cameraManager
    
    /**
     * 获取图像处理器 - 供Fragment调用
     */
    fun getImageProcessor() = imageProcessor
    
    /**
     * 获取性能监控器 - 供Fragment调用
     */
    fun getPerformanceMonitor() = performanceMonitor
    
    /**
     * 设置相机状态 - 更新当前相机状态
     * 
     * @param state 新的相机状态
     * 
     * 【使用场景】
     * 在Fragment中，当相机状态变化时调用此方法更新状态
     */
    fun setCameraState(state: CameraState) {
        _cameraState.value = state
    }
    
    /**
     * 设置当前滤镜 - 更新选择的滤镜类型
     * 
     * @param filterType 新的滤镜类型
     * 
     * 【使用场景】
     * 用户在UI上选择滤镜时调用
     */
    fun setFilter(filterType: FilterType) {
        _currentFilter.value = filterType
    }
    
    /**
     * 拍照 - 触发相机拍摄照片
     * 
     * 【功能说明】
     * 1. 设置状态为TakingPhoto
     * 2. 调用CameraManager拍照
     * 3. 拍照成功：更新photoTaken，状态回到Preview
     * 4. 拍照失败：更新error，状态设为Error
     * 
     * 【状态变化】
     * Preview → TakingPhoto → Preview（成功）
     * Preview → TakingPhoto → Error（失败）
     * 
     * 【使用场景】
     * 用户点击拍照按钮时调用
     */
    fun takePhoto() {
        // 设置状态为拍照中
        _cameraState.value = CameraState.TakingPhoto
        
        // 调用CameraManager拍照
        cameraManager.takePhoto(
            // 拍照成功回调
            onImageCaptured = { result ->
                val uri = result.savedUri
                if (uri != null) {
                    // 更新拍照结果
                    _photoTaken.value = Photo(uriString = uri.toString())
                    // 状态回到预览
                    _cameraState.value = CameraState.Preview
                }
            },
            // 拍照失败回调
            onError = { exception ->
                // 设置错误信息
                _error.value = exception.message
                // 设置错误状态
                _cameraState.value = CameraState.Error(exception.message ?: "Unknown error")
            }
        )
    }
    
    /**
     * 切换闪光灯 - 开启或关闭闪光灯
     * 
     * 【功能说明】
     * 1. 调用CameraManager切换闪光灯
     * 2. 更新本地状态缓存
     * 
     * 【使用场景】
     * 用户点击闪光灯按钮时调用
     */
    fun toggleFlash() {
        cameraManager.toggleFlash()
        isFlashOn = cameraManager.isFlashOn()
    }
    
    /**
     * 切换摄像头 - 在前置和后置摄像头之间切换
     * 
     * 【功能说明】
     * 1. 调用CameraManager切换摄像头
     * 2. 更新本地状态缓存
     * 
     * 【注意】
     * 切换摄像头后需要重新启动预览
     * 
     * 【使用场景】
     * 用户点击切换摄像头按钮时调用
     */
    fun switchCamera() {
        cameraManager.switchCamera()
        isFrontCamera = cameraManager.isUsingFrontCamera()
    }
    
    /**
     * 检查闪光灯是否开启
     * 
     * @return true: 闪光灯开启, false: 闪光灯关闭
     */
    fun isFlashOn() = isFlashOn
    
    /**
     * 检查是否使用前置摄像头
     * 
     * @return true: 前置摄像头, false: 后置摄像头
     */
    fun isFrontCamera() = isFrontCamera
    
    /**
     * 更新性能指标 - 从监控器获取最新数据
     * 
     * 【功能说明】
     * 从PerformanceMonitor获取最新的性能指标，
     * 更新到LiveData，供UI显示。
     * 
     * 【为什么用postValue而不是setValue？】
     * 因为这个方法可能在后台线程调用（如ImageAnalysis回调），
     * postValue可以在任何线程调用，会在主线程更新LiveData。
     * 
     * 【使用场景】
     * 在ImageAnalysis回调中调用，定期更新性能指标
     */
    fun updatePerformanceMetrics() {
        val fps = performanceMonitor.getCurrentFps()
        val inferenceTime = performanceMonitor.getLastInferenceTime()
        val memoryUsage = performanceMonitor.getMemoryUsage()
        
        // 使用postValue在后台线程安全更新
        _performanceMetrics.postValue(PerformanceMetrics(
            fps = fps,
            inferenceTimeMs = inferenceTime,
            memoryUsageMB = memoryUsage.getUsedMemoryMB()
        ))
    }
    
    /**
     * 清除错误信息 - 重置错误状态
     * 
     * 【使用场景】
     * 用户看到错误提示后，点击关闭或重试时调用
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * 清除拍照结果 - 重置拍照状态
     * 
     * 【使用场景】
     * 处理完拍照结果后调用，避免重复处理
     */
    fun clearPhoto() {
        _photoTaken.value = null
    }
    
    /**
     * 保存照片 - 将照片保存到设备存储
     * 
     * @param imageData 照片的字节数组
     * @return 保存结果
     * 
     * 【当前实现】
     * 返回模拟数据，实际项目中应该调用Repository保存
     */
    suspend fun savePhoto(imageData: ByteArray): Result<Photo> {
        // 模拟保存成功
        // 实际项目中应该调用：return cameraRepository.savePhoto(imageData)
        return Result.success(Photo(uriString = "saved_photo_${System.currentTimeMillis()}"))
    }
    
    /**
     * 转换滤镜类型 - 将Domain层的FilterType转换为SDK层的FilterType
     * 
     * 【为什么需要转换？】
     * Domain层和SDK层都有FilterType枚举，它们是不同的类型。
     * 这样设计的原因：
     * 1. SDK层可以独立于Domain层使用
     * 2. 避免层之间的直接依赖
     * 3. 符合Clean Architecture的依赖规则
     * 
     * @param domainFilterType Domain层的滤镜类型
     * @return SDK层的滤镜类型
     */
    fun getSdkFilterType(domainFilterType: FilterType): SdkFilterType {
        return when (domainFilterType) {
            FilterType.NONE -> SdkFilterType.NONE
            FilterType.BEAUTY -> SdkFilterType.BEAUTY
            FilterType.SUPER_RESOLUTION -> SdkFilterType.SUPER_RESOLUTION
            FilterType.NIGHT_MODE -> SdkFilterType.NIGHT_MODE
        }
    }
    
    /**
     * 清理资源 - 在ViewModel销毁时调用
     * 
     * 【功能说明】
     * 释放CameraManager和ImageProcessor占用的资源，
     * 避免内存泄漏。
     * 
     * 【调用时机】
     * ViewModel被系统销毁时自动调用
     * （如Activity被销毁且不会重建时）
     */
    override fun onCleared() {
        super.onCleared()
        // 释放相机资源
        cameraManager.release()
        // 释放图像处理器资源
        imageProcessor.release()
    }
}
