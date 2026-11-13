package com.chen.myviewer.novel

import androidx.core.net.toUri

const val NOVEL_SOURCE_URL = "https://raw.githubusercontent.com/speauty/legado.sources/main/subscriptions/active.json"

data class NovelSourceCatalog(val name: String, val url: String, val repository: String)

val NOVEL_SOURCE_CATALOGS = listOf(
    NovelSourceCatalog("speauty 自用", NOVEL_SOURCE_URL, "https://github.com/speauty/legado.sources"),
    NovelSourceCatalog("XIU2 自用", "https://raw.githubusercontent.com/XIU2/Yuedu/master/shuyuan", "https://github.com/XIU2/Yuedu"),
    NovelSourceCatalog("tickmao 综合", "https://raw.githubusercontent.com/tickmao/Novel/master/sources/legado/full.json", "https://github.com/tickmao/Novel"),
)

fun novelCatalogName(url: String): String = NOVEL_SOURCE_CATALOGS.firstOrNull { it.url == url }?.name
    ?: url.toUri().host.orEmpty().ifBlank { "自定义仓库" }
