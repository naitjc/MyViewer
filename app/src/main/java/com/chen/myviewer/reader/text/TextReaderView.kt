package com.chen.myviewer.reader.text

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import com.chen.myviewer.data.ReaderSettings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TextReaderView(context: Context) : View(context) {
    var onMenu: () -> Unit = {}
    var onPosition: (Int, Boolean) -> Unit = { _, _ -> }
    var onBoundary: (String) -> Unit = {}
    private var document: TextDocument? = null
    private var settings = ReaderSettings()
    private var mode = "PAGE"
    private var paginator: TextPaginator? = null
    private var page: TextPage? = null
    private var target: TextPage? = null
    private var anchor = 0
    private var scrollChunk = 0
    private var scrollYLocal = 0f
    private var turn = 0f
    private var next = true
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var animator: ValueAnimator? = null
    private val scroller = OverScroller(context)
    private var lastFling = 0
    private var velocity: VelocityTracker? = null
    private val density = resources.displayMetrics.density
    private val margin = 24f * density
    private val top = 36f * density
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("serif", Typeface.NORMAL) }
    private val decoration = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private var foreground = Color.rgb(49, 44, 37)
    private var background = Color.rgb(243, 234, 217)
    private var lastReport = 0L
    val currentOffset: Int get() = anchor
    val readyForAutoReading: Boolean get() = paginator != null && page != null && animator?.isRunning != true

    fun nextAutomatically(): Boolean {
        if (!readyForAutoReading || animator?.isRunning == true) return true
        val current = page ?: return true
        if (paginator?.adjacent(current, true) == null) return false
        prepare(true); animateTurn(true)
        return true
    }

    fun scrollAutomatically(distance: Float): Boolean {
        val engine = paginator ?: return true
        if (engine.chunks.isEmpty()) return false
        scroller.forceFinished(true)
        moveScroll(distance)
        val lastTop = max(0f, engine.chunk(engine.chunks.lastIndex).layout.height - (height - top - margin - 20 * density))
        return scrollChunk < engine.chunks.lastIndex || scrollYLocal < lastTop - .01f
    }

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean { scroller.forceFinished(true); return true }
        override fun onSingleTapUp(e: MotionEvent): Boolean { performClick(); onMenu(); return true }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean { moveScroll(distanceY); return true }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            lastFling = 0; scroller.fling(0, 0, 0, (-velocityY).toInt(), 0, 0, -100000, 100000); postInvalidateOnAnimation(); return true
        }
    })

    fun configure(doc: TextDocument, value: ReaderSettings, readingMode: String, initialOffset: Int) {
        val changed = document !== doc
        val reflow = changed || value.fontSize != settings.fontSize || value.lineSpacing != settings.lineSpacing
        val modeChanged = mode != readingMode
        if (changed) anchor = initialOffset
        document = doc; settings = value; mode = readingMode
        val colors = when (value.background) {
            "NIGHT" -> Color.rgb(29, 33, 32) to Color.rgb(205, 209, 201)
            "WHITE" -> Color.rgb(250, 250, 247) to Color.rgb(35, 40, 37)
            "GREEN" -> Color.rgb(219, 232, 211) to Color.rgb(37, 55, 37)
            else -> Color.rgb(243, 234, 217) to Color.rgb(49, 44, 37)
        }
        background = colors.first; foreground = colors.second
        textPaint.color = foreground
        if (reflow || modeChanged) rebuild() else invalidate()
    }
    fun goTo(offset: Int) { anchor = offset.coerceIn(0, ((document?.text?.length ?: 1) - 1).coerceAtLeast(0)); rebuild() }
    fun flush() { report(true) }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); rebuild() }
    private fun rebuild() {
        cancelTurn(); scroller.forceFinished(true)
        val doc = document ?: return
        if (width < margin * 2 + 10 || height < top * 2 + 10 || doc.text.isEmpty()) { invalidate(); return }
        textPaint.textSize = settings.fontSize * resources.displayMetrics.scaledDensity
        paginator = TextPaginator(doc, textPaint, (width - margin * 2).toInt(), (height - top - margin - 30 * density).toInt().coerceAtLeast(1), settings.lineSpacing)
        val engine = paginator ?: return
        page = engine.pageAt(anchor)
        scrollChunk = engine.chunkIndex(anchor)
        val chunk = engine.chunk(scrollChunk)
        val line = chunk.layout.getLineForOffset((anchor - chunk.chunk.start).coerceIn(0, chunk.chunk.end - chunk.chunk.start))
        scrollYLocal = chunk.layout.getLineTop(line).toFloat()
        if (mode == "PAGE") anchor = page?.start ?: anchor
        invalidate(); report(true)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); canvas.drawColor(background)
        val engine = paginator ?: return
        if (mode == "SCROLL") drawScroll(canvas, engine)
        else {
            val current = page ?: return
            val destination = target
            if (destination == null || turn <= 0f) drawPage(canvas, current)
            else if (settings.animation == "CURL") drawCurl(canvas, current, destination) else drawSlide(canvas, current, destination)
        }
        decoration.color = foreground; decoration.alpha = 150; decoration.textSize = 11 * resources.displayMetrics.scaledDensity; decoration.typeface = Typeface.DEFAULT
        val label = document?.chapterAt(anchor).orEmpty().take(24)
        canvas.drawText(label, margin, 20 * density, decoration)
        val progress = if (document!!.text.isEmpty()) 0 else (anchor * 100L / document!!.text.length).toInt()
        val caption = "$progress%"
        canvas.drawText(caption, width - margin - decoration.measureText(caption), height - 14 * density, decoration)
        decoration.alpha = 255
    }
    private fun drawPage(canvas: Canvas, value: TextPage) {
        canvas.save(); canvas.translate(margin, top); value.layout.draw(canvas); canvas.restore()
    }
    private fun drawSlide(canvas: Canvas, current: TextPage, destination: TextPage) {
        val sign = if (next) -1 else 1
        canvas.save(); canvas.translate(sign * turn * width, 0f); drawPage(canvas, current); canvas.restore()
        canvas.save(); canvas.translate(sign * (turn - 1) * width, 0f); drawPage(canvas, destination); canvas.restore()
    }
    private fun drawCurl(canvas: Canvas, current: TextPage, destination: TextPage) {
        drawPage(canvas, destination)
        val edge = if (next) width * (1 - turn) else width * turn
        val bend = min(width * .32f, width * min(turn, 1 - turn) * 1.4f).coerceAtLeast(1f)
        canvas.save()
        if (next) canvas.clipRect(0f, 0f, edge, height.toFloat()) else canvas.clipRect(edge, 0f, width.toFloat(), height.toFloat())
        canvas.drawColor(background); drawPage(canvas, current); canvas.restore()
        val outside = if (next) edge + bend else edge - bend
        val fold = Path().apply {
            moveTo(edge, 0f); quadTo(outside, height * .15f, outside, height * .5f)
            quadTo(outside, height * .85f, edge, height.toFloat()); close()
        }
        decoration.color = blend(background, foreground, .08f); canvas.drawPath(fold, decoration)
        canvas.save(); canvas.clipPath(fold); canvas.translate(edge * 2, 0f); canvas.scale(-1f, 1f)
        textPaint.alpha = 48; drawPage(canvas, current); textPaint.alpha = 255; canvas.restore()
        val shadowWidth = max(8f * density, bend * .22f)
        shadow.shader = LinearGradient(edge - shadowWidth, 0f, edge + shadowWidth, 0f, intArrayOf(Color.TRANSPARENT, Color.argb(75, 0, 0, 0), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(edge - shadowWidth, 0f, edge + shadowWidth, height.toFloat(), shadow); shadow.shader = null
    }
    private fun drawScroll(canvas: Canvas, engine: TextPaginator) {
        canvas.save(); canvas.clipRect(margin, top, width - margin, height - margin - 20 * density)
        var y = top - scrollYLocal; var index = scrollChunk
        while (index < engine.chunks.size && y < height) {
            val chunk = engine.chunk(index)
            canvas.save(); canvas.translate(margin, y); chunk.layout.draw(canvas); canvas.restore()
            y += chunk.layout.height; index++
        }
        canvas.restore()
    }
    private fun moveScroll(delta: Float) {
        val engine = paginator ?: return
        if (engine.chunks.isEmpty()) return
        scrollYLocal += delta
        while (scrollYLocal < 0 && scrollChunk > 0) { scrollChunk--; scrollYLocal += engine.chunk(scrollChunk).layout.height }
        scrollYLocal = scrollYLocal.coerceAtLeast(0f)
        while (scrollChunk < engine.chunks.lastIndex && scrollYLocal >= engine.chunk(scrollChunk).layout.height) { scrollYLocal -= engine.chunk(scrollChunk).layout.height; scrollChunk++ }
        if (scrollChunk == engine.chunks.lastIndex) {
            val visibleHeight = height - top - margin - 20 * density
            scrollYLocal = scrollYLocal.coerceAtMost(max(0f, engine.chunk(scrollChunk).layout.height - visibleHeight))
        }
        val chunk = engine.chunk(scrollChunk)
        val line = chunk.layout.getLineForVertical(scrollYLocal.toInt())
        anchor = chunk.chunk.start + chunk.layout.getLineStart(line)
        report(false); invalidate()
    }
    override fun computeScroll() {
        if (scroller.computeScrollOffset()) { val now = scroller.currY; moveScroll((now - lastFling).toFloat()); lastFling = now; postInvalidateOnAnimation() }
        else if (mode == "SCROLL") report(true)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mode == "SCROLL") {
            val handled = detector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) report(true)
            return handled || true
        }
        if (animator?.isRunning == true) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; moved = false
                velocity?.recycle(); velocity = VelocityTracker.obtain(); velocity?.addMovement(event); return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                val dx = event.x - downX
                if (abs(dx) > ViewConfiguration.get(context).scaledTouchSlop && abs(dx) > abs(event.y - downY)) moved = true
                if (moved) { prepare(dx < 0); turn = (abs(dx) / width).coerceIn(0f, 1f); invalidate() }
                return true
            }
            MotionEvent.ACTION_UP -> {
                velocity?.addMovement(event); velocity?.computeCurrentVelocity(1000)
                if (moved && target != null) {
                    val speed = velocity?.xVelocity ?: 0f
                    animateTurn(turn > .25f || (abs(speed) > 750 && (speed < 0) == next))
                } else if (!moved) {
                    performClick()
                    when { event.x < width * .28f -> { prepare(false); animateTurn(true) }; event.x > width * .72f -> { prepare(true); animateTurn(true) }; else -> onMenu() }
                } else cancelTurn()
                velocity?.recycle(); velocity = null; return true
            }
            MotionEvent.ACTION_CANCEL -> { animateTurn(false); velocity?.recycle(); velocity = null; return true }
        }
        return true
    }
    private fun prepare(forward: Boolean) {
        if (target == null || next != forward) { next = forward; target = page?.let { paginator?.adjacent(it, forward) } }
    }
    private fun animateTurn(commit: Boolean) {
        if (target == null) { onBoundary(if (next) "已经到末尾了" else "已经是开头了"); cancelTurn(); return }
        val selected = target
        var cancelled = false
        animator = ValueAnimator.ofFloat(turn, if (commit) 1f else 0f).apply {
            duration = (240 * (if (commit) 1 - turn else turn)).toLong().coerceAtLeast(90)
            interpolator = DecelerateInterpolator()
            addUpdateListener { turn = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled && commit && selected != null) { page = selected; anchor = selected.start; report(true) }
                    target = null; turn = 0f; invalidate()
                }
            }); start()
        }
    }
    private fun cancelTurn() { animator?.cancel(); animator = null; target = null; turn = 0f }
    private fun report(force: Boolean) {
        val now = android.os.SystemClock.uptimeMillis()
        if (force || now - lastReport >= 160) { lastReport = now; onPosition(anchor, force) }
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDetachedFromWindow() { cancelTurn(); scroller.forceFinished(true); velocity?.recycle(); velocity = null; super.onDetachedFromWindow() }
    private fun blend(a: Int, b: Int, ratio: Float) = Color.rgb((Color.red(a) * (1 - ratio) + Color.red(b) * ratio).toInt(), (Color.green(a) * (1 - ratio) + Color.green(b) * ratio).toInt(), (Color.blue(a) * (1 - ratio) + Color.blue(b) * ratio).toInt())
}
