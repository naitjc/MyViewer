package com.chen.myviewer.novel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chen.myviewer.ViewerApplication
import com.chen.myviewer.data.Book
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class NovelViewModel(application: Application) : AndroidViewModel(application) {
    val repository = (application as ViewerApplication).novels
    val detail = MutableStateFlow<NovelTitle?>(null)
    val available = MutableStateFlow<Set<String>>(emptySet())
    val error = MutableStateFlow<String?>(null)
    private var job: Job? = null
    fun showSaved(book: Book) {
        job?.cancel()
        detail.value = null
        error.value = null
        job = viewModelScope.launch {
            try {
                val title = repository.title(book)
                available.value = withContext(Dispatchers.IO) { repository.available(title) }
                detail.value = title
            } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { error.value = failure.message ?: "无法读取已保存的小说" }
        }
    }
    fun back() { job?.cancel(); detail.value = null; error.value = null }
}
