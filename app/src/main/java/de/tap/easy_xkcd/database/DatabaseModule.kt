package de.tap.easy_xkcd.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DatabaseModule {
    @Singleton
    @Provides
    fun provideComicRoomDatabase(@ApplicationContext context: Context)
            = Room.databaseBuilder(context, ComicRoomDatabase::class.java, "comic_database").build()

    @Provides
    fun provideComicDao(database: ComicRoomDatabase) = database.comicDao()

    @Singleton
    @Provides
    fun provideArticleRoomDatabase(@ApplicationContext context: Context)
            = Room.databaseBuilder(context, ArticleRoomDatabase::class.java, "article_database").build()

    @Provides
    fun provideArticleDao(database: ArticleRoomDatabase) = database.articleDao()
}