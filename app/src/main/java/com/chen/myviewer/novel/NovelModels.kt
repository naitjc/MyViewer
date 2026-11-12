package com.chen.myviewer.novel

import com.chen.myviewer.data.Book
import com.chen.myviewer.reader.comic.ComicArchive
import org.json.JSONArray
import org.json.JSONObject

data class NovelSource(val json: JSONObject) {
    val url: String get() = json.getString("bookSourceUrl")
    val id: String get() = ComicArchive.digest(url)
    val name: String get() = json.getString("bookSourceName")
    val baseUrl: String get() = url.substringBefore('#')
    fun rule(group: String): JSONObject = json.optJSONObject(group)
        ?: json.optString(group).takeIf { it.startsWith('{') }?.let(::JSONObject) ?: JSONObject()
}

data class NovelChapter(val name: String, val url: String, val locked: Boolean = false) {
    fun json() = JSONObject().put("name", name).put("url", url).put("locked", locked)
}

data class NovelBook(val url: String, val name: String, val author: String = "", val cover: String = "", val intro: String = "", val tocUrl: String = url, val chapters: List<NovelChapter> = emptyList()) {
    fun json() = JSONObject().put("url", url).put("name", name).put("author", author).put("cover", cover)
        .put("intro", intro).put("tocUrl", tocUrl).put("chapters", JSONArray(chapters.map { it.json() }))
    companion object {
        fun fromJson(j: JSONObject): NovelBook {
            val chapters = j.optJSONArray("chapters") ?: JSONArray()
            return NovelBook(j.getString("url"), j.getString("name"), j.optString("author"), j.optString("cover"), j.optString("intro"), j.optString("tocUrl", j.getString("url")),
                List(chapters.length()) { chapters.getJSONObject(it).let { c -> NovelChapter(c.getString("name"), c.getString("url"), c.optBoolean("locked")) } })
        }
    }
}

data class NovelTitle(val book: Book, val sourceId: String, val novel: NovelBook)
