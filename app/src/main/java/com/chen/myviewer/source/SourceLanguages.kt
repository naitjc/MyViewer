package com.chen.myviewer.source

import java.util.Locale

/** Region and script variants share a language filter, including zh-Hans and zh-Hant. */
internal fun sourceLanguageGroup(tag: String): String = Locale.forLanguageTag(tag.replace('_', '-')).language
    .ifBlank { tag.lowercase(Locale.ROOT) }

internal fun sourceLanguageLabel(language: String): String = Locale.forLanguageTag(language)
    .getDisplayLanguage(Locale.SIMPLIFIED_CHINESE).ifBlank { language }
