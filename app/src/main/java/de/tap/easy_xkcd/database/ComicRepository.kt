package de.tap.easy_xkcd.database

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.utils.PrefHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

// If a comic has not been cached yet, the comic will be null here
// The number can then be used to request caching it.
data class ComicContainer(
    val number: Int,
    val comic: Comic?,
) {
    fun hasComic() = (comic != null)
}

fun Comic.toContainer() = ComicContainer(number, this)

fun Flow<List<Comic>>.mapToComicContainer() = map { list -> list.map { it.toContainer() } }

fun Flow<Map<Int, Comic>>.mapToComicContainer(size: Int) = map { comicMap ->
    MutableList(size) { index ->
        ComicContainer(index + 1, comicMap[index + 1])
    }
}

interface ComicRepository {
    val comics: Flow<List<ComicContainer>>

    val favorites: Flow<List<ComicContainer>>

    val unreadComics: Flow<List<ComicContainer>>

    suspend fun cacheComic(number: Int)

    suspend fun isFavorite(number: Int): Boolean

    suspend fun removeAllFavorites()

    suspend fun setRead(number: Int, read: Boolean)

    suspend fun setFavorite(number: Int, favorite: Boolean)

    suspend fun setBookmark(number: Int)
}

class ComicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ComicRepository {

    private val prefHelper = PrefHelper(context)

    private val comicDao = ComicRoomDatabase.getDatabase(context).comicDao()

    private val client = OkHttpClient()

    override val comics = comicDao.getComics().mapToComicContainer(prefHelper.newest)

    override val favorites = comicDao.getFavorites().mapToComicContainer()

    override val unreadComics = comics.map {
        it.filter { container -> container.comic == null || !container.comic.read }
    }

    override suspend fun isFavorite(number: Int): Boolean = comicDao.isFavorite(number)

    override suspend fun setRead(number: Int, read: Boolean) = comicDao.setRead(number, read)

    override suspend fun setFavorite(number: Int, favorite: Boolean) = comicDao.setFavorite(number, favorite)

    override suspend fun removeAllFavorites() = comicDao.removeAllFavorites()

    override suspend fun setBookmark(number: Int) { prefHelper.bookmark = number }

    private fun downloadComic(number: Int): Comic? {
        val response =
            client.newCall(Request.Builder().url(RealmComic.getJsonUrl(number)).build())
                .execute()

        val body = response.body?.string()
        if (body == null) {
            Timber.e("Got empty body for comic $number")
            return null
        }

        var json = JSONObject()
        try {
            json = JSONObject(body)
        } catch (e: JSONException) {
            if (number == 404) {
                Timber.i("Json not found, but that's expected for comic 404")
            } else {
                Timber.e(e, "Occurred at comic $number")
                return null
            }
        }

        return Comic.buildFromJson(number, json, context)
    }

    override suspend fun cacheComic(number: Int) {
        withContext(Dispatchers.IO) {
            if (comicDao.getComic(number) == null) {
                Timber.d("Caching comic %d in database", number)
                downloadComic(number)?.let {
                    comicDao.insert(it)
                }
            }
        }
    }
}

@Module
@InstallIn(ViewModelComponent::class)
abstract class ComicRepositoryModule {
    @Binds
    abstract fun bindComicRepository(comicRepositoryImpl: ComicRepositoryImpl): ComicRepository
}