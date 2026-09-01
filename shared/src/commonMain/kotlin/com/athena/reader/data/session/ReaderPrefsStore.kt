package com.athena.reader.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.athena.reader.nostr.model.Coordinate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReaderPrefsStore(
    private val dataStore: DataStore<Preferences>,
) {
    val prefs: Flow<ReaderPrefs> = dataStore.data.map { it.toPrefs() }

    suspend fun update(transform: (ReaderPrefs) -> ReaderPrefs) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toPrefs())
            prefs[KEY_FONT_SIZE] = next.fontSize
            prefs[KEY_ZOOM] = next.zoomPercent
            prefs[KEY_FONT] = next.font.name
            prefs[KEY_LINE_HEIGHT] = next.lineHeight
            prefs[KEY_TURN_MODE] = next.turnMode.name
            prefs[KEY_MARGIN] = next.marginDp
            if (next.lastBookNaddr == null) prefs.remove(KEY_LAST_BOOK) else {
                prefs[KEY_LAST_BOOK] = next.lastBookNaddr
            }
        }
    }

    suspend fun setLastBook(coordinate: Coordinate) {
        update { it.withLastBook(coordinate) }
    }

    private companion object {
        val KEY_FONT_SIZE = intPreferencesKey("reader_font_size")
        val KEY_ZOOM = intPreferencesKey("reader_zoom")
        val KEY_FONT = stringPreferencesKey("reader_font")
        val KEY_LINE_HEIGHT = floatPreferencesKey("reader_line_height")
        val KEY_TURN_MODE = stringPreferencesKey("reader_turn_mode")
        val KEY_MARGIN = intPreferencesKey("reader_margin")
        val KEY_LAST_BOOK = stringPreferencesKey("reader_last_book")
    }
}

private fun Preferences.toPrefs(): ReaderPrefs {
    val rawSize = this[intPreferencesKey("reader_font_size")] ?: ReaderPrefs.DEFAULT_FONT_SIZE
    val rawZoom = this[intPreferencesKey("reader_zoom")] ?: ReaderPrefs.DEFAULT_ZOOM
    val rawLine = this[floatPreferencesKey("reader_line_height")] ?: ReaderPrefs.DEFAULT_LINE_HEIGHT
    val rawMargin = this[intPreferencesKey("reader_margin")] ?: ReaderPrefs.DEFAULT_MARGIN
    return ReaderPrefs(
        fontSize = rawSize.coerceIn(ReaderPrefs.MIN_FONT_SIZE, ReaderPrefs.MAX_FONT_SIZE),
        zoomPercent = rawZoom.coerceIn(ReaderPrefs.MIN_ZOOM, ReaderPrefs.MAX_ZOOM),
        font = this[stringPreferencesKey("reader_font")]
            ?.let { name -> ReaderFontKind.entries.firstOrNull { it.name == name } }
            ?: ReaderFontKind.Garamond,
        lineHeight = rawLine.coerceIn(ReaderPrefs.MIN_LINE_HEIGHT, ReaderPrefs.MAX_LINE_HEIGHT),
        turnMode = this[stringPreferencesKey("reader_turn_mode")]
            ?.let { name -> ReaderTurnMode.entries.firstOrNull { it.name == name } }
            ?: ReaderTurnMode.Paged,
        marginDp = rawMargin.coerceIn(ReaderPrefs.MIN_MARGIN, ReaderPrefs.MAX_MARGIN),
        lastBookNaddr = this[stringPreferencesKey("reader_last_book")],
    )
}
