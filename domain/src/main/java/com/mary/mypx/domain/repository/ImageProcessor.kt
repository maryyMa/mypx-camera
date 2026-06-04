package com.mary.mypx.domain.repository

/**
 * 图像处理器接口 - 定义图像处理操作的契约
 *
 * 【为什么需要这个接口？】
 * 图像处理需要 Android Bitmap API，但 Domain 层不能依赖 Android。
 * 通过接口隔离，Domain 层定义"做什么"，Data 层实现"怎么做"。
 */
interface ImageProcessor {

    /**
     * 裁剪图片
     *
     * @param imageData 原始图片字节数组
     * @param x 裁剪区域左上角 x 坐标
     * @param y 裁剪区域左上角 y 坐标
     * @param width 裁剪区域宽度
     * @param height 裁剪区域高度
     * @return 裁剪后的图片字节数组
     */
    fun crop(imageData: ByteArray, x: Int, y: Int, width: Int, height: Int): ByteArray

    /**
     * 调整图片色彩
     *
     * @param imageData 原始图片字节数组
     * @param brightness 亮度调整值（-100 到 100，0 表示不变）
     * @param contrast 对比度调整值（-100 到 100，0 表示不变）
     * @param saturation 饱和度调整值（-100 到 100，0 表示不变）
     * @return 调整后的图片字节数组
     */
    fun adjustColors(
        imageData: ByteArray,
        brightness: Int = 0,
        contrast: Int = 0,
        saturation: Int = 0
    ): ByteArray
}
