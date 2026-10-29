package com.chen.myviewer

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.chen.myviewer.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SettingsStoreTest {
    @Test fun librarySortPersistsIndependentlyOfReadingPreferences() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.filesDir, "sort-reopen.preferences_pb")
        withStore(file) { store ->
            store.setLibrarySort(LibrarySort.TITLE)
            store.setLibraryColumns(4)
            store.update(ReaderSettings(fontSize = 27f, copyOnImport = true, autoPageSeconds = 18, autoScrollSpeed = 7))
        }
        // Close every writer and cache before reopening the actual persisted file.
        withStore(file) { restored ->
            assertEquals(LibrarySort.TITLE, restored.librarySort.first())
            assertEquals(4, restored.libraryColumns.first())
            assertEquals(27f, restored.settings.first().fontSize, 0f)
            assertTrue(restored.settings.first().copyOnImport)
            assertEquals(18, restored.settings.first().autoPageSeconds)
            assertEquals(7, restored.settings.first().autoScrollSpeed)
            restored.setLibrarySort(LibrarySort.IMPORTED)
            restored.setLibraryColumns(6)
        }
        withStore(file) { restored ->
            assertEquals(LibrarySort.IMPORTED, restored.librarySort.first())
            assertEquals(6, restored.libraryColumns.first())
            assertEquals(27f, restored.settings.first().fontSize, 0f)
            assertEquals(18, restored.settings.first().autoPageSeconds)
            assertEquals(7, restored.settings.first().autoScrollSpeed)
        }
    }
    private suspend fun withStore(file: File, block: suspend (SettingsStore) -> Unit) {
        val job = SupervisorJob()
        val data = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        try { block(SettingsStore(data)) } finally { job.cancelAndJoin() }
    }
}
