package com.chen.myviewer.source

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

internal class SourceHttp(context: Context, interceptor: okhttp3.Interceptor? = null) {
    private val preferences = context.getSharedPreferences("source-cookies", Context.MODE_PRIVATE)
    private val cookies = mutableListOf<Cookie>()
    private val client: OkHttpClient

    init {
        val stored = runCatching { JSONArray(preferences.getString("cookies", "[]")) }.getOrDefault(JSONArray())
        repeat(stored.length()) { i ->
            val item = stored.getJSONObject(i)
            Cookie.parse(item.getString("url").toHttpUrl(), item.getString("value"))?.let(cookies::add)
        }
        client = OkHttpClient.Builder().apply { interceptor?.let { addInterceptor(it) } }
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS).cookieJar(object : CookieJar {
                override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) {
                    cookies.removeAll { it.expiresAt < System.currentTimeMillis() }
                    cookies.filter { it.matches(url) }
                }
                override fun saveFromResponse(url: HttpUrl, received: List<Cookie>) = synchronized(cookies) {
                    received.forEach { cookie ->
                        cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
                        cookies.add(cookie)
                    }
                    val values = JSONArray()
                    cookies.forEach { values.put(org.json.JSONObject().put("url", "${if (it.secure) "https" else "http"}://${it.domain}${it.path}").put("value", it.toString())) }
                    preferences.edit().putString("cookies", values.toString()).apply()
                }
            }).build()
    }

    fun request(url: String, headers: Map<String, String> = emptyMap(), method: String = "GET", body: ByteArray? = null, timeoutSeconds: Double = 30.0): HttpResult {
        check(!Thread.currentThread().isInterrupted) { "请求已取消。" }
        val builder = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
        headers.forEach { (key, value) -> builder.header(key, value) }
        val needsBody = method in setOf("POST", "PUT", "PATCH", "PROPPATCH", "REPORT")
        builder.method(method, if (body != null || needsBody) (body ?: byteArrayOf()).toRequestBody(headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value?.toMediaTypeOrNull()) else null)
        val call = client.newCall(builder.build())
        call.timeout().timeout(timeoutSeconds.coerceIn(1.0, 60.0).toLong(), TimeUnit.SECONDS)
        call.execute().use { response ->
            val data = response.body?.byteStream()?.use { boundedBytes(it, 64 * 1024 * 1024) } ?: byteArrayOf()
            return HttpResult(response.code, response.headers.toMultimap().mapValues { it.value.joinToString(", ") }, response.request.url.toString(), data)
        }
    }

    fun download(url: String, target: File, headers: Map<String, String> = emptyMap()) {
        if (target.exists() && target.length() > 0) return
        val response = request(url, headers)
        require(response.status in 200..299) { "图片请求失败（HTTP ${response.status}）。" }
        require(response.data.isNotEmpty()) { "图片内容为空。" }
        saveImage(target, response.data)
    }
}

internal fun saveImage(target: File, bytes: ByteArray) {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "漫画源返回的内容无法解码为图片。" }
    atomicWrite(target, bytes)
}

internal data class HttpResult(val status: Int, val headers: Map<String, String>, val url: String, val data: ByteArray)
internal fun boundedBytes(input: InputStream, limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(32 * 1024)
    while (true) {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("操作已取消")
        val count = input.read(buffer)
        if (count < 0) break
        require(output.size() <= limit - count) { "下载内容超过大小限制。" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
internal fun atomicWrite(file: File, bytes: ByteArray) {
    file.parentFile?.mkdirs()
    val atomic = android.util.AtomicFile(file)
    val output = atomic.startWrite()
    try { output.write(bytes); atomic.finishWrite(output) }
    catch (error: Throwable) { atomic.failWrite(output); throw error }
}
