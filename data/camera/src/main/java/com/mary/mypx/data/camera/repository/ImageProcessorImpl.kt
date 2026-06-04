package com.mary.mypx.data.camera.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.mary.mypx.domain.repository.ImageProcessor
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * 图像处理器实现 - 使用 Android Bitmap API 处理图像
 *
 * 【职责】
 * 实现 Domain 层定义的 ImageProcessor 接口，
 * 封装 Android Bitmap 操作细节。
 */
class ImageProcessorImpl : ImageProcessor {

    /**
     * 裁剪图片
     */
    override fun crop(
        imageData: ByteArray,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): ByteArray {
        val originalBitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
            ?: throw Exception("Failed to decode image")

        // 边界检查
        val safeX = x.coerceIn(0, originalBitmap.width - 1)
        val safeY = y.coerceIn(0, originalBitmap.height - 1)
        val safeWidth = width.coerceIn(1, originalBitmap.width - safeX)
        val safeHeight = height.coerceIn(1, originalBitmap.height - safeY)

        // 裁剪
        val croppedBitmap = Bitmap.createBitmap(
            originalBitmap,
            safeX,
            safeY,
            safeWidth,
            safeHeight
        )

        // 转换为字节数组
        val result = bitmapToByteArray(croppedBitmap)

        // 释放资源
        if (croppedBitmap !== originalBitmap) {
            croppedBitmap.recycle()
        }
        originalBitmap.recycle()

        return result
    }

    /**
     * 调整图片色彩
     */
    override fun adjustColors(
        imageData: ByteArray,
        brightness: Int,
        contrast: Int,
        saturation: Int
    ): ByteArray {
        // 没有调整，直接返回
        if (brightness == 0 && contrast == 0 && saturation == 0) {
            return imageData
        }

        val originalBitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
            ?: throw Exception("Failed to decode image")

        val width = originalBitmap.width
        val height = originalBitmap.height
        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // 计算调整系数
        val brightnessFactor = brightness / 100.0f * 255
        val contrastFactor = (259.0f * (contrast + 255)) / (255.0f * (259 - contrast))
        val saturationFactor = 1.0f + saturation / 100.0f

        // 逐像素处理
        val pixels = IntArray(width * height)
        originalBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            var r = Color.red(pixel)
            var g = Color.green(pixel)
            var b = Color.blue(pixel)
            val a = Color.alpha(pixel)

            // 调整亮度
            if (brightness != 0) {
                r = (r + brightnessFactor).roundToInt().coerceIn(0, 255)
                g = (g + brightnessFactor).roundToInt().coerceIn(0, 255)
                b = (b + brightnessFactor).roundToInt().coerceIn(0, 255)
            }

            // 调整对比度
            if (contrast != 0) {
                r = (contrastFactor * (r - 128) + 128).roundToInt().coerceIn(0, 255)
                g = (contrastFactor * (g - 128) + 128).roundToInt().coerceIn(0, 255)
                b = (contrastFactor * (b - 128) + 128).roundToInt().coerceIn(0, 255)
            }

            // 调整饱和度
            if (saturation != 0) {
                val gray = 0.299f * r + 0.587f * g + 0.114f * b
                r = (gray + saturationFactor * (r - gray)).roundToInt().coerceIn(0, 255)
                g = (gray + saturationFactor * (g - gray)).roundToInt().coerceIn(0, 255)
                b = (gray + saturationFactor * (b - gray)).roundToInt().coerceIn(0, 255)
            }

            pixels[i] = Color.argb(a, r, g, b)
        }

        resultBitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        val result = bitmapToByteArray(resultBitmap)

        originalBitmap.recycle()
        resultBitmap.recycle()

        return result
    }

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        return outputStream.toByteArray()
    }
}
