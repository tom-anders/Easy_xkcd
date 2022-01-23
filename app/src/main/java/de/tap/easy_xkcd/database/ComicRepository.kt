package de.tap.easy_xkcd.database

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.tap.xkcd_reader.BuildConfig
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton

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

fun Map<Int, Comic>.mapToComicContainer(size: Int) =
    MutableList(size) { index ->
        ComicContainer(index + 1, this[index + 1])
    }

sealed class ProgressStatus {
    data class Max(val max: Int) : ProgressStatus()
    object IncrementProgress : ProgressStatus()
    data class SetProgress(val value: Int) : ProgressStatus()
    object ResetProgress : ProgressStatus()
    object Finished : ProgressStatus()
}

interface ComicRepository {
    val comics: Flow<List<ComicContainer>>

    val favorites: Flow<List<ComicContainer>>

    val unreadComics: Flow<List<ComicContainer>>

    val newestComicNumber: Flow<Int>

    suspend fun cacheComic(number: Int)

    suspend fun cacheAllComics(): Flow<ProgressStatus>

    suspend fun getUriForSharing(number: Int): Uri?

    suspend fun getRedditThread(comic: Comic): String?

    suspend fun saveOfflineBitmap(number: Int)

    suspend fun isFavorite(number: Int): Boolean

    suspend fun removeAllFavorites()

    suspend fun setRead(number: Int, read: Boolean)

    suspend fun setFavorite(number: Int, favorite: Boolean)

    suspend fun setBookmark(number: Int)

    suspend fun findNewestComic(): Int

    suspend fun migrateRealmDatabase(): Flow<ProgressStatus>

    suspend fun oldestUnreadComic(): Comic?
}

@Singleton
class ComicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ComicRepository {

    private val prefHelper = PrefHelper(context)

    private val comicDao = ComicRoomDatabase.getDatabase(context).comicDao()

    private val client = OkHttpClient()

    override val newestComicNumber = flow {
        findNewestComic().also {
            prefHelper.setNewestComic(it)
            emit(it)
        }
    }.stateIn(CoroutineScope(Dispatchers.IO), SharingStarted.Eagerly, prefHelper.newest)

    override val comics = combine(comicDao.getComics(), newestComicNumber) { comics, newest ->
        comics.mapToComicContainer(newest)
    }

    override val favorites = comicDao.getFavorites().mapToComicContainer()

    override val unreadComics = comics.map {
        it.filter { container -> container.comic == null || !container.comic.read }
    }

    override suspend fun isFavorite(number: Int): Boolean = comicDao.isFavorite(number)

    override suspend fun setRead(number: Int, read: Boolean) = comicDao.setRead(number, read)

    override suspend fun setFavorite(number: Int, favorite: Boolean) {
        if (favorite) saveOfflineBitmap(number)

        comicDao.setFavorite(number, favorite)
    }

    override suspend fun removeAllFavorites() = comicDao.removeAllFavorites()

    override suspend fun setBookmark(number: Int) {
        prefHelper.bookmark = number
    }

    override suspend fun oldestUnreadComic() = comicDao.oldestUnreadComic()

    override suspend fun findNewestComic(): Int {
        return downloadComic(0)?.also {
            comicDao.insert(it)
        }?.number ?: prefHelper.newest
    }

    override suspend fun migrateRealmDatabase() = flow {
        if (!prefHelper.hasMigratedRealmDatabase() || BuildConfig.DEBUG ) {
            copyResultsFromRealm { realm ->
                realm.where(RealmComic::class.java).findAll()
            }.also {
                emit(ProgressStatus.Max(it.size))
            }.map { realmComic ->
                val comic = Comic(realmComic.comicNumber)
                comic.favorite = realmComic.isFavorite
                comic.read = realmComic.isRead
                comic.title = realmComic.title
                comic.transcript = realmComic.transcript
                comic.url = realmComic.url
                comic.altText = realmComic.altText

                //TODO Inserting them all at once as a list would probably be faster
                comicDao.insert(comic)

                emit(ProgressStatus.IncrementProgress)
            }
            emit(ProgressStatus.Finished)
            prefHelper.setHasMigratedRealmDatabase()
        }
    }

    override suspend fun getRedditThread(comic: Comic) = withContext(Dispatchers.IO) {
        try {
            return@withContext "https://www.reddit.com" + client.newCall(
                Request.Builder()
                    .url("https://www.reddit.com/r/xkcd/search.json?q=${comic.title}&restrict_sr=on")
                    .build()
            )
                .execute().body?.let {
                    JSONObject(it.string())
                        .getJSONObject("data")
                        .getJSONArray("children").getJSONObject(0).getJSONObject("data")
                        .getString("permalink")
                }
        } catch (e: Exception) {
            Timber.e(e, "When getting reddit thread for $comic")
            return@withContext null
        }
    }

    override suspend fun getUriForSharing(number: Int): Uri? {
        saveOfflineBitmap(number)
        return FileProvider.getUriForFile(
            context,
            "de.tap.easy_xkcd.fileProvider",
            getComicImageFile(number)
        )
    }

    private fun getComicImageFile(number: Int) =
        File(prefHelper.getOfflinePath(context), "${number}.png")

    private fun hasDownloadedComicImage(number: Int) = getComicImageFile(number).exists()

    override suspend fun saveOfflineBitmap(number: Int) = withContext(Dispatchers.IO) {
        if (!hasDownloadedComicImage(number)) {
            comicDao.getComic(number)?.let { comic ->
                client.newCall(Request.Builder().url(comic.url).build()).execute().body?.let {
                    try {
                        FileOutputStream(getComicImageFile(number)).write(it.bytes())
                    } catch (e: Exception) {
                        Timber.e(e, "While downloading comic $number")
                    }
                }
            }
        }
    }

    private fun downloadComic(number: Int): Comic? {
        try {
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
                    json = JSONObject()
                    json.put("num", 404)
                } else {
                    Timber.e(e, "Occurred at comic $number")
                    return null
                }
            }
            return Comic.buildFromJson(json, context)

        } catch (e: Exception) {
            Timber.e(e, "While downloading comic $number")
            return null
        }
    }

    //TODO figure out how to do this in parallel with flow
    override suspend fun cacheAllComics() = flow { emit(ProgressStatus.ResetProgress) }
//        flow {
//            withContext(Dispatchers.IO) {
//                (1..prefHelper.newest).filter { number -> comicDao.getComic(number) == null }
//                    .also { emit(ProgressStatus.Max(it.size)) }
//                    .map {
//                        Timber.d("Caching $it")
//                        withContext(Dispatchers.IO) {
//                            async {
//                                Timber.d("Caching $it")
//                                cacheComic(it)
//
//                                withContext(Dispatchers.Main) {
//                                    emit(ProgressStatus.IncrementProgress)
//                                }
//                            }
//                        }
//                    }.awaitAll()
//                emit(ProgressStatus.ResetProgress)

//                (1..prefHelper.newest).filter { number ->
//                    comicDao.getComic(number)?.transcript?.isEmpty() ?: false
//                }
//                    .also { emit(ProgressStatus.Max(it.size)) }
//                    .map {
//                        async(Dispatchers.IO) {
//                            //TODO download missing transcripts from explainxkcd here
//                            emit(ProgressStatus.IncrementProgress)
//                        }
//                    }.awaitAll()
//                emit(ProgressStatus.Finished)
//            }
//        }

    //TODO Need to
    override suspend fun cacheComic(number: Int) {
        withContext(Dispatchers.IO) {
            if (comicDao.getComic(number) == null) {
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