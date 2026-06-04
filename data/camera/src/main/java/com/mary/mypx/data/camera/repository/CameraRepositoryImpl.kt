package com.mary.mypx.data.camera.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.mary.mypx.data.camera.network.GitHubApi
import com.mary.mypx.data.camera.network.GitHubUploadRequest
import com.mary.mypx.domain.model.Photo
import com.mary.mypx.domain.repository.CameraRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 相机仓库实现 - CameraRepository 接口的具体实现
 *
 * 【职责】
 * 1. 实现 Domain 层定义的 Repository 接口
 * 2. 处理具体的数据存取逻辑（MediaStore、GitHub API）
 * 3. 封装第三方库的细节
 *
 * 【依赖】
 * - Context：访问 Android 系统服务（MediaStore、ContentResolver）
 * - GitHub Token：从 BuildConfig 注入
 */
class CameraRepositoryImpl(
    private val context: Context,
    private val githubToken: String = ""
) : CameraRepository {

    companion object {
        private const val TAG = "CameraRepositoryImpl"
        private const val GITHUB_BASE_URL = "https://api.github.com/"
        private const val GITHUB_OWNER = "maryyMa"
        private const val GITHUB_REPO = "mypx-camera"
        private const val GITHUB_IMAGE_DIR = "images/"
    }

    // GitHub API（懒加载）
    private val gitHubApi: GitHubApi by lazy {
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
            .baseUrl(GITHUB_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApi::class.java)
    }

    /**
     * 拍照 - 从相机捕获当前帧
     *
     * 注意：实际的拍照逻辑在 camera-sdk 的 BeautyTextureView 中处理，
     * 这里只是返回一个占位实现。
     */
    override suspend fun capturePhoto(): Result<Photo> {
        // 拍照由 camera-sdk 直接处理，这里不需要实现
        return Result.failure(UnsupportedOperationException("Use BeautyTextureView.takePhoto() instead"))
    }

    /**
     * 保存照片到系统相册
     *
     * 【实现逻辑】
     * 1. 生成带时间戳的文件名
     * 2. 使用 MediaStore API 保存到 Pictures/MyPx 目录
     * 3. 返回保存后的 Photo 对象
     */
    override suspend fun saveToGallery(imageData: ByteArray): Result<Photo> {
        return withContext(Dispatchers.IO) {
            try {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "IMG_$timeStamp.jpg"

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MyPx")
                    }
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(imageData)
                    }

                    Log.d(TAG, "Photo saved to gallery: $uri")
                    Result.success(Photo(uriString = uri.toString()))
                } else {
                    Log.e(TAG, "Failed to create MediaStore entry")
                    Result.failure(Exception("Failed to create MediaStore entry"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save photo to gallery", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 上传图片到 GitHub
     *
     * 【实现逻辑】
     * 1. 将图片转换为 Base64 编码
     * 2. 调用 GitHub Contents API 上传
     * 3. 返回下载链接
     */
    override suspend fun uploadToGitHub(imageData: ByteArray, fileName: String): Result<String> {
        if (githubToken.isEmpty()) {
            return Result.failure(Exception("GitHub token not configured"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val filePath = "$GITHUB_IMAGE_DIR$fileName"

                // 将图片转换为 Base64
                val base64Content = android.util.Base64.encodeToString(
                    imageData,
                    android.util.Base64.NO_WRAP
                )

                // 创建请求体
                val request = GitHubUploadRequest(
                    message = "Upload image: $fileName via MyPx",
                    content = base64Content
                )

                // 调用 API
                Log.d(TAG, "Uploading image to: $filePath")
                val response = gitHubApi.uploadFile(
                    owner = GITHUB_OWNER,
                    repo = GITHUB_REPO,
                    path = filePath,
                    token = "token $githubToken",
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
    }
}
