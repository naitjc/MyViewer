package com.chen.myviewer.reader.comic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import java.io.Closeable
import java.io.File
import kotlin.math.abs

enum class ComicTapAction { PREVIOUS, NEXT, MENU }

object ComicTouchPolicy {
    fun tap(x: Float, width: Int, rtl: Boolean, zoomed: Boolean): ComicTapAction {
        if (width <= 0 || zoomed) return ComicTapAction.MENU
        val left = x < width * .3f
        val right = x > width * .7f
        if (!left && !right) return ComicTapAction.MENU
        return if (right != rtl) ComicTapAction.NEXT else ComicTapAction.PREVIOUS
    }
}

data class ComicTile(val sample: Int, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun rect() = Rect(left, top, right, bottom)
}

object ComicTilePlanner {
    private const val EDGE = 512
    fun plan(width: Int, height: Int, sample: Int, visible: Rect): List<ComicTile> {
        if (width <= 0 || height <= 0 || visible.isEmpty) return emptyList()
        val step = EDGE.toLong() * sample
        val firstX = (visible.left / step).toInt()
        val lastX = ((visible.right - 1) / step).toInt()
        val firstY = (visible.top / step).toInt()
        val lastY = ((visible.bottom - 1) / step).toInt()
        val maxY = ((height - 1) / step).toInt()
        val centerX = (firstX + lastX) / 2f
        val centerY = (firstY + lastY) / 2f
        val visibleTiles = mutableListOf<Pair<Float, ComicTile>>()
        val ahead = mutableListOf<ComicTile>()
        for (y in (firstY - 1).coerceAtLeast(0)..(lastY + 1).coerceAtMost(maxY)) {
            for (x in firstX..lastX) {
                val tile = ComicTile(sample, (x * step).toInt(), (y * step).toInt(), ((x + 1) * step).coerceAtMost(width.toLong()).toInt(), ((y + 1) * step).coerceAtMost(height.toLong()).toInt())
                if (y in firstY..lastY) visibleTiles += (abs(x - centerX) + abs(y - centerY)) to tile else ahead += tile
            }
        }
        return visibleTiles.sortedBy { it.first }.map { it.second } + ahead
    }
}

internal interface ComicRaster : Closeable {
    val width: Int
    val height: Int
    fun decode(tile: ComicTile): Bitmap?
}

internal class AndroidComicRaster(file: File) : ComicRaster {
    @Suppress("DEPRECATION")
    private val decoder = requireNotNull(BitmapRegionDecoder.newInstance(file.absolutePath, false)) { "图片解码失败" }
    private val lock = Any()
    override val width: Int = decoder.width
    override val height: Int = decoder.height
    override fun decode(tile: ComicTile): Bitmap? = synchronized(lock) {
        if (decoder.isRecycled) null else decoder.decodeRegion(tile.rect(), BitmapFactory.Options().apply { inSampleSize = tile.sample; inPreferredConfig = Bitmap.Config.RGB_565 })
    }
    override fun close() = synchronized(lock) { if (!decoder.isRecycled) decoder.recycle() }
}

data class ComicPage(val file: File, val size: ImageSize, val preview: Bitmap)

object ComicPreviews {
    fun decode(file: File, size: ImageSize): Bitmap {
        var sample = 1
        while (size.width / sample > 720 || size.height / sample > 2048 || size.width.toLong() * size.height / sample / sample > 1_000_000) sample *= 2
        return requireNotNull(BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 })) { "图片预览无法解码" }
    }
}
