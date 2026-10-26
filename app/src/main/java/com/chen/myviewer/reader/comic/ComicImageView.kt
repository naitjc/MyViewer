package com.chen.myviewer.reader.comic

import android.content.Context
import android.graphics.*
import android.util.LruCache
import android.view.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.*

/** A full-image preview remains underneath bounded, reusable high-resolution tiles. */
class ComicImageView internal constructor(context: Context, private val openRaster: (File) -> ComicRaster) : View(context) {
    constructor(context: Context) : this(context, { AndroidComicRaster(it) })

    var onMenu: () -> Unit = {}
    var onPage: (Boolean) -> Unit = {}
    var onFocus: (Float) -> Unit = {}
    var onError: (String) -> Unit = {}
    var interactive = true
    var rtl = false
    var tapToTurn = true
    private var file: File? = null
    private var bound = false
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var raster: ComicRaster? = null
    private var preview: Bitmap? = null
    private var preparedPage: ComicPage? = null
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var generation = 0
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var initialZoom = 1f
    private var wasScaling = false
    private var lastError = ""
    private var loadJob: Job? = null
    private var tileJob: Job? = null
    private var wantedTiles = emptyList<ComicTile>()
    private val failedTiles = mutableSetOf<ComicTile>()
    private val tiles = object : LruCache<ComicTile, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: ComicTile, value: Bitmap) = value.allocationByteCount
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val visible = Rect()
    private val sourceVisible = Rect()
    private val destination = RectF()
    private var viewportTop = -1
    private var viewportBottom = -1

    private val touch = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            when (ComicTouchPolicy.tap(e.x, width, rtl, zoom > 1.01f || !tapToTurn)) {
                ComicTapAction.PREVIOUS -> onPage(false)
                ComicTapAction.NEXT -> onPage(true)
                ComicTapAction.MENU -> performClick()
            }
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean { zoomAt(if (zoom > 1.1f) 1f else 2.5f, e.x, e.y); return true }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (zoom > 1.01f) { panX -= distanceX; panY -= distanceY; clamp(); invalidate(); reportFocus(); return true }
            return false
        }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (zoom > 1.01f || wasScaling || !tapToTurn || e1 == null) return false
            if (abs(e2.x - e1.x) > 45 * resources.displayMetrics.density && abs(velocityX) > abs(velocityY)) {
                onPage(if (rtl) velocityX > 0 else velocityX < 0); return true
            }
            return false
        }
    })
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean { wasScaling = true; parent?.requestDisallowInterceptTouchEvent(true); return true }
        override fun onScale(detector: ScaleGestureDetector): Boolean { zoomAt((zoom * detector.scaleFactor).coerceIn(1f, 8f), detector.focusX, detector.focusY); return true }
    })

    fun setPage(page: ComicPage, requestedZoom: Float = 1f) = setImage(page.file, requestedZoom, page)

    fun setImage(value: File, requestedZoom: Float = 1f, prepared: ComicPage? = null) {
        if (bound && file?.absolutePath == value.absolutePath && scope.isActive &&
            (lastError.isEmpty() || prepared == null || prepared.preview === preparedPage?.preview)) return
        generation++; val token = generation
        bound = true; file = value; initialZoom = requestedZoom
        loadJob?.cancel(); tileJob?.cancel(); tileJob = null
        disposeRaster(raster); raster = null
        sourceWidth = prepared?.size?.width ?: 0; sourceHeight = prepared?.size?.height ?: 0
        preparedPage = prepared; preview = prepared?.preview; tiles.evictAll(); wantedTiles = emptyList(); failedTiles.clear(); lastError = ""
        zoom = requestedZoom; panX = 0f; panY = 0f
        invalidate()
        loadJob = scope.launch {
            var opened: ComicRaster? = null
            try {
                val ready = withContext(renderDispatcher) {
                    val created = openRaster(value); opened = created
                    prepared?.preview ?: ComicPreviews.decode(value, ImageSize(created.width, created.height))
                }
                if (token != generation) return@launch
                raster = opened; preview = ready
                sourceWidth = opened!!.width; sourceHeight = opened!!.height
                preparedPage = ComicPage(value, ImageSize(sourceWidth, sourceHeight), ready)
                invalidate()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (token == generation) { lastError = e.message ?: "图片无法显示"; onError(lastError); invalidate() } }
            finally { if (opened !== raster) disposeRaster(opened) }
        }
    }

    /** Lazy-list translation does not necessarily redraw an Android View's display list. */
    fun updateViewport(top: Int, bottom: Int) {
        if (viewportTop == top && viewportBottom == bottom) return
        viewportTop = top; viewportBottom = bottom
        invalidate()
    }
    private fun baseScale(): Float = if (sourceWidth == 0 || sourceHeight == 0) 1f else min(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
    private fun originX(s: Float) = (width - sourceWidth * s) / 2 + panX
    private fun originY(s: Float) = (height - sourceHeight * s) / 2 + panY
    private fun zoomAt(value: Float, x: Float, y: Float) {
        val old = baseScale() * zoom
        if (sourceWidth == 0 || old <= 0) return
        val sx = (x - originX(old)) / old; val sy = (y - originY(old)) / old
        zoom = value.coerceIn(1f, 8f)
        val current = baseScale() * zoom
        panX = x - sx * current - (width - sourceWidth * current) / 2
        panY = y - sy * current - (height - sourceHeight * current) / 2
        clamp(); invalidate(); reportFocus()
    }
    private fun clamp() {
        val s = baseScale() * zoom
        val maxX = max(0f, (sourceWidth * s - width) / 2)
        val maxY = max(0f, (sourceHeight * s - height) / 2)
        panX = panX.coerceIn(-maxX, maxX); panY = panY.coerceIn(-maxY, maxY)
    }
    private fun reportFocus() {
        val s = baseScale() * zoom
        if (sourceHeight > 0 && s > 0) onFocus((-originY(s) / s / sourceHeight).coerceIn(0f, 1f))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sourceWidth == 0 || sourceHeight == 0) {
            paint.color = Color.GRAY; paint.textSize = 14 * resources.displayMetrics.scaledDensity
            canvas.drawText(if (lastError.isEmpty()) "图片加载中…" else lastError.take(30), 20f, height / 2f, paint); return
        }
        val s = baseScale() * zoom
        if (s <= 0f) return
        val ox = originX(s); val oy = originY(s)
        // A bounded preview covers the whole image before newly visible HD tiles arrive.
        preview?.let { bitmap ->
            destination.set(ox, oy, ox + sourceWidth * s, oy + sourceHeight * s)
            canvas.drawBitmap(bitmap, null, destination, paint)
        }
        if (viewportTop >= 0 && viewportBottom >= 0) visible.set(0, viewportTop.coerceAtLeast(0), width, viewportBottom.coerceAtMost(height))
        else if (!getLocalVisibleRect(visible)) return
        if (visible.isEmpty) return
        visible.bottom = min(visible.bottom, visible.top + resources.displayMetrics.heightPixels * 2)
        sourceVisible.set(
            floor((visible.left - ox) / s).toInt().coerceIn(0, sourceWidth - 1),
            floor((visible.top - oy) / s).toInt().coerceIn(0, sourceHeight - 1),
            ceil((visible.right - ox) / s).toInt().coerceIn(1, sourceWidth),
            ceil((visible.bottom - oy) / s).toInt().coerceIn(1, sourceHeight),
        )
        if (sourceVisible.isEmpty) return
        var sample = 1
        while (sample * 2 * s < 1f) sample *= 2
        var plan = ComicTilePlanner.plan(sourceWidth, sourceHeight, sample, sourceVisible)
        // Alpha images can require 4 bytes/pixel even with RGB_565 requested. Fit all visible
        // tiles within the real memory budget, otherwise an LRU can endlessly evict its own work.
        while (plan.count { it.left < sourceVisible.right && it.right > sourceVisible.left && it.top < sourceVisible.bottom && it.bottom > sourceVisible.top } > 12) {
            sample *= 2; plan = ComicTilePlanner.plan(sourceWidth, sourceHeight, sample, sourceVisible)
        }
        wantedTiles = plan.take(12)
        for (tile in wantedTiles) {
            val bitmap = tiles.get(tile) ?: continue
            destination.set(ox + tile.left * s, oy + tile.top * s, ox + tile.right * s, oy + tile.bottom * s)
            canvas.drawBitmap(bitmap, null, destination, paint)
        }
        startTileWorker()
    }
    private fun startTileWorker() {
        val source = raster ?: return
        if (tileJob?.isActive == true || wantedTiles.none { tiles.get(it) == null && it !in failedTiles }) return
        val token = generation
        tileJob = scope.launch {
            while (token == generation) {
                val tile = wantedTiles.firstOrNull { tiles.get(it) == null && it !in failedTiles } ?: break
                try {
                    val bitmap = withContext(renderDispatcher) { source.decode(tile) }
                    if (token != generation) break
                    if (bitmap != null) tiles.put(tile, bitmap) else failedTiles.add(tile)
                    invalidate()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { failedTiles.add(tile) }
            }
        }
    }
    @android.annotation.SuppressLint("ClickableViewAccessibility") // Confirmed taps route to performClick or page actions.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) wasScaling = false
        scale.onTouchEvent(event); touch.onTouchEvent(event)
        if (zoom > 1.01f || scale.isInProgress) parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }
    override fun performClick(): Boolean { super.performClick(); onMenu(); return true }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { clamp(); super.onSizeChanged(w, h, oldw, oldh) }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!scope.isActive) { scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate); bound = false; file?.let { setImage(it, initialZoom, preparedPage) } }
    }
    override fun onDetachedFromWindow() {
        generation++; bound = false; scope.cancel()
        disposeRaster(raster); raster = null; preview = null; tiles.evictAll()
        super.onDetachedFromWindow()
    }
    private fun disposeRaster(value: ComicRaster?) { if (value != null) cleanupScope.launch { runCatching { value.close() } } }
    companion object {
        private val renderDispatcher = Dispatchers.IO.limitedParallelism(3)
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
