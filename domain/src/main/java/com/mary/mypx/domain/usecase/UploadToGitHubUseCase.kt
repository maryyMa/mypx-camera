package com.mary.mypx.domain.usecase

import com.mary.mypx.domain.repository.CameraRepository

/**
 * 上传到 GitHub 用例 - 自动添加水印后上传到 GitHub 仓库
 *
 * 【业务场景】
 * 用户选择上传功能，将照片上传到 GitHub 仓库进行云存储。
 * 上传前会自动添加水印。
 *
 * 【执行流程】
 * 1. 接收图片数据和文件名
 * 2. 自动添加水印
 * 3. 上传到 GitHub
 * 4. 返回下载链接
 *
 * @param cameraRepository 相机仓库
 * @param addWatermarkUseCase 水印用例
 */
class UploadToGitHubUseCase(
    private val cameraRepository: CameraRepository,
    private val addWatermarkUseCase: AddWatermarkUseCase
) {

    /**
     * 上传图片到 GitHub
     *
     * @param imageData 图片字节数组
     * @param fileName 文件名（如 "IMG_20260604_120000.jpg"）
     * @return 下载链接
     */
    suspend operator fun invoke(imageData: ByteArray, fileName: String): Result<String> {
        // 1. 自动添加水印
        val watermarked = addWatermarkUseCase(imageData, "MyPx")
            .getOrElse { imageData }  // 水印失败则用原图

        // 2. 上传到 GitHub
        return cameraRepository.uploadToGitHub(watermarked, fileName)
    }
}
