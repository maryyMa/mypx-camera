package com.mary.mypx.domain.usecase

/**
 * 加水印用例 - 在图片上添加水印
 *
 * 【业务场景】
 * 用户在预览页面选择添加水印，在图片上叠加水印文字或图片。
 *
 * 【执行流程】
 * 1. 接收原始图片数据
 * 2. 在指定位置绘制水印
 * 3. 返回添加水印后的图片数据
 *
 * 【注意】
 * 这是纯图片处理操作，不涉及网络或存储，不需要 Repository。
 */
class AddWatermarkUseCase {

    /**
     * 添加文字水印
     *
     * @param imageData 原始图片字节数组
     * @param text 水印文字
     * @param position 水印位置（LEFT_TOP, LEFT_BOTTOM, RIGHT_TOP, RIGHT_BOTTOM）
     * @return 添加水印后的图片字节数组
     */
    operator fun invoke(
        imageData: ByteArray,
        text: String,
        position: WatermarkPosition = WatermarkPosition.RIGHT_BOTTOM
    ): Result<ByteArray> {
        // 实际实现需要在 data 层或 sdk 中处理
        // 这里只定义接口
        return Result.success(imageData)
    }
}

/**
 * 水印位置枚举
 */
enum class WatermarkPosition {
    LEFT_TOP,
    LEFT_BOTTOM,
    RIGHT_TOP,
    RIGHT_BOTTOM
}
