package com.chen.myviewer.novel

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal object NovelTextDecoder {
    private val charsetPattern = Regex("charset\\s*=\\s*[\"']?\\s*([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)

    fun decode(bytes: ByteArray, contentType: String, requestCharset: String = "UTF-8"): String {
        fun read(name: String): String? = try {
            val charset = Charset.forName(if (name.lowercase() in setOf("gbk", "gb2312", "gb_2312-80")) "GB18030" else name)
            charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF").takeIf { '\u0000' !in it }
        } catch (_: CharacterCodingException) { null }
        catch (_: IllegalArgumentException) { null }

        val bom = when {
            bytes.take(2) == listOf(0xff.toByte(), 0xfe.toByte()) -> "UTF-16LE"
            bytes.take(2) == listOf(0xfe.toByte(), 0xff.toByte()) -> "UTF-16BE"
            else -> null
        }
        if (bom != null) return read(bom) ?: error("小说网页文字解码失败，文件编码为 $bom。")
        read("UTF-8")?.let { return it }
        val prefix = bytes.take(8192).toByteArray().toString(Charsets.ISO_8859_1)
        val meta = charsetPattern.find(prefix)?.groupValues?.get(1)
        val header = charsetPattern.find(contentType)?.groupValues?.get(1)
        for (name in listOfNotNull(meta, requestCharset.takeUnless { it.equals("UTF-8", true) }, header, "GB18030").distinct()) {
            read(name)?.let { return it }
        }
        error("小说网页文字解码失败，请更新书源后重试。")
    }
}
