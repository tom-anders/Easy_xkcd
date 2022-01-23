package de.tap.easy_xkcd.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ComicDao {
    @MapInfo(keyColumn = "number")
    @Query("SELECT * FROM Comic")
    fun getComics() : Flow<Map<Int, Comic>>

    @Query("SELECT * FROM Comic WHERE number=:number")
    suspend fun getComic(number: Int): Comic?

    suspend fun isFavorite(number: Int) = getComic(number)?.favorite ?: false

    @Query("UPDATE Comic SET favorite=:favorite WHERE number=:number")
    suspend fun setFavorite(number: Int, favorite: Boolean)

    @Query("DELETE FROM Comic WHERE favorite")
    suspend fun removeAllFavorites()

    suspend fun isRead(number: Int) = getComic(number)?.read ?: false

    @Query("UPDATE Comic SET read=:read WHERE number=:number")
    suspend fun setRead(number: Int, read: Boolean)

    @Query("SELECT * FROM Comic WHERE favorite")
    fun getFavorites() : Flow<List<Comic>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(comic: Comic)

    @Query("SELECT * FROM COMIC WHERE NOT read LIMIT 1")
    suspend fun oldestUnreadComic() : Comic
}