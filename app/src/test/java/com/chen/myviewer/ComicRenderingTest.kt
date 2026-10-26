package com.chen.myviewer

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import com.chen.myviewer.reader.comic.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComicRenderingTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val color = Color.rgb(120, 160, 190)
    private fun page() = ComicPage(File(context.cacheDir, "prepared.png"), ImageSize(600, 12000), Bitmap.createBitmap(50, 1000, Bitmap.Config.RGB_565).apply { eraseColor(color) })
    private fun draw(view: ComicImageView, top: Int): Bitmap {
        view.updateViewport(top, top + 1000)
        val bitmap = Bitmap.createBitmap(600, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.translate(0f, -top.toFloat()); canvas.clipRect(0, top, 600, top + 1000)
        view.draw(canvas); return bitmap
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
        assertTrue("Timed out waiting for background decoder", condition())
    }
    private fun detach(view: ComicImageView) { view.javaClass.getDeclaredMethod("onDetachedFromWindow").apply { isAccessible = true }.invoke(view) }

    @Test fun repeatedBindingDuringSlowOpenDoesNotRestartDecoder() {
        val gate = CountDownLatch(1); val opens = AtomicInteger()
        val raster = FakeRaster()
        val view = ComicImageView(context) { opens.incrementAndGet(); gate.await(5, TimeUnit.SECONDS); raster }
        view.layout(0, 0, 600, 12000)
        val page = page()
        try {
            view.setPage(page); await { opens.get() == 1 }
            repeat(30) { view.setPage(page) }
            val bitmap = draw(view, 7000)
            assertEquals(page.preview.getPixel(0, 0), bitmap.getPixel(300, 500))
            assertEquals(1, opens.get())
        } finally { gate.countDown(); detach(view) }
    }
    @Test fun fastViewportMovesKeepPreviewWhileHdDecodeIsBlocked() {
        val gate = CountDownLatch(1)
        val raster = FakeRaster(gate)
        val view = ComicImageView(context) { raster }
        val page = page()
        view.layout(0, 0, 600, 12000); view.setPage(page)
        try {
            await { draw(view, 0); raster.started.get() > 0 }
            for (top in listOf(1000, 3500, 8000, 11000, 5000)) {
                val bitmap = draw(view, top)
                for (x in listOf(20, 300, 580)) for (y in listOf(20, 500, 980)) assertEquals("Missing preview at viewport $top", page.preview.getPixel(0, 0), bitmap.getPixel(x, y))
            }
            gate.countDown()
            await { raster.lastTop.get() >= 4500 }
            assertTrue("Viewport changes must not queue repeated cancelled decodes", raster.started.get() < 20)
        } finally { gate.countDown(); detach(view) }
    }
    @Test fun detachingNeverWaitsForDecodeOnMainThread() {
        val gate = CountDownLatch(1); val raster = FakeRaster(gate)
        val view = ComicImageView(context) { raster }
        view.layout(0, 0, 600, 12000); view.setPage(page())
        try {
            await { draw(view, 0); raster.started.get() > 0 }
            val start = System.nanoTime(); detach(view)
            assertTrue("Detach blocked the UI waiting for decoding", System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1))
            gate.countDown(); await { raster.closed.get() }
            assertFalse(raster.closedOnMain.get())
        } finally { gate.countDown() }
    }
    private inner class FakeRaster(private val gate: CountDownLatch? = null) : ComicRaster {
        override val width = 600
        override val height = 12000
        val started = AtomicInteger(); val lastTop = AtomicInteger(-1)
        val closed = AtomicBoolean(); val closedOnMain = AtomicBoolean()
        private val lock = Any()
        override fun decode(tile: ComicTile): Bitmap = synchronized(lock) {
            started.incrementAndGet(); gate?.await(5, TimeUnit.SECONDS); lastTop.set(tile.top)
            Bitmap.createBitmap((tile.right - tile.left) / tile.sample, (tile.bottom - tile.top) / tile.sample, Bitmap.Config.RGB_565).apply { eraseColor(color) }
        }
        override fun close() = synchronized(lock) { closedOnMain.set(Looper.myLooper() == Looper.getMainLooper()); closed.set(true) }
    }
}
