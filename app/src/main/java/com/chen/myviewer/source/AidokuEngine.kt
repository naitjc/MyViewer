package com.chen.myviewer.source

import android.content.Context
import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.ImportValues
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.Parser
import com.dylibso.chicory.wasm.types.FunctionImport
import com.dylibso.chicory.wasm.types.MemoryLimits
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

internal class AidokuEngine(context: Context, val source: InstalledSource, private val http: SourceHttp) {
    private val host = AidokuHost(context, source.id, http)
    private val module = Parser.parse(File(source.directory, "main.wasm"))
    private val exports = (0 until module.exportSection().exportCount()).map { module.exportSection().getExport(it).name() }.toSet()
    private val instance: Instance

    init {
        val functions = (0 until module.importSection().importCount()).map { index ->
            val import = module.importSection().getImport(index)
            require(import is FunctionImport) { "漫画源使用了不支持的 WASM 导入类型。" }
            HostFunction(import.module(), import.name(), module.typeSection().getType(import.typeIndex())) { caller, arguments ->
                host.memory = caller.memory()
                val value = host.call(import.module(), import.name(), arguments)
                if (module.typeSection().getType(import.typeIndex()).returns().isEmpty()) longArrayOf() else longArrayOf(value)
            }
        }
        var instructions = 0
        host.deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
        instance = Instance.builder(module).withImportValues(ImportValues.builder().withFunctions(functions).build())
            .withMemoryLimits(MemoryLimits(32, 2048))
            .withUnsafeExecutionListener { _, _ -> if (++instructions and 8191 == 0) host.checkDeadline() }.build()
        host.memory = instance.memory()
        loadDefaults()
        if ("start" in exports) instance.export("start").apply()
    }

    private fun loadDefaults() {
        host.setDefault("url", PostcardWriter().string(source.entry.baseUrl).bytes())
        val language = source.entry.languages.firstOrNull { it.startsWith("zh") }
            ?: source.entry.languages.firstOrNull { it == "en" } ?: source.entry.languages.firstOrNull()
        if (language != null) {
            host.setDefault("languages", PostcardWriter().unsigned(1).string(language).bytes())
        }
        val settings = File(source.directory, "settings.json")
        if (settings.exists()) {
            fun visit(array: JSONArray) {
                repeat(array.length()) { index ->
                    val item = array.getJSONObject(index)
                    item.optJSONArray("items")?.let(::visit)
                    val key = item.optString("key")
                    val value = item.opt("default")
                    if (key.isNotBlank() && value != null && value != JSONObject.NULL) {
                        val encoded = when (value) {
                            is Boolean -> byteArrayOf(if (value) 1 else 0)
                            is String -> PostcardWriter().string(value).bytes()
                            is Double, is Float -> PostcardWriter().float((value as Number).toFloat()).bytes()
                            is Number -> PostcardWriter().unsigned((value.toLong() shl 1) xor (value.toLong() shr 63)).bytes()
                            is JSONArray -> PostcardWriter().unsigned(value.length().toLong()).apply { repeat(value.length()) { string(value.getString(it)) } }.bytes()
                            else -> null
                        }
                        encoded?.let { host.setDefault(key, it) }
                    }
                }
            }
            visit(JSONArray(settings.readText()))
        }
    }

    private fun invoke(name: String, vararg args: Long): ByteArray {
        host.deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
        host.lastMessage = null
        val pointer = instance.export(name).apply(*args).first().toInt()
        check(pointer > 0) { host.lastMessage ?: "漫画源调用失败（$name，代码 $pointer）。" }
        try {
            val size = host.memory.readInt(pointer)
            if (size == -1) {
                val length = host.memory.readInt(pointer + 8)
                require(length in 12..16_777_216)
                error(host.memory.readString(pointer + 12, length - 12))
            }
            require(size in 8..16_777_216) { "漫画源返回的数据大小无效。" }
            return host.memory.readBytes(pointer + 8, size - 8)
        } finally { instance.export("free_result").apply(pointer.toLong()) }
    }

    @Synchronized fun search(query: String, page: Int): SourceSearchResult {
        val queryId = host.store(query.toByteArray())
        val filtersId = host.store(byteArrayOf(0))
        try {
            val reader = Postcard(invoke("get_search_manga_list", queryId.toLong(), page.toLong(), filtersId.toLong()))
            val result = SourceSearchResult(reader.list(reader::manga), reader.boolean())
            reader.complete()
            return result
        } finally { host.values.remove(queryId); host.values.remove(filtersId) }
    }
    @Synchronized fun details(manga: SourceManga): SourceManga {
        val id = host.store(manga.payload)
        try {
            val reader = Postcard(invoke("get_manga_update", id.toLong(), 1, 1))
            return reader.manga().also { reader.complete() }
        } finally { host.values.remove(id) }
    }
    @Synchronized fun pages(manga: SourceManga, chapter: SourceChapter): List<SourcePage> {
        val mangaId = host.store(manga.payload)
        val chapterId = host.store(chapter.payload)
        try {
            val reader = Postcard(invoke("get_page_list", mangaId.toLong(), chapterId.toLong()))
            return reader.list(reader::page).also { reader.complete(); require(it.isNotEmpty()) { "此章节没有可阅读的页面。" } }
        } finally { host.values.remove(mangaId); host.values.remove(chapterId) }
    }
    @Synchronized fun imageRequest(page: SourcePage): SourceRequest {
        if ("get_image_request" !in exports) return SourceRequest("GET", page.url)
        val urlId = host.store(PostcardWriter().string(page.url).bytes())
        val contextId = page.context?.let { host.store(PostcardWriter().map(it).bytes()) } ?: -1
        try {
            val reader = Postcard(invoke("get_image_request", urlId.toLong(), contextId.toLong()))
            val requestId = reader.signed().toInt()
            reader.complete()
            return (host.values.remove(requestId) as? SourceRequest) ?: error("漫画源未返回有效图片请求。")
        } finally { host.values.remove(urlId); host.values.remove(contextId) }
    }
    @Synchronized fun processPageImage(page: SourcePage, response: HttpResult, request: SourceRequest): ByteArray {
        if ("process_page_image" !in exports) return response.data
        val checkpoint = host.canvas.checkpoint()
        var responseId = -1
        var contextId = -1
        try {
            val imageId = host.canvas.image(response.data)
            val writer = PostcardWriter().unsigned(response.status.toLong()).map(response.headers)
                .unsigned(1).string(request.url).map(request.headers).signed(imageId.toLong())
            responseId = host.store(writer.bytes())
            contextId = page.context?.let { host.store(PostcardWriter().map(it).bytes()) } ?: -1
            val reader = Postcard(invoke("process_page_image", responseId.toLong(), contextId.toLong()))
            val result = reader.signed().toInt()
            reader.complete()
            return host.canvas.data(result)
        } finally {
            host.values.remove(responseId)
            host.values.remove(contextId)
            host.canvas.releaseAfter(checkpoint)
        }
    }

}
