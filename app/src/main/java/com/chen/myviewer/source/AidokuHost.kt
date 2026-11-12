package com.chen.myviewer.source

import android.content.Context
import android.util.Base64
import com.dylibso.chicory.runtime.Memory
import org.jsoup.Jsoup
import org.mozilla.javascript.Context as JsContext
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

internal data class SourceRequest(var method: String, var url: String = "", val headers: MutableMap<String, String> = mutableMapOf(), var body: ByteArray? = null, var timeout: Double = 30.0, var response: HttpResult? = null)

internal class AidokuHost(context: Context, sourceId: String, val http: SourceHttp) {
    val values = mutableMapOf<Int, Any>()
    private var nextId = 1
    lateinit var memory: Memory
    var deadline = Long.MAX_VALUE
    var lastMessage: String? = null
    private val defaults = context.getSharedPreferences("source-$sourceId", Context.MODE_PRIVATE)
    private val html = AidokuHtml(this)
    val canvas = AidokuCanvas(this)
    private var permits = 0
    private var periodMs = 0L
    private val requestTimes = ArrayDeque<Long>()
    private val jsFactory = object : org.mozilla.javascript.ContextFactory() {
        override fun makeContext(): JsContext = super.makeContext().apply {
            optimizationLevel = -1
            languageVersion = JsContext.VERSION_ES6
            instructionObserverThreshold = 10_000
            setClassShutter { false }
        }
        override fun observeInstructionCount(cx: JsContext, instructionCount: Int) { checkDeadline() }
    }

