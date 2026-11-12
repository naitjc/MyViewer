package com.chen.myviewer.novel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chen.myviewer.ViewerApplication
import com.chen.myviewer.data.Book
import com.chen.myviewer.source.boundedBytes
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class NovelBrowserState(
    val sources: List<NovelSource> = emptyList(),
    val source: NovelSource? = null,
    val query: String = "",
    val results: List<NovelBook> = emptyList(),
    val page: Int = 0,
    val hasNextPage: Boolean = false,
    val detail: NovelTitle? = null,
    val busy: String? = null,
    val error: String? = null,
    val downloaded: Set<String> = emptySet(),
    val downloadStatus: String? = null,
)

class NovelViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ViewerApplication
    val repository = app.novels
    val state = MutableStateFlow(NovelBrowserState())
    private var job: Job? = null
    private var downloadJob: Job? = null
    private var downloadingSource: String? = null
    private var generation = 0L
    init { work("正在读取小说源…") { refreshSources() } }
    private fun work(message: String, action: suspend () -> Unit) {
        job?.cancel()
        val revision = ++generation
        job = viewModelScope.launch {
            state.update { it.copy(busy = message, error = null) }
            try { action() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (revision == generation) state.update { it.copy(error = error.message ?: "小说源操作失败") } }
            finally { if (revision == generation) state.update { it.copy(busy = null) } }
        }
    }
    private suspend fun refreshSources() {
        val sources = withContext(Dispatchers.IO) { repository.sources() }
        state.update { it.copy(sources = sources) }
    }
    fun importUrl(url: String) = work("正在导入小说源…") { repository.importUrl(url); refreshSources() }
    fun importFile(uri: Uri) = work("正在导入小说源…") {
        val json = withContext(Dispatchers.IO) {
            app.contentResolver.openInputStream(uri)?.use { boundedBytes(it, 32 * 1024 * 1024).toString(Charsets.UTF_8) } ?: error("无法读取书源文件。")
        }
        repository.importJson(json)
        refreshSources()
    }
    fun uninstall(source: NovelSource) = work("正在删除小说源…") {
        if (downloadingSource == source.id) downloadJob?.cancelAndJoin()
        repository.uninstall(source.id)
        refreshSources()
    }
    fun choose(source: NovelSource) {
        job?.cancel(); generation++
        state.update { it.copy(source = source, detail = null, query = "", results = emptyList(), page = 0, hasNextPage = false, busy = null, error = null) }
    }
    fun query(query: String) { state.update { it.copy(query = query) } }
    fun search(more: Boolean = false) {
        val current = state.value
        val source = current.source ?: return
        if (current.query.isBlank()) return
        val page = if (more) current.page + 1 else 1
        work("正在搜索小说…") {
            val results = repository.search(source.id, current.query, page)
            state.update { previous ->
                val combined = (if (more) previous.results + results else results).distinctBy { it.url }
                previous.copy(results = combined, page = page,
                    hasNextPage = results.isNotEmpty() && (!more || combined.size > previous.results.size))
            }
        }
    }
    fun show(book: NovelBook) {
        val source = state.value.source ?: return
        work("正在读取小说目录…") {
            showTitle(repository.saveTitle(source.id, repository.details(source.id, book)))
        }
    }
    fun showSaved(book: Book) = work("正在读取小说详情…") { showTitle(repository.title(book)) }
    private suspend fun showTitle(title: NovelTitle) {
        val downloaded = withContext(Dispatchers.IO) { repository.downloaded(title) }
        state.update { it.copy(detail = title, downloaded = downloaded) }
        if (title.book.coverPath == null) try { repository.cover(title) } catch (error: CancellationException) { throw error } catch (_: Exception) { }
    }
    fun refreshDetail() {
        val title = state.value.detail ?: return
        work("正在刷新小说目录…") { showTitle(repository.saveTitle(title.sourceId, repository.details(title.sourceId, title.novel), title.book.onShelf)) }
    }
    fun addToShelf() {
        val title = state.value.detail ?: return
        work("正在加入书架…") { showTitle(repository.saveTitle(title.sourceId, title.novel, true)) }
    }
    fun download(chapters: List<NovelChapter>) {
        val title = state.value.detail ?: return
        if (downloadJob?.isActive == true) return
        downloadingSource = title.sourceId
        downloadJob = viewModelScope.launch {
            state.update { it.copy(downloadStatus = "正在下载正文…", error = null) }
            try {
                repository.download(title, chapters.filterNot { it.locked }) { done, total -> state.update { it.copy(downloadStatus = "正在下载正文 · $done / $total") } }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { state.update { it.copy(error = error.message ?: "小说下载失败，请重试") } }
            finally {
                downloadingSource = null
                val downloaded = withContext(NonCancellable + Dispatchers.IO) { repository.downloaded(title) }
                state.update { it.copy(downloadStatus = null, downloaded = if (it.detail?.book?.id == title.book.id) downloaded else it.downloaded) }
            }
        }
    }
    fun cancelDownload() { downloadJob?.cancel() }
    fun deleteDownloads() {
        val title = state.value.detail ?: return
        work("正在删除离线正文…") { downloadJob?.cancelAndJoin(); repository.deleteDownloads(title); state.update { it.copy(downloaded = emptySet()) } }
    }
    fun back() {
        job?.cancel(); generation++
        state.update { if (it.detail != null) it.copy(detail = null, busy = null, error = null) else it.copy(source = null, results = emptyList(), busy = null, error = null) }
    }
    fun dismissError() { state.update { it.copy(error = null) } }
}
