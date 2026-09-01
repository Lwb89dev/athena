package com.athena.reader.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.athena.reader.platform.databaseBuilder
import com.athena.reader.platform.ioDispatcher

@Database(
    entities = [
        BookEntity::class,
        SectionEntity::class,
        HighlightEntity::class,
        ProgressEntity::class,
        FavoriteEntity::class,
        SyncStateEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@ConstructedBy(AthenaDatabaseConstructor::class)
abstract class AthenaDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun sectionDao(): SectionDao
    abstract fun highlightDao(): HighlightDao
    abstract fun progressDao(): ProgressDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        const val NAME = "athena.db"
    }
}

/** Generated per platform by the Room compiler; we only declare it. */
@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object AthenaDatabaseConstructor : RoomDatabaseConstructor<AthenaDatabase> {
    override fun initialize(): AthenaDatabase
}

/**
 * The bundled SQLite driver ships its own libsqlite, so Android and the desktop
 * run byte-identical SQL instead of whatever the OS happens to provide.
 */
fun createDatabase(): AthenaDatabase = databaseBuilder()
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(ioDispatcher)
    .fallbackToDestructiveMigration(dropAllTables = true)
    .build()
