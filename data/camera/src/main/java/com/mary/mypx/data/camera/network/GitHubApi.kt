package com.mary.mypx.data.camera.network

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * GitHub Contents API 接口
 *
 * API 文档: https://docs.github.com/en/rest/repos/contents
 */
interface GitHubApi {

    /**
     * 上传文件到仓库
     *
     * @param owner 仓库所有者
     * @param repo 仓库名称
     * @param path 文件路径
     * @param token GitHub Token (格式: "token xxx")
     * @param request 上传请求体
     */
    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun uploadFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Header("Authorization") token: String,
        @Body request: GitHubUploadRequest
    ): GitHubUploadResponse
}

data class GitHubUploadRequest(
    val message: String,
    val content: String,
    val branch: String = "master"
)

data class GitHubUploadResponse(
    val content: ContentInfo?
)

data class ContentInfo(
    val name: String,
    val path: String,
    val sha: String,
    val download_url: String?
)
