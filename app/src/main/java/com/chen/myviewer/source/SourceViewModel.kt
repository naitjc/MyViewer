package com.chen.myviewer.source

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chen.myviewer.ViewerApplication
import com.chen.myviewer.data.Book
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SourceBrowserState(
    val catalog: List<SourceEntry> = emptyList(),
    val installed: List<InstalledSource> = emptyList(),
    val repositoryUrl: String = COMMUNITY_SOURCE_URL,
    val source: SourceEntry? = null,
    val query: String = "",
    val results: List<SourceManga> = emptyList(),
    val page: Int = 0,
    val hasNextPage: Boolean = false,
    val detail: OnlineTitle? = null,
    val busy: String? = null,
    val error: String? = null,
    val downloaded: Set<String> = emptySet(),
    val downloadStatus: String? = null,
)

class SourceViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ViewerApplication
    val repository = app.sources
    val state = MutableStateFlow(SourceBrowserState(repositoryUrl = repository.repositoryUrl))
    private var job: Job? = null
    private var downloadJob: Job? = null
    private var generation = 0L
    init { viewModelScope.launch {
        val installed = withContext(Dispatchers.IO) { repository.installed() }
        state.update { it.copy(installed = installed) }
    } }

    fun loadCatalog(refresh: Boolean = false, url: String = state.value.repositoryUrl) = work("正在加载源仓库…") {
        val catalog = repository.catalog(refresh, url)
        val installed = withContext(Dispatchers.IO) { repository.installed() }
        state.update { it.copy(catalog = catalog, installed = installed, repositoryUrl = repository.repositoryUrl) }
    }
    fun install(entry: SourceEntry) = work("正在安装 ${entry.name}…") {
        repository.install(entry)
        val installed = withContext(Dispatchers.IO) { repository.installed() }
        state.update { it.copy(installed = installed) }
    }
    fun choose(entry: SourceEntry) {
        job?.cancel()
        state.update { it.copy(source = entry, query = "", results = emptyList(), page = 0, detail = null, error = null) }
        search()
    }
    fun query(value: String) { state.update { it.copy(query = value) } }
    fun search(more: Boolean = false) {
        val current = state.value
        val source = current.source ?: return
        val page = if (more) current.page + 1 else 1
        work("正在搜索…") {
            val results = repository.search(source.id, current.query, page)
            state.update { it.copy(results = (if (more) it.results + results.entries else results.entries).distinctBy(SourceManga::key),
                page = page, hasNextPage = results.hasNextPage) }
        }
    }
    fun show(manga: SourceManga) {
        val source = state.value.source ?: return
        work("正在读取漫画详情…") {
            val details = repository.details(source.id, manga)
            val title = repository.saveTitle(source.id, details, onShelf = false)
            showTitle(title)
        }
    }
    fun showSaved(book: Book) = work("正在读取漫画详情…") { showTitle(repository.title(book)) }
    private suspend fun showTitle(title: OnlineTitle) {
        val downloaded = withContext(Dispatchers.IO) { title.manga.chapters.orEmpty().filter { repository.downloaded(title.book.id, it.key) }.map { it.key }.toSet() }
        state.update { it.copy(detail = title, downloaded = downloaded) }
    }
    fun refreshDetail() {
        val title = state.value.detail ?: return
        work("正在刷新章节…") {
            val manga = repository.details(title.sourceId, title.manga)
            showTitle(repository.saveTitle(title.sourceId, manga, title.book.onShelf))
        }
    }
    fun addToShelf() {
        val title = state.value.detail ?: return
        work("正在加入书架…") { showTitle(repository.saveTitle(title.sourceId, title.manga)) }
    }
    fun download(chapter: SourceChapter) {
        val title = state.value.detail ?: return
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            state.update { it.copy(downloadStatus = "正在下载 ${chapter.label}…", error = null) }
            try {
                repository.download(title, chapter) { done, total -> state.update { it.copy(downloadStatus = "正在下载 ${chapter.label} · $done / $total") } }
                if (state.value.detail?.book?.id == title.book.id) state.update { it.copy(downloaded = it.downloaded + chapter.key) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { state.update { it.copy(error = error.message ?: "下载失败，请重试") } }
            finally { state.update { it.copy(downloadStatus = null) } }
        }
    }
    fun deleteDownload(chapter: SourceChapter) {
        val title = state.value.detail ?: return
        work("正在删除下载…") {
            repository.deleteDownload(title.book.id, chapter.key)
            state.update { it.copy(downloaded = it.downloaded - chapter.key) }
        }
    }
    fun cancelDownload() { downloadJob?.cancel() }
    fun back(): Boolean {
        val current = state.value
        if (current.detail == null && current.source == null) return false
        generation++
        job?.cancel()
        state.update { if (it.detail != null) it.copy(detail = null, busy = null, error = null) else it.copy(source = null, busy = null, error = null) }
        return true
    }
    fun dismissError() { state.update { it.copy(error = null) } }
    private fun work(message: String, action: suspend () -> Unit) {
        val currentGeneration = ++generation
        job?.cancel()
        job = viewModelScope.launch {
            state.update { it.copy(busy = message, error = null) }
            try { action() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (generation == currentGeneration) state.update { it.copy(error = error.message ?: "漫画源操作失败，请重试") } }
            finally { if (generation == currentGeneration) state.update { it.copy(busy = null) } }
        }
    }
}
