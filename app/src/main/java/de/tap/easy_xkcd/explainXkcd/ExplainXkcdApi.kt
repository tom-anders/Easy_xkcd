package de.tap.easy_xkcd.explainXkcd

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Singleton

data class SectionResponse(
    val parse: Sections,
) {
    data class Sections (
        val title: String,
        val sections: List<Section>,
    ) {
        data class Section (
            @SerializedName("line")
            val title: String,
            val index: String,
        )
    }

    fun findPageIdOfTranscript() =
        parse.sections.firstOrNull {
            it.title == "Transcript"
        }?.index?.toIntOrNull()
}

data class SectionTextResponse(
    val parse: Parse
) {
    data class Parse(
        val text: Text
    ) {
        data class Text (
            @SerializedName("*")
            val text: String
        )
    }

    val text get() = parse.text.text
}

interface ExplainXkcdApi {
    @GET("api.php?action=parse&redirects&prop=sections&format=json")
    fun getSections(@Query("page") number: Int): Call<SectionResponse>

    @GET("api.php?action=parse&redirects&prop=text&format=json")
    fun getSection(@Query("page") number: Int, @Query("section") sectionId: Int): Call<SectionTextResponse>
}

@Module
@InstallIn(SingletonComponent::class)
class ExplainXkcdApiModule {
    @Singleton
    @Provides
    fun provideExplainXkcdApi(okHttpClient: OkHttpClient) = Retrofit.Builder()
        .baseUrl("https://www.explainxkcd.com/wiki/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(okHttpClient)
        .build()
        .create(ExplainXkcdApi::class.java)
}