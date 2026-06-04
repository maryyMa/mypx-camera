package com.mary.mypx.domain.usecase

import com.mary.mypx.domain.repository.ImageProcessor

/**
 * 裁切图片用例 - 对图片进行裁剪
 *
 * @param imageProcessor 图像处理器（由 Data 层实现）
 */
class CropImageUseCase(private val imageProcessor: ImageProcessor) {

    /**
     * 裁剪图片
     *
     * @param imageData 原始图片字节数组
     * @param x 裁剪区域左上角 x 坐标
     * @param y 裁剪区域左上角 y 坐标
     * @param width 裁剪区域宽度
     * @param height 裁剪区域高度
     * @return 裁剪后的图片字节数组
     */
    operator fun invoke(
        imageData: ByteArray,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): Result<ByteArray> {
        return try {
            val result = imageProcessor.crop(imageData, x, y, width, height)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
