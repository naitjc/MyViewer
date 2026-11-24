package com.chen.myviewer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chen.myviewer.BuildConfig
import com.chen.myviewer.MainViewModel
import com.chen.myviewer.data.ReaderSettings

@Composable
internal fun SettingsScreen(vm: MainViewModel, settings: ReaderSettings, columns: Int, onColumns: (Int) -> Unit, expanded: List<String>, onToggleSection: (String) -> Unit) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    val usage by vm.storageUsage.collectAsStateWithLifecycle()
    val backupBusy by vm.backupBusy.collectAsStateWithLifecycle()
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    val exportBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }
    LaunchedEffect(Unit) { vm.refreshStorageUsage() }
    LaunchedEffect("storage" in expanded) { if ("storage" in expanded) vm.refreshStorageUsage() }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "header-reading") {
            SettingsSectionHeading("阅读")
        }
        item(key = "layout") {
            PreferenceCard("书架布局", "每行 $columns 本", Icons.Outlined.GridView, "layout" in expanded, { onToggleSection("layout") }) {
                for (row in listOf(1..3, 4..6)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { count ->
                            val selected = columns == count
                            FilterChip(
                                selected = selected,
                                onClick = { onColumns(count) },
                                label = { Text("$count 本", fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                                leadingIcon = { if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) },
                                shape = RoundedCornerShape(10.dp),
                                colors = shelfChipColors(),
                                border = shelfChipBorder(selected),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Text("本数越少，封面越大。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "reading") {
            val background = when (settings.background) { "WHITE" -> "浅色"; "GREEN" -> "护眼"; "NIGHT" -> "深色"; else -> "纸色" }
            PreferenceCard("小说阅读", "${settings.fontSize.toInt()} 号 · $background · ${if (settings.animation == "CURL") "仿真翻书" else "平移翻页"}", Icons.Outlined.TextFields, "reading" in expanded, { onToggleSection("reading") }) { ReadingSettings(settings, vm::updateSettings) }
        }
        item(key = "header-storage") {
            SettingsSectionHeading("书籍与存储")
        }
        item(key = "import") {
            PreferenceCard("导入偏好", if (settings.copyOnImport) "默认复制到应用" else "默认直接读取原文件", Icons.Outlined.FileDownload, "import" in expanded, { onToggleSection("import") }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("默认复制到应用", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(if (settings.copyOnImport) "保存独立副本，复制后不依赖原文件" else "默认直接读取原文件，节省存储空间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp)); Switch(settings.copyOnImport, { vm.updateSettings(settings.copy(copyOnImport = it)) })
                }
            }
        }
        item(key = "folders") {
            PreferenceCard("文件夹来源", "${folders.size} 个已保存的扫描来源", Icons.Outlined.FolderOpen, "folders" in expanded, { onToggleSection("folders") }) {
                if (folders.isEmpty()) {
                    Text("还没有添加文件夹", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("从书库选择文件夹导入后，可在这里刷新书籍。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else folders.forEachIndexed { index, folder ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(folder.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(if (folder.recursive) "包含子文件夹" else "仅当前文件夹", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.selectedFolder(Uri.parse(folder.uri), folder.recursive) }) { Icon(Icons.Outlined.Refresh, "刷新文件夹：${folder.name}") }
                        IconButton(onClick = { vm.removeFolder(folder.uri) }) { Icon(Icons.Outlined.Close, "移除扫描来源：${folder.name}") }
                    }
                }
            }
        }
        item(key = "storage") {
            PreferenceCard("存储管理", "缓存 ${formatFileSize(usage.comicCacheBytes)} · 已下载 ${formatFileSize(usage.onlineDownloadsBytes)}", Icons.Outlined.CleaningServices, "storage" in expanded, { onToggleSection("storage") }) {
                StorageUsageRow("漫画临时缓存", formatFileSize(usage.comicCacheBytes))
                StorageUsageRow("离线下载章节", formatFileSize(usage.onlineDownloadsBytes))
                StorageUsageRow("应用内书籍副本", formatFileSize(usage.copiedBooksBytes))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                Text("书籍、进度和书签会保留，漫画图片在阅读时重新加载。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { vm.clearComicCache() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Outlined.DeleteSweep, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("清理漫画图片缓存") }
            }
        }
        item(key = "backup") {
            PreferenceCard("备份与恢复", "导出或恢复书架、进度、书签与设置", Icons.Outlined.Backup, "backup" in expanded, { onToggleSection("backup") }) {
                Text("将书库元数据、阅读进度、章节状态、书签、阅读时长及阅读设置导出为 JSON 文件。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                            exportBackupLauncher.launch("myviewer-backup-$date.json")
                        },
                        enabled = !backupBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.UploadFile, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("导出备份")
                    }
                    OutlinedButton(
                        onClick = { importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        enabled = !backupBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.DownloadForOffline, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("恢复备份")
                    }
                }
            }
        }
        item(key = "header-other") {
            SettingsSectionHeading("其他")
        }
        item(key = "about") {
            PreferenceCard("关于 MyViewer", "版本 ${BuildConfig.VERSION_NAME}", Icons.Outlined.Info, "about" in expanded, { onToggleSection("about") }) {
                Text("TXT 小说 · ZIP 与在线漫画", style = MaterialTheme.typography.bodyMedium)
                Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("恢复本地备份？") },
            text = { Text("将从所选备份文件恢复书库记录、阅读进度、书签、阅读时长和设置。") },
            confirmButton = {
                TextButton(onClick = { pendingRestoreUri = null; vm.importBackup(uri) }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun StorageUsageRow(label: String, sizeLabel: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(sizeLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

internal fun formatFileSize(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe < 1024L -> "$safe B"
        safe < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", safe / 1024.0)
        safe < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", safe / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", safe / (1024.0 * 1024.0 * 1024.0))
    }
}

@Composable
private fun SettingsSectionHeading(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

@Composable
private fun PreferenceCard(title: String, subtitle: String, icon: ImageVector, expanded: Boolean, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, tween(180), label = "设置分组箭头")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        shadowElevation = 0.dp
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = if (expanded) "收起$title" else "展开$title", onClick = onToggle)
                    .semantics { contentDescription = "${title}分组"; stateDescription = if (expanded) "已展开" else "已收起" }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(22.dp).rotate(arrow), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(180)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(140)) + fadeOut(tween(140))
            ) {
                Column(Modifier.fillMaxWidth()) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                }
            }
        }
    }
}
