package com.mary.mypx.feature.camera.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * 水印覆盖视图 - 在图片上平铺显示水印文字
 * 
 * 【功能说明】
 * 以对角线平铺的方式显示水印文字，常用于照片预览界面
 * 
 * 【使用方式】
 * 在布局文件中添加此视图，覆盖在 ImageView 上方即可
 */
class WatermarkOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 水印文字 */
    private var watermarkText = "mypx"

    /** 水印文字画笔 */
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255) // 半透明白色
        textSize = 32f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    /** 水印间距（水平和垂直） */
    private val horizontalSpacing = 300f
    private val verticalSpacing = 200f

    /** 旋转角度（对角线效果） */
    private val rotationAngle = -30f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        if (width == 0f || height == 0f) return

        // 保存画布状态
        canvas.save()

        // 旋转画布实现对角线效果
        canvas.rotate(rotationAngle, width / 2, height / 2)

        // 计算需要覆盖的区域（旋转后需要更大的范围）
        val diagonal = Math.sqrt((width * width + height * height).toDouble()).toFloat()
        val startX = -(diagonal - width) / 2
        val startY = -(diagonal - height) / 2
        val endX = width + (diagonal - width) / 2
        val endY = height + (diagonal - height) / 2

        // 平铺绘制水印文字
        var y = startY
        while (y < endY) {
            var x = startX
            while (x < endX) {
                canvas.drawText(watermarkText, x, y, textPaint)
                x += horizontalSpacing
            }
            y += verticalSpacing
        }

        // 恢复画布状态
        canvas.restore()
    }

    /**
     * 设置水印文字
     * @param text 水印文字内容
     */
    fun setWatermarkText(text: String) {
        watermarkText = text
        invalidate()
    }
}
