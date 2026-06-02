package com.mary.mypx.feature.camera.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * 裁剪覆盖视图 - 自由裁剪图片
 * 
 * 【功能说明】
 * 显示裁剪选框，支持拖动调整裁剪区域
 * - 四个角拖动调整大小
 * - 中间拖动移动位置
 * - 半透明遮罩显示裁剪区域
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 裁剪区域 */
    private var cropRect = RectF()

    /** 遮罩画笔（半透明黑色） */
    private val maskPaint = Paint().apply {
        color = Color.argb(150, 0, 0, 0)
        style = Paint.Style.FILL
    }

    /** 裁剪框画笔 */
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    /** 裁剪框内辅助线画笔 */
    private val gridPaint = Paint().apply {
        color = Color.argb(100, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    /** 触摸区域大小 */
    private val touchAreaSize = 40f

    /** 最小裁剪区域 */
    private val minCropSize = 100f

    /** 尺寸文字画笔 */
    private val dimTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    /** 尺寸文字背景画笔 */
    private val dimBgPaint = Paint().apply {
        color = Color.argb(160, 0, 0, 0)
        style = Paint.Style.FILL
    }

    /** 当前触摸模式 */
    private enum class TouchMode {
        NONE,
        MOVE,
        RESIZE_LEFT_TOP,
        RESIZE_RIGHT_TOP,
        RESIZE_LEFT_BOTTOM,
        RESIZE_RIGHT_BOTTOM,
        RESIZE_LEFT,
        RESIZE_RIGHT,
        RESIZE_TOP,
        RESIZE_BOTTOM
    }

    private var touchMode = TouchMode.NONE
    private var lastX = 0f
    private var lastY = 0f

    /** 是否已初始化 */
    private var isInitialized = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            resetCropRect()
        }
    }

    /**
     * 重置裁剪区域为视图的 4/5，居中显示
     */
    fun resetCropRect() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 计算 4/5 的尺寸
        val cropW = w * 4f / 5f
        val cropH = h * 4f / 5f

        // 居中
        val left = (w - cropW) / 2f
        val top = (h - cropH) / 2f

        cropRect.set(left, top, left + cropW, top + cropH)
        isInitialized = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isInitialized) return

        val width = width.toFloat()
        val height = height.toFloat()

        // 绘制遮罩（裁剪区域外部）
        // 上方
        canvas.drawRect(0f, 0f, width, cropRect.top, maskPaint)
        // 下方
        canvas.drawRect(0f, cropRect.bottom, width, height, maskPaint)
        // 左方
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, maskPaint)
        // 右方
        canvas.drawRect(cropRect.right, cropRect.top, width, cropRect.bottom, maskPaint)

        // 绘制裁剪框边框
        canvas.drawRect(cropRect, borderPaint)

        // 绘制九宫格辅助线
        val thirdWidth = cropRect.width() / 3
        val thirdHeight = cropRect.height() / 3
        // 竖线
        canvas.drawLine(cropRect.left + thirdWidth, cropRect.top, cropRect.left + thirdWidth, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left + thirdWidth * 2, cropRect.top, cropRect.left + thirdWidth * 2, cropRect.bottom, gridPaint)
        // 横线
        canvas.drawLine(cropRect.left, cropRect.top + thirdHeight, cropRect.right, cropRect.top + thirdHeight, gridPaint)
        canvas.drawLine(cropRect.left, cropRect.top + thirdHeight * 2, cropRect.right, cropRect.top + thirdHeight * 2, gridPaint)

        // 绘制中心尺寸文字（如 720x720）
        val cropW = cropRect.width().toInt()
        val cropH = cropRect.height().toInt()
        val dimText = "${cropW}x${cropH}"
        val centerX = cropRect.centerX()
        val centerY = cropRect.centerY()

        // 文字背景
        val textWidth = dimTextPaint.measureText(dimText)
        val textHeight = dimTextPaint.descent() - dimTextPaint.ascent()
        val bgRect = RectF(
            centerX - textWidth / 2 - 16f,
            centerY - textHeight / 2 - 8f,
            centerX + textWidth / 2 + 16f,
            centerY + textHeight / 2 + 8f
        )
        canvas.drawRoundRect(bgRect, 8f, 8f, dimBgPaint)

        // 文字
        val textY = centerY - (dimTextPaint.descent() + dimTextPaint.ascent()) / 2
        canvas.drawText(dimText, centerX, textY, dimTextPaint)

        // 绘制四个角的控制点
        val cornerSize = 20f
        val cornerPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        // 左上角
        canvas.drawLine(cropRect.left, cropRect.top, cropRect.left + cornerSize, cropRect.top, cornerPaint)
        canvas.drawLine(cropRect.left, cropRect.top, cropRect.left, cropRect.top + cornerSize, cornerPaint)
        // 右上角
        canvas.drawLine(cropRect.right, cropRect.top, cropRect.right - cornerSize, cropRect.top, cornerPaint)
        canvas.drawLine(cropRect.right, cropRect.top, cropRect.right, cropRect.top + cornerSize, cornerPaint)
        // 左下角
        canvas.drawLine(cropRect.left, cropRect.bottom, cropRect.left + cornerSize, cropRect.bottom, cornerPaint)
        canvas.drawLine(cropRect.left, cropRect.bottom, cropRect.left, cropRect.bottom - cornerSize, cornerPaint)
        // 右下角
        canvas.drawLine(cropRect.right, cropRect.bottom, cropRect.right - cornerSize, cropRect.bottom, cornerPaint)
        canvas.drawLine(cropRect.right, cropRect.bottom, cropRect.right, cropRect.bottom - cornerSize, cornerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                touchMode = getTouchMode(event.x, event.y)
                return touchMode != TouchMode.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                handleMove(dx, dy)
                lastX = event.x
                lastY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                touchMode = TouchMode.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 获取触摸模式
     */
    private fun getTouchMode(x: Float, y: Float): TouchMode {
        val area = touchAreaSize

        // 检查四个角
        if (isInArea(x, y, cropRect.left, cropRect.top, area)) return TouchMode.RESIZE_LEFT_TOP
        if (isInArea(x, y, cropRect.right, cropRect.top, area)) return TouchMode.RESIZE_RIGHT_TOP
        if (isInArea(x, y, cropRect.left, cropRect.bottom, area)) return TouchMode.RESIZE_LEFT_BOTTOM
        if (isInArea(x, y, cropRect.right, cropRect.bottom, area)) return TouchMode.RESIZE_RIGHT_BOTTOM

        // 检查四条边
        if (isNearLine(x, y, cropRect.left, cropRect.top, cropRect.left, cropRect.bottom, area)) return TouchMode.RESIZE_LEFT
        if (isNearLine(x, y, cropRect.right, cropRect.top, cropRect.right, cropRect.bottom, area)) return TouchMode.RESIZE_RIGHT
        if (isNearLine(x, y, cropRect.left, cropRect.top, cropRect.right, cropRect.top, area)) return TouchMode.RESIZE_TOP
        if (isNearLine(x, y, cropRect.left, cropRect.bottom, cropRect.right, cropRect.bottom, area)) return TouchMode.RESIZE_BOTTOM

        // 检查是否在裁剪区域内（移动）
        if (cropRect.contains(x, y)) return TouchMode.MOVE

        return TouchMode.NONE
    }

    private fun isInArea(x: Float, y: Float, targetX: Float, targetY: Float, area: Float): Boolean {
        return Math.abs(x - targetX) < area && Math.abs(y - targetY) < area
    }

    private fun isNearLine(x: Float, y: Float, x1: Float, y1: Float, x2: Float, y2: Float, area: Float): Boolean {
        if (x1 == x2) { // 竖线
            return Math.abs(x - x1) < area && y in (y1 - area)..(y2 + area)
        } else { // 横线
            return Math.abs(y - y1) < area && x in (x1 - area)..(x2 + area)
        }
    }

    /**
     * 处理移动
     */
    private fun handleMove(dx: Float, dy: Float) {
        val w = width.toFloat()
        val h = height.toFloat()

        when (touchMode) {
            TouchMode.MOVE -> {
                var newLeft = cropRect.left + dx
                var newTop = cropRect.top + dy
                var newRight = cropRect.right + dx
                var newBottom = cropRect.bottom + dy

                // 边界检查
                if (newLeft < 0) { newRight -= newLeft; newLeft = 0f }
                if (newTop < 0) { newBottom -= newTop; newTop = 0f }
                if (newRight > w) { newLeft -= (newRight - w); newRight = w }
                if (newBottom > h) { newTop -= (newBottom - h); newBottom = h }

                cropRect.set(newLeft, newTop, newRight, newBottom)
            }
            TouchMode.RESIZE_LEFT_TOP -> {
                val newLeft = (cropRect.left + dx).coerceIn(0f, cropRect.right - minCropSize)
                val newTop = (cropRect.top + dy).coerceIn(0f, cropRect.bottom - minCropSize)
                cropRect.left = newLeft
                cropRect.top = newTop
            }
            TouchMode.RESIZE_RIGHT_TOP -> {
                val newRight = (cropRect.right + dx).coerceIn(cropRect.left + minCropSize, w)
                val newTop = (cropRect.top + dy).coerceIn(0f, cropRect.bottom - minCropSize)
                cropRect.right = newRight
                cropRect.top = newTop
            }
            TouchMode.RESIZE_LEFT_BOTTOM -> {
                val newLeft = (cropRect.left + dx).coerceIn(0f, cropRect.right - minCropSize)
                val newBottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minCropSize, h)
                cropRect.left = newLeft
                cropRect.bottom = newBottom
            }
            TouchMode.RESIZE_RIGHT_BOTTOM -> {
                val newRight = (cropRect.right + dx).coerceIn(cropRect.left + minCropSize, w)
                val newBottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minCropSize, h)
                cropRect.right = newRight
                cropRect.bottom = newBottom
            }
            TouchMode.RESIZE_LEFT -> {
                cropRect.left = (cropRect.left + dx).coerceIn(0f, cropRect.right - minCropSize)
            }
            TouchMode.RESIZE_RIGHT -> {
                cropRect.right = (cropRect.right + dx).coerceIn(cropRect.left + minCropSize, w)
            }
            TouchMode.RESIZE_TOP -> {
                cropRect.top = (cropRect.top + dy).coerceIn(0f, cropRect.bottom - minCropSize)
            }
            TouchMode.RESIZE_BOTTOM -> {
                cropRect.bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minCropSize, h)
            }
            TouchMode.NONE -> {}
        }
    }

    /**
     * 获取裁剪区域（相对坐标 0-1）
     */
    fun getCropRect(): RectF {
        val w = width.toFloat()
        val h = height.toFloat()
        return RectF(
            cropRect.left / w,
            cropRect.top / h,
            cropRect.right / w,
            cropRect.bottom / h
        )
    }
}
