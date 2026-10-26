package com.chen.myviewer

import com.chen.myviewer.reader.text.TextParser
import org.junit.Assert.*
import org.junit.Test

class TextParserTest {
    @Test fun utf8BomAndNewlinesKeepCorrectChapterOffsets() {
        val raw = "\uFEFF序章\r\n第一行。\r\n\r\n第一章 归来\r\n第二行。\r\n第二章 风起\r\n结束。"
        val document = TextParser.parse(raw.byteInputStream())
        assertEquals("序章\n第一行。\n\n第一章 归来\n第二行。\n第二章 风起\n结束。", document.text)
        assertEquals(3, document.chapters.size)
        assertEquals(document.text.indexOf("第一章"), document.chapters[1].offset)
        assertEquals("第一章 归来", document.chapterAt(document.text.indexOf("第二行")))
    }
    @Test fun detectsGb18030WithoutMojibake() {
        val original = "第一章 雨后的车站\n站台上还有一盏灯。"
        val document = TextParser.parse(original.toByteArray(charset("GB18030")).inputStream())
        assertEquals(original, document.text)
        assertEquals("GB18030", document.encoding)
    }
    @Test fun detectsUtf16LittleEndianBom() {
        val original = "第一章\n你好，世界。"
        val bytes = byteArrayOf(0xff.toByte(), 0xfe.toByte()) + original.toByteArray(Charsets.UTF_16LE)
        assertEquals(original, TextParser.parse(bytes.inputStream()).text)
    }
    @Test fun explicitEncodingOverrideIsHonored() {
        val original = "第一章\n雨停了。"
        assertEquals(original, TextParser.parse(original.toByteArray(Charsets.UTF_16LE).inputStream(), "UTF-16LE").text)
    }
    @Test fun supportsEnglishChapterTitlesAndNoHeadingsFallback() {
        assertEquals(2, TextParser.fromText("Chapter 1 Dawn\nHello\nChapter 2 Night\nGoodbye").chapters.size)
        val plain = TextParser.fromText("一个没有目录的故事。\n雨落在窗前。")
        assertTrue(plain.chapters.isEmpty()); assertEquals("正文", plain.chapterAt(4))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsEmptyFiles() { TextParser.parse(byteArrayOf().inputStream()) }
    @Test fun snippetAtEndIsSafe() {
        val doc = TextParser.fromText("序章\n结束")
        assertEquals("", doc.snippet(doc.text.length))
    }
}

