package de.tap.easy_xkcd.database

import androidx.room.Database
import androidx.room.RoomDatabase
import de.tap.easy_xkcd.database.comics.Comic
import de.tap.easy_xkcd.database.comics.ComicDao
import de.tap.easy_xkcd.database.whatif.Article
import de.tap.easy_xkcd.database.whatif.ArticleDao
import javax.inject.Singleton

@Singleton
@Database(entities = [Comic::class], version = 1)
abstract class ComicRoomDatabase : RoomDatabase() {
   abstract fun comicDao(): ComicDao
}

@Singleton
@Database(entities = [Article::class], version = 1)
abstract class ArticleRoomDatabase : RoomDatabase() {
   abstract fun articleDao(): ArticleDao
}
