package com.chen.myviewer.source

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** The original Postcard payload is retained when passing an item back to its source. */
data class SourceManga(
    val key: String,
    val title: String,
    val cover: String?,
    val authors: List<String>,
    val description: String?,
    val url: String?,
    val tags: List<String>,
    val viewer: Int,
    val chapters: List<SourceChapter>?,
    val payload: ByteArray,
) {
    fun json() = JSONObject().put("key", key).put("title", title).put("cover", cover)
        .put("authors", JSONArray(authors)).put("description", description).put("url", url)
        .put("tags", JSONArray(tags)).put("viewer", viewer)
        .put("chapters", chapters?.let { JSONArray(it.map(SourceChapter::json)) })
        .put("payload", Base64.encodeToString(payload, Base64.NO_WRAP))

    companion object {
        fun fromJson(json: JSONObject): SourceManga = SourceManga(
            json.getString("key"), json.getString("title"), json.optionalString("cover"),
            json.optJSONArray("authors").strings(), json.optionalString("description"), json.optionalString("url"),
            json.optJSONArray("tags").strings(), json.optInt("viewer"),
            json.optJSONArray("chapters")?.let { a -> List(a.length()) { SourceChapter.fromJson(a.getJSONObject(it)) } },
            Base64.decode(json.getString("payload"), Base64.DEFAULT),
        )
    }
}

data class SourceChapter(val key: String, val title: String?, val number: Float?, val locked: Boolean, val payload: ByteArray) {
    val label: String get() = listOfNotNull(number?.let { "第 ${if (it % 1f == 0f) it.toInt().toString() else it.toString()} 话" }, title?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { key }
    fun json() = JSONObject().put("key", key).put("title", title).put("number", number).put("locked", locked)
        .put("payload", Base64.encodeToString(payload, Base64.NO_WRAP))
    companion object {
        fun fromJson(json: JSONObject) = SourceChapter(json.getString("key"), json.optionalString("title"),
            if (json.isNull("number")) null else json.getDouble("number").toFloat(), json.optBoolean("locked"),
            Base64.decode(json.getString("payload"), Base64.DEFAULT))
    }
}

data class SourcePage(val url: String, val context: Map<String, String>? = null)
data class SourceSearchResult(val entries: List<SourceManga>, val hasNextPage: Boolean)

internal fun JSONObject.optionalString(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
internal fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { getString(it) }
