package com.chen.myviewer.source

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SourcePreference(val key: String, val title: String, val kind: String, val value: Any, val choices: List<Pair<String, Any>> = emptyList())

internal class SourcePreferences(private val context: Context) {
    fun read(source: InstalledSource): List<SourcePreference> {
        val definitions = mutableListOf<JSONObject>()
        definitions += JSONObject().put("key", "url").put("title", "站点地址").put("type", "text").put("default", source.entry.baseUrl)
        if (source.entry.languages.size > 1) definitions += JSONObject().put("key", "languages").put("title", "内容语言").put("type", "multi-select")
            .put("values", JSONArray(source.entry.languages)).put("titles", JSONArray(source.entry.languages))
            .put("default", JSONArray(listOf(source.entry.languages.firstOrNull { it.startsWith("zh") } ?: source.entry.languages.first())))
        fun visit(array: JSONArray) {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                item.optJSONArray("items")?.let(::visit)
                if (item.optString("type") in setOf("switch", "select", "text", "multi-select") && item.has("key")) definitions += item
            }
        }
        val file = File(source.directory, "settings.json")
        if (file.exists()) visit(JSONArray(file.readText()))
        val preferences = context.getSharedPreferences("source-${source.id}", Context.MODE_PRIVATE)
        return definitions.mapNotNull { definition ->
            val key = definition.getString("key")
            val kind = definition.getString("type")
            val values = definition.optJSONArray("values")
            val titles = definition.optJSONArray("titles")
            val choices = if (values == null) emptyList() else List(values.length()) { index -> (titles?.optString(index) ?: values.get(index).toString()) to values.get(index) }
            val fallback = definition.opt("default")?.takeIf { it != JSONObject.NULL }
                ?: when (kind) { "switch" -> false; "multi-select" -> JSONArray(); "select" -> choices.firstOrNull()?.second ?: ""; else -> "" }
            val encoded = preferences.getString(key, null)
            val value = runCatching {
                if (encoded == null) fallback else {
                    val reader = Postcard(Base64.decode(encoded, Base64.DEFAULT))
                    when (fallback) {
                        is Boolean -> reader.boolean()
                        is Double, is Float -> reader.float()
                        is Number -> reader.signed().toInt()
                        is JSONArray -> JSONArray(reader.list(reader::string))
                        else -> reader.string()
                    }
                }
            }.getOrDefault(fallback)
            SourcePreference(key, definition.optString("title", key), kind, value, choices)
        }.distinctBy { it.key }
    }

    fun write(sourceId: String, setting: SourcePreference) {
        if (setting.key == "url") {
            val uri = java.net.URI(setting.value.toString())
            require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) { "请输入完整的站点网址。" }
        }
        val writer = PostcardWriter()
        val bytes = when (val value = setting.value) {
            is Boolean -> byteArrayOf(if (value) 1 else 0)
            is Double, is Float -> writer.float((value as Number).toFloat()).bytes()
            is Number -> writer.unsigned((value.toLong() shl 1) xor (value.toLong() shr 63)).bytes()
            is JSONArray -> writer.unsigned(value.length().toLong()).apply { repeat(value.length()) { string(value.getString(it)) } }.bytes()
            else -> writer.string(value.toString().trimEnd('/').takeIf { setting.key == "url" } ?: value.toString()).bytes()
        }
        context.getSharedPreferences("source-$sourceId", Context.MODE_PRIVATE).edit().putString(setting.key, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
    }
}
