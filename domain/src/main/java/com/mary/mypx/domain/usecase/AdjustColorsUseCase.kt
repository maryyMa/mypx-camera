package com.mary.mypx.domain.usecase

import com.mary.mypx.domain.repository.ImageProcessor

/**
 * 调节色彩用例 - 调整图片的亮度、对比度、饱和度
 *
 * @param imageProcessor 图像处理器（由 Data 层实现）
 */
class AdjustColorsUseCase(private val imageProcessor: ImageProcessor) {

    /**
     * 调整图片色彩
     *
     * @param imageData 原始图片字节数组
     * @param brightness 亮度调整值（-100 到 100，0 表示不变）
     * @param contrast 对比度调整值（-100 到 100，0 表示不变）
     * @param saturation 饱和度调整值（-100 到 100，0 表示不变）
     * @return 调整后的图片字节数组
     */
    operator fun invoke(
        imageData: ByteArray,
        brightness: Int = 0,
        contrast: Int = 0,
        saturation: Int = 0
    ): Result<ByteArray> {
        return try {
            val result = imageProcessor.adjustColors(imageData, brightness, contrast, saturation)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
