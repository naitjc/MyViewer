package com.chen.myviewer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

private data class ReadingPalette(val id: String, val label: String, val paper: Color, val ink: Color)
private val ReadingPalettes = listOf(
    ReadingPalette("PAPER", "纸色", Color(0xFFF3EAD9), Color(0xFF312C25)),
    ReadingPalette("WHITE", "浅色", Color(0xFFFAFAF7), Color(0xFF232825)),
    ReadingPalette("GREEN", "护眼", Color(0xFFDBE8D3), Color(0xFF253725)),
    ReadingPalette("NIGHT", "深色", Color(0xFF1D2120), Color(0xFFCDD1C9)),
)

@Composable
fun ReadingSettings(settings: ReaderSettings, update: (ReaderSettings) -> Unit) {
    val palette = ReadingPalettes.firstOrNull { it.id == settings.background } ?: ReadingPalettes.first()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = RoundedCornerShape(16.dp), color = palette.paper, border = BorderStroke(1.dp, palette.ink.copy(alpha = .08f)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("阅读预览", style = MaterialTheme.typography.labelSmall, color = palette.ink.copy(alpha = .6f))
                Text("雨停时，故事刚好翻到下一页。", fontFamily = FontFamily.Serif, fontSize = settings.fontSize.sp,
                    lineHeight = (settings.fontSize * settings.lineSpacing).sp, maxLines = 3, color = palette.ink)
            }
        }
        Column {
            SettingValue("字号", "${settings.fontSize.toInt()} 号")
            Slider(settings.fontSize, { update(settings.copy(fontSize = it)) }, valueRange = 14f..36f, steps = 21)
        }
        Column {
            SettingValue("行距", String.format(Locale.CHINA, "%.2f 倍", settings.lineSpacing))
            Slider(settings.lineSpacing, { update(settings.copy(lineSpacing = it)) }, valueRange = 1.2f..2.3f)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("阅读背景", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingPalettes.forEach { choice ->
                    Surface(onClick = { update(settings.copy(background = choice.id)) }, modifier = Modifier.weight(1f).height(68.dp).semantics { selected = palette.id == choice.id }, shape = RoundedCornerShape(13.dp), color = choice.paper,
                        border = BorderStroke(if (palette.id == choice.id) 2.dp else 1.dp, if (palette.id == choice.id) MaterialTheme.colorScheme.primary else choice.ink.copy(alpha = .15f))) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Box(Modifier.height(16.dp)) { if (palette.id == choice.id) Icon(Icons.Outlined.Check, null, Modifier.size(15.dp), tint = choice.ink) }
                            Text(choice.label, fontSize = 12.sp, color = choice.ink)
                        }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("翻页动画", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SLIDE" to "平移", "CURL" to "仿真翻书").forEach { (id, label) ->
                    FilterChip(settings.animation == id, { update(settings.copy(animation = id)) }, label = { Text(label) }, modifier = Modifier.weight(1f))
                }
            }
        }
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("亮度跟随系统", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(if (settings.brightness < 0) "使用手机当前亮度" else "为阅读单独设置亮度", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(settings.brightness < 0, { update(settings.copy(brightness = if (it) -1f else .5f)) })
            }
            if (settings.brightness >= 0) Slider(settings.brightness, { update(settings.copy(brightness = it)) }, valueRange = .03f..1f)
        }
    }
}

@Composable
private fun SettingValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .08f)) {
            Text(value, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
