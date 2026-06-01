package com.mary.mypx.domain.repository

import com.mary.mypx.domain.model.FilterType
import com.mary.mypx.domain.model.Photo

/**
 * 相机仓库接口 - 定义相机数据操作的契约
 * 
 * 【Repository模式的作用】
 * Repository是领域层和数据层之间的桥梁，它：
 * 1. 隐藏数据来源细节（本地存储、网络、相机硬件等）
 * 2. 提供统一的数据访问接口
 * 3. 便于单元测试（可以用Mock实现替代真实实现）
 * 
 * 【为什么用接口而不是具体类？】
 * 这是Clean Architecture的核心原则：依赖倒置
 * - 领域层定义接口（做什么）
 * - 数据层实现接口（怎么做）
 * - 领域层不依赖具体实现，便于替换和测试
 * 
 * 【suspend函数】
 * 所有方法都是suspend函数，因为：
 * 1. 相机操作是耗时操作（拍照、保存文件）
 * 2. AI处理是CPU密集型操作
 * 3. 使用协程可以避免阻塞主线程
 * 
 * 【Result包装】
 * 使用Kotlin的Result类型包装返回值，好处是：
 * 1. 明确表示操作可能失败
 * 2. 强制调用者处理成功和失败两种情况
 * 3. 避免异常传播，更函数式的错误处理
 */
interface CameraRepository {
    
    /**
     * 拍照 - 捕获当前相机画面
     * 
     * 【功能说明】
     * 触发相机拍摄一张照片，返回照片信息。
     * 这是同步操作，会等待拍照完成。
     * 
     * 【返回值】
     * - 成功：返回Photo对象，包含照片URI等信息
     * - 失败：返回Result.failure，包含错误信息
     * 
     * 【可能的失败原因】
     * - 相机权限被拒绝
     * - 存储空间不足
     * - 相机硬件错误
     */
    suspend fun takePhoto(): Result<Photo>
    
    /**
     * 处理图像 - 对图像应用AI滤镜
     * 
     * 【功能说明】
     * 将原始图像数据应用指定的滤镜效果，返回处理后的图像数据。
     * 这是CPU密集型操作，可能需要较长时间。
     * 
     * 【参数说明】
     * @param imageData 原始图像的字节数组（JPEG格式）
     * @param filterType 要应用的滤镜类型
     * 
     * 【返回值】
     * - 成功：返回处理后的图像字节数组
     * - 失败：返回Result.failure
     * 
     * 【性能考虑】
     * - 处理时间应该<100ms，否则会影响用户体验
     * - 可以使用GPU加速（TensorFlow Lite GPU Delegate）
     * - 大图像可以先缩小再处理，提高速度
     */
    suspend fun processImage(imageData: ByteArray, filterType: FilterType): Result<ByteArray>
    
    /**
     * 保存照片 - 将照片保存到持久化存储
     * 
     * 【功能说明】
     * 将图像数据保存到设备存储，返回保存后的照片信息。
     * 保存后照片会出现在系统相册中。
     * 
     * 【参数说明】
     * @param imageData 要保存的图像字节数组
     * 
     * 【返回值】
     * - 成功：返回保存后的Photo对象，包含新的URI
     * - 失败：返回Result.failure
     * 
     * 【存储位置】
     * 通常保存到 Pictures/MyPx 目录下
     */
    suspend fun savePhoto(imageData: ByteArray): Result<Photo>
    
    /**
     * 获取性能指标 - 返回当前的性能监控数据
     * 
     * 【功能说明】
     * 返回当前的性能指标，包括帧率、推理耗时、内存使用等。
     * 用于在UI上显示性能监控信息。
     * 
     * 【返回值】
     * 返回PerformanceMetrics对象，包含各项性能指标
     * 
     * 【同步方法】
     * 这是普通函数而不是suspend函数，因为：
     * 1. 只是读取内存中的数据，不涉及IO操作
     * 2. 需要在主线程调用以更新UI
     */
    fun getPerformanceMetrics(): PerformanceMetrics
}

/**
 * 性能指标数据类 - 存储性能监控数据
 * 
 * 【字段说明】
 * @param fps 帧率，衡量UI流畅度
 * @param inferenceTimeMs AI推理耗时（毫秒）
 * @param memoryUsageMB 内存使用量（MB）
 */
data class PerformanceMetrics(
    val fps: Float,              // 帧率
    val inferenceTimeMs: Long,   // 推理耗时（毫秒）
    val memoryUsageMB: Float     // 内存使用（MB）
)
