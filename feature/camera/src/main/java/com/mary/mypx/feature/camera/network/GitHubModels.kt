package com.mary.mypx.feature.camera.network

/**
 * GitHub API 请求体封装
 *
 * 用于上传图片到 GitHub Contents API
 */
data class GitHubUploadRequest(
    val message: String,
    val content: String, // base64 encoded
    val branch: String = "master"
)

/**
 * GitHub API 响应封装
 */
data class GitHubUploadResponse(
    val content: ContentInfo?
)

data class ContentInfo(
    val name: String,
    val path: String,
    val sha: String,
    val download_url: String?
)