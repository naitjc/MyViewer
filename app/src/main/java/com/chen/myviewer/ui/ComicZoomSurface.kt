package com.chen.myviewer.ui

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Transforms the continuous reading canvas, including adjacent images, in the same window. */
internal class ComicCanvasState {
    var scale by mutableFloatStateOf(1f); private set
    var pan by mutableStateOf(Offset.Zero); private set
    var size by mutableStateOf(IntSize.Zero)
    private var held: Pair<Float, Offset>? = null
    val zoomed: Boolean get() = scale > 1.01f

    fun transform(factor: Float, delta: Offset, anchor: Offset, overflow: (Float) -> Unit) {
        if (!factor.isFinite() || !anchor.x.isFinite() || !anchor.y.isFinite()) return
        val next = (scale * factor).coerceIn(1f, 8f)
        val center = Offset(size.width / 2f, size.height / 2f)
        val offset = (pan - (anchor - center)) * (next / scale) + (anchor - center) + delta
        scale = next
        val limitX = size.width * (next - 1f) / 2
        val limitY = size.height * (next - 1f) / 2
        pan = Offset(offset.x.coerceIn(-limitX, limitX), offset.y.coerceIn(-limitY, limitY))
        if (held == null && next > 1.01f) overflow(-(offset.y - pan.y) / next)
    }
    fun toggle(anchor: Offset) {
        if (zoomed) { scale = 1f; pan = Offset.Zero }
        else transform(2.5f, Offset.Zero, anchor) {}
    }
    fun hold(anchor: Offset) { held = scale to pan; transform(2.5f, Offset.Zero, anchor) {} }
    fun release() { held?.let { scale = it.first; pan = it.second }; held = null }
    fun visibleTop(height: Int): Float = height / 2f + (-height / 2f - pan.y) / scale
    fun visibleBottom(height: Int): Float = height / 2f + (height / 2f - pan.y) / scale
}

@Composable
internal fun ComicZoomSurface(state: ComicCanvasState, menu: () -> Unit, overflow: (Float) -> Unit, content: @Composable BoxScope.() -> Unit) {
    val onMenu by rememberUpdatedState(menu)
    val onOverflow by rememberUpdatedState(overflow)
    Box(Modifier.fillMaxSize().clipToBounds().semantics { stateDescription = "缩放 ${(state.scale * 100).toInt()}%" }.onSizeChanged { state.size = it }.pointerInput(state) {
        // Observe the initial pass so native image views cannot swallow taps. Ordinary one-finger
        // scrolling remains with LazyColumn; only zooming and held magnification consume motion.
        coroutineScope {
            var pendingTap: Job? = null
            var lastTapTime = -10000L
            var lastTap = Offset.Zero
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val doubleTap = down.uptimeMillis - lastTapTime in 0..viewConfiguration.doubleTapTimeoutMillis && (down.position - lastTap).getDistance() < 64 * density
                pendingTap?.cancel()
                var tap = true
                var multi = false
                var holding = false
                var lastTime = down.uptimeMillis
                try {
                    while (true) {
                        val event = if (tap && !holding && !multi) {
                            withTimeoutOrNull((down.uptimeMillis + viewConfiguration.longPressTimeoutMillis - lastTime).coerceAtLeast(1)) { awaitPointerEvent(PointerEventPass.Initial) }
                        } else awaitPointerEvent(PointerEventPass.Initial)
                        if (event == null) { state.hold(down.position); holding = true; tap = false; lastTapTime = -10000; continue }
                        lastTime = event.changes.maxOf { it.uptimeMillis }
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            if (holding) { state.release(); holding = false }
                            multi = true; tap = false
                            state.transform(event.calculateZoom(), event.calculatePan(), event.calculateCentroid(useCurrent = false), onOverflow)
                            event.changes.forEach { it.consume() }
                        } else {
                            val moved = event.changes.firstOrNull { it.id == down.id }?.let { (it.position - down.position).getDistance() > viewConfiguration.touchSlop } ?: false
                            if (moved) tap = false
                            if (pressed > 0 && (holding || multi || (state.zoomed && !tap))) {
                                state.transform(1f, event.calculatePan(), event.calculateCentroid(useCurrent = false), onOverflow)
                                event.changes.forEach { it.consume() }
                            }
                        }
                        if (pressed == 0) {
                            if (tap) {
                                if (doubleTap) { lastTapTime = -10000; state.toggle(down.position) }
                                else { lastTapTime = lastTime; lastTap = down.position; pendingTap = launch { delay(viewConfiguration.doubleTapTimeoutMillis); onMenu() } }
                            } else lastTapTime = -10000
                            break
                        }
                    }
                } finally { state.release() }
            }
        }
    }) {
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = state.scale; scaleY = state.scale; translationX = state.pan.x; translationY = state.pan.y }, content = content)
    }
}
