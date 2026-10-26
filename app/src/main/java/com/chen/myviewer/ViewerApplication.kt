package com.chen.myviewer

import android.app.Application
import androidx.room.Room
import com.chen.myviewer.data.*

class ViewerApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, ViewerDatabase::class.java, "myviewer.db").build() }
    val settings by lazy { SettingsStore(this) }
    val repository by lazy { LibraryRepository(this, database.dao()) }
}

