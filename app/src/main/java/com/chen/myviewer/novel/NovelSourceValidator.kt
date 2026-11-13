package com.chen.myviewer.novel

import com.chen.myviewer.reader.comic.ComicArchive
import com.chen.myviewer.source.SourceHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class NovelSourceCheck(
    val fingerprint: String,
    val passed: Boolean,
    val stage: String,
    val message: String,
    val checkedAt: Long = System.currentTimeMillis(),
    val samples: Int = 0,
) {
    fun json() = JSONObject().put("fingerprint", fingerprint).put("passed", passed).put("stage", stage)
        .put("message", message).put("checkedAt", checkedAt).put("samples", samples)

    companion object {
        fun fromJson(json: JSONObject) = NovelSourceCheck(json.getString("fingerprint"), json.getBoolean("passed"),
            json.getString("stage"), json.getString("message"), json.getLong("checkedAt"), json.optInt("samples"))
    }
}

internal class NovelSourceValidator(private val http: SourceHttp) {
    suspend fun check(source: NovelSource): NovelSourceCheck {
        var stage = "搜索"
        var samples = 0
        return try {
            withTimeout(90_000) {
                runInterruptible(Dispatchers.IO) {
                    val engine = LegadoEngine(source, http)
                    val keywords = listOf(source.rule("ruleSearch").optString("checkKeyWord"), "红楼梦", "三国演义").filter { it.isNotBlank() }.distinct()
                    val results = keywords.asSequence().map { engine.search(it, 1) }.firstOrNull { it.isNotEmpty() }.orEmpty()
                    require(results.isNotEmpty()) { "测试关键词没有匹配作品。" }
                    stage = "详情与目录"
                    val book = engine.details(results.first())
                    val readable = book.chapters.filterNot { it.locked }
                    require(readable.isNotEmpty()) { "没有可读取的章节。" }
                    stage = "正文"
                    val chapters = listOf(readable.first(), readable[readable.size / 2]).distinctBy { it.url }
                    val bodies = mutableSetOf<String>()
                    chapters.forEach { chapter ->
                        val text = engine.content(book, chapter)
                        require(text.count { it == '\uFFFD' } * 1000 <= text.length && '\u0000' !in text) { "正文样本存在文字解码错误。" }
                        require(bodies.add(ComicArchive.digest(text))) { "不同章节返回了相同的正文样本，请检查正文规则。" }
                        samples++
                    }
                    NovelSourceCheck(source.fingerprint, true, "完成", "搜索、详情、目录及 $samples 个正文样本通过", samples = samples)
                }
            }
        } catch (_: StackOverflowError) {
            NovelSourceCheck(source.fingerprint, false, stage, "小说源正则或脚本递归过深。", samples = samples)
        } catch (_: TimeoutCancellationException) {
            NovelSourceCheck(source.fingerprint, false, stage, "校验超时，可稍后重新验证。", samples = samples)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            NovelSourceCheck(source.fingerprint, false, stage, error.message.orEmpty().take(350).ifBlank { error.javaClass.simpleName }, samples = samples)
        }
    }
}

internal fun novelSourceFingerprint(json: JSONObject): String {
    val definition = JSONObject(json.toString())
    definition.keys().asSequence().filter { it.startsWith("_myviewer") }.toList().forEach(definition::remove)
    return ComicArchive.digest(definition.toString())
}
