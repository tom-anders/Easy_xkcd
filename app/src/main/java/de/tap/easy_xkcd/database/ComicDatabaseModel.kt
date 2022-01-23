package de.tap.easy_xkcd.comicBrowsing

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.GlideApp
import de.tap.easy_xkcd.database.Comic
import de.tap.easy_xkcd.database.DatabaseManager
import de.tap.easy_xkcd.database.RealmComic
import de.tap.easy_xkcd.utils.*
import io.realm.Realm
import io.realm.Sort
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

interface ComicDatabaseModel {
    suspend fun findNewestComic(): Int
    suspend fun updateDatabase(
        newestComic: Int,
        comicSavedCallback: () -> Unit
    )

    // Loads comic from realm if exists, otherwise downloads it and stores it in Realm
    suspend fun getComic(number: Int): RealmComic

    suspend fun getUriForSharing(comic: Comic): Uri

    fun getAllComics(): List<RealmComic>

    fun getFavoriteComics(): List<RealmComic>

    fun removeAllFavorites()

    fun getUnreadComics(): List<RealmComic>

    fun isFavorite(number: Int): Boolean

    fun setBookmark(number: Int)

    suspend fun toggleFavorite(number: Int)

    fun isRead(number: Int): Boolean

    fun setRead(number: Int, isRead: Boolean)

    fun getRandomComic(): Int

    suspend fun getRedditThread(comic: Comic): String

}

