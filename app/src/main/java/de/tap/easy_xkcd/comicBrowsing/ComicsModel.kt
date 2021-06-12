package de.tap.easy_xkcd.comicBrowsing

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.database.DatabaseManager
import de.tap.easy_xkcd.database.RealmComic
import de.tap.easy_xkcd.utils.PrefHelper
import io.realm.Realm
import io.realm.RealmResults
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.lang.Exception
import javax.inject.Inject
import kotlin.system.measureTimeMillis

interface ComicsModel {
    suspend fun findNewestComic(): Int
    suspend fun updateDatabase(
        newestComic: Int,
        comicSavedCallback: () -> Unit
    )
}

class ComicsModelImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ComicsModel {
    private val prefHelper = PrefHelper(context)
    private val databaseManager = DatabaseManager(context)

    private val client = OkHttpClient()

    override suspend fun findNewestComic(): Int = withContext(Dispatchers.IO) {
        RealmComic.findNewestComicNumber()
    }

    override suspend fun updateDatabase(
        newestComic: Int,
        comicSavedCallback: () -> Unit
    ) {
        val comicsToDownload = (1..newestComic).map {
            val realm = Realm.getDefaultInstance()
            val comic = realm.where(RealmComic::class.java).equalTo("comicNumber", it).findFirst()
            Pair(it, if (comic != null) realm.copyFromRealm(comic) else null)
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
                    comic?.let { downloadOfflineData(it) }

                    val realm = Realm.getDefaultInstance()
                    realm.executeTransaction { realm.copyToRealmOrUpdate(comic) }
                    realm.close()

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
                    val response =
                        client.newCall(Request.Builder().url(comic.url).build()).execute()
                    RealmComic.saveOfflineBitmap(response, prefHelper, comic.comicNumber, context)
                } catch (e: Exception) {
                    Timber.e(e, "Download failed at ${comic.comicNumber} (${comic.url})")
                }
            }

            comic.isOffline = true
        }
    }
}

@Module
@InstallIn(ViewModelComponent::class)
abstract class ComicsModelModule {
    @Binds
    abstract fun bindComicsModel(comicsModelImpl: ComicsModelImpl): ComicsModel
}


