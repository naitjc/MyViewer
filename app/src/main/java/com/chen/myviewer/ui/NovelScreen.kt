package com.chen.myviewer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回书库") }
            Text(title?.novel?.name ?: "已保存的小说", Modifier.padding(12.dp), style = MaterialTheme.typography.titleLarge)
        }
        error?.let { Text(it, Modifier.padding(16.dp)) }
        title?.let { saved ->
            Text("本地可读 ${available.size} / ${saved.novel.chapters.size} 章", Modifier.padding(16.dp))
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                items(saved.novel.chapters, key = { it.url }) { chapter ->
                    val readable = chapter.url in available
                    Column(Modifier.fillMaxWidth().clickable(enabled = readable) { reader.open(saved.book, novel = saved, novelChapter = chapter) }.padding(vertical = 14.dp)) {
                        Text(chapter.name)
                        if (!readable) Text("没有本地正文", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        } ?: if (error == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else Unit
    }
}
