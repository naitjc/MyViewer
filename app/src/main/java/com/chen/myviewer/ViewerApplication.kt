package com.chen.myviewer

import android.app.Application
import androidx.room.Room
import com.chen.myviewer.data.*

class ViewerApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, ViewerDatabase::class.java, "myviewer.db").addMigrations(ViewerDatabase.MIGRATION_1_2, ViewerDatabase.MIGRATION_2_3, ViewerDatabase.MIGRATION_3_4, ViewerDatabase.MIGRATION_4_5).build() }
    val settings by lazy { SettingsStore(this) }
    val repository by lazy { LibraryRepository(this, database.dao()) }
    val readingTime by lazy { ReadingTimeRepository(database.dao()) }
    val novels by lazy { com.chen.myviewer.novel.NovelRepository(this, database.dao()) }
    internal val folderDownloads by lazy { com.chen.myviewer.download.FolderDownloadRepository(this, sources, novels) }
    val sources by lazy { com.chen.myviewer.source.SourceRepository(this, database.dao()) }
}

