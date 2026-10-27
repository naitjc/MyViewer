package com.chen.myviewer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chen.myviewer.data.*
import com.chen.myviewer.reader.comic.ComicArchive
import com.chen.myviewer.reader.text.TextDocument
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID

data class ImportUi(
    val candidates: List<ImportCandidate> = emptyList(),
    val copy: Boolean = false,
    val busy: Boolean = false,
    val message: String = "",
    val result: ImportResult? = null,
)
data class ReaderSession(
    val book: Book,
    val position: ReadingPosition,
    val preferences: BookPreferences,
    val text: TextDocument? = null,
    val comic: ComicArchive? = null,
    val jump: Int = 0,
    val jumpOffset: Int = 0,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ViewerApplication
    val repository = app.repository
    val library = repository.dao.observeLibrary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val folders = repository.dao.folders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())
    val librarySort = app.settings.librarySort.stateIn(viewModelScope, SharingStarted.Eagerly, LibrarySort.RECENT)
    val libraryColumns = app.settings.libraryColumns.stateIn(viewModelScope, SharingStarted.Eagerly, 3)
    val importing = MutableStateFlow<ImportUi?>(null)
    val reader = MutableStateFlow<ReaderSession?>(null)
    val loading = MutableStateFlow<Book?>(null)
    val error = MutableStateFlow<String?>(null)
    val failedBook = MutableStateFlow<Book?>(null)
    val notice = MutableStateFlow<String?>(null)
    private var openJob: Job? = null
    private val saves = Channel<ReadingPosition>(Channel.CONFLATED)
    private var sequence = 0L
    private var lastSave = 0L
    private var closeSave: Deferred<Unit>? = null
    private var latestComicPosition: ReadingPosition? = null
    private var lastComicPublication = 0L

    init { viewModelScope.launch { for (position in saves) runCatching { repository.save(position) }.onFailure { notice.value = "阅读位置保存失败，请检查存储空间。" } } }

    fun selectedFiles(uris: List<Uri>, temporaryUris: Set<Uri> = emptySet()) = viewModelScope.launch {
        importing.value = ImportUi(copy = settings.value.copyOnImport, busy = true, message = "正在检查文件…")
        runCatching { uris.map { repository.describe(it) } }.onSuccess { candidates ->
            val supported = candidates.filter { LibraryRepository.supported(it.name) }.map { it.copy(directAllowed = it.uri !in temporaryUris) }
            importing.value = ImportUi(supported, settings.value.copyOnImport || supported.any { !it.directAllowed }, message = if (supported.isEmpty()) "没有找到 TXT 或 ZIP 文件。" else "已选择 ${supported.size} 个文件")
        }.onFailure { importing.value = ImportUi(message = it.message ?: "文件无法读取") }
    }
    fun selectedFolder(uri: Uri, recursive: Boolean = true, directAllowed: Boolean = true) = viewModelScope.launch {
        importing.value = ImportUi(copy = settings.value.copyOnImport, busy = true, message = "正在扫描文件夹…")
        runCatching { repository.scan(uri, recursive) { count -> importing.update { it?.copy(message = "已发现 $count 本…") } } }.onSuccess {
            importing.value = ImportUi(it.map { candidate -> candidate.copy(directAllowed = directAllowed) }, settings.value.copyOnImport || !directAllowed, message = "找到 ${it.size} 本，可选择本次导入方式")
        }.onFailure { importing.value = ImportUi(message = it.message ?: "扫描失败，请重新授权文件夹。") }
    }
    fun importCopy(value: Boolean) { importing.update { it?.copy(copy = value) } }
    fun startImport() = viewModelScope.launch {
        val selection = importing.value ?: return@launch
        if (selection.busy || selection.candidates.isEmpty()) return@launch
        importing.value = selection.copy(busy = true, message = "准备导入…")
        app.settings.update(settings.value.copy(copyOnImport = selection.copy))
        try {
            val result = repository.import(selection.candidates, selection.copy) { current, total -> importing.update { it?.copy(message = "正在导入 $current / $total") } }
            importing.value = selection.copy(result = result, message = "导入完成")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { importing.value = selection.copy(message = e.message ?: "导入失败") }
    }
    fun dismissImport() { if (importing.value?.busy != true) importing.value = null }

    fun open(book: Book) {
        openJob?.cancel()
        val previousPosition = reader.value?.position
        val old = reader.value?.comic
        reader.value = null; error.value = null; failedBook.value = null; loading.value = book
        openJob = viewModelScope.launch {
            var archive: ComicArchive? = null
            try {
                closeSave?.await()
                previousPosition?.let { repository.save(it.copy(sequence = it.sequence + 1)) }
                withContext(Dispatchers.IO) { old?.close() }
                val text = if (book.kind == "TEXT") repository.loadText(book) else null
                archive = if (book.kind == "COMIC") repository.loadComic(book) else null
                val prefs = repository.dao.preferences(book.id) ?: withContext(Dispatchers.IO) {
                    BookPreferences(book.id, archive?.suggestMode() ?: "PAGE", archive?.direction ?: "LTR")
                }.also { repository.dao.putPreferences(it) }
                var position = repository.startSession(book)
                if (archive != null) {
                    val index = archive.restoreIndex(position.imageEntry, position.imageIndex)
                    position = position.copy(imageIndex = index, imageEntry = archive.images[index].name,
                        progress = (index + if (prefs.mode == "PAGE") 1f else position.imageRatio) / archive.images.size)
                }
                if (text != null) position = position.copy(textOffset = position.textOffset.coerceIn(0, (text.text.length - 1).coerceAtLeast(0)))
                sequence = 0L
                latestComicPosition = null; lastComicPublication = 0L
                reader.value = ReaderSession(book, position, prefs, text, archive)
            } catch (e: CancellationException) { withContext(NonCancellable + Dispatchers.IO) { archive?.close() }; throw e }
            catch (e: Exception) { withContext(Dispatchers.IO) { archive?.close() }; error.value = e.message ?: "书籍暂时无法打开"; failedBook.value = book }
            finally { if (loading.value?.id == book.id) loading.value = null }
        }
    }
    fun textPosition(offset: Int, force: Boolean = true) {
        val session = reader.value ?: return
        val length = session.text?.text?.length ?: return
        val position = session.position.copy(textOffset = offset.coerceIn(0, length), progress = if (length == 0) 0f else offset.toFloat() / length, sequence = ++sequence)
        reader.value = session.copy(position = position); enqueue(position, force)
    }
    fun comicPosition(index: Int, ratio: Float = 0f, force: Boolean = true) {
        val session = reader.value ?: return; val archive = session.comic ?: return
        val safe = index.coerceIn(0, archive.images.lastIndex)
        val progress = if (session.preferences.mode == "PAGE") (safe + 1f) / archive.images.size else (safe + ratio.coerceIn(0f, 1f)) / archive.images.size
        val position = session.position.copy(imageIndex = safe, imageEntry = archive.images[safe].name, imageRatio = ratio.coerceIn(0f, 1f), progress = progress, sequence = ++sequence)
        latestComicPosition = position
        val now = android.os.SystemClock.uptimeMillis()
        if (force || safe != session.position.imageIndex || now - lastComicPublication >= 200) {
            lastComicPublication = now; reader.value = session.copy(position = position)
        }
        enqueue(position, force)
    }
    fun seekText(offset: Int) { reader.update { it?.copy(jump = it.jump + 1, jumpOffset = offset) } }
    fun seekComic(index: Int, ratio: Float = 0f) { comicPosition(index, ratio); reader.update { it?.copy(jump = it.jump + 1) } }
    private fun enqueue(position: ReadingPosition, force: Boolean) {
        val now = android.os.SystemClock.uptimeMillis()
        if (force || now - lastSave >= 700) { lastSave = now; saves.trySend(position) }
    }
    fun flushPosition() {
        val session = reader.value ?: return
        val position = latestComicPosition?.takeIf { it.sessionId == session.position.sessionId } ?: session.position
        reader.value = session.copy(position = position); enqueue(position, true)
    }
    fun mode(mode: String) = viewModelScope.launch {
        flushPosition()
        val session = reader.value ?: return@launch
        val prefs = session.preferences.copy(mode = mode)
        repository.dao.putPreferences(prefs); reader.update { current -> if (current?.book?.id == session.book.id) current.copy(preferences = prefs) else current }
    }
    fun direction(direction: String) = viewModelScope.launch {
        val session = reader.value ?: return@launch
        val prefs = session.preferences.copy(direction = direction)
        repository.dao.putPreferences(prefs); reader.update { current -> if (current?.book?.id == session.book.id) current.copy(preferences = prefs) else current }
    }
    fun addBookmark() = viewModelScope.launch {
        val session = reader.value ?: return@launch; val doc = session.text ?: return@launch
        val offset = session.position.textOffset
        repository.dao.putBookmark(Bookmark(UUID.randomUUID().toString(), session.book.id, offset, doc.chapterAt(offset), doc.snippet(offset)))
        notice.value = "已保存书签"
    }
    fun deleteBookmark(id: String) = viewModelScope.launch { repository.dao.deleteBookmark(id) }
    fun closeReader() {
        val previous = reader.value
        val position = latestComicPosition?.takeIf { it.sessionId == previous?.position?.sessionId } ?: previous?.position
        position?.let { closeSave = viewModelScope.async { repository.save(it.copy(sequence = ++sequence)) } }
        latestComicPosition = null
        reader.value = null; loading.value = null; openJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) { previous?.comic?.close() }
    }
    fun updateSettings(value: ReaderSettings) = viewModelScope.launch { app.settings.update(value) }
    fun setLibrarySort(value: LibrarySort) = viewModelScope.launch { app.settings.setLibrarySort(value) }
    fun setLibraryColumns(value: Int) = viewModelScope.launch { app.settings.setLibraryColumns(value) }
    fun hide(book: Book) = viewModelScope.launch { repository.dao.shelf(book.id, false) }
    fun clearHistory() = viewModelScope.launch { repository.dao.clearHistory() }
    fun removeFolder(uri: String) = viewModelScope.launch { repository.dao.deleteFolder(uri) }
    fun clearComicCache() = viewModelScope.launch {
        if (reader.value != null) return@launch
        withContext(Dispatchers.IO) { java.io.File(app.cacheDir, "comic").deleteRecursively() }
        notice.value = "漫画缓存已清理，书籍、进度和书签已保留"
    }
    fun relink(book: Book, uri: Uri) = viewModelScope.launch {
        runCatching { repository.dao.relink(book.id, uri.toString()); repository.dao.book(book.id)?.let(::open) }.onFailure { error.value = it.message }
    }
    fun encoding(value: String) = viewModelScope.launch {
        val book = reader.value?.book ?: return@launch
        repository.dao.encoding(book.id, value); open(book.copy(encoding = value))
    }
    override fun onCleared() { reader.value?.comic?.close(); super.onCleared() }
}
