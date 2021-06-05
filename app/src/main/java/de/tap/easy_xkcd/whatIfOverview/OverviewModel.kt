package de.tap.easy_xkcd.whatIfOverview

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.database.DatabaseManager
import de.tap.easy_xkcd.utils.Article
import de.tap.easy_xkcd.utils.JsonParser
import de.tap.easy_xkcd.utils.PrefHelper
import io.reactivex.rxjava3.core.*
import io.realm.Case
import io.realm.Realm
import kotlinx.coroutines.*
import okhttp3.*
import okio.buffer
import okio.sink
import org.jsoup.Jsoup
import ru.gildor.coroutines.okhttp.await
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import javax.inject.Inject

interface OverviewModel {
    suspend fun updateWhatIfDatabase()

    suspend fun updateWhatIfOfflineDatabase(articleDownloadedCallback: () -> Unit)

    fun numberOfOfflineArticlesNotDownloaded(): Int

    fun getArticles(
        hideRead: Boolean = false,
        onlyFavorites: Boolean = false,
        searchQuery: String? = null
    ): List<Article>

    fun toggleArticleFavorite(article: Article)

    fun setAllRead()

    fun setAllUnread()
}

@Module
@InstallIn(ViewModelComponent::class)
abstract class OverviewModelModule {
    @Binds
    abstract fun bindOverViewModel(overviewModelImpl: OverviewModelImpl): OverviewModel
}

class OverviewModelImpl @Inject constructor(@ApplicationContext context: Context) : OverviewModel {
    val prefHelper = PrefHelper(context)
    val databaseManager = DatabaseManager(context)

    private val OFFLINE_WHATIF_PATH = "/easy xkcd/what if/"
    private val OFFLINE_WHATIF_OVERVIEW_PATH = "/easy xkcd/what if/overview/"

    override fun getArticles(
        hideRead: Boolean,
        onlyFavorites: Boolean,
        searchQuery: String?
    ): List<Article> {
        val realm = Realm.getDefaultInstance()
        var query = realm.where(Article::class.java)

        if (onlyFavorites)
            query = query.equalTo("favorite", true)
        else if (hideRead)
            query = query.equalTo("read", false)

        searchQuery?.let {
            query = query.contains("title", searchQuery, Case.INSENSITIVE)
        }

        return realm.copyFromRealm(query.findAll())
    }

    override suspend fun updateWhatIfDatabase() {
        val realm = Realm.getDefaultInstance()
        realm.beginTransaction()

        try {
            val document = withContext(Dispatchers.IO) {
                Jsoup.parse(
                    JsonParser.getNewHttpClient().newCall(
                        Request.Builder()
                            .url("https://what-if.xkcd.com/archive/")
                            .build()
                    ).await().body!!.string()
                )
            }

            val titles = document.select("h1")
            val thumbnails = document.select("img.archive-image")
            for (number in 1..titles.size) {
                var article =
                    realm.where(Article::class.java).equalTo("number", number).findFirst()
                if (article == null) {
                    article = Article()
                    article.number = number
                    article.title = titles[number - 1].text()
                    article.thumbnail =
                        "https://what-if.xkcd.com/" + thumbnails[number - 1].attr("src") // -1 cause articles a 1-based indexed
                    article.isOffline = false

                    // Import from the legacy database
                    article.isRead = prefHelper.checkRead(number)
                    article.isFavorite = prefHelper.checkWhatIfFav(number)
                    realm.copyToRealm(article)
                    Timber.d("Stored new article: ${article.number} ${article.title} ${article.thumbnail}")
                }
            }
        } catch (e: IOException) {
            Timber.e(e)
        } finally {
            realm.commitTransaction()
            realm.close()
        }
    }

    override suspend fun updateWhatIfOfflineDatabase(articleDownloadedCallback: () -> Unit) {
        val realm = Realm.getDefaultInstance()
        val articlesToDownload = realm.copyFromRealm(
            realm.where(Article::class.java).equalTo("offline", false).findAll()
        )
        realm.close()

        if (articlesToDownload.isEmpty()) {
            return
        }

        val client = OkHttpClient()

        withContext(Dispatchers.IO) {
            articlesToDownload.map {
                async {
                    downloadArticle(it, client, prefHelper, articleDownloadedCallback)
                }
            }.awaitAll()
        }
    }

    fun downloadArticle(
        article: Article,
        client: OkHttpClient,
        prefHelper: PrefHelper,
        articleDownloadedCallback: () -> Unit,
    ) {
        if (Article.hasOfflineFilesForArticle(article.number, prefHelper)) {
            Timber.d("Already has files for article %d", article.number)
            return
        }

        val response = client.newCall(
            Request.Builder()
                .url("https://what-if.xkcd.com/" + article.number)
                .build()
        ).execute()

        val doc = Jsoup.parse(response.body!!.string())
        val dir = File(
            prefHelper.offlinePath
                .absolutePath + OFFLINE_WHATIF_PATH + article.number
        )
        if (!dir.exists()) dir.mkdirs()
        var file =
            File(dir, article.number.toString() + ".html")
        val writer =
            BufferedWriter(FileWriter(file))
        writer.write(doc.outerHtml())
        writer.close()

        // Download images
        var count = 1
        for (e in doc.select(".illustration")) {
            try {
                val url = "https://what-if.xkcd.com" + e.attr("src")
                val request: Request = Request.Builder()
                    .url(url)
                    .build()
                val imgResponse = client.newCall(request).execute()
                file = File(dir, "$count.png")
                val sink = file.sink().buffer()
                sink.writeAll(imgResponse.body!!.source())
                sink.close()
                response.body!!.close()
                count++
            } catch (e2: Exception) {
                Timber.e(e2, "article %d", article.number)
            }
        }

        // Download thumbnail
        downloadThumbnail(article, client, prefHelper)
        val realm = Realm.getDefaultInstance()
        realm.executeTransaction {
            article.isOffline = true
            realm.copyToRealmOrUpdate(article)
        }
        realm.close()
        Timber.d("Successfully downloaded article %d", article.number)

        articleDownloadedCallback()
    }

    private fun downloadThumbnail(article: Article, client: OkHttpClient, prefHelper: PrefHelper) {
        val sdCard = prefHelper.offlinePath
        val dir = File(sdCard.absolutePath + OFFLINE_WHATIF_OVERVIEW_PATH)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, article.number.toString() + ".png")
        try {
            val response = client.newCall(
                Request.Builder().url(article.thumbnail).build()
            ).execute()
            val sink = file.sink().buffer()
            sink.writeAll(response.body!!.source())
            sink.close()
            response.body!!.close()
        } catch (e: java.lang.Exception) {
            Timber.e(e)
        }
    }

    override fun toggleArticleFavorite(article: Article) {
        article.isFavorite = !article.isFavorite
        val realm = Realm.getDefaultInstance()
        realm.executeTransaction {
            realm.copyToRealmOrUpdate(article)
        }
        realm.close()
    }

    override fun numberOfOfflineArticlesNotDownloaded(): Int {
        return Realm.getDefaultInstance().where(Article::class.java).equalTo("offline", false)
            .findAll().size
    }

    override fun setAllRead() {
        databaseManager.setAllArticlesReadStatus(true)
    }

    override fun setAllUnread() {
        databaseManager.setAllArticlesReadStatus(false)
    }
}