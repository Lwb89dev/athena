package com.athena.reader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY publishedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE coordinate = :coordinate")
    fun observe(coordinate: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE coordinate = :coordinate")
    suspend fun get(coordinate: String): BookEntity?

    @Query(
        """
        SELECT * FROM books
        WHERE title LIKE '%' || :query || '%'
           OR authorName LIKE '%' || :query || '%'
           OR topics LIKE '%' || :query || '%'
        ORDER BY publishedAt DESC
        """,
    )
    fun search(query: String): Flow<List<BookEntity>>

    /**
     * The cache holds whatever has ever been fetched, including a global sweep.
     * Scoping the *read* as well as the fetch is what stops yesterday's spam
     * from showing up in today's followed-only feed.
     */
    @Query("SELECT * FROM books WHERE authorPubkey IN (:authors) ORDER BY publishedAt DESC")
    fun observeByAuthors(authors: List<String>): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE imported = 1 ORDER BY publishedAt DESC")
    fun observeImported(): Flow<List<BookEntity>>

    @Query(
        """
        SELECT * FROM books
        WHERE authorPubkey IN (:authors)
          AND (title LIKE '%' || :query || '%'
            OR authorName LIKE '%' || :query || '%'
            OR topics LIKE '%' || :query || '%')
        ORDER BY publishedAt DESC
        """,
    )
    fun searchByAuthors(authors: List<String>, query: String): Flow<List<BookEntity>>

    @Query(
        """
        SELECT * FROM books
        WHERE imported = 1
          AND (title LIKE '%' || :query || '%'
            OR authorName LIKE '%' || :query || '%'
            OR topics LIKE '%' || :query || '%')
        ORDER BY publishedAt DESC
        """,
    )
    fun searchImported(query: String): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(books: List<BookEntity>)
}

@Dao
interface SectionDao {
    @Query("SELECT * FROM sections WHERE bookCoordinate = :bookCoordinate ORDER BY position ASC")
    fun observeForBook(bookCoordinate: String): Flow<List<SectionEntity>>

    @Query("SELECT * FROM sections WHERE bookCoordinate = :bookCoordinate ORDER BY position ASC")
    suspend fun getForBook(bookCoordinate: String): List<SectionEntity>

    @Query("SELECT * FROM sections WHERE coordinate = :coordinate")
    suspend fun get(coordinate: String): SectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sections: List<SectionEntity>)
}

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE authorPubkey = :pubkey ORDER BY createdAt DESC")
    fun observeMine(pubkey: String): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE bookCoordinate = :bookCoordinate ORDER BY startOffset ASC")
    fun observeForBook(bookCoordinate: String): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE published = 0 AND authorPubkey = :pubkey")
    suspend fun pending(pubkey: String): List<HighlightEntity>

    @Query("SELECT * FROM highlights WHERE authorPubkey = :pubkey AND visibility = :visibility")
    suspend fun byVisibility(pubkey: String, visibility: String): List<HighlightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(highlights: List<HighlightEntity>)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM reading_progress WHERE bookCoordinate = :bookCoordinate")
    fun observe(bookCoordinate: String): Flow<ProgressEntity?>

    @Query("SELECT * FROM reading_progress WHERE bookCoordinate = :bookCoordinate")
    suspend fun get(bookCoordinate: String): ProgressEntity?

    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM reading_progress")
    suspend fun getAll(): List<ProgressEntity>

    @Query("SELECT * FROM reading_progress WHERE synced = 0")
    suspend fun unsynced(): List<ProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ProgressEntity)

    @Query("DELETE FROM reading_progress WHERE bookCoordinate = :coordinate")
    suspend fun delete(coordinate: String)

    @Query("DELETE FROM reading_progress")
    suspend fun deleteAll()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    suspend fun getAll(): List<FavoriteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE bookCoordinate = :coordinate)")
    suspend fun isFavorite(coordinate: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE bookCoordinate = :coordinate)")
    fun observeIsFavorite(coordinate: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE bookCoordinate = :coordinate")
    suspend fun delete(coordinate: String)

    @Query("DELETE FROM favorites")
    suspend fun clear()
}

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE slot = :slot")
    suspend fun get(slot: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)

    @Query("DELETE FROM sync_state")
    suspend fun clear()
}
