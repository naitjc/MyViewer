package com.chen.myviewer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chen.myviewer.MainViewModel
import com.chen.myviewer.novel.NovelViewModel

@Composable
internal fun SavedNovelScreen(vm: NovelViewModel, reader: MainViewModel, back: () -> Unit) {
    val title by vm.detail.collectAsStateWithLifecycle()
    val available by vm.available.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回书库") }
                    Text(
                        title?.novel?.name ?: "已保存的小说",
                        Modifier.weight(1f).padding(end = 12.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            }
        }
        error?.let {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text(it, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        title?.let { saved ->
            Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)) {
                    Text(
                        "本地可读 ${available.size} / ${saved.novel.chapters.size} 章",
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(saved.novel.chapters, key = { it.url }) { chapter ->
                    val readable = chapter.url in available
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (readable) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (readable) 0.65f else 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = readable) { reader.open(saved.book, novel = saved, novelChapter = chapter) }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    chapter.name,
                                    fontWeight = FontWeight.Medium,
                                    color = if (readable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!readable) Text("没有本地正文", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (readable) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } ?: if (error == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else Unit
    }
}
