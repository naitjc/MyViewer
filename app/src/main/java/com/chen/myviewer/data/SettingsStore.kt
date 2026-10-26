package com.chen.myviewer.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.viewerSettings by preferencesDataStore("reading_settings")

data class ReaderSettings(
    val fontSize: Float = 20f,
    val lineSpacing: Float = 1.65f,
    val background: String = "PAPER",
    val brightness: Float = -1f,
    val animation: String = "SLIDE",
    val copyOnImport: Boolean = false,
)

class SettingsStore(context: Context) {
    private val store = context.viewerSettings
    private val font = floatPreferencesKey("font_size")
    private val spacing = floatPreferencesKey("line_spacing")
    private val bg = stringPreferencesKey("background")
    private val light = floatPreferencesKey("brightness")
    private val animation = stringPreferencesKey("animation")
    private val copy = booleanPreferencesKey("copy_on_import")
    val settings = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map {
        ReaderSettings(
            (it[font] ?: 20f).coerceIn(14f, 36f), (it[spacing] ?: 1.65f).coerceIn(1.2f, 2.3f),
            it[bg] ?: "PAPER", (it[light] ?: -1f).coerceIn(-1f, 1f), it[animation] ?: "SLIDE", it[copy] ?: false,
        )
    }
    suspend fun update(value: ReaderSettings) { store.edit {
        it[font] = value.fontSize; it[spacing] = value.lineSpacing; it[bg] = value.background
        it[light] = value.brightness; it[animation] = value.animation; it[copy] = value.copyOnImport
    } }
}

