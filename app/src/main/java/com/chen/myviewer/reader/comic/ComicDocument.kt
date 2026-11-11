package com.chen.myviewer.reader.comic

import java.io.Closeable
import java.io.File

interface ComicDocument : Closeable {
    val imageNames: List<String>
    val direction: String
    val eagerSizeReads: Boolean get() = true
    fun imageFile(index: Int): File
    fun size(index: Int): ImageSize
    fun cachedSize(index: Int): ImageSize?
    fun restoreIndex(entry: String, fallback: Int): Int
    fun cachedPage(index: Int): ComicPage?
    fun page(index: Int): ComicPage
    fun suggestMode(): String
}
