package com.athena.reader.platform

import androidx.room.Room
import androidx.room.RoomDatabase
import com.athena.reader.data.local.AthenaDatabase
import java.io.File

actual fun databaseBuilder(): RoomDatabase.Builder<AthenaDatabase> {
    val file = File(appDataDirectory(), AthenaDatabase.NAME)
    return Room.databaseBuilder<AthenaDatabase>(name = file.absolutePath)
}
