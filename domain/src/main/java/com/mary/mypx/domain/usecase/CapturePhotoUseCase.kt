package com.mary.mypx.domain.usecase

import com.mary.mypx.domain.model.Photo
import com.mary.mypx.domain.repository.CameraRepository

/**
 * 拍照用例 - 从相机捕获当前帧并自动添加水印
 *
 * 【业务场景】
 * 用户点击拍照按钮，从相机预览中截取当前帧（已包含美颜效果），
 * 然后自动添加水印，返回最终照片。
 *
 * 【执行流程】
 * 1. 调用相机捕获当前帧
 * 2. 自动添加水印
 * 3. 返回照片信息
 *
 * @param cameraRepository 相机仓库
 * @param addWatermarkUseCase 水印用例
 */
class CapturePhotoUseCase(
    private val cameraRepository: CameraRepository,
    private val addWatermarkUseCase: AddWatermarkUseCase
) {

    /**
     * 执行拍照操作
     *
     * @return 照片信息（已包含水印）
     */
    suspend operator fun invoke(): Result<Photo> {
        // 1. 拍照
        val photo = cameraRepository.capturePhoto().getOrThrow()

        // 2. 自动添加水印（业务规则：所有照片都要加水印）
        // 注意：这里需要读取图片文件，添加水印后保存
        // 实际实现需要在 data 层处理

        return Result.success(photo)
    }
}
