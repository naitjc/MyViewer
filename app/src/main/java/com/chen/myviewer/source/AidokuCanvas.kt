package com.chen.myviewer.source

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** Bitmap operations used by image sources to restore scrambled page strips. */
internal class AidokuCanvas(private val host: AidokuHost) {
    private data class Surface(val bitmap: Bitmap, val canvas: Canvas = Canvas(bitmap))
    private val allocated = mutableSetOf<Bitmap>()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun image(bytes: ByteArray): Int {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        checkSize(bounds.outWidth, bounds.outHeight)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("源图片无法解码。")
        allocated.add(bitmap)
        return host.store(bitmap)
    }
    private fun checkSize(width: Int, height: Int) {
        require(width > 0 && height > 0 && width.toLong() * height <= 16_777_216) { "图片重组尺寸超出支持范围。" }
        require(allocated.sumOf { it.allocationByteCount.toLong() } + width.toLong() * height * 4 <= 128L * 1024 * 1024) { "图片重组内存超出限制。" }
    }
    fun data(id: Int): ByteArray {
        val bitmap = host.values[id] as? Bitmap ?: error("漫画源未返回有效图片。")
        return ByteArrayOutputStream().also { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }.toByteArray()
    }
    fun checkpoint(): Set<Bitmap> = allocated.toSet()
    fun releaseAfter(checkpoint: Set<Bitmap>) {
        val pending = allocated - checkpoint
        host.values.entries.removeAll { (_, value) -> value in pending || (value is Surface && value.bitmap in pending) }
        pending.forEach { it.recycle() }
        allocated.removeAll(pending)
    }
    fun call(name: String, args: LongArray): Long {
        fun number(index: Int) = Float.fromBits(args[index].toInt())
        val surface = host.values[args.getOrElse(0) { -1 }.toInt()] as? Surface
        val bitmap = host.values[args.getOrElse(0) { -1 }.toInt()] as? Bitmap
        return when (name) {
            "new_context" -> {
                val width = number(0).roundToInt()
                val height = number(1).roundToInt()
                checkSize(width, height)
                val created = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                allocated.add(created)
                host.store(Surface(created)).toLong()
            }
            "new_image" -> image(host.memory.readBytes(args[0].toInt(), args[1].toInt())).toLong()
            "get_image" -> host.store(surface?.bitmap).toLong()
            "get_image_width" -> (bitmap?.width?.toFloat() ?: 0f).toRawBits().toLong()
            "get_image_height" -> (bitmap?.height?.toFloat() ?: 0f).toRawBits().toLong()
            "get_image_data" -> host.store(data(args[0].toInt())).toLong()
            "copy_image", "draw_image" -> {
                val context = surface ?: error("漫画源画布无效。")
                val source = host.values[args[1].toInt()] as? Bitmap ?: error("漫画源图片无效。")
                val rect = if (name == "copy_image") Rect(number(2).roundToInt(), number(3).roundToInt(), (number(2) + number(4)).roundToInt(), (number(3) + number(5)).roundToInt()) else null
                val index = if (name == "copy_image") 6 else 2
                val destination = RectF(number(index), number(index + 1), number(index) + number(index + 2), number(index + 1) + number(index + 3))
                context.canvas.drawBitmap(source, rect, destination, paint)
                0
            }
            "set_transform" -> {
                val matrix = Matrix().apply {
                    postTranslate(number(1), number(2))
                    postScale(number(3), number(4))
                    postRotate(Math.toDegrees(number(5).toDouble()).toFloat())
                }
                (surface ?: error("漫画源画布无效。")).canvas.setMatrix(matrix)
                0
            }
            else -> error("暂不支持漫画源画布接口 canvas.$name。")
        }
    }
}
