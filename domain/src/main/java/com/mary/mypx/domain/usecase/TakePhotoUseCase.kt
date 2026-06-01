package com.mary.mypx.domain.usecase

import com.mary.mypx.domain.model.Photo
import com.mary.mypx.domain.repository.CameraRepository

/**
 * 拍照用例 - 封装拍照的业务逻辑
 * 
 * 【UseCase模式的作用】
 * UseCase（用例）是领域层的核心组件，它：
 * 1. 封装单一的业务逻辑（一个UseCase只做一件事）
 * 2. 协调Repository完成业务流程
 * 3. 保持ViewModel的简洁，避免业务逻辑泄漏到UI层
 * 
 * 【为什么用operator fun invoke？】
 * Kotlin允许重载invoke操作符，这样UseCase可以像函数一样调用：
 * ```kotlin
 * // 不使用invoke，需要显式调用execute
 * val result = takePhotoUseCase.execute()
 * 
 * // 使用invoke，可以像函数一样调用
 * val result = takePhotoUseCase()
 * ```
 * 这样更简洁，也更符合"用例就是一个操作"的概念。
 * 
 * 【依赖注入】
 * 通过构造函数注入CameraRepository，好处是：
 * 1. 便于测试：可以传入MockRepository
 * 2. 解耦：UseCase不依赖具体的Repository实现
 * 3. 灵活：可以轻松替换不同的实现
 * 
 * @param cameraRepository 相机仓库，提供拍照功能
 */
class TakePhotoUseCase(private val cameraRepository: CameraRepository) {
    
    /**
     * 执行拍照操作
     * 
     * 【功能说明】
     * 调用相机仓库的拍照功能，返回拍照结果。
     * 这是一个挂起函数，会在协程中执行，不会阻塞主线程。
     * 
     * 【返回值】
     * - 成功：返回Photo对象，包含照片URI、时间戳等信息
     * - 失败：返回Result.failure，包含错误信息
     * 
     * 【调用示例】
     * ```kotlin
     * viewModelScope.launch {
     *     val result = takePhotoUseCase()
     *     result.fold(
     *         onSuccess = { photo -> /* 处理成功 */ },
     *         onFailure = { error -> /* 处理失败 */ }
     *     )
     * }
     * ```
     */
    suspend operator fun invoke(): Result<Photo> {
        // 直接委托给Repository实现
        // 这里没有额外的业务逻辑，因为拍照本身就是单一操作
        // 如果有更复杂的业务逻辑（如拍照后自动保存），可以在这里添加
        return cameraRepository.takePhoto()
    }
}
