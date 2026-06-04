package com.mary.mypx.domain.usecase

import com.mary.mypx.domain.model.Photo
import com.mary.mypx.domain.repository.CameraRepository

/**
 * 保存到相册用例 - 自动添加水印后保存到系统相册
 *
 * 【业务场景】
 * 用户点击保存按钮，将照片保存到设备存储。
 * 保存前会自动添加水印。
 *
 * 【执行流程】
 * 1. 接收图片数据
 * 2. 自动添加水印
 * 3. 保存到系统相册
 * 4. 返回保存后的照片信息
 *
 * @param cameraRepository 相机仓库
 * @param addWatermarkUseCase 水印用例
 */
class SaveToGalleryUseCase(
    private val cameraRepository: CameraRepository,
    private val addWatermarkUseCase: AddWatermarkUseCase
) {

    /**
     * 保存照片到系统相册
     *
     * @param imageData 图片字节数组（JPEG格式）
     * @return 保存后的照片信息
     */
    suspend operator fun invoke(imageData: ByteArray): Result<Photo> {
        // 1. 自动添加水印
        val watermarked = addWatermarkUseCase(imageData, "MyPx")
            .getOrElse { imageData }  // 水印失败则用原图

        // 2. 保存到相册
        return cameraRepository.saveToGallery(watermarked)
    }
}