@Singleton
class ComicDatabaseModelImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefHelper: PrefHelper,
    private val databaseManager: DatabaseManager,
) : ComicDatabaseModel {

    private val client = OkHttpClient()

    override fun getRandomComic(): Int {
        return Random.nextInt(returnWithRealm { it.where(RealmComic::class.java).findAll().size })
    }

    override fun getAllComics(): List<RealmComic> = copyResultsFromRealm {
        it.where(RealmComic::class.java).findAllSorted("comicNumber", Sort.ASCENDING)
    }

    override fun getFavoriteComics() = copyResultsFromRealm {
        it.where(RealmComic::class.java)
            .equalTo("isFavorite", true)
            .findAllSorted("comicNumber", Sort.ASCENDING)
    }

    override fun removeAllFavorites() {
        for (comic in getFavoriteComics()) {
            comic.isFavorite = false
            copyToRealmOrUpdate(comic)
        }
    }

    override fun getUnreadComics() = copyResultsFromRealm {
        it.where(RealmComic::class.java)
            .equalTo("isRead", false)
            .findAllSorted("comicNumber", Sort.ASCENDING)
    }

    private fun getComicFromRealm(comicNumber: Int, realm: Realm): RealmComic? =
        realm.where(RealmComic::class.java).equalTo("comicNumber", comicNumber).findFirst()

    override fun isFavorite(number: Int) = returnWithRealm {
        getComicFromRealm(number, it)?.isFavorite == true
    }

    override fun setBookmark(number: Int) {
        prefHelper.bookmark = number
    }

    override suspend fun toggleFavorite(number: Int) = withContext(Dispatchers.IO) {
        doWithRealm { realm ->
            getComicFromRealm(number, realm)?.let { comic ->
                realm.executeTransaction {
                    comic.isFavorite = !comic.isFavorite
                    realm.copyToRealmOrUpdate(comic)
                }
                if (comic.isFavorite) {
                    if (!RealmComic.isOfflineComicAlreadyDownloaded(comic.comicNumber, prefHelper, context)) {
                        RealmComic.saveOfflineBitmap(
                            OkHttpClient().newCall(Request.Builder().url(comic.url).build()).execute(),
                            prefHelper, comic.comicNumber, context
                        )
                    }
                } else {
                    File(prefHelper.getOfflinePath(context), "${comic.comicNumber}.png").delete()
                }
            }
        }
    }

    override fun isRead(number: Int) = returnWithRealm {
        getComicFromRealm(number, it)?.isRead == true
    }

    override fun setRead(number: Int, isRead: Boolean) {
        doWithRealm { realm ->
            getComicFromRealm(number, realm)?.let { comic ->
                realm.executeTransaction {
                    comic.isRead = isRead
                    realm.copyToRealmOrUpdate(comic)
                }
            }
        }
    }

    override suspend fun getRedditThread(comic: Comic) = withContext(Dispatchers.IO) {
        "https://www.reddit.com" + JsonParser.getJSONFromUrl("https://www.reddit.com/r/xkcd/search.json?q=" + comic.title + "&restrict_sr=on")
            .getJSONObject("data")
            .getJSONArray("children").getJSONObject(0).getJSONObject("data").getString("permalink")
    }

    override suspend fun findNewestComic(): Int = withContext(Dispatchers.IO) {
        RealmComic.findNewestComicNumber()
    }

    override suspend fun getComic(number: Int): RealmComic {
        val comic = copyFromRealm { realm ->
            getComicFromRealm(number, realm)
        }

        return comic!!
    }

    override suspend fun updateDatabase(
        newestComic: Int,
        comicSavedCallback: () -> Unit
    ) {
        val comicsToDownload = (1..newestComic).map { number ->
            Pair(number, copyFromRealm { realm ->
                getComicFromRealm(number, realm)
            })
        }.filter {
            // If a comic does not exists in the database at all (i.e. if the realm query returned null)
            // we have to download it in any case.
            // Otherwise, only download it if we're in offline mode and the comic has not been
            // downloaded yet
            val comic = it.second
            comic == null || (prefHelper.fullOfflineEnabled() && !comic.isOffline)
        }.toMap()

        withContext(Dispatchers.IO) {
            comicsToDownload.map {
                async(Dispatchers.IO) {
                    val comic = if (it.value == null) {
                        downloadComic(it.key)
                    } else {
                        it.value
                    }
                    comic?.let {
                        downloadOfflineData(it)
                        copyToRealmOrUpdate(it)
                    }

                    comicSavedCallback()
                }
            }.awaitAll()
        }

        if (!prefHelper.transcriptsFixed()) {
            databaseManager.fixTranscripts()
            prefHelper.setTranscriptsFixed()
            Timber.d("Transcripts fixed!")
        }
    }

    private fun downloadComic(number: Int): RealmComic? {
        val response =
            client.newCall(Request.Builder().url(RealmComic.getJsonUrl(number)).build())
                .execute()

        val body = response.body?.string()
        if (body == null) {
            Timber.e("Got empty body for comic $number")
            return null
        }

        var json: JSONObject? = null
        try {
            json = JSONObject(body)
        } catch (e: JSONException) {
            if (number == 404) {
                Timber.i("Json not found, but that's expected for comic 404")
            } else {
                Timber.e(e, "Occurred at comic $number")
            }
        }

        return RealmComic.buildFromJson(number, json, context)
    }

    private fun downloadOfflineData(comic: RealmComic) {
        if (prefHelper.fullOfflineEnabled() && !comic.isOffline) {
            if (RealmComic.isOfflineComicAlreadyDownloaded(
                    comic.comicNumber,
                    prefHelper,
                    context
                )
            ) {
                Timber.i("Already has offline files for comic ${comic.comicNumber}, skipping download...")
            } else {
                try {
                    RealmComic.saveOfflineBitmap(
                        GlideApp.with(context)
                            .asBitmap()
                            .load(comic.url)
                            .submit()
                            .get(), prefHelper, comic.comicNumber, context)
                } catch (e: Exception) {
                    Timber.e(e, "Download failed at ${comic.comicNumber} (${comic.url})")
                }
            }

            comic.isOffline = true
        }
    }

    override suspend fun getUriForSharing(comic: Comic): Uri = withContext(Dispatchers.IO) {
        if (!RealmComic.isOfflineComicAlreadyDownloaded(comic.number, prefHelper, context)) {
            RealmComic.saveOfflineBitmap(
                OkHttpClient().newCall(Request.Builder().url(comic.url).build()).execute(),
                prefHelper, comic.number, context
            )
        }
        FileProvider.getUriForFile(
            context,
            "de.tap.easy_xkcd.fileProvider",
            File(prefHelper.getOfflinePath(context), "${comic.number}.png")
        )
    }
}

@Module
@InstallIn(ViewModelComponent::class)
abstract class ComicsModelModule {
    @Binds
    abstract fun bindComicsModel(comicsModelImpl: ComicDatabaseModelImpl): ComicDatabaseModel
}


