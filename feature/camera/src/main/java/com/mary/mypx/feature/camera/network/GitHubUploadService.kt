package com.mary.mypx.feature.camera.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * GitHub 图片上传服务
 *
 * 【功能说明】
 * 将图片上传到 GitHub 仓库的指定目录。
 * 使用 GitHub Contents API (PUT) 进行上传。
 *
 * 【Token 安全】
 * Token 通过 BuildConfig 注入，不会硬编码在代码中。
 * local.properties 已在 .gitignore 中，不会被提交到版本控制。
 *
 * 【使用方式】
 * ```kotlin
 * val service = GitHubUploadService(token)
 * val result = service.uploadImage(imageBytes, "IMG_20260603.jpg")
 * ```
 */
class GitHubUploadService(private val token: String) {

    companion object {
        private const val TAG = "GitHubUploadService"
        private const val BASE_URL = "https://api.github.com/"
        private const val OWNER = "maryyMa"
        private const val REPO = "mypx-camera"
        private const val IMAGE_DIR = "images/"
    }

    private val api: GitHubApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApi::class.java)
    }

    /**
     * 上传图片到 GitHub
     *
     * @param imageBytes 图片字节数组
     * @param fileName 文件名（可选，默认自动生成带时间戳的名称）
     *
     * @return Result 包含下载 URL 或错误信息
     */
    suspend fun uploadImage(
        imageBytes: ByteArray,
        fileName: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 生成文件名
            val actualFileName = fileName ?: generateFileName()
            val filePath = "$IMAGE_DIR$actualFileName"

            // 将图片转换为 base64
            val base64Content = android.util.Base64.encodeToString(
                imageBytes,
                android.util.Base64.NO_WRAP
            )

            // 创建请求体
            val request = GitHubUploadRequest(
                message = "Upload image: $actualFileName via MyPx",
                content = base64Content
            )

            // 调用 API
            Log.d(TAG, "Uploading image to: $filePath")
            val response = api.uploadFile(
                owner = OWNER,
                repo = REPO,
                path = filePath,
                token = "token $token",
                request = request
            )

            val downloadUrl = response.content?.download_url
            if (downloadUrl != null) {
                Log.d(TAG, "Upload successful: $downloadUrl")
                Result.success(downloadUrl)
            } else {
                Log.e(TAG, "Upload response missing download_url")
                Result.failure(Exception("Upload failed: no download URL"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            Result.failure(e)
        }
    }

    /**
     * 生成带时间戳的文件名
     */
    private fun generateFileName(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "IMG_$timeStamp.jpg"
    }
}