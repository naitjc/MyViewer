package com.chen.myviewer.reader.comic

import android.content.Context
import android.graphics.*
import android.view.*
import kotlinx.coroutines.*
import kotlin.math.*
import java.io.File

/** Only the visible source region is decoded, including when the image is a very long strip. */
class ComicImageView(context: Context) : View(context) {
    var onMenu: () -> Unit = {}
    var onPage: (Boolean) -> Unit = {}
    var onFocus: (Float) -> Unit = {}
    var onError: (String) -> Unit = {}
    var interactive = true
    var rtl = false
    private var file: File? = null
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var decoder: BitmapRegionDecoder? = null
    private val decoderLock = Any()
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var tile: Bitmap? = null
    private var tileRect = Rect()
    private var pendingKey = ""
    private var tileJob: Job? = null
    private var generation = 0
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var initialZoom = 1f
    private var wasScaling = false
    private var lastError = ""
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val touch = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { performClick(); onMenu(); return true }
        override fun onDoubleTap(e: MotionEvent): Boolean { zoomAt(if (zoom > 1.1f) 1f else 2.5f, e.x, e.y); return true }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (zoom > 1.01f) { panX -= distanceX; panY -= distanceY; clamp(); invalidate(); reportFocus(); return true }
            return false
        }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (zoom > 1.01f || wasScaling || e1 == null) return false
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
    fun setImage(value: File, requestedZoom: Float = 1f) {
        if (file?.absolutePath == value.absolutePath && decoder != null) return
        generation++; val token = generation; file = value; initialZoom = requestedZoom
        sourceWidth = 0; sourceHeight = 0; tile = null; pendingKey = ""; lastError = ""
        zoom = requestedZoom; panX = 0f; panY = 0f
        scope.launch {
            try {
                val created = withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION") val d = BitmapRegionDecoder.newInstance(value.absolutePath, false)
                    requireNotNull(d) { "图片解码失败" }
                }
                if (token != generation || !isActive) { created.recycle(); return@launch }
                synchronized(decoderLock) { decoder?.recycle(); decoder = created }
                sourceWidth = created.width; sourceHeight = created.height; invalidate()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { lastError = e.message ?: "图片无法显示"; onError(lastError); invalidate() }
        }
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
        val visible = Rect()
        if (!getLocalVisibleRect(visible)) return
        // A long-strip view can be taller than the entire screen; never decode its full height.
        visible.bottom = min(visible.bottom, visible.top + resources.displayMetrics.heightPixels * 2)
        val wanted = Rect(
            floor((visible.left - ox) / s).toInt().coerceIn(0, sourceWidth - 1),
            floor((visible.top - oy) / s).toInt().coerceIn(0, sourceHeight - 1),
            ceil((visible.right - ox) / s).toInt().coerceIn(1, sourceWidth),
            ceil((visible.bottom - oy) / s).toInt().coerceIn(1, sourceHeight),
        )
        if (wanted.width() <= 0 || wanted.height() <= 0) return
        var sample = 1
        while (sample * 2 * s < 1f) sample *= 2
        if (tile != null) {
            val destination = RectF(ox + tileRect.left * s, oy + tileRect.top * s, ox + tileRect.right * s, oy + tileRect.bottom * s)
            canvas.drawBitmap(tile!!, null, destination, paint)
        }
        val quantum = 256 * sample
        val region = Rect(
            ((wanted.left / quantum - 1) * quantum).coerceAtLeast(0), ((wanted.top / quantum - 1) * quantum).coerceAtLeast(0),
            ((wanted.right / quantum + 2) * quantum).coerceAtMost(sourceWidth), ((wanted.bottom / quantum + 2) * quantum).coerceAtMost(sourceHeight),
        )
        val key = "$generation:${region.flattenToString()}:$sample"
        if (key != pendingKey) requestTile(region, sample, key)
    }
    private fun requestTile(region: Rect, sample: Int, key: String) {
        pendingKey = key; val token = generation
        tileJob?.cancel()
        tileJob = scope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { synchronized(decoderLock) {
                    decoder?.takeUnless { it.isRecycled }?.decodeRegion(region, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 })
                } }
                if (token == generation && key == pendingKey && bitmap != null) { tile = bitmap; tileRect = Rect(region); invalidate() }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (token == generation) { lastError = "图片暂时无法解码，请重开这一页"; onError(lastError) } }
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) wasScaling = false
        scale.onTouchEvent(event); touch.onTouchEvent(event)
        if (zoom > 1.01f || scale.isInProgress) parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { clamp(); pendingKey = ""; super.onSizeChanged(w, h, oldw, oldh) }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!scope.isActive) { scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate); file?.let { setImage(it, initialZoom) } }
    }
    override fun onDetachedFromWindow() {
        generation++; scope.cancel(); synchronized(decoderLock) { decoder?.recycle(); decoder = null }; tile = null
        super.onDetachedFromWindow()
    }
}
