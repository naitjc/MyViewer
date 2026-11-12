package com.chen.myviewer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chen.myviewer.data.*
import com.chen.myviewer.reader.comic.ComicArchive
import com.chen.myviewer.reader.comic.ComicDocument
import com.chen.myviewer.novel.NovelTitle
import com.chen.myviewer.novel.NovelChapter
import com.chen.myviewer.source.OnlineComicDocument
import com.chen.myviewer.source.OnlineTitle
import com.chen.myviewer.source.SourceChapter
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
    val comic: ComicDocument? = null,
    val jump: Int = 0,
    val jumpOffset: Int = 0,
    val onlineTitle: OnlineTitle? = null,
    val chapter: SourceChapter? = null,
    val novelTitle: NovelTitle? = null,
    val novelChapter: NovelChapter? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ViewerApplication
    val repository = app.repository
    val library = repository.dao.observeLibrary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val folders = repository.dao.folders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())
    val librarySort = app.settings.librarySort.stateIn(viewModelScope, SharingStarted.Eagerly, LibrarySort.RECENT)
    val libraryColumns = app.settings.libraryColumns.stateIn(viewModelScope, SharingStarted.Eagerly, 3)
    val bookReadingTimes = repository.dao.observeBookReadingTimes().map { rows -> rows.associate { it.bookId to it.totalMs } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    private val readingClock = ReadingTimeTracker(app.readingTime::record)
    fun readingTotals(day: String) = repository.dao.observeReadingTotals(day)
    fun readingVisibility(token: String, visible: Boolean) {
        reader.value?.takeIf { it.position.sessionId == token }?.let { readingClock.visible(token, it.book.id, visible) }
    }
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

    init {
        viewModelScope.launch { for (position in saves) runCatching { repository.save(position) }.onFailure { notice.value = "阅读位置保存失败，请检查存储空间。" } }
        viewModelScope.launch { app.readingTime.errors.collect { notice.value = "阅读时间保存失败，请检查存储空间。" } }
    }

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

    fun open(book: Book, online: OnlineTitle? = null, chapter: SourceChapter? = null, novel: NovelTitle? = null, novelChapter: NovelChapter? = null) {
        if (book.storage == "NOVEL" && novel == null) {
            viewModelScope.launch {
                try {
                    val title = app.novels.title(book)
                    val last = repository.dao.position(book.id)?.chapterKey
                    val selected = title.novel.chapters.firstOrNull { it.url == last } ?: title.novel.chapters.firstOrNull() ?: error("请刷新小说目录。")
                    open(book, novel = title, novelChapter = selected)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error.value = e.message ?: "在线小说暂时无法打开" }
            }
            return
        }
        if (book.storage == "ONLINE" && online == null) {
            viewModelScope.launch {
                try {
                    val title = app.sources.title(book)
                    val last = repository.dao.position(book.id)?.chapterKey
                    val selected = title.manga.chapters?.firstOrNull { it.key == last }
                        ?: title.manga.chapters?.lastOrNull() ?: error("请在漫画详情中刷新章节列表。")
                    open(book, title, selected)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error.value = e.message ?: "在线漫画暂时无法打开" }
            }
            return
        }
        readingClock.stop()
        openJob?.cancel()
        val previousPosition = reader.value?.position
        val old = reader.value?.comic
        reader.value = null; error.value = null; failedBook.value = null; loading.value = book
        openJob = viewModelScope.launch {
            var archive: ComicDocument? = null
            try {
                closeSave?.await()
                previousPosition?.let { repository.save(it.copy(sequence = it.sequence + 1)) }
                withContext(Dispatchers.IO) { old?.close() }
                val text = if (novel != null && novelChapter != null) app.novels.loadChapter(novel, novelChapter) else if (book.kind == "TEXT") repository.loadText(book) else null
                archive = if (online != null && chapter != null) {
                    OnlineComicDocument(app.sources, online, chapter, app.sources.chapterPages(online, chapter))
                } else if (book.kind == "COMIC") repository.loadComic(book) else null
                val prefs = repository.dao.preferences(book.id) ?: withContext(Dispatchers.IO) {
                    BookPreferences(book.id, archive?.suggestMode() ?: "PAGE", archive?.direction ?: "LTR")
                }.also { repository.dao.putPreferences(it) }
                var position = repository.startSession(book)
                if (chapter != null) {
                    val saved = repository.dao.chapterPosition(book.id, chapter.key)
                    position = position.copy(chapterKey = chapter.key, imageIndex = saved?.imageIndex ?: 0,
                        imageEntry = "", imageRatio = saved?.imageRatio ?: 0f, progress = saved?.progress ?: 0f)
                    repository.dao.putPosition(position)
                }
                if (novelChapter != null) {
                    val saved = repository.dao.chapterPosition(book.id, novelChapter.url)
                    position = position.copy(chapterKey = novelChapter.url, textOffset = saved?.textOffset ?: 0, progress = saved?.progress ?: 0f)
                    repository.dao.putPosition(position)
                }
                if (archive != null) {
                    val index = archive.restoreIndex(position.imageEntry, position.imageIndex)
                    position = position.copy(imageIndex = index, imageEntry = archive.imageNames[index],
                        progress = (index + if (prefs.mode == "PAGE") 1f else position.imageRatio) / archive.imageNames.size)
                }
                if (text != null) position = position.copy(textOffset = position.textOffset.coerceIn(0, (text.text.length - 1).coerceAtLeast(0)))
                sequence = 0L
                latestComicPosition = null; lastComicPublication = 0L
                reader.value = ReaderSession(book, position, prefs, text, archive, onlineTitle = online, chapter = chapter, novelTitle = novel, novelChapter = novelChapter)
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
        val safe = index.coerceIn(0, archive.imageNames.lastIndex)
        val progress = if (session.preferences.mode == "PAGE") (safe + 1f) / archive.imageNames.size else (safe + ratio.coerceIn(0f, 1f)) / archive.imageNames.size
        val position = session.position.copy(imageIndex = safe, imageEntry = archive.imageNames[safe], imageRatio = ratio.coerceIn(0f, 1f), progress = progress, sequence = ++sequence)
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
        repository.dao.putBookmark(Bookmark(UUID.randomUUID().toString(), session.book.id, offset, doc.chapterAt(offset), doc.snippet(offset), chapterKey = session.position.chapterKey))
        notice.value = "已保存书签"
    }
    fun deleteBookmark(id: String) = viewModelScope.launch { repository.dao.deleteBookmark(id) }
    fun finishReading(token: String) {
        val session = reader.value?.takeIf { it.position.sessionId == token } ?: return
        val comic = session.comic
        if (comic != null && comic.cachedPage(comic.imageNames.lastIndex) == null) return
        closeCurrentReader(completed = true)
    }
    fun turnComic(forward: Boolean, token: String) {
        val session = reader.value?.takeIf { it.position.sessionId == token } ?: return
        val comic = session.comic ?: return
        if (forward && session.position.imageIndex == comic.imageNames.lastIndex) {
            if (comic.cachedPage(session.position.imageIndex) != null) finishReading(token)
        } else seekComic(session.position.imageIndex + if (forward) 1 else -1)
    }
    fun closeReader() = closeCurrentReader(completed = false)
    private fun closeCurrentReader(completed: Boolean) {
        readingClock.stop()
        val previous = reader.value
        var position = latestComicPosition?.takeIf { it.sessionId == previous?.position?.sessionId } ?: previous?.position
        if (completed && previous != null && position != null) {
            val lastImage = previous.comic?.imageNames?.lastIndex ?: position.imageIndex
            position = position.copy(progress = 1f, textOffset = previous.text?.text?.lastIndex?.coerceAtLeast(0) ?: position.textOffset,
                imageIndex = lastImage, imageEntry = previous.comic?.imageNames?.get(lastImage) ?: position.imageEntry, imageRatio = 1f)
        }
        position?.let {
            val saved = it.copy(sequence = ++sequence)
            closeSave = viewModelScope.async {
                try {
                    if (completed) repository.dao.finishReading(saved, previous?.novelTitle?.novel?.chapters?.map { it.url } ?: previous?.onlineTitle?.manga?.chapters.orEmpty().map { chapter -> chapter.key })
                    else repository.save(saved)
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) { notice.value = "阅读状态保存失败，请重新打开后重试。" }
            }
        }
        latestComicPosition = null
        reader.value = null; loading.value = null; openJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) { previous?.comic?.close() }
        if (completed) notice.value = if (previous?.chapter != null || previous?.novelChapter != null) "本章已读完" else "已读完"
    }
    fun updateSettings(value: ReaderSettings) = viewModelScope.launch { app.settings.update(value) }
    fun setLibrarySort(value: LibrarySort) = viewModelScope.launch { app.settings.setLibrarySort(value) }
    fun setLibraryColumns(value: Int) = viewModelScope.launch { app.settings.setLibraryColumns(value) }
    fun hide(book: Book) = viewModelScope.launch { repository.dao.shelf(book.id, false) }
    fun setCompleted(book: Book, completed: Boolean) = viewModelScope.launch {
        try {
            repository.dao.setCompleted(book.id, completed)
            notice.value = if (completed) "已标记已读完" else "已取消已读完标记"
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { notice.value = "阅读状态保存失败，请重试。" }
    }
    fun clearHistory() = viewModelScope.launch { repository.dao.clearHistory() }
    fun removeFolder(uri: String) = viewModelScope.launch { repository.dao.deleteFolder(uri) }
    fun clearComicCache() = viewModelScope.launch {
        if (reader.value != null) return@launch
        withContext(Dispatchers.IO) {
            java.io.File(app.cacheDir, "comic").deleteRecursively()
            java.io.File(app.cacheDir, "online").deleteRecursively()
        }
        notice.value = "漫画缓存已清理，书籍、进度和书签已保留"
    }
    fun relink(book: Book, uri: Uri) = viewModelScope.launch {
        runCatching { repository.dao.relink(book.id, uri.toString()); repository.dao.book(book.id)?.let { open(it) } }.onFailure { error.value = it.message }
    }
    fun encoding(value: String) = viewModelScope.launch {
        val book = reader.value?.book ?: return@launch
        repository.dao.encoding(book.id, value); open(book.copy(encoding = value))
    }
    override fun onCleared() { readingClock.stop(); reader.value?.comic?.close(); super.onCleared() }
}
