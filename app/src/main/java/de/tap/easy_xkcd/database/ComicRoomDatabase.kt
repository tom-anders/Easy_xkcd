package de.tap.easy_xkcd.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import javax.inject.Singleton

@Singleton
@Database(entities = [Comic::class], version = 1)
abstract class ComicRoomDatabase : RoomDatabase() {
   abstract fun comicDao(): ComicDao
}