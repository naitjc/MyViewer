package com.chen.myviewer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.chen.myviewer.data.ReaderSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

internal class AutoReadingState {
    var running by mutableStateOf(false); private set
    fun start() { running = true }
    fun stop() { running = false }
}

@Composable
internal fun AutoReadingEffect(
    state: AutoReadingState,
    mode: String,
    settings: ReaderSettings,
    ready: () -> Boolean,
    nextPage: () -> Boolean = { false },
    scroll: suspend (Float) -> Boolean = { false },
    finished: () -> Unit,
    flush: () -> Unit,
) {
    val currentReady by rememberUpdatedState(ready)
    val currentNext by rememberUpdatedState(nextPage)
    val currentScroll by rememberUpdatedState(scroll)
    val currentFinished by rememberUpdatedState(finished)
    val currentFlush by rememberUpdatedState(flush)
    val density = LocalDensity.current.density
    LaunchedEffect(state.running, mode, settings.autoPageSeconds, settings.autoScrollSpeed) {
        if (!state.running) return@LaunchedEffect
        try {
            if (mode == "PAGE") {
                while (isActive && state.running) {
                    while (!currentReady() && state.running) delay(100)
                    if (!state.running) break
                    delay(settings.autoPageSeconds.coerceIn(2, 60) * 1000L)
                    if (!state.running) break
                    if (!currentReady()) continue
                    if (!currentNext()) { state.stop(); currentFinished(); break }
                    withFrameNanos { }
                }
            } else {
                var previous = withFrameNanos { it }
                while (isActive && state.running) {
                    val now = withFrameNanos { it }
                    val elapsed = ((now - previous) / 1_000_000_000f).coerceIn(0f, .05f)
                    previous = now
                    if (!state.running) break
                    if (!currentReady()) continue
                    val distance = elapsed * settings.autoScrollSpeed.coerceIn(1, 10) * 10f * density
                    if (!currentScroll(distance)) { state.stop(); currentFinished(); break }
                }
            }
        } finally { currentFlush() }
    }
}

@Composable
internal fun AutoReadingSettings(settings: ReaderSettings, mode: String, onChange: (ReaderSettings) -> Unit, onStart: () -> Unit) {
    Text("自动阅读", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(16.dp))
    if (mode == "PAGE") {
        Text("翻页间隔 · ${settings.autoPageSeconds} 秒／页", style = MaterialTheme.typography.titleMedium)
        Slider(settings.autoPageSeconds.toFloat(), { onChange(settings.copy(autoPageSeconds = it.roundToInt())) }, valueRange = 2f..60f, steps = 57)
        Text("每隔设定时间翻到下一页，读到末尾时自动停止。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text("滚动速度 · ${settings.autoScrollSpeed} 档", style = MaterialTheme.typography.titleMedium)
        Slider(settings.autoScrollSpeed.toFloat(), { onChange(settings.copy(autoScrollSpeed = it.roundToInt())) }, valueRange = 1f..10f, steps = 8)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("慢"); Text("快") }
        Text("匀速向下阅读，读到末尾时自动停止。", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text("触摸画面即可暂停，也可使用画面下方的暂停按钮。", Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) { Text("开始自动阅读") }
}
