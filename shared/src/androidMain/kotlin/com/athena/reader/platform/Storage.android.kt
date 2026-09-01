package com.athena.reader.platform

import androidx.room.Room
import androidx.room.RoomDatabase
import com.athena.reader.data.local.AthenaDatabase

actual fun databaseBuilder(): RoomDatabase.Builder<AthenaDatabase> {
    val context = AndroidPlatform.applicationContext
    val file = context.getDatabasePath(AthenaDatabase.NAME)
    return Room.databaseBuilder<AthenaDatabase>(context, file.absolutePath)
}
