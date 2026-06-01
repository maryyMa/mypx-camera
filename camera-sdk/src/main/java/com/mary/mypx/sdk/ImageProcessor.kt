package com.mary.mypx.sdk

import android.graphics.Bitmap

/**
 * 图像处理器接口 - 定义图像处理的契约
 * 
 * 【接口设计原则】
 * 这是SDK模块的核心接口，遵循：
 * 1. 单一职责：只负责图像处理
 * 2. 开闭原则：对扩展开放，对修改关闭
 * 3. 依赖倒置：依赖抽象而不是具体实现
 * 
 * 【为什么用接口？】
 * 1. 可替换：可以轻松切换不同的实现（CPU处理、GPU处理、云端处理）
 * 2. 可测试：可以用Mock实现进行单元测试
 * 3. 可扩展：添加新滤镜只需添加新实现
 * 
 * 【suspend函数】
 * process是suspend函数，因为：
 * 1. 图像处理是CPU密集型操作
 * 2. 需要在后台线程执行
 * 3. 可能需要较长时间（尤其是AI处理）
 */
interface ImageProcessor {
    
    /**
     * 处理图像 - 对图像应用滤镜效果
     * 
     * 【功能说明】
     * 将输入的Bitmap应用指定的滤镜效果，返回处理后的Bitmap。
     * 这是图像处理的核心方法，所有滤镜都通过这个方法应用。
     * 
     * 【参数说明】
     * @param bitmap 输入的原始图像
     *               Bitmap是Android的图像格式，包含像素数据
     * @param filterType 要应用的滤镜类型
     *                   使用枚举确保类型安全
     * 
     * 【返回值】
     * 处理后的Bitmap，可以直接用于显示或保存
     * 
     * 【性能考虑】
     * 1. 处理时间应该<100ms
     * 2. 可以使用GPU加速
     * 3. 对大图像可以先缩小再处理
     * 
     * 【调用示例】
     * ```kotlin
     * val processedBitmap = imageProcessor.process(originalBitmap, FilterType.BEAUTY)
     * imageView.setImageBitmap(processedBitmap)
     * ```
     */
    suspend fun process(bitmap: Bitmap, filterType: FilterType): Bitmap
    
    /**
     * 释放资源 - 清理处理器占用的资源
     * 
     * 【功能说明】
     * 释放图像处理器占用的资源，包括：
     * 1. 模型文件（如果有）
     * 2. GPU资源
     * 3. 临时内存
     * 
     * 【调用时机】
     * 在不再需要处理器时调用，避免内存泄漏
     */
    fun release()
}

/**
 * 滤镜类型枚举 - 定义SDK支持的滤镜
 * 
 * 【与domain层的区别】
 * 这是SDK层的FilterType，与domain层的FilterType是不同的枚举。
 * 这样设计的原因：
 * 1. SDK层可以独立于domain层使用
 * 2. SDK层的滤镜可能比domain层更丰富
 * 3. 避免层之间的直接依赖
 * 
 * 【在feature层转换】
 * 在feature层，需要将domain层的FilterType转换为SDK层的FilterType
 */
enum class FilterType {
    /** 无滤镜，返回原图 */
    NONE,
    /** 美颜滤镜，提亮肤色、平滑皮肤 */
    BEAUTY,
    /** 超分辨率，将低清图片变高清 */
    SUPER_RESOLUTION,
    /** 夜景增强，在暗光环境下提亮画面 */
    NIGHT_MODE
}
