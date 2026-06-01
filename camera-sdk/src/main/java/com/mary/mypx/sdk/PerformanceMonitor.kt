package com.mary.mypx.sdk

import android.os.Debug
import java.util.concurrent.TimeUnit

/**
 * 性能监控器 - 实时监控应用性能指标
 * 
 * 【为什么需要性能监控？】
 * 1. 用户体验：帧率过低会导致卡顿，内存过高会导致OOM崩溃
 * 2. 性能优化：通过监控数据找出性能瓶颈
 * 3. 问题定位：当出现性能问题时，可以快速定位原因
 * 4. 面试展示：展示对性能优化的理解和实践
 * 
 * 【监控指标】
 * 1. FPS（帧率）：衡量UI流畅度，理想值30fps+
 * 2. 推理耗时：AI模型处理时间，理想值<100ms
 * 3. 内存使用：监控内存泄漏，避免OOM
 * 
 * 【使用场景】
 * ```kotlin
 * val monitor = PerformanceMonitor()
 * 
 * // 在图像分析回调中
 * monitor.startFrame()
 * // ... 处理图像 ...
 * monitor.startInference()
 * // ... AI推理 ...
 * monitor.endInference()
 * 
 * // 获取性能指标
 * val fps = monitor.getCurrentFps()
 * val inferenceTime = monitor.getLastInferenceTime()
 * val memory = monitor.getMemoryUsage()
 * ```
 */
class PerformanceMonitor {
    
    // ==================== FPS相关变量 ====================
    
    /**
     * 帧计数器 - 记录一段时间内处理的帧数
     * 
     * 每处理一帧就加1，当计算FPS后重置为0
     */
    private var frameCount = 0
    
    /**
     * 上次计算FPS的时间 - 用于计算时间间隔
     * 
     * 使用System.currentTimeMillis()获取当前时间（毫秒）
     */
    private var lastFpsTime = System.currentTimeMillis()
    
    /**
     * 当前帧率 - 计算得到的FPS值
     * 
     * 计算公式：帧数 * 1000 / 时间间隔（毫秒）
     */
    private var currentFps = 0f
    
    // ==================== 推理耗时相关变量 ====================
    
    /**
     * 推理开始时间 - 记录AI推理开始的时间戳
     * 
     * 使用System.nanoTime()获取高精度时间（纳秒）
     */
    private var inferenceStartTime = 0L
    
    /**
     * 上次推理耗时 - 记录最近一次AI推理的耗时（毫秒）
     * 
     * 计算公式：(结束时间 - 开始时间) / 1000000
     */
    private var lastInferenceTime = 0L
    
    // ==================== 公开方法 ====================
    
    /**
     * 开始新帧 - 在每帧处理开始时调用
     * 
     * 【功能说明】
     * 记录帧计数，当时间间隔>=1秒时计算FPS。
     * 应该在图像分析回调的开始处调用。
     * 
     * 【FPS计算原理】
     * FPS = 帧数 / 时间（秒）
     * 例如：30帧 / 1秒 = 30fps
     * 
     * 【为什么每秒计算一次？】
     * 1. 避免频繁计算影响性能
     * 2. 1秒内的FPS变化不会太大
     * 3. 便于在UI上显示
     */
    fun startFrame() {
        // 帧计数加1
        frameCount++
        
        // 获取当前时间
        val currentTime = System.currentTimeMillis()
        // 计算距离上次计算FPS的时间间隔
        val elapsedTime = currentTime - lastFpsTime
        
        // 如果时间间隔>=1秒，计算FPS
        if (elapsedTime >= 1000) {
            // 计算FPS：帧数 * 1000 / 时间间隔
            currentFps = frameCount * 1000f / elapsedTime
            // 重置帧计数
            frameCount = 0
            // 更新上次计算时间
            lastFpsTime = currentTime
        }
    }
    
    /**
     * 开始推理计时 - 在AI推理开始前调用
     * 
     * 【功能说明】
     * 记录AI推理开始的时间戳，用于计算推理耗时。
     * 应该在调用AI模型前调用。
     * 
     * 【为什么用nanoTime而不是currentTimeMillis？】
     * 1. 精度更高：nanoTime是纳秒级，currentTimeMillis是毫秒级
     * 2. 不受系统时间调整影响
     * 3. 适合测量短时间间隔
     */
    fun startInference() {
        inferenceStartTime = System.nanoTime()
    }
    
    /**
     * 结束推理计时 - 在AI推理结束后调用
     * 
     * 【功能说明】
     * 计算AI推理的耗时，保存到lastInferenceTime。
     * 应该在AI模型调用完成后调用。
     * 
     * 【耗时计算】
     * 耗时 = (结束时间 - 开始时间) / 1000000
     * 因为nanoTime是纳秒，需要转换为毫秒
     */
    fun endInference() {
        // 计算推理耗时（毫秒）
        lastInferenceTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inferenceStartTime)
    }
    
    /**
     * 获取上次推理耗时 - 返回最近一次AI推理的耗时
     * 
     * 【返回值】
     * 推理耗时（毫秒），理想值<100ms
     * 
     * 【使用场景】
     * 在UI上显示推理耗时，让用户知道AI处理的速度
     */
    fun getLastInferenceTime(): Long = lastInferenceTime
    
    /**
     * 获取当前帧率 - 返回最近计算的FPS值
     * 
     * 【返回值】
     * 当前帧率（fps），理想值30fps+
     * 
     * 【使用场景】
     * 在UI上显示帧率，监控UI流畅度
     */
    fun getCurrentFps(): Float = currentFps
    
    /**
     * 获取内存使用情况 - 返回当前的内存使用数据
     * 
     * 【功能说明】
     * 获取Java堆和Native堆的内存使用情况。
     * 用于监控内存泄漏和OOM风险。
     * 
     * 【返回值】
     * MemoryUsage对象，包含：
     * - usedJavaMemory：已使用的Java堆内存
     * - maxJavaMemory：最大可用Java堆内存
     * - nativeHeapSize：Native堆大小
     * - nativeHeapAllocated：已分配的Native堆内存
     * 
     * 【内存监控意义】
     * 1. 如果usedJavaMemory接近maxJavaMemory，可能OOM
     * 2. 如果nativeHeapAllocated持续增长，可能有Native内存泄漏
     * 3. 如果usedJavaMemory持续增长，可能有Java内存泄漏
     */
    fun getMemoryUsage(): MemoryUsage {
        // 获取Java运行时
        val runtime = Runtime.getRuntime()
        // 计算已使用的Java堆内存
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        // 获取最大可用Java堆内存
        val maxMemory = runtime.maxMemory()
        // 获取Native堆大小
        val nativeHeapSize = Debug.getNativeHeapSize()
        // 获取已分配的Native堆内存
        val nativeHeapAlloc = Debug.getNativeHeapAllocatedSize()
        
        return MemoryUsage(
            usedJavaMemory = usedMemory,
            maxJavaMemory = maxMemory,
            nativeHeapSize = nativeHeapSize,
            nativeHeapAllocated = nativeHeapAlloc
        )
    }
    
    /**
     * 内存使用数据类 - 存储内存使用信息
     * 
     * 【字段说明】
     * @param usedJavaMemory 已使用的Java堆内存（字节）
     * @param maxJavaMemory 最大可用Java堆内存（字节）
     * @param nativeHeapSize Native堆大小（字节）
     * @param nativeHeapAllocated 已分配的Native堆内存（字节）
     * 
     * 【单位转换方法】
     * 提供了多个方法将字节转换为MB，便于显示：
     * - getUsedMemoryMB()：已使用Java内存（MB）
     * - getMaxMemoryMB()：最大可用Java内存（MB）
     * - getNativeHeapMB()：Native堆大小（MB）
     * - getNativeAllocatedMB()：已分配Native内存（MB）
     */
    data class MemoryUsage(
        val usedJavaMemory: Long,      // 已使用Java堆内存（字节）
        val maxJavaMemory: Long,       // 最大可用Java堆内存（字节）
        val nativeHeapSize: Long,      // Native堆大小（字节）
        val nativeHeapAllocated: Long  // 已分配的Native堆内存（字节）
    ) {
        /**
         * 获取已使用Java内存（MB）
         * 公式：字节 / 1024 / 1024
         */
        fun getUsedMemoryMB(): Float = usedJavaMemory / (1024f * 1024f)
        
        /**
         * 获取最大可用Java内存（MB）
         */
        fun getMaxMemoryMB(): Float = maxJavaMemory / (1024f * 1024f)
        
        /**
         * 获取Native堆大小（MB）
         */
        fun getNativeHeapMB(): Float = nativeHeapSize / (1024f * 1024f)
        
        /**
         * 获取已分配Native内存（MB）
         */
        fun getNativeAllocatedMB(): Float = nativeHeapAllocated / (1024f * 1024f)
    }
}
