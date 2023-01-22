package de.tap.easy_xkcd.utils

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
    fun provideAppTheme(@ApplicationContext context: Context) = AppTheme(context)

    @Singleton
    @Provides
    fun provideOnlineChecker(@ApplicationContext context: Context) = OnlineChecker(context)

    @Provides
    fun provideOkHttpClient() = OkHttpClient()
}
