package de.tap.easy_xkcd.database.whatif

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM Article")
    fun getArticles(): Flow<List<Article>>

    @Query("SELECT * FROM Article")
    fun getArticlesSuspend(): List<Article>

    @Query("SELECT * FROM Article WHERE number=:number")
    suspend fun getArticle(number: Int): Article?

    suspend fun getTitle(number: Int) = getArticle(number)?.title ?: ""

    suspend fun isFavorite(number: Int): Boolean = getArticle(number)?.favorite == true

    @Query("UPDATE Article SET favorite=:favorite WHERE number=:number")
    suspend fun setFavorite(number: Int, favorite: Boolean)

    suspend fun isRead(number: Int): Boolean = getArticle(number)?.read == true

    @Query("UPDATE Article SET read=:read WHERE number=:number")
    suspend fun setRead(number: Int, read: Boolean)

    @Query("UPDATE Article SET read=1")
    suspend fun setAllRead()

    @Query("UPDATE Article SET read=0")
    suspend fun setAllUnread()

    @Query("SELECT * FROM Article WHERE favorite")
    fun getFavorites() : Flow<List<Article>>

    @Query("SELECT * FROM Article WHERE NOT read")
    fun getUnread() : Flow<List<Article>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(articles: List<Article>)

    @Query("SELECT * FROM Article WHERE title LIKE '%' || :query || '%'")
    suspend fun searchArticlesByTitle(query: String): List<Article>

    @Query("DELETE FROM Article")
    suspend fun deleteAllArticles()
}