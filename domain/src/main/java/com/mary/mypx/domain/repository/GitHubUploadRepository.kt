package com.mary.mypx.domain.repository

/**
 * GitHub 上传仓库接口 - 定义图片上传到 GitHub 的契约
 *
 * 【功能说明】
 * 将图片上传到 GitHub 仓库，用于备份和分享。
 * 使用 GitHub Contents API 进行上传。
 *
 * 【API 格式】
 * PUT /repos/{owner}/{repo}/contents/{path}
 * Body: { "message": "...", "content": "base64..." }
 */
interface GitHubUploadRepository {

    /**
     * 上传图片到 GitHub
     *
     * @param imageBytes 图片的字节数组（JPEG 格式）
     * @param fileName 文件名（如 "IMG_20260603_120000.jpg"）
     * @param commitMessage 提交信息
     *
     * @return Result 包含上传后的文件 URL 或错误信息
     */
    suspend fun uploadImage(
        imageBytes: ByteArray,
        fileName: String,
        commitMessage: String = "Upload image via MyPx"
    ): Result<String>
}