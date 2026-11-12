package com.chen.myviewer

import android.app.Application
import android.util.Base64
import androidx.room.Room
import com.chen.myviewer.data.ViewerDatabase
import com.chen.myviewer.source.*
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class JmComicSourceTest {
    private fun encrypt(value: String, secret: String): String {
        val key = MessageDigest.getInstance("MD5").digest(secret.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }.toByteArray()
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }
    @Test fun compiledJmSourceDecodesPageContextAndReassemblesGeneratedImage() {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, ViewerDatabase::class.java).allowMainThreadQueries().build()
        try {
            val http = SourceHttp(context, Interceptor { chain ->
                val request = chain.request()
                val body = if (request.url.encodedPath.endsWith(".txt")) encrypt("""{"Server":["jm.example.test"]}""", "diosfjckwpqpdfjkvnqQjsik") else {
                    val ts = request.header("tokenparam")!!.substringBefore(',')
                    val data = when (request.url.encodedPath) {
                        "/setting" -> """{"img_host":"https://images.example.test"}"""
                        "/chapter" -> """{"id":"123456","images":["test.png"]}"""
                        else -> """{"total":1,"content":[{"id":"123456","name":"原创示例","author":"测试作者"}]}"""
                    }
                    JSONObject().put("data", encrypt(data, ts + "185Hcomic3PAPP7R")).toString()
                }
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
            })
            val repository = SourceRepository(context, db.dao(), http)
            val source = repository.installPackage(SourceEntry("zh.jmcomic", "禁漫天堂", 3, listOf("zh"), "https://example.test/jm.aix", "https://18comic.vip", 2),
                javaClass.getResourceAsStream("/aidoku/zh.jmcomic-v3.aix")!!.use { it.readBytes() })
            val engine = AidokuEngine(context, source, http)
            val result = engine.search("", 1)
            val manga = result.entries.single()
            assertEquals("原创示例", manga.title)
            val chapter = SourceChapter("123456", null, null, false, PostcardWriter().string("123456").bytes() + ByteArray(9))
            val pages = engine.pages(manga, chapter)
            assertEquals("123456", pages.single().context!!["ep_id"])
            assertEquals("test.png", pages.single().context!!["filename"])
            val original = android.graphics.Bitmap.createBitmap(32, 100, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(original)
            val colors = listOf(android.graphics.Color.RED, android.graphics.Color.GREEN, android.graphics.Color.BLUE, android.graphics.Color.YELLOW, android.graphics.Color.CYAN,
                android.graphics.Color.MAGENTA, android.graphics.Color.GRAY, android.graphics.Color.WHITE, android.graphics.Color.BLACK, android.graphics.Color.LTGRAY)
            colors.forEachIndexed { index, color ->
                canvas.drawRect(0f, index * 10f, 32f, index * 10f + 10f, android.graphics.Paint().apply { this.color = color })
            }
            val data = java.io.ByteArrayOutputStream().also { original.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            val page = SourcePage("https://images.example.test/test.png", mapOf("ep_id" to "230000", "filename" to "test.png"))
            val restored = engine.processPageImage(page, HttpResult(200, emptyMap(), page.url, data), SourceRequest("GET", page.url))
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(restored, 0, restored.size)
            colors.asReversed().forEachIndexed { index, color -> assertEquals(color, bitmap.getPixel(12, index * 10 + 5)) }
            bitmap.recycle()
            original.recycle()
        } finally { db.close() }
    }
}
