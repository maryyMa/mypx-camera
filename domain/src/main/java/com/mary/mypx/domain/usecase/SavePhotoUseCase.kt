package com.mary.mypx.domain.usecase

import com.mary.mypx.domain.model.Photo
import com.mary.mypx.domain.repository.CameraRepository

/**
 * 保存照片用例 - 封装照片保存的业务逻辑
 * 
 * 【业务场景】
 * 用户对照片满意后，点击保存按钮，将照片保存到设备存储。
 * 保存后照片会出现在系统相册中，用户可以分享或编辑。
 * 
 * 【保存流程】
 * 1. 接收图像数据（ByteArray）
 * 2. 调用Repository保存到存储
 * 3. 返回保存后的照片信息（包含新的URI）
 * 
 * 【为什么需要保存用例？】
 * 1. 封装保存逻辑：可能需要添加额外的业务逻辑
 * 2. 统一接口：所有UseCase都通过invoke调用
 * 3. 便于扩展：如保存前压缩、添加水印等
 * 
 * @param cameraRepository 相机仓库，提供保存功能
 */
class SavePhotoUseCase(private val cameraRepository: CameraRepository) {
    
    /**
     * 执行保存照片操作
     * 
     * 【功能说明】
     * 将图像数据保存到设备的持久化存储中。
     * 保存成功后，照片会出现在系统相册的 Pictures/MyPx 目录下。
     * 
     * 【参数说明】
     * @param imageData 要保存的图像字节数组
     *                   通常是JPEG格式，包含完整的图像数据
     * 
     * 【返回值】
     * - 成功：返回Photo对象，包含：
     *   - uriString: 保存后的文件URI
     *   - timestamp: 保存时间
     *   - 其他元数据
     * - 失败：返回Result.failure
     * 
     * 【可能的失败原因】
     * 1. 存储空间不足
     * 2. 没有写入权限
     * 3. 文件系统错误
     * 
     * 【调用示例】
     * ```kotlin
     * viewModelScope.launch {
     *     val result = savePhotoUseCase(imageData)
     *     result.fold(
     *         onSuccess = { photo -> 
     *             showSuccess("照片已保存")
     *             // 可以跳转到预览页面显示保存的照片
     *         },
     *         onFailure = { error -> 
     *             showError("保存失败: ${error.message}")
     *         }
     *     )
     * }
     * ```
     */
    suspend operator fun invoke(imageData: ByteArray): Result<Photo> {
        // 直接委托给Repository实现
        // 未来可以在这里添加：
        // 1. 保存前压缩图像
        // 2. 添加水印
        // 3. 记录保存历史
        // 4. 上传到云端备份
        return cameraRepository.savePhoto(imageData)
    }
}
