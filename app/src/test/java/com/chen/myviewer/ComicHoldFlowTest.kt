package com.chen.myviewer

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.chen.myviewer.ui.ComicCanvasState
import com.chen.myviewer.ui.ComicZoomSurface
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComicHoldFlowTest {
    @get:Rule val compose = createComposeRule()
    private var menus = 0
    private var overflow = 0f
    private lateinit var hostView: android.view.View

    @Before fun setup() {
        val state = ComicCanvasState()
        compose.setContent {
            hostView = LocalView.current
            Box(Modifier.size(200.dp, 400.dp).testTag("canvas")) {
                ComicZoomSurface(state, { menus++ }, { overflow += it }) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(Color.Blue)
                        drawRect(Color.Red, Offset(size.width * .3f, size.height * .275f), Size(size.width * .4f, size.height * .45f))
                        drawRect(Color.Green, Offset(size.width * .05f, size.height * .025f), Size(size.width * .15f, size.height * .125f))
                    }
                }
            }
        }
    }

    @Test fun continuousHoldMovesTheImageWithTheFingerAndRestoresOnRelease() {
        val before = frame()
        canvas().performTouchInput { down(Offset(width * .45f, height * .5f)) }
        compose.mainClock.advanceTimeBy(700)
        zoom(250).assertExists()
        canvas().performTouchInput { moveTo(Offset(width * .8f, height * .5f)) }
        assertEquals(Color.Blue.toArgb(), pixel(frame(), .4f, .5f))
        canvas().performTouchInput { moveTo(Offset(width * .8f, height * .8f)) }
        assertEquals(Color.Blue.toArgb(), pixel(frame(), .8f, .3f))
        canvas().performTouchInput { up() }
        zoom(100).assertExists()
        assertArrayEquals(pixels(before), pixels(frame()))
        assertEquals(0, menus); assertEquals(0f, overflow, 0f)
    }

    @Test fun movingHeldFocusFromPersistentZoomRestoresThePreviousFraming() {
        canvas().performTouchInput { doubleClick(center) }
        zoom(250).assertExists()
        canvas().performTouchInput { down(center); moveTo(Offset(width * .75f, height * .6f)); up() }
        val before = frame()
        canvas().performTouchInput { down(Offset(width * .45f, height * .5f)) }
        compose.mainClock.advanceTimeBy(700)
        zoom(625).assertExists()
        canvas().performTouchInput { moveTo(Offset(width * .05f, height * .05f)) }
        canvas().performTouchInput { up() }
        zoom(250).assertExists()
        assertArrayEquals(pixels(before), pixels(frame()))
        assertEquals(0, menus); assertEquals(0f, overflow, 0f)
    }

    private fun canvas() = compose.onNodeWithTag("canvas")
    private fun zoom(value: Int) = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "缩放 $value%"))
    private fun frame(): Bitmap {
        val bounds = canvas().fetchSemanticsNode().boundsInRoot
        lateinit var result: Bitmap
        compose.runOnIdle {
            val frame = Bitmap.createBitmap(hostView.width, hostView.height, Bitmap.Config.ARGB_8888)
            hostView.draw(android.graphics.Canvas(frame))
            result = Bitmap.createBitmap(frame, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        }
        return result
    }
    private fun pixel(bitmap: Bitmap, x: Float, y: Float) = bitmap.getPixel((bitmap.width * x).toInt(), (bitmap.height * y).toInt())
    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
}
