package de.tap.easy_xkcd.reddit

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.tap.easy_xkcd.database.comics.XkcdApiComic
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Singleton

data class RedditSearchResult(
    val data: Data
) {
    data class Data (
        val children: List<Result>
    ) {
        data class Result (
            val data: Data
        ) {
            data class Data (
                val permalink: String
            )
        }
    }

    val url: String? get() = data.children.firstOrNull()?.data?.permalink?.let {
        "https://reddit.com/$it"
    }
}

interface RedditSearchApi {
    @GET("search.json?restrict_sr=on")
    suspend fun search(@Query("q") query: String): RedditSearchResult?
}

@Module
@InstallIn(SingletonComponent::class)
class RedditSearchApiModule {
    @Singleton
    @Provides
    fun provideRedditSearchApi(okHttpClient: OkHttpClient) = Retrofit.Builder()
        .baseUrl("https://www.reddit.com/r/xkcd/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(okHttpClient)
        .build()
        .create(RedditSearchApi::class.java)
}
