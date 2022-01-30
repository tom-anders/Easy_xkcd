package de.tap.easy_xkcd.database

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.content.FileProvider
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.tap.xkcd_reader.BuildConfig
import com.tap.xkcd_reader.R
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.GlideApp
import de.tap.easy_xkcd.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.regex.PatternSyntaxException
import javax.inject.Inject
import javax.inject.Singleton

// If a comic has not been cached yet, the comic will be null here
// The number can then be used to request caching it.
data class ComicContainer(
    val number: Int,
    val comic: Comic?,
    val searchPreview: String = "",
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

    val comicCached: SharedFlow<Comic>

    val foundNewComic: Channel<Unit>

    suspend fun cacheComic(number: Int)

    val cacheAllComics: Flow<ProgressStatus>

    suspend fun getUriForSharing(number: Int): Uri?

    suspend fun getRedditThread(comic: Comic): String?

    suspend fun isFavorite(number: Int): Boolean

    @ExperimentalCoroutinesApi
    suspend fun saveOfflineBitmaps(): Flow<ProgressStatus>

    suspend fun removeAllFavorites()

    suspend fun setRead(number: Int, read: Boolean)

    suspend fun setFavorite(number: Int, favorite: Boolean)

    suspend fun setBookmark(number: Int)

    suspend fun migrateRealmDatabase()

    suspend fun oldestUnreadComic(): Comic?

    fun getOfflineUri(number: Int): Uri?

    suspend fun searchComics(query: String) : List<ComicContainer>
}

@Singleton
class ComicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefHelper: PrefHelper,
    private val comicDao: ComicDao,
    private val client: OkHttpClient,
    val coroutineScope: CoroutineScope,
) : ComicRepository {

//    override val comicCached = Channel<Comic>()
    val _comicCached = MutableSharedFlow<Comic>()
    override val comicCached = _comicCached

    override val foundNewComic = Channel<Unit>()

    @ExperimentalCoroutinesApi
    override val newestComicNumber = flow {
        downloadComic(0).collect { newestComic ->
            if (newestComic.number != prefHelper.newest) {
                // In offline mode, we need to cache all the new comics here
                if (prefHelper.fullOfflineEnabled()) {
                    val firstComicToCache = prefHelper.newest + 1
                    coroutineScope.launch {
                        (firstComicToCache .. newestComic.number).map { number ->
                            downloadComic(number).collect { comic ->
                                saveOfflineBitmap(number).onCompletion {
                                    comicDao.insert(comic)
                                    _comicCached.emit(comic)
                                }.collect()
                            }
                        }
                    }
                }

                if (prefHelper.newest != 0) {
                    foundNewComic.send(Unit)
                }

                prefHelper.setNewestComic(newestComic.number)
                emit(newestComic.number)
            }
        }
    }.stateIn(coroutineScope, SharingStarted.Lazily, prefHelper.newest)

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

    override suspend fun migrateRealmDatabase() {
        if (!prefHelper.hasMigratedRealmDatabase() || BuildConfig.DEBUG ) {
            // Needed for fresh install, will initialize the (empty) realm database
            val databaseManager = DatabaseManager(context)

            val migratedComics = copyResultsFromRealm { realm ->
                realm.where(RealmComic::class.java).findAll()
            }.map { realmComic ->
                Comic(realmComic.comicNumber).apply {
                    favorite = realmComic.isFavorite
                    read = realmComic.isRead
                    title = realmComic.title
                    transcript = realmComic.transcript
                    url = realmComic.url
                    altText = realmComic.altText
                }
            }
            Timber.d("Migrating ${migratedComics.size} comics")
            comicDao.insert(migratedComics)
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
    override val cacheAllComics = flow {
        emit(ProgressStatus.ResetProgress)

        val allComics = comicDao.getComicsSuspend()
        (1..prefHelper.newest)
            .filter { number -> !allComics.containsKey(number) }
            .also { emit(ProgressStatus.Max(it.size)) }
            .map {
                downloadComic(it)
            }
            .merge()
            .collect {
                comicDao.insert(it)
                emit(ProgressStatus.IncrementProgress)
            }

        emit(ProgressStatus.Finished)
    }

    @ExperimentalCoroutinesApi
    override suspend fun cacheComic(number: Int) {
        val comicInDatabase = comicDao.getComic(number)
        if (comicInDatabase == null) {
            downloadComic(number).collect { comic ->
                comicDao.insert(comic)
                _comicCached.emit(comic)
            }
        } else {
            // This should only happen when migrating the old realm database, where there might
            // be a race condition between the cache request and the realm comics being
            // inserted into the new database
            _comicCached.emit(comicInDatabase)
        }
    }

    /**
     * Creates a preview for the transcript of comics that contain the query
     * @param query the users's query
     * @param transcript the comic's transcript
     * @return a short preview of the transcript with the query highlighted
     * @note Copied over from the old SearchResultsActivity.java, can probably be
     * optimized/refactored/simplified
     */
    private fun getPreview(query: String, transcript: String): String {
        var transcript = transcript
        return try {
            val firstWord = query.split(" ".toRegex()).toTypedArray()[0].toLowerCase()
            transcript = transcript.replace(".", ". ").replace("?", "? ").replace("]]", " ")
                .replace("[[", " ").replace("{{", " ").replace("}}", " ")
            val words = ArrayList(
                Arrays.asList(
                    *transcript.toLowerCase().split(" ".toRegex()).toTypedArray()
                )
            )
            var i = 0
            var found = false
            while (!found && i < words.size) {
                found =
                    if (query.length < 5) words[i].matches(Regex(".*\\b$firstWord\\b.*")) else words[i].contains(
                        firstWord
                    )
                if (!found) i++
            }
            var start = i - 6
            var end = i + 6
            if (i < 6) start = 0
            if (words.size - i < 6) end = words.size
            val sb = StringBuilder()
            for (s in words.subList(start, end)) {
                sb.append(s)
                sb.append(" ")
            }
            val s = sb.toString()
            "..." + s.replace(query, "<b>$query</b>") + "..."
        } catch (e: PatternSyntaxException) {
            e.printStackTrace()
            " "
        }
    }

    override suspend fun searchComics(query: String) = comicDao.searchComics(query).map { comic ->
        val preview = when {
            comic.title.contains(query) -> {
                comic.number.toString()
            }
            comic.altText.contains(query) -> {
                getPreview(query, comic.altText)
            }
            else -> {
                getPreview(query, comic.transcript)
            }
        }
        ComicContainer(comic.number, comic, preview)
    }
}

@Module
@InstallIn(ViewModelComponent::class)
class ComicRepositoryModule {
    @Provides
    fun provideComicRepository(
        @ApplicationContext context: Context,
        prefHelper: PrefHelper,
        comicDao: ComicDao,
        client: OkHttpClient,
    ): ComicRepository = ComicRepositoryImpl(context, prefHelper, comicDao, client, CoroutineScope(Dispatchers.Main))
}