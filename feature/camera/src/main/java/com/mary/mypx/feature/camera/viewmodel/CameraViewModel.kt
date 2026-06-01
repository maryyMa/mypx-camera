package com.mary.mypx.feature.camera.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mary.mypx.domain.model.CameraState
import com.mary.mypx.domain.model.FilterType
import com.mary.mypx.domain.model.Photo
import com.mary.mypx.domain.repository.CameraRepository
import com.mary.mypx.domain.repository.PerformanceMetrics
import com.mary.mypx.sdk.CameraManager
import com.mary.mypx.sdk.ImageProcessor
import com.mary.mypx.sdk.PerformanceMonitor
import com.mary.mypx.sdk.TFLiteImageProcessor
import com.mary.mypx.sdk.FilterType as SdkFilterType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    
    private val cameraManager = CameraManager(application)
    private val imageProcessor: ImageProcessor = TFLiteImageProcessor(application)
    private val performanceMonitor = PerformanceMonitor()
    
    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Initializing)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()
    
    private val _currentFilter = MutableStateFlow(FilterType.NONE)
    val currentFilter: StateFlow<FilterType> = _currentFilter.asStateFlow()
    
    private val _photoTaken = MutableLiveData<Photo?>()
    val photoTaken: LiveData<Photo?> = _photoTaken
    
    private val _performanceMetrics = MutableLiveData<PerformanceMetrics>()
    val performanceMetrics: LiveData<PerformanceMetrics> = _performanceMetrics
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    private var isFlashOn = false
    private var isFrontCamera = false
    
    fun getCameraManager() = cameraManager
    fun getImageProcessor() = imageProcessor
    fun getPerformanceMonitor() = performanceMonitor
    
    fun setCameraState(state: CameraState) {
        _cameraState.value = state
    }
    
    fun setFilter(filterType: FilterType) {
        _currentFilter.value = filterType
    }
    
    fun takePhoto() {
        _cameraState.value = CameraState.TakingPhoto
        
        cameraManager.takePhoto(
            onImageCaptured = { result ->
                val uri = result.savedUri
                if (uri != null) {
                    _photoTaken.value = Photo(uriString = uri.toString())
                    _cameraState.value = CameraState.Preview
                }
            },
            onError = { exception ->
                _error.value = exception.message
                _cameraState.value = CameraState.Error(exception.message ?: "Unknown error")
            }
        )
    }
    
    fun toggleFlash() {
        cameraManager.toggleFlash()
        isFlashOn = cameraManager.isFlashOn()
    }
    
    fun switchCamera() {
        cameraManager.switchCamera()
        isFrontCamera = cameraManager.isUsingFrontCamera()
    }
    
    fun isFlashOn() = isFlashOn
    fun isFrontCamera() = isFrontCamera
    
    fun updatePerformanceMetrics() {
        val fps = performanceMonitor.getCurrentFps()
        val inferenceTime = performanceMonitor.getLastInferenceTime()
        val memoryUsage = performanceMonitor.getMemoryUsage()
        
        _performanceMetrics.postValue(PerformanceMetrics(
            fps = fps,
            inferenceTimeMs = inferenceTime,
            memoryUsageMB = memoryUsage.getUsedMemoryMB()
        ))
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun clearPhoto() {
        _photoTaken.value = null
    }
    
    suspend fun savePhoto(imageData: ByteArray): Result<Photo> {
        // For now, return a success result with a dummy photo
        // In a real app, this would use a repository to save the photo
        return Result.success(Photo(uriString = "saved_photo_${System.currentTimeMillis()}"))
    }
    
    fun getSdkFilterType(domainFilterType: FilterType): SdkFilterType {
        return when (domainFilterType) {
            FilterType.NONE -> SdkFilterType.NONE
            FilterType.BEAUTY -> SdkFilterType.BEAUTY
            FilterType.SUPER_RESOLUTION -> SdkFilterType.SUPER_RESOLUTION
            FilterType.NIGHT_MODE -> SdkFilterType.NIGHT_MODE
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        cameraManager.release()
        imageProcessor.release()
    }
}
