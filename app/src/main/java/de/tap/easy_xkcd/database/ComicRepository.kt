package de.tap.easy_xkcd.database

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.net.ipsec.ike.TunnelModeChildSessionParams
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.tap.xkcd_reader.BuildConfig
import com.tap.xkcd_reader.R
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.GlideApp
import de.tap.easy_xkcd.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception
import java.util.concurrent.ExecutionException
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

    val comicCached: Channel<Comic>

    suspend fun cacheComic(number: Int)

    suspend fun cacheAllComics(): Flow<ProgressStatus>

    suspend fun getUriForSharing(number: Int): Uri?

    suspend fun getRedditThread(comic: Comic): String?

    suspend fun isFavorite(number: Int): Boolean

    @ExperimentalCoroutinesApi
    suspend fun saveOfflineBitmaps(): Flow<ProgressStatus>

    suspend fun removeAllFavorites()

    suspend fun setRead(number: Int, read: Boolean)

    suspend fun setFavorite(number: Int, favorite: Boolean)

    suspend fun setBookmark(number: Int)

    suspend fun migrateRealmDatabase(): Flow<ProgressStatus>

    suspend fun oldestUnreadComic(): Comic?

    fun getOfflineUri(number: Int): Uri?
}

@Singleton
class ComicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefHelper: PrefHelper,
    private val comicDao: ComicDao,
    private val client: OkHttpClient,
) : ComicRepository {

    override val comicCached = Channel<Comic>()

    @ExperimentalCoroutinesApi
    override val newestComicNumber = flow {
        downloadComic(0).collect { newestComic ->
            if (newestComic.number != prefHelper.newest) {
                comicDao.insert(newestComic)

                // In offline mode, we need to cache all the new comics here
                if (prefHelper.fullOfflineEnabled()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        (prefHelper.newest + 1 .. newestComic.number).map { number ->
                            downloadComic(number).collect { comic ->
                                saveOfflineBitmap(number).onCompletion {
                                    comicDao.insert(comic)
                                    comicCached.send(comic)
                                }.collect()
                            }
                        }
                    }
                }

                prefHelper.setNewestComic(newestComic.number)
                emit(newestComic.number)

                // The newest comic in inserted into Room asynchronously, so our comics flow
                // might trigger before it has been inserted. Thus, make sure that the browser
                // and overview will still notice that we've found a new one
                comicCached.send(newestComic)
            }
        }
    }.stateIn(CoroutineScope(Dispatchers.Main), SharingStarted.Lazily, prefHelper.newest)

    override val comics = combine(comicDao.getComics(), newestComicNumber) { comics, newest ->
        comics.mapToComicContainer(newest)
    }

    override val favorites = comicDao.getFavorites().mapToComicContainer()

    override val unreadComics = comics.map {
        it.filter { container -> container.comic == null || !container.comic.read }
    }

    override suspend fun isFavorite(number: Int): Boolean = comicDao.isFavorite(number)

    override suspend fun setRead(number: Int, read: Boolean) {
        if (comicDao.isRead(number) != read) {
            comicDao.setRead(number, read)
        }
    }

    override suspend fun setFavorite(number: Int, favorite: Boolean) {
        if (favorite) saveOfflineBitmap(number).collect {}

        comicDao.setFavorite(number, favorite)
    }

    override suspend fun removeAllFavorites() = comicDao.removeAllFavorites()

    override suspend fun setBookmark(number: Int) {
        prefHelper.bookmark = number
    }

    override suspend fun oldestUnreadComic() = comicDao.oldestUnreadComic()

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
        saveOfflineBitmap(number).collect {}
        return getOfflineUri(number)
    }

    override fun getOfflineUri(number: Int): Uri? {
        return when (number) {
            //Fix for offline users who downloaded the HUGE version of #1826 or #2185
            1826 -> Uri.parse("android.resource://${context.packageName}/${R.mipmap.birdwatching}")
            2185 -> Uri.parse("android.resource://${context.packageName}/${R.mipmap.cumulonimbus_2x}")

            else -> FileProvider.getUriForFile(
                    context,
                    "de.tap.easy_xkcd.fileProvider",
                    getComicImageFile(number)
                )
        }
    }

    private fun getComicImageFile(number: Int) =
        File(prefHelper.getOfflinePath(context), "${number}.png")

    private fun hasDownloadedComicImage(number: Int) = getComicImageFile(number).exists()

    @ExperimentalCoroutinesApi
    fun saveOfflineBitmap(number: Int): Flow<Unit> = callbackFlow {
        val comic = comicDao.getComic(number)
        if (!hasDownloadedComicImage(number) && comic != null) {
            GlideApp.with(context)
                .asBitmap()
                .load(comic.url)
                .into(object: CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        try {
                            val fos = FileOutputStream(getComicImageFile(number))
                            resource.compress(Bitmap.CompressFormat.PNG, 100, fos)
                            fos.flush()
                            fos.close()
                            channel.close()
                        } catch (e: Exception) {
                            Timber.e(e, "While downloading comic $number")
                            channel.close(e)
                        }
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) { channel.close() }

                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        } else {
            channel.close()
        }
        awaitClose()
    }

    @ExperimentalCoroutinesApi
    override suspend fun saveOfflineBitmaps(): Flow<ProgressStatus> = channelFlow {
        (1..prefHelper.newest).map {
            saveOfflineBitmap(it).onCompletion {
                send(ProgressStatus.IncrementProgress)
            }
        }.also { send(ProgressStatus.Max(it.size)) }
            .merge()
            .collect {}
        send(ProgressStatus.Finished)
    }

    @ExperimentalCoroutinesApi
    private fun downloadComic(number: Int): Flow<Comic> = callbackFlow {
        client.newCall(Request.Builder().url(RealmComic.getJsonUrl(number)).build())
            .enqueue(object: okhttp3.Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Timber.e(e)
                    channel.close()
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string()
                    if (body == null) {
                        Timber.e("Got empty body for comic $number")
                        channel.close()
                        return
                    }

                    var json: JSONObject
                    try {
                        json = JSONObject(body)
                    } catch (e: JSONException) {
                        if (number == 404) {
                            Timber.i("Json not found, but that's expected for comic 404")
                            json = JSONObject()
                            json.put("num", 404)
                        } else {
                            Timber.e(e, "Occurred at comic $number")
                            channel.close()
                            return
                        }
                    }
                    trySend(Comic.buildFromJson(json, context)).onFailure(Timber::e)
                    channel.close()
                }
            })
        awaitClose()
    }

    @ExperimentalCoroutinesApi
    override suspend fun cacheAllComics() = flow {
        emit(ProgressStatus.ResetProgress)

        (1..prefHelper.newest)
            .filter { number -> comicDao.getComic(number) == null }
            .map {
                downloadComic(it)
            }
            .also { emit(ProgressStatus.Max(it.size)) }
            .merge()
            .collect {
                comicDao.insert(it)
                emit(ProgressStatus.IncrementProgress)
            }

        emit(ProgressStatus.Finished)
    }

    @ExperimentalCoroutinesApi
    override suspend fun cacheComic(number: Int) {
        withContext(Dispatchers.IO) {
            if (comicDao.getComic(number) == null) {
                downloadComic(number).collect { comic ->
                    comicDao.insert(comic)
                    comicCached.send(comic)
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