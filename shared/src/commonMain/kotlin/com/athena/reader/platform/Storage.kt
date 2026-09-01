package com.athena.reader.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import com.athena.reader.data.local.AthenaDatabase
import okio.Path.Companion.toPath

/**
 * Room's KMP builder is created per platform (it needs the platform file API
 * and, on Android, a Context); everything after `.build()` is common code.
 */
expect fun databaseBuilder(): RoomDatabase.Builder<AthenaDatabase>

/**
 * One DataStore for the whole app. It holds public session data only — which
 * npub is logged in and through which signer — never key material.
 */
fun createDataStore(): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
    produceFile = { "${appDataDirectory()}/$PREFERENCES_FILE".toPath() },
)

private const val PREFERENCES_FILE = "athena.preferences_pb"
