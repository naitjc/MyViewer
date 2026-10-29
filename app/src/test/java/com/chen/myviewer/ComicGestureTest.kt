package com.chen.myviewer

import android.app.Activity
import android.graphics.*
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.chen.myviewer.reader.comic.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComicGestureTest {
    @Test fun heldMagnificationMovesTheImageWithTheFingerAndRestoresOnRelease() = withView { view, page ->
        view.setPage(page)
        val before = pixels(view)
        var turns = 0; var menus = 0
        val positions = mutableListOf<Float>()
        view.onPage = { turns++ }; view.onMenu = { menus++ }; view.onFocus = { positions += it }
        val down = SystemClock.uptimeMillis()
        event(view, down, MotionEvent.ACTION_DOWN, 180f to 400f)
        idle(ViewConfiguration.getLongPressTimeout().toLong() + 100)
        assertEquals(Color.RED, pixels(view)[400 * 400 + 180])
        event(view, down, MotionEvent.ACTION_MOVE, 320f to 400f)
        assertEquals("Dragging right must move the red image region right", Color.BLUE, pixels(view)[400 * 400 + 160])
        event(view, down, MotionEvent.ACTION_MOVE, 320f to 640f)
        assertEquals("Dragging down must move the red image region down", Color.BLUE, pixels(view)[280 * 400 + 320])
        assertTrue("Temporary focus must not overwrite the reading position", positions.isEmpty())
        event(view, down, MotionEvent.ACTION_UP, 320f to 640f); idle(400)
        assertArrayEquals(before, pixels(view))
        assertEquals(0, turns); assertEquals(0, menus)
    }

    @Test fun heldMagnificationRestoresOnReleaseWithoutTurningOrOpeningMenu() = withView { view, page ->
        view.setPage(page)
        var turns = 0; var menus = 0
        view.onPage = { turns++ }; view.onMenu = { menus++ }
        val before = pixels(view)
        val down = SystemClock.uptimeMillis()
        event(view, down, MotionEvent.ACTION_DOWN, 60f to 400f)
        idle(ViewConfiguration.getLongPressTimeout().toLong() + 100)
        assertFalse("Holding must magnify the existing canvas", before.contentEquals(pixels(view)))
        event(view, down, MotionEvent.ACTION_UP, 60f to 400f)
        idle(400)
        assertArrayEquals("Release must restore the original framing", before, pixels(view))
        assertEquals(0, turns); assertEquals(0, menus)
    }

    @Test fun cancellingHoldRestoresThePreviousPersistentZoom() = withView { view, page ->
        view.setPage(page)
        val original = pixels(view)
        val first = SystemClock.uptimeMillis()
        event(view, first, MotionEvent.ACTION_DOWN, 200f to 400f); idle(60)
        event(view, first, MotionEvent.ACTION_UP, 200f to 400f); idle(80)
        val second = SystemClock.uptimeMillis()
        event(view, second, MotionEvent.ACTION_DOWN, 200f to 400f); idle(60)
        event(view, second, MotionEvent.ACTION_UP, 200f to 400f); idle(400)
        val zoomed = pixels(view)
        assertFalse(original.contentEquals(zoomed))
        val hold = SystemClock.uptimeMillis()
        event(view, hold, MotionEvent.ACTION_DOWN, 60f to 400f)
        idle(ViewConfiguration.getLongPressTimeout().toLong() + 100)
        assertFalse(zoomed.contentEquals(pixels(view)))
        event(view, hold, MotionEvent.ACTION_MOVE, 320f to 620f)
        event(view, hold, MotionEvent.ACTION_CANCEL, 60f to 400f)
        assertArrayEquals(zoomed, pixels(view))
    }

    @Test fun pinchStaysOnTheSameCanvasAndZoomedEdgeTapsStillTurnPages() = withView { view, page ->
        view.setPage(page)
        val before = pixels(view); val root = view.rootView
        val turns = mutableListOf<Boolean>(); var menus = 0
        view.onPage = { turns += it }; view.onMenu = { menus++ }
        val down = SystemClock.uptimeMillis()
        event(view, down, MotionEvent.ACTION_DOWN, 170f to 400f); idle(30)
        event(view, down, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 170f to 400f, 230f to 400f)
        for (distance in listOf(60f, 100f, 140f)) { idle(30); event(view, down, MotionEvent.ACTION_MOVE, (200f - distance) to 400f, (200f + distance) to 400f) }
        event(view, down, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 60f to 400f, 340f to 400f)
        event(view, down, MotionEvent.ACTION_UP, 60f to 400f); idle(400)
        assertSame(root, view.rootView)
        assertFalse("Pinching must persist after both fingers lift", before.contentEquals(pixels(view)))
        assertTrue("Pinch must not turn a page", turns.isEmpty()); assertEquals(0, menus)
        tap(view, 370f); tap(view, 30f)
        assertEquals(listOf(true, false), turns)
    }

    @Test fun edgeTapsWorkBeforeLoadingAndAfterClearingTheImage() = withView { view, page ->
        val turns = mutableListOf<Boolean>(); view.onPage = { turns += it }
        tap(view, 370f)
        view.setPage(page); view.clearPage()
        tap(view, 30f)
        assertEquals(listOf(true, false), turns)
    }

    private fun withView(block: (ComicImageView, ComicPage) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = ComicImageView(controller.get()) { object : ComicRaster {
            override val width = 400; override val height = 800
            override fun decode(tile: ComicTile): Bitmap? = null
            override fun close() {}
        } }
        controller.get().setContentView(view); idle(100); view.layout(0, 0, 400, 800)
        val preview = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        Canvas(preview).apply {
            drawColor(Color.BLUE)
            drawRect(120f, 220f, 280f, 580f, Paint().apply { color = Color.RED })
            drawRect(20f, 20f, 80f, 120f, Paint().apply { color = Color.GREEN })
        }
        try { block(view, ComicPage(File(controller.get().cacheDir, "gesture.png"), ImageSize(400, 800), preview)) }
        finally { controller.pause().stop().destroy() }
    }
    private fun pixels(view: ComicImageView): IntArray {
        val bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return IntArray(400 * 800).also { bitmap.getPixels(it, 0, 400, 0, 0, 400, 800) }
    }
    private fun tap(view: ComicImageView, x: Float) {
        val down = SystemClock.uptimeMillis()
        event(view, down, MotionEvent.ACTION_DOWN, x to 400f); idle(60)
        event(view, down, MotionEvent.ACTION_UP, x to 400f); idle(400)
    }
    private fun event(view: ComicImageView, down: Long, action: Int, vararg points: Pair<Float, Float>) {
        val properties = points.indices.map { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
        val coordinates = points.map { (px, py) -> MotionEvent.PointerCoords().apply { x = px; y = py; pressure = 1f; size = 1f } }.toTypedArray()
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, points.size, properties, coordinates, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
        view.dispatchTouchEvent(event); event.recycle()
    }
    private fun idle(milliseconds: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(milliseconds))
}
