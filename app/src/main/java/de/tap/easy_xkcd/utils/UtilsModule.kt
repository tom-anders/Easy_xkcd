package de.tap.easy_xkcd.utils

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.tap.easy_xkcd.database.DatabaseManager
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class UtilsModule {
    @Singleton
    @Provides
    fun provideAppSettings(@ApplicationContext context: Context) = AppSettings(context)

    @Singleton
    @Provides
    fun provideSharedPrefManager(@ApplicationContext context: Context) = SharedPrefManager(context)

    @Singleton
    @Provides
    fun provideThemePrefs(@ApplicationContext context: Context) = ThemePrefs(context)

    @Singleton
    @Provides
    fun provideDatabaseManager(@ApplicationContext context: Context) = DatabaseManager(context)

    @Singleton
    @Provides
    fun provideOnlineChecker(@ApplicationContext context: Context) = OnlineChecker(context)

    @Provides
    fun provideOkHttpClient() = OkHttpClient()
}
