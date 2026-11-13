package com.chen.myviewer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chen.myviewer.download.FolderDownloadViewModel

@Composable
internal fun rememberFolderDownloadPicker(bookId: String, vm: FolderDownloadViewModel, download: (String?, Uri) -> Unit): (String?) -> Unit {
    var pending by rememberSaveable(bookId) { mutableStateOf<String?>(null) }
    var choosing by rememberSaveable(bookId) { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folder ->
        if (folder != null && choosing) download(pending, folder)
        pending = null
        choosing = false
    }
    return { chapter -> pending = chapter; choosing = true; picker.launch(vm.lastFolder) }
}

@Composable
internal fun FolderDownloadStatus(vm: FolderDownloadViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    state.message?.let { message ->
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(horizontal = 12.dp)) {
                if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    TextButton(onClick = if (state.running) vm::cancel else vm::dismiss) { Text(if (state.running) "取消保存" else "关闭") }
                }
            }
        }
    }
}
