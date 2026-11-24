package com.chen.myviewer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.myviewer.data.ReaderSettings
import java.util.Locale

internal data class ReadingPalette(val id: String, val label: String, val paper: Color, val ink: Color)
internal val ReadingPalettes = listOf(
    ReadingPalette("PAPER", "纸色", Color(0xFFF3EAD9), Color(0xFF312C25)),
    ReadingPalette("WHITE", "浅色", Color(0xFFFAFAF7), Color(0xFF232825)),
    ReadingPalette("GREEN", "护眼", Color(0xFFDBE8D3), Color(0xFF253725)),
    ReadingPalette("NIGHT", "深色", Color(0xFF1D2120), Color(0xFFCDD1C9)),
)

internal fun readingPalette(id: String): ReadingPalette =
    ReadingPalettes.firstOrNull { it.id == id } ?: ReadingPalettes.first()

@Composable
fun ReadingSettings(settings: ReaderSettings, update: (ReaderSettings) -> Unit, readingMode: String? = null, onMode: (String) -> Unit = {}, showPreview: Boolean = true) {
    val palette = readingPalette(settings.background)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (showPreview) Surface(
            shape = RoundedCornerShape(16.dp),
            color = palette.paper,
            border = BorderStroke(1.dp, palette.ink.copy(alpha = .12f)),
            shadowElevation = 0.5.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("阅读预览", style = MaterialTheme.typography.labelSmall, color = palette.ink.copy(alpha = .58f))
                Text(
                    "雨停时，故事刚好翻到下一页。",
                    fontFamily = FontFamily.Serif,
                    fontSize = settings.fontSize.sp,
                    lineHeight = (settings.fontSize * settings.lineSpacing).sp,
                    maxLines = 3,
                    color = palette.ink
                )
            }
        }
        SettingSlider("字号", settings.fontSize, "${settings.fontSize.roundToInt()} 号", 14f..36f, 1f) { update(settings.copy(fontSize = it)) }
        SettingSlider("行距", settings.lineSpacing, String.format(Locale.CHINA, "%.2f 倍", settings.lineSpacing), 1.2f..2.3f, .05f) { update(settings.copy(lineSpacing = it)) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("阅读背景", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingPalettes.forEach { choice ->
                    val selected = palette.id == choice.id
                    Surface(
                        onClick = { update(settings.copy(background = choice.id)) },
                        modifier = Modifier.weight(1f).height(58.dp).semantics { this.selected = selected },
                        shape = RoundedCornerShape(14.dp),
                        color = choice.paper,
                        border = BorderStroke(
                            if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else choice.ink.copy(alpha = .16f)
                        )
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (selected) {
                                Icon(Icons.Outlined.Check, null, Modifier.size(15.dp), tint = choice.ink)
                                Spacer(Modifier.height(2.dp))
                            }
                            Text(
                                choice.label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = choice.ink
                            )
                        }
                    }
                }
            }
        }
        if (readingMode != null) ReaderChoice("阅读方式", listOf("PAGE" to "翻页", "SCROLL" to "连续"), readingMode, onMode)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("翻页动画", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SLIDE" to "平移", "CURL" to "仿真翻书").forEach { (id, label) ->
                    val selected = settings.animation == id
                    FilterChip(
                        selected = selected,
                        onClick = { update(settings.copy(animation = id)) },
                        label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                        shape = RoundedCornerShape(10.dp),
                        colors = shelfChipColors(),
                        border = shelfChipBorder(selected),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        BrightnessSettings(settings, update)
        ReaderDisplaySwitches(settings, update)
    }
}

@Composable
internal fun ReaderDisplaySwitches(settings: ReaderSettings, update: (ReaderSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("沉浸式全屏", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("阅读时隐藏系统状态栏与导航栏", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = settings.immersiveMode,
                onCheckedChange = { update(settings.copy(immersiveMode = it)) },
                modifier = Modifier.semantics { contentDescription = "沉浸式全屏" }
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("极简页脚状态栏", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("在底部显示章节、进度、时间与电量", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = settings.showFooterBar,
                onCheckedChange = { update(settings.copy(showFooterBar = it)) },
                modifier = Modifier.semantics { contentDescription = "极简页脚状态栏" }
            )
        }
    }
}

@Composable
private fun SettingValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .1f)) {
            Text(value, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f, steps: Int = 0, valueLabel: String,
    onFinished: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val dragging by interaction.collectIsDraggedAsState()
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.outlineVariant
    val thumbRing = MaterialTheme.colorScheme.surfaceContainerLowest
    Slider(value, onChange, modifier.heightIn(min = 48.dp), valueRange = range, steps = steps,
        onValueChangeFinished = onFinished, interactionSource = interaction,
        thumb = {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (dragging) Box(Modifier.wrapContentSize(unbounded = true).offset(y = (-32).dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = active) {
                        Text(valueLabel, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Box(Modifier.size(20.dp).background(active, CircleShape).border(2.dp, thumbRing, CircleShape))
            }
        }, track = { state ->
            Canvas(Modifier.fillMaxWidth().height(5.dp)) {
                val fraction = ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                drawLine(inactive, Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = size.height, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(active, Offset(0f, center.y), Offset(size.width * fraction, center.y), strokeWidth = size.height, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                if (steps in 1..8) for (index in 0..steps + 1) {
                    val position = index.toFloat() / (steps + 1)
                    drawCircle(if (position <= fraction) active else inactive, radius = 3.dp.toPx(), center = Offset(size.width * position, center.y))
                }
            }
        })
}

@Composable
internal fun SettingSlider(label: String, value: Float, valueLabel: String, range: ClosedFloatingPointRange<Float>, increment: Float, update: (Float) -> Unit) {
    Column {
        SettingValue(label, valueLabel)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { update((value - increment).coerceIn(range)) }) { Icon(Icons.Outlined.Remove, "减小$label") }
            ReaderSlider(value, update, Modifier.weight(1f).semantics { contentDescription = label }, range,
                steps = ((range.endInclusive - range.start) / increment).roundToInt() - 1, valueLabel = valueLabel)
            IconButton(onClick = { update((value + increment).coerceIn(range)) }) { Icon(Icons.Outlined.Add, "增大$label") }
        }
    }
}

@Composable
internal fun BrightnessSettings(settings: ReaderSettings, update: (ReaderSettings) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("亮度", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(if (settings.brightness < 0) "跟随系统" else "${(settings.brightness * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Switch(settings.brightness < 0, { update(settings.copy(brightness = if (it) -1f else .5f)) }, Modifier.semantics { contentDescription = "亮度跟随系统" })
        }
        if (settings.brightness >= 0) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.BrightnessLow, null, Modifier.size(20.dp))
            ReaderSlider(settings.brightness, { update(settings.copy(brightness = it)) }, Modifier.weight(1f).padding(horizontal = 12.dp).semantics { contentDescription = "亮度" }, range = .03f..1f, valueLabel = "${(settings.brightness * 100).roundToInt()}%")
            Icon(Icons.Outlined.BrightnessHigh, null, Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun ReaderChoice(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (id, text) ->
                val isSelected = selected == id
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(id) },
                    label = { Text(text, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = shelfChipColors(),
                    border = shelfChipBorder(isSelected)
                )
            }
        }
    }
}
