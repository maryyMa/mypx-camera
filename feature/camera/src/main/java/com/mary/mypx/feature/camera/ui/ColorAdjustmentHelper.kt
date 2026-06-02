package com.mary.mypx.feature.camera.ui

import android.content.Context
import android.graphics.Bitmap
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBrightnessFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageContrastFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSaturationFilter

/**
 * 色彩调节工具类 - 使用 GPUImage 处理图片色彩
 * 
 * 【功能说明】
 * 使用 GPUImage 库进行 GPU 加速的图片色彩调节
 * - 亮度调节：-1.0 到 1.0（默认 0.0）
 * - 对比度调节：0.0 到 4.0（默认 1.0）
 * - 饱和度调节：0.0 到 2.0（默认 1.0）
 * 
 * 【注意】
 * applyFilter 必须在主线程调用，因为 GPUImage 需要 GL 上下文
 */
class ColorAdjustmentHelper(context: Context) {

    /** GPUImage 实例 */
    private val gpuImage = GPUImage(context)

    /** 亮度滤镜 */
    private val brightnessFilter = GPUImageBrightnessFilter(0.0f)

    /** 对比度滤镜 */
    private val contrastFilter = GPUImageContrastFilter(1.0f)

    /** 饱和度滤镜 */
    private val saturationFilter = GPUImageSaturationFilter(1.0f)

    /** 滤镜组 */
    private val filterGroup = GPUImageFilterGroup()

    init {
        // 添加滤镜到滤镜组
        filterGroup.addFilter(brightnessFilter)
        filterGroup.addFilter(contrastFilter)
        filterGroup.addFilter(saturationFilter)

        // 设置滤镜组
        gpuImage.setFilter(filterGroup)
    }

    /**
     * 设置亮度
     * @param value 进度条值 0-200（100 为中间值，即原始亮度）
     */
    fun setBrightness(value: Int) {
        // 将 0-200 映射到 -1.0 到 1.0
        val brightness = (value - 100) / 100.0f
        brightnessFilter.setBrightness(brightness)
    }

    /**
     * 设置对比度
     * @param value 进度条值 0-200（100 为中间值，即原始对比度）
     */
    fun setContrast(value: Int) {
        // 将 0-200 映射到 0.0 到 4.0
        val contrast = value / 100.0f * 2.0f
        contrastFilter.setContrast(contrast)
    }

    /**
     * 设置饱和度
     * @param value 进度条值 0-200（100 为中间值，即原始饱和度）
     */
    fun setSaturation(value: Int) {
        // 将 0-200 映射到 0.0 到 2.0
        val saturation = value / 100.0f
        saturationFilter.setSaturation(saturation)
    }

    /**
     * 应用滤镜并返回处理后的 Bitmap
     * 【注意】必须在主线程调用
     * @param bitmap 原始图片
     * @return 处理后的图片
     */
    fun applyFilter(bitmap: Bitmap): Bitmap {
        gpuImage.setImage(bitmap)
        return gpuImage.bitmapWithFilterApplied
    }

    /**
     * 重置所有参数
     */
    fun reset() {
        brightnessFilter.setBrightness(0.0f)
        contrastFilter.setContrast(1.0f)
        saturationFilter.setSaturation(1.0f)
    }
}
