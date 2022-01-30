package de.tap.easy_xkcd.database

import com.google.gson.annotations.SerializedName
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.tap.easy_xkcd.explainXkcd.ExplainXkcdApi
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Path
import javax.inject.Singleton

data class XkcdApiComic(
    val num: Int,
    val transcript: String,
    val alt: String,
    val title: String,
    @SerializedName("img")
    val url: String,
    val day: String,
    val month: String,
    val year: String,
)

interface XkcdApi {
    @GET("{number}/info.0.json")
    fun getComic(@Path("number") number: Int): Call<XkcdApiComic>

    @GET("info.0.json")
    fun getNewestComic(): Call<XkcdApiComic>
}

@Module
@InstallIn(SingletonComponent::class)
class XkcdApiModule {
    @Singleton
    @Provides
    fun provideXkcdApi(okHttpClient: OkHttpClient) = Retrofit.Builder()
        .baseUrl("https://xkcd.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(okHttpClient)
        .build()
        .create(XkcdApi::class.java)
}
