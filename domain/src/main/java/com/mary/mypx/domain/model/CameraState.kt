package com.mary.mypx.domain.model

/**
 * 相机状态密封类 - 使用密封类实现状态机模式
 * 
 * 【为什么用密封类？】
 * 密封类（sealed class）是一种特殊的抽象类，它限制了子类的继承范围。
 * 使用密封类的好处：
 * 1. 类型安全：when表达式可以检查所有可能的状态，编译器会确保没有遗漏
 * 2. 状态明确：所有可能的状态都在一个地方定义，便于理解和维护
 * 3. 不可变性：每个状态都是单例或不可变数据，线程安全
 * 
 * 【状态机模式】
 * 相机功能可以抽象为一个状态机，每个状态代表相机的一种工作状态：
 * - 初始化 → 预览 → 拍照 → 处理 → 预览（循环）
 * - 任何状态都可能进入错误状态
 * 
 * 【使用示例】
 * ```kotlin
 * when (cameraState) {
 *     is CameraState.Initializing -> showLoading()
 *     is CameraState.Preview -> showPreview()
 *     is CameraState.TakingPhoto -> showCaptureAnimation()
 *     is CameraState.Processing -> showProgress(state.progress)
 *     is CameraState.Error -> showError(state.message)
 * }
 * ```
 */
sealed class CameraState {
    
    /**
     * 初始化状态 - 相机正在启动
     * 
     * 当相机刚打开或切换摄像头时进入此状态。
     * 此时应该显示加载动画，禁用拍照按钮。
     * 
     * 【触发条件】
     * - 应用首次打开相机
     * - 用户切换前后摄像头
     * - 从后台返回相机界面
     */
    object Initializing : CameraState()
    
    /**
     * 预览状态 - 相机正常预览中
     * 
     * 这是相机的主要工作状态，用户可以看到实时画面。
     * 此时应该启用拍照按钮和滤镜选择。
     * 
     * 【触发条件】
     * - 初始化完成后自动进入
     * - 拍照完成后返回
     * - 处理完成后返回
     */
    object Preview : CameraState()
    
    /**
     * 拍照状态 - 正在拍摄照片
     * 
     * 用户点击拍照按钮后进入此状态。
     * 此时应该显示拍照动画，禁用所有按钮防止重复操作。
     * 
     * 【触发条件】
     * - 用户点击拍照按钮
     * 
     * 【持续时间】
     * 通常很短（毫秒级），取决于设备性能
     */
    object TakingPhoto : CameraState()
    
    /**
     * 处理状态 - 正在处理照片
     * 
     * 照片拍摄完成后，如果需要AI处理，会进入此状态。
     * 可以显示处理进度，让用户知道处理进展。
     * 
     * @param progress 处理进度，范围0.0到1.0，默认为0
     * 
     * 【触发条件】
     * - 拍照完成后需要AI处理时
     * - 用户选择滤镜后需要处理时
     * 
     * 【为什么用data class？】
     * 因为需要携带进度数据，每次进度变化都是一个新的状态实例。
     */
    data class Processing(val progress: Float = 0f) : CameraState()
    
    /**
     * 错误状态 - 相机出现异常
     * 
     * 当相机操作失败时进入此状态，包含错误信息。
     * 应该显示错误提示，并提供重试选项。
     * 
     * @param message 错误信息，用于显示给用户或记录日志
     * 
     * 【触发条件】
     * - 相机权限被拒绝
     * - 相机硬件不可用
     * - 拍照失败
     * - AI处理失败
     * 
     * 【错误处理策略】
     * 1. 显示用户友好的错误信息
     * 2. 记录详细错误日志用于调试
     * 3. 提供重试按钮让用户恢复
     */
    data class Error(val message: String) : CameraState()
}
