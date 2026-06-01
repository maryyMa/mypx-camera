package com.mary.mypx.domain.usecase

import com.mary.mypx.domain.model.FilterType
import com.mary.mypx.domain.repository.CameraRepository

/**
 * 图像处理用例 - 封装AI图像处理的业务逻辑
 * 
 * 【业务场景】
 * 用户拍摄照片后，可能需要应用AI滤镜（美颜、超分、夜景增强）。
 * 这个用例封装了图像处理的完整流程。
 * 
 * 【为什么单独封装？】
 * 1. 单一职责：拍照和图像是两个独立的操作
 * 2. 可复用：同一个图像可以应用不同的滤镜
 * 3. 可测试：可以独立测试图像处理逻辑
 * 
 * 【性能考虑】
 * 图像处理是CPU密集型操作，应该：
 * 1. 在后台线程执行（使用协程）
 * 2. 控制处理时间在100ms以内
 * 3. 对大图像先缩小再处理
 * 
 * @param cameraRepository 相机仓库，提供图像处理功能
 */
class ProcessImageUseCase(private val cameraRepository: CameraRepository) {
    
    /**
     * 执行图像处理操作
     * 
     * 【功能说明】
     * 将原始图像数据应用指定的滤镜效果，返回处理后的图像数据。
     * 
     * 【参数说明】
     * @param imageData 原始图像的字节数组（JPEG格式）
     *                  为什么用ByteArray而不是Bitmap？
     *                  因为ByteArray是纯数据，不依赖Android框架，
     *                  符合领域层不依赖具体实现的原则。
     * @param filterType 要应用的滤镜类型
     *                   使用枚举确保类型安全
     * 
     * 【返回值】
     * - 成功：返回处理后的图像字节数组，可以直接用于显示或保存
     * - 失败：返回Result.failure，包含错误信息
     * 
     * 【调用示例】
     * ```kotlin
     * viewModelScope.launch {
     *     val result = processImageUseCase(imageData, FilterType.BEAUTY)
     *     result.fold(
     *         onSuccess = { processedData -> 
     *             // 显示处理后的图像
     *             val bitmap = BitmapFactory.decodeByteArray(processedData, 0, processedData.size)
     *             imageView.setImageBitmap(bitmap)
     *         },
     *         onFailure = { error -> 
     *             showError(error.message)
     *         }
     *     )
     * }
     * ```
     */
    suspend operator fun invoke(imageData: ByteArray, filterType: FilterType): Result<ByteArray> {
        // 直接委托给Repository实现
        // 如果需要更复杂的业务逻辑，可以在这里添加，例如：
        // 1. 处理前检查图像格式
        // 2. 处理后验证结果
        // 3. 记录处理日志
        // 4. 统计处理时间
        return cameraRepository.processImage(imageData, filterType)
    }
}
