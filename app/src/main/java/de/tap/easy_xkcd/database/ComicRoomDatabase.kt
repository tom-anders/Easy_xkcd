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

   //TODO can we use @Singleton / dependency injection instead?
   companion object {
      @Volatile
      private var INSTANCE: ComicRoomDatabase? = null

      fun getDatabase(context: Context): ComicRoomDatabase {
         return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
               context.applicationContext,
               ComicRoomDatabase::class.java,
               "comic_database",
            ).build()
            INSTANCE = instance

            instance
         }
      }
   }
}