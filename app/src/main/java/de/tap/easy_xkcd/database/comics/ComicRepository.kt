package de.tap.easy_xkcd.database.comics

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.text.HtmlCompat
import com.tap.xkcd_reader.BuildConfig
import com.tap.xkcd_reader.R
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.tap.easy_xkcd.GlideApp
import de.tap.easy_xkcd.database.DatabaseManager
import de.tap.easy_xkcd.database.ProgressStatus
import de.tap.easy_xkcd.database.RealmComic
import de.tap.easy_xkcd.database.comics.XkcdApi
import de.tap.easy_xkcd.database.comics.XkcdApiComic
import de.tap.easy_xkcd.explainXkcd.ExplainXkcdApi
import de.tap.easy_xkcd.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
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

interface ComicRepository {
    val comics: Flow<List<ComicContainer>>

    val favorites: Flow<List<ComicContainer>>

    val unreadComics: Flow<List<ComicContainer>>

    val newestComicNumber: Flow<Int>

    val comicCached: SharedFlow<Comic>

    val foundNewComic: Channel<Unit>

    suspend fun cacheComic(number: Int)

    fun cacheAllComics(cacheMissingTranscripts: Boolean): Flow<ProgressStatus>

    suspend fun getUriForSharing(number: Int): Uri?

    suspend fun getRedditThread(comic: Comic): String?

    suspend fun isFavorite(number: Int): Boolean

    val saveOfflineBitmaps: Flow<ProgressStatus>

    suspend fun removeOfflineBitmaps()

    suspend fun removeAllFavorites()

    suspend fun setRead(number: Int, read: Boolean)

    suspend fun setFavorite(number: Int, favorite: Boolean)

    suspend fun setBookmark(number: Int)

    suspend fun migrateRealmDatabase()

    suspend fun oldestUnreadComic(): Comic?

    fun getOfflineUri(number: Int): Uri?

    suspend fun searchComics(query: String) : Flow<List<ComicContainer>>

    suspend fun getOrCacheTranscript(comic: Comic): String?
}

@Singleton
class ComicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefHelper: PrefHelper,
    private val comicDao: ComicDao,
    private val client: OkHttpClient,
    val coroutineScope: CoroutineScope,
    private val explainXkcdApi: ExplainXkcdApi,
    private val xkcdApi: XkcdApi,
) : ComicRepository {

//    override val comicCached = Channel<Comic>()
    val _comicCached = MutableSharedFlow<Comic>()
    override val comicCached = _comicCached

    override val foundNewComic = Channel<Unit>()

    override val newestComicNumber = flow {
        try {
            val newestComic = Comic(xkcdApi.getNewestComic(), context)
            if (newestComic.number != prefHelper.newest) {
                // In offline mode, we need to cache all the new comics here
                if (prefHelper.fullOfflineEnabled() && prefHelper.mayDownloadDataForOfflineMode(context)) {
                    val firstComicToCache = prefHelper.newest + 1
                    coroutineScope.launch {
                        (firstComicToCache..newestComic.number).map { number ->
                            downloadComic(number)?.let { comic ->
                                saveOfflineBitmap(number)
                                    comicDao.insert(comic)
                                    _comicCached.emit(comic)
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
        } catch (e: Exception) {
            Timber.e(e, "While downloading newest comic")
        }
    }.flowOn(Dispatchers.IO).stateIn(coroutineScope, SharingStarted.Lazily, prefHelper.newest)

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
        if (favorite) saveOfflineBitmap(number)

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
                Comic(
                    XkcdApiComic(
                        num = realmComic.comicNumber,
                        transcript = realmComic.transcript,
                        alt = realmComic.altText,
                        title = realmComic.title,
                        url = realmComic.url,
                        day = "", month = "", year = "",
                    ), context
                ).apply {
                    read = realmComic.isRead
                    favorite = realmComic.isFavorite
                }
            }
            Timber.d("Migrating ${migratedComics.size} comics")
            comicDao.insert(migratedComics)
            prefHelper.setHasMigratedRealmDatabase()
        }
    }

    //TODO Use RedditSearchApi via Retrofit
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
        return getOfflineUri(number)
    }

    override fun getOfflineUri(number: Int): Uri? {
        return when (number) {
            //Fix for offline users who downloaded the HUGE version of #1826 or #2185
            1826 -> Uri.parse("android.resource://${context.packageName}/${R.mipmap.birdwatching}")
            2185 -> Uri.parse("android.resource://${context.packageName}/${R.mipmap.cumulonimbus_2x}")

            else -> try {
                FileProvider.getUriForFile(
                    context,
                    "de.tap.easy_xkcd.fileProvider",
                    getComicImageFile(number)
                )
            } catch (e: Exception) {
                Timber.e(e, "When getting offline URI for $number")
                null
            }
        }
    }

    private fun getComicImageFile(number: Int) =
        File(prefHelper.getOfflinePath(context), "${number}.png")

    private fun hasDownloadedComicImage(number: Int) = getComicImageFile(number).exists()

    private suspend fun saveOfflineBitmap(number: Int) = withContext(Dispatchers.IO) {
        val comic = comicDao.getComic(number)
        if (!hasDownloadedComicImage(number) && comic != null) {
            try {
                GlideApp.with(context)
                    .asBitmap()
                    .load(comic.url)
                    .submit()
                    .get()?.let { bitmap ->
                        val fos = FileOutputStream(getComicImageFile(number))
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                        fos.flush()
                        fos.close()
                    }
            } catch (e: Exception) {
                Timber.e(e, "While downloading comic $number")
            }
        }
    }

    override val saveOfflineBitmaps: Flow<ProgressStatus> = flow {
        var max: Int
        (1..prefHelper.newest)
            .filter { !hasDownloadedComicImage(it) }
            .also { max = it.size }
            .map {
                flow {
                    emit(saveOfflineBitmap(it))
                }
        }.merge()
            .collectIndexed { index, _ ->
                emit(ProgressStatus.SetProgress(index + 1, max))
            }
        emit(ProgressStatus.Finished)
    }

    override suspend fun removeOfflineBitmaps() {
        withContext(Dispatchers.IO) {
            (1..prefHelper.newest).filter {
                !comicDao.isFavorite(it)
            }.map {
                getComicImageFile(it).delete()
            }
        }
    }

    private suspend fun downloadComic(number: Int): Comic? {
        return if (number == 404) {
            Comic.makeComic404()
        } else {
            try {
                Comic(xkcdApi.getComic(number), context)
            } catch (e: Exception) {
                Timber.e(e, "Failed to download comic $number")
                null
            }
        }
    }

    override fun cacheAllComics(cacheMissingTranscripts: Boolean) = flow {
        val allComics = comicDao.getComicsSuspend().toMutableMap()
        var max: Int
        (1..prefHelper.newest)
            .filter { number -> !allComics.containsKey(number) }
            .also { max = it.size }
            .map { number ->
                flow {
                    downloadComic(number)?.let { emit(it) }
                }
            }
            .merge()
            .collectIndexed { index, comic ->
                comicDao.insert(comic)
                allComics[comic.number] = comic
                emit(ProgressStatus.SetProgress(index + 1, max))
            }

        if (cacheMissingTranscripts) {
            emit(ProgressStatus.ResetProgress)
            (1..prefHelper.newest)
                .mapNotNull { allComics[it] }
                .filter { comic -> comic.transcript == "" }
                .also {
                    max = it.size
                    emit(ProgressStatus.SetProgress(0, max))
                }
                .map { flow { emit(getOrCacheTranscript(it)) } }
                .merge()
                .collectIndexed { index, _ ->
                    emit(ProgressStatus.SetProgress(index + 1, max))
                }
        }

        emit(ProgressStatus.Finished)
    }

    override suspend fun cacheComic(number: Int) {
        val comicInDatabase = comicDao.getComic(number)
        if (comicInDatabase == null) {
            downloadComic(number)?.let { comic ->
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

    override suspend fun searchComics(query: String) = flow {
        emit(comicDao.searchComicsByTitle(query).map { comic ->
            ComicContainer(comic.number, comic, comic.number.toString())
        })
        emit(comicDao.searchComicsByAltText(query).map { comic ->
            ComicContainer(comic.number, comic, getPreview(query, comic.altText))
        })
        emit(comicDao.searchComicsByTranscript(query).map { comic ->
            ComicContainer(comic.number, comic, getPreview(query, comic.transcript))
        })
    }

    private fun String.asCleanedTranscript() =
        HtmlCompat.fromHtml(
            this.replace("\n", "<br />")
                .split("<span id=\"Discussion\"")[0],
            HtmlCompat.FROM_HTML_MODE_LEGACY
        ).toString()
            .replace(Regex("""^\s*Transcript\[edit]"""), "")
            .trim()

    private suspend fun getTranscriptFromApi(number: Int) =
        // On Explainxkcd, this includes the dynamic transcript, which is HUGE!
        // So hardcode the short version here.
        if (number == 2131) {
            """[This was an interactive and dynamic comic during April 1st from its release until its completion. But the final and current image, will be the official image to transcribe. But the dynamic part of the comic as well as the "error image" displayed to services that could not render the dynamic comic is also transcribed here below.]\n\n[The final picture shows the winner of the gold medal in the Emojidome bracket tournament, as well as the runner up with the silver medal. There is no text. The winner is the "Space", "Stars" or "Milky Way" emoji, which is shown with a blue band on top of a dark blue band on top of an almost black background, indicating the light band of the Milky Way in the night sky. Stars (in both five point star shape and as dots) in light blue are spread out in all three bands of color. The large gold medal with its red neck string, is floating close to the middle of the picture, lacking any kind of neck in space to tie it around. To the left of the gold medal is the runner up, the brown Hedgehog, with light-brown face. It clutches the smaller silver medal, also with red neck string, which floats out there in space. The hedgehog with medal is depicted small enough to fit inside the neck string on the gold medal.]"""
        } else {
            try {
                withContext(Dispatchers.IO) {
                    explainXkcdApi.getSections(number).execute().body()?.findPageIdOfTranscript()?.let {
                        explainXkcdApi.getSection(number, it).text.asCleanedTranscript()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "While getting transcript for $number from explainxkcd.com")
                null
            }
        }

    override suspend fun getOrCacheTranscript(comic: Comic): String? {
        return if (comic.transcript != "") {
            comic.transcript
        } else {
            try {
                getTranscriptFromApi(comic.number)?.let { transcript ->
                    comic.transcript = transcript
                    comicDao.insert(comic)

                    transcript
                }
            } catch (e: Exception) {
                Timber.e(e, "While getting transcript for comic $comic")
                null
            }
        }
    }
}

@Module
@InstallIn(ViewModelComponent::class, SingletonComponent::class)
class ComicRepositoryModule {
    @Provides
    fun provideComicRepository(
        @ApplicationContext context: Context,
        prefHelper: PrefHelper,
        comicDao: ComicDao,
        client: OkHttpClient,
        explainXkcdApi: ExplainXkcdApi,
        xkcdApi: XkcdApi
    ): ComicRepository = ComicRepositoryImpl(context, prefHelper, comicDao, client, CoroutineScope(Dispatchers.Main), explainXkcdApi, xkcdApi)
}