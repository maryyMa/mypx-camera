package com.mary.mypx.feature.camera.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mary.mypx.data.camera.repository.CameraRepositoryImpl
import com.mary.mypx.data.camera.repository.ImageProcessorImpl
import com.mary.mypx.domain.model.Photo
import com.mary.mypx.domain.usecase.AdjustColorsUseCase
import com.mary.mypx.domain.usecase.CropImageUseCase
import com.mary.mypx.domain.usecase.SaveToGalleryUseCase
import com.mary.mypx.domain.usecase.UploadToGitHubUseCase
import com.mary.mypx.domain.usecase.AddWatermarkUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 预览视图模型 - 管理照片预览页面的状态和业务逻辑
 *
 * 【Clean Architecture 实现】
 * - 只调用 UseCase，不直接调用 Repository 或第三方库
 * - 管理 UI 状态，通过 StateFlow 通知 Fragment
 */
class PreviewViewModel(application: Application) : AndroidViewModel(application) {

    // ==================== Repository ====================
    private val repository = CameraRepositoryImpl(
        context = application,
        githubToken = com.mary.mypx.feature.camera.BuildConfig.GITHUB_TOKEN
    )
    private val imageProcessor = ImageProcessorImpl()

    // ==================== UseCase ====================
    private val addWatermarkUseCase = AddWatermarkUseCase()
    private val cropImageUseCase = CropImageUseCase(imageProcessor)
    private val adjustColorsUseCase = AdjustColorsUseCase(imageProcessor)
    private val saveToGalleryUseCase = SaveToGalleryUseCase(repository, addWatermarkUseCase)
    private val uploadToGitHubUseCase = UploadToGitHubUseCase(repository, addWatermarkUseCase)

    // ==================== 状态 ====================

    /** 原始图片数据 */
    private val _imageData = MutableStateFlow<ByteArray?>(null)
    val imageData: StateFlow<ByteArray?> = _imageData.asStateFlow()

    /** 编辑后的图片数据 */
    private val _editedImageData = MutableStateFlow<ByteArray?>(null)
    val editedImageData: StateFlow<ByteArray?> = _editedImageData.asStateFlow()

    /** 加载状态 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 错误信息 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 成功信息 */
    private val _success = MutableStateFlow<String?>(null)
    val success: StateFlow<String?> = _success.asStateFlow()

    /** 是否有未保存的修改 */
    private val _hasChanges = MutableStateFlow(false)
    val hasChanges: StateFlow<Boolean> = _hasChanges.asStateFlow()

    // ==================== 公开方法 ====================

    /**
     * 设置原始图片数据
     *
     * @param bitmap 原始图片
     */
    fun setOriginalImage(bitmap: Bitmap) {
        _imageData.value = bitmapToByteArray(bitmap)
        _editedImageData.value = bitmapToByteArray(bitmap)
        _hasChanges.value = false
    }

    /**
     * 裁剪图片
     *
     * @param x 左上角 x 坐标
     * @param y 左上角 y 坐标
     * @param width 宽度
     * @param height 高度
     */
    fun cropImage(x: Int, y: Int, width: Int, height: Int) {
        val data = _editedImageData.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = cropImageUseCase(data, x, y, width, height)
            result.fold(
                onSuccess = { newData ->
                    _editedImageData.value = newData
                    _hasChanges.value = true
                    _success.value = "裁剪完成"
                },
                onFailure = { e ->
                    _error.value = "裁剪失败: ${e.message}"
                }
            )
            _isLoading.value = false
        }
    }

    /**
     * 调节色彩
     *
     * @param brightness 亮度（-100 到 100）
     * @param contrast 对比度（-100 到 100）
     * @param saturation 饱和度（-100 到 100）
     */
    fun adjustColors(brightness: Int, contrast: Int, saturation: Int) {
        val data = _imageData.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = adjustColorsUseCase(data, brightness, contrast, saturation)
            result.fold(
                onSuccess = { newData ->
                    _editedImageData.value = newData
                    _hasChanges.value = true
                },
                onFailure = { e ->
                    _error.value = "调节失败: ${e.message}"
                }
            )
            _isLoading.value = false
        }
    }

    /**
     * 保存照片到系统相册
     *
     * 【Clean Architecture 调用链】
     * Fragment → ViewModel → SaveToGalleryUseCase → CameraRepository → MediaStore
     */
    fun saveToGallery() {
        val data = _editedImageData.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = saveToGalleryUseCase(data)
            result.fold(
                onSuccess = { photo ->
                    _success.value = "照片已保存到相册"
                    _hasChanges.value = false
                },
                onFailure = { e ->
                    _error.value = "保存失败: ${e.message}"
                }
            )
            _isLoading.value = false
        }
    }

    /**
     * 上传图片到 GitHub
     *
     * 【Clean Architecture 调用链】
     * Fragment → ViewModel → UploadToGitHubUseCase → CameraRepository → GitHub API
     */
    fun uploadToGitHub() {
        val data = _editedImageData.value ?: return
        val fileName = generateFileName()
        viewModelScope.launch {
            _isLoading.value = true
            val result = uploadToGitHubUseCase(data, fileName)
            result.fold(
                onSuccess = { url ->
                    _success.value = "上传成功: $url"
                },
                onFailure = { e ->
                    _error.value = "上传失败: ${e.message}"
                }
            )
            _isLoading.value = false
        }
    }

    /**
     * 获取当前编辑后的 Bitmap
     */
    fun getEditedBitmap(): Bitmap? {
        val data = _editedImageData.value ?: return null
        return BitmapFactory.decodeByteArray(data, 0, data.size)
    }

    /**
     * 获取原始 Bitmap
     */
    fun getOriginalBitmap(): Bitmap? {
        val data = _imageData.value ?: return null
        return BitmapFactory.decodeByteArray(data, 0, data.size)
    }

    /**
     * 重置为原始图片
     */
    fun resetToOriginal() {
        _editedImageData.value = _imageData.value
        _hasChanges.value = false
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * 清除成功信息
     */
    fun clearSuccess() {
        _success.value = null
    }

    // ==================== 私有方法 ====================

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        return outputStream.toByteArray()
    }

    private fun generateFileName(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "IMG_$timeStamp.jpg"
    }
}