    fun store(value: Any?): Int {
        if (value == null) return -5
        require(values.size < 100_000) { "漫画源创建了过多临时对象。" }
        return nextId++.also { values[it] = value }
    }
    fun string(args: LongArray, index: Int): String = memory.readString(args[index].toInt(), args[index + 1].toInt())
    fun buffer(id: Int): ByteArray? = when (val value = values[id]) { is ByteArray -> value; is String -> value.toByteArray(); else -> null }
    fun checkDeadline() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("操作已取消")
        check(System.nanoTime() < deadline) { "漫画源执行超时，请重试。" }
    }
    fun has(name: String): Boolean = defaults.contains(name)
    fun setDefault(name: String, value: ByteArray) { if (!has(name)) defaults.edit().putString(name, Base64.encodeToString(value, Base64.NO_WRAP)).apply() }

    fun call(module: String, name: String, args: LongArray): Long {
        checkDeadline()
        return when (module) {
            "std" -> standard(name, args)
            "env" -> environment(name, args)
            "net" -> network(name, args)
            "html" -> try { html.call(name, args) } catch (error: org.jsoup.select.Selector.SelectorParseException) { -4 }
            "canvas" -> canvas.call(name, args)
            "defaults" -> preference(name, args)
            "js" -> javascript(name, args)
            else -> error("暂不支持漫画源接口 $module.$name。")
        }
    }

    private fun standard(name: String, args: LongArray): Long = when (name) {
        "destroy" -> { values.remove(args[0].toInt()); 0 }
        "buffer_len" -> buffer(args[0].toInt())?.size?.toLong() ?: -1
        "read_buffer" -> {
            val bytes = buffer(args[0].toInt()) ?: return -1
            if (bytes.size > args[2]) -2 else { memory.write(args[1].toInt(), bytes); 0 }
        }
        "current_date" -> java.lang.Double.doubleToRawLongBits(System.currentTimeMillis() / 1000.0)
        "utc_offset" -> (TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000).toLong()
        "parse_date" -> {
            val timestamp = runCatching {
                val locale = string(args, 4).takeIf { it.isNotBlank() }?.replace('_', '-')?.let(Locale::forLanguageTag) ?: Locale.US
                val timezone = string(args, 6)
                SimpleDateFormat(string(args, 2), locale).apply {
                    timeZone = if (timezone == "current") TimeZone.getDefault() else TimeZone.getTimeZone(timezone.ifBlank { "UTC" })
                    isLenient = false
                }.parse(string(args, 0))!!.time / 1000.0
            }.getOrDefault(-5.0)
            java.lang.Double.doubleToRawLongBits(timestamp)
        }
        else -> error("暂不支持漫画源接口 std.$name。")
    }

    private fun environment(name: String, args: LongArray): Long = when (name) {
        "print" -> { lastMessage = string(args, 0).take(1000); 0 }
        "abort" -> error("漫画源运行中止。${lastMessage.orEmpty()}")
        "sleep" -> { TimeUnit.SECONDS.sleep(args[0].coerceIn(0, 60)); checkDeadline(); 0 }
        "send_partial_result" -> 0 // A complete result follows through the ordinary return value.
        else -> error("暂不支持漫画源接口 env.$name。")
    }

    private fun preference(name: String, args: LongArray): Long {
        val key = string(args, 0)
        return when (name) {
            "get" -> defaults.getString(key, null)?.let { store(Base64.decode(it, Base64.DEFAULT)).toLong() } ?: -1
            "set" -> {
                val edit = defaults.edit()
                if (args[2] == 6L || args[3] == 0L) edit.remove(key)
                else {
                    val ptr = args[3].toInt()
                    val size = memory.readInt(ptr)
                    require(size in 8..4_194_304)
                    edit.putString(key, Base64.encodeToString(memory.readBytes(ptr + 8, size - 8), Base64.NO_WRAP))
                }
                edit.apply(); 0
            }
            else -> error("暂不支持漫画源接口 defaults.$name。")
        }
    }

    private fun send(request: SourceRequest): Long {
        if (permits > 0) {
            val now = System.currentTimeMillis()
            while (requestTimes.isNotEmpty() && now - requestTimes.first() >= periodMs) requestTimes.removeFirst()
            if (requestTimes.size >= permits) Thread.sleep((periodMs - (now - requestTimes.removeFirst())).coerceAtLeast(0))
            requestTimes.addLast(System.currentTimeMillis())
        }
        checkDeadline()
        return try {
            request.response = http.request(request.url, request.headers, request.method, request.body, request.timeout)
            0
        } catch (error: java.io.IOException) {
            lastMessage = "网络请求失败，请检查网络后重试。"
            -10
        }
    }

    private fun network(name: String, args: LongArray): Long {
        if (name == "init") {
            val method = listOf("GET", "POST", "PUT", "HEAD", "DELETE", "PATCH", "OPTIONS", "CONNECT", "TRACE").getOrNull(args[0].toInt()) ?: return -3
            return store(SourceRequest(method)).toLong()
        }
        if (name == "set_rate_limit") {
            permits = args[0].toInt().coerceAtLeast(0)
            periodMs = args[1] * when (args[2]) { 1L -> 60_000; 2L -> 3_600_000; else -> 1000 }
            requestTimes.clear(); return 0
        }
        if (name == "send_all") {
            var failed = false
            repeat(args[1].toInt()) { index ->
                val address = args[0].toInt() + index * 4
                val request = values[memory.readInt(address)] as? SourceRequest
                val result = request?.let(::send) ?: -1
                if (result < 0) { failed = true; memory.writeI32(address, result.toInt()) }
            }
            return if (failed) -10 else 0
        }
        val request = values[args[0].toInt()] as? SourceRequest ?: return -1
        val response = request.response
        return when (name) {
            "set_url" -> { request.url = string(args, 1); 0 }
            "set_header" -> { request.headers[string(args, 1)] = string(args, 3); 0 }
            "set_body" -> { request.body = memory.readBytes(args[1].toInt(), args[2].toInt()); 0 }
            "set_timeout" -> { request.timeout = java.lang.Double.longBitsToDouble(args[1]); 0 }
            "send" -> send(request)
            "data_len" -> response?.data?.size?.toLong() ?: -8
            "read_data" -> {
                val data = response?.data ?: return -8
                if (data.size > args[2]) -6 else { memory.write(args[1].toInt(), data); 0 }
            }
            "get_header" -> store(response?.headers?.entries?.firstOrNull { it.key.equals(string(args, 1), true) }?.value).toLong()
            "get_image" -> response?.let { canvas.image(it.data).toLong() } ?: -8
            "get_status_code" -> response?.status?.toLong() ?: -8
            "get_url" -> store(response?.url).toLong()
            "html" -> response?.let { store(Jsoup.parse(it.data.inputStream(), null, it.url)).toLong() } ?: -8
            else -> error("暂不支持漫画源接口 net.$name。")
        }
    }

    private fun javascript(name: String, args: LongArray): Long = jsFactory.call { cx ->
        when (name) {
            "context_create" -> store(cx.initSafeStandardObjects()).toLong()
            "context_eval", "context_eval_async", "context_get" -> {
                val scope = values[args[0].toInt()] as? Scriptable ?: return@call -1L
                val code = string(args, 1)
                val result = if (name == "context_get") ScriptableObject.getProperty(scope, code) else cx.evaluateString(scope, code, "source", 1, null)
                store(JsContext.toString(result)).toLong()
            }
            else -> error("此漫画源需要尚未支持的浏览器接口 js.$name。")
        }
    }
}
