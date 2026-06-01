package com.mary.mypx.domain.model

/**
 * 性能指标数据类 - 用于监控应用性能
 * 
 * 【为什么需要性能监控？】
 * 1. 用户体验：帧率过低会导致卡顿，内存过高会导致OOM崩溃
 * 2. 性能优化：通过监控数据找出性能瓶颈
 * 3. 面试展示：展示对性能优化的理解和实践
 * 
 * 【性能指标说明】
 * - FPS：每秒帧数，衡量流畅度。相机预览需要30fps+才流畅
 * - 推理耗时：AI模型处理一张图片的时间，需要<100ms才不影响体验
 * - 内存占用：当前使用的内存，过高会导致OOM
 * - 最大内存：设备分配给应用的最大内存
 * 
 * 【使用场景】
 * ```kotlin
 * // 在UI上显示性能指标
 * viewModel.performanceMetrics.observe(this) { metrics ->
 *     fpsText.text = "FPS: ${metrics.fps}"
 *     inferenceText.text = "推理: ${metrics.inferenceTimeMs}ms"
 *     memoryText.text = "内存: ${metrics.memoryUsageMB}MB"
 * }
 * ```
 * 
 * @param fps 帧率（Frames Per Second），理想值30fps+，范围通常0-60
 * @param inferenceTimeMs AI推理耗时（毫秒），理想值<100ms
 * @param memoryUsageMB 当前内存使用量（MB），用于监控内存泄漏
 * @param maxMemoryMB 应用最大可用内存（MB），用于计算内存使用率
 */
data class PerformanceMetrics(
    val fps: Float = 0f,           // 帧率
    val inferenceTimeMs: Long = 0L, // AI推理耗时（毫秒）
    val memoryUsageMB: Float = 0f,  // 当前内存使用（MB）
    val maxMemoryMB: Float = 0f     // 最大可用内存（MB）
)
