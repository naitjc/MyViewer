package com.chen.myviewer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.viewerSettings by preferencesDataStore("reading_settings")

enum class LibrarySort(val label: String) { RECENT("最近阅读"), TITLE("书名"), IMPORTED("导入时间") }

data class ReaderSettings(
    val fontSize: Float = 20f,
    val lineSpacing: Float = 1.65f,
    val background: String = "PAPER",
    val brightness: Float = -1f,
    val animation: String = "SLIDE",
    val copyOnImport: Boolean = false,
)

class SettingsStore internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.viewerSettings)
    private val font = floatPreferencesKey("font_size")
    private val spacing = floatPreferencesKey("line_spacing")
    private val bg = stringPreferencesKey("background")
    private val light = floatPreferencesKey("brightness")
    private val animation = stringPreferencesKey("animation")
    private val copy = booleanPreferencesKey("copy_on_import")
    private val sort = stringPreferencesKey("library_sort")
    private val columns = intPreferencesKey("library_columns")
    private val preferences = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    val librarySort = preferences.map { prefs -> LibrarySort.entries.firstOrNull { it.name == prefs[sort] } ?: LibrarySort.RECENT }
    val libraryColumns = preferences.map { (it[columns] ?: 3).coerceIn(1, 6) }
    val settings = preferences.map {
        ReaderSettings(
            (it[font] ?: 20f).coerceIn(14f, 36f), (it[spacing] ?: 1.65f).coerceIn(1.2f, 2.3f),
            it[bg] ?: "PAPER", (it[light] ?: -1f).coerceIn(-1f, 1f), it[animation] ?: "SLIDE", it[copy] ?: false,
        )
    }
    suspend fun setLibrarySort(value: LibrarySort) { store.edit { it[sort] = value.name } }
    suspend fun setLibraryColumns(value: Int) { store.edit { it[columns] = value.coerceIn(1, 6) } }
    suspend fun update(value: ReaderSettings) { store.edit {
        it[font] = value.fontSize; it[spacing] = value.lineSpacing; it[bg] = value.background
        it[light] = value.brightness; it[animation] = value.animation; it[copy] = value.copyOnImport
    } }
}

