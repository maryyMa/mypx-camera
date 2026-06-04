package com.mary.mypx.domain.repository

import com.mary.mypx.domain.model.Photo

/**
 * 相机仓库接口 - 定义所有相机相关的数据操作
 *
 * 【Repository模式的作用】
 * Repository是领域层和数据层之间的桥梁：
 * - 领域层定义接口（做什么）
 * - 数据层实现接口（怎么做）
 * - 领域层不依赖具体实现，便于替换和测试
 */
interface CameraRepository {

    /**
     * 拍照 - 从相机捕获当前帧
     *
     * @return 照片信息（包含临时文件路径）
     */
    suspend fun capturePhoto(): Result<Photo>

    /**
     * 保存照片到系统相册
     *
     * @param imageData 图片字节数组（JPEG格式）
     * @return 保存后的照片信息（包含相册URI）
     */
    suspend fun saveToGallery(imageData: ByteArray): Result<Photo>

    /**
     * 上传图片到 GitHub
     *
     * @param imageData 图片字节数组
     * @param fileName 文件名
     * @return 下载链接
     */
    suspend fun uploadToGitHub(imageData: ByteArray, fileName: String): Result<String>
}
