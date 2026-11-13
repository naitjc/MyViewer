package com.chen.myviewer.download

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chen.myviewer.ViewerApplication
import com.chen.myviewer.novel.NovelTitle
import com.chen.myviewer.source.OnlineTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class FolderDownloadState(val running: Boolean = false, val message: String? = null, val saved: Int = 0)

internal class FolderDownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ViewerApplication
    private val repository = app.folderDownloads
    private val preferences = app.getSharedPreferences("folder-downloads", 0)
    private val mutableState = MutableStateFlow(FolderDownloadState())
    val state = mutableState.asStateFlow()
    val lastFolder: Uri? get() = preferences.getString("folder", null)?.let(Uri::parse)
    private var job: Job? = null

    fun comic(title: OnlineTitle, chapterKey: String?, folder: Uri) {
        val chapters = title.manga.chapters.orEmpty().asReversed().filter { !it.locked && (chapterKey == null || it.key == chapterKey) }
        start(folder) { repository.comic(title, chapters, folder, ::progress) }
    }

    fun novel(title: NovelTitle, chapterKey: String?, folder: Uri) {
        val chapters = title.novel.chapters.filter { !it.locked && (chapterKey == null || it.url == chapterKey) }
        start(folder) { repository.novel(title, chapters, folder, ::progress) }
    }

    private fun progress(message: String, saved: Int) { mutableState.update { it.copy(message = message, saved = saved) } }

    private fun start(folder: Uri, action: suspend () -> Unit) {
        if (job?.isCompleted == false) return
        job = viewModelScope.launch {
            mutableState.value = FolderDownloadState(running = true, message = "正在准备下载…")
            try {
                runCatching { app.contentResolver.takePersistableUriPermission(folder, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                preferences.edit { putString("folder", folder.toString()) }
                action()
                mutableState.update { it.copy(message = "下载完成，已保存 ${it.saved} 个文件到所选文件夹") }
            } catch (error: CancellationException) {
                mutableState.update { it.copy(message = "已取消，已保存 ${it.saved} 个完整文件") }
                throw error
            } catch (error: Exception) {
                mutableState.update { it.copy(message = "下载失败，已保存 ${it.saved} 个完整文件。${error.message ?: "请检查网络、文件夹授权或剩余空间后重试。"}") }
            } finally { mutableState.update { it.copy(running = false) } }
        }
    }

    fun cancel() { job?.cancel() }
    fun dismiss() { if (!mutableState.value.running) mutableState.value = FolderDownloadState() }
}
