package de.tap.easy_xkcd.database.whatif

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.tap.easy_xkcd.GlideApp
import de.tap.easy_xkcd.database.ProgressStatus
import de.tap.easy_xkcd.reddit.RedditSearchApi
import de.tap.easy_xkcd.utils.AppSettings
import de.tap.easy_xkcd.utils.AppTheme
import de.tap.easy_xkcd.utils.SharedPrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import ru.gildor.coroutines.okhttp.await
import timber.log.Timber
import java.io.*
import javax.inject.Inject
import javax.inject.Singleton


data class LoadedArticle(
    val article: Article,
    val html: String,

    val refs: List<String>
) {
    val number: Int get() = article.number
    val title: String get() = article.title
    val favorite: Boolean get() = article.favorite

    companion object {
        fun none() = LoadedArticle(Article(0, "", "", false, false), "", emptyList())
    }
}

interface ArticleRepository {
    val articles: Flow<List<Article>>

    suspend fun updateDatabase()

    suspend fun getRedditThread(article: Article): Uri?

    suspend fun setFavorite(number: Int, favorite: Boolean)

    suspend fun setRead(number: Int, read: Boolean)

    suspend fun searchArticles(query: String): List<Article>

    suspend fun setAllRead()

    suspend fun setAllUnread()

    suspend fun loadArticle(number: Int): LoadedArticle?

    suspend fun downloadArticle(number: Int)

    val downloadAllArticles: Flow<ProgressStatus>

    val downloadArchiveImages: Flow<ProgressStatus>

    suspend fun deleteAllOfflineArticles()
}

@Singleton
class ArticleRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPrefs: SharedPrefManager,
    private val settings: AppSettings,
    private val appTheme: AppTheme,
    private val articleDao: ArticleDao,
    private val okHttpClient: OkHttpClient,
    private val redditSearchApi: RedditSearchApi,
) : ArticleRepository {
    companion object {
        const val OFFLINE_WHATIF_PATH = "/what if/"
        const val OFFLINE_WHATIF_OVERVIEW_PATH = "/what if/overview"
    }

    override val articles: Flow<List<Article>> = articleDao.getArticles()

    override suspend fun updateDatabase() = withContext(Dispatchers.IO) {
        if (!sharedPrefs.hasAlreadyResetWhatifDatabase) {
            Timber.i("Resetting what-if database")

            articleDao.deleteAllArticles()
            settings.fullOfflineWhatIf = false
            deleteAllOfflineArticles()

            sharedPrefs.hasAlreadyResetWhatifDatabase = true
        }

        try {
            val document =
                Jsoup.parse(
                    okHttpClient.newCall(
                        Request.Builder()
                            .url("https://what-if.xkcd.com/archive/")
                            .build()
                    ).await().body?.string()
                )
            if (document != null) {
                val entries = document.select(".archive-entry")

                val previousNumberOfArticles = articleDao.getArticlesSuspend().size

                val articles = (previousNumberOfArticles + 1..entries.size).map { number ->
                    Article(
                        number = number,
                        title = entries[number - 1].selectFirst(".archive-title")?.text() ?: "",
                        thumbnail = entries[number - 1].selectFirst(".archive-image")?.attr("src") ?: "",
                    ).also {
                        if (settings.fullOfflineWhatIf) {
                            downloadArticle(number)
                        }
                    }
                }
                articleDao.insert(articles)
            }
        } catch (e: IOException) {
            Timber.e(e)
        }
    }

    private fun getImageUrlFromElement(e: Element) =
        // Usually it's only the path, but sometimes it's also the full url or http instead of https,
        // so extract the path here first just in case
        "https://what-if.xkcd.com${Uri.parse(e.attr("src")).path}"

    override suspend fun downloadArticle(number: Int) {
        withContext(Dispatchers.IO) {
            try {
                val dir = File(settings.getOfflinePath(context).absolutePath + OFFLINE_WHATIF_PATH + number.toString())
                dir.mkdirs()
                val doc = Jsoup.connect("https://what-if.xkcd.com/$number").get()
                val writer = BufferedWriter(FileWriter(File(dir,  "$number.html")))
                writer.write(doc.outerHtml())
                writer.close()

                //download images
                doc.select(".illustration").mapIndexed { index, e ->
                    try {
                        GlideApp.with(context)
                            .asBitmap()
                            .load(getImageUrlFromElement(e))
                            .submit()
                            .get()?.let { bitmap ->
                                val fos = FileOutputStream(File(dir, "${index + 1}.png"))
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                                fos.flush()
                                fos.close()
                            }
                    } catch (e2: Exception) {
                        Timber.e(e2, "While downloading image #${index} for article $number element ${e}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "At article $number")
            }
        }
    }

    override val downloadAllArticles: Flow<ProgressStatus> = flow {
        // In theory offline mode could be enabled before the whatif fragment has ever been shown
        updateDatabase()

        var max: Int
        articleDao.getArticlesSuspend()
            .also { max = it.size }
            .map {
                flow {
                    emit(downloadArticle(it.number))
                }
            }.merge()
            .collectIndexed { index, _ ->
                emit(ProgressStatus.SetProgress(index + 1, max))
            }
    }

    override val downloadArchiveImages: Flow<ProgressStatus> = flow {
        try {
            var max: Int
            val dir = File(settings.getOfflinePath(context).absolutePath + OFFLINE_WHATIF_OVERVIEW_PATH)
            dir.mkdirs()
            val doc = Jsoup.connect("https://what-if.xkcd.com/archive/")
                .userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/32.0.1700.19 Safari/537.36")
                .get()
            doc.select("img.archive-image")
                .also { max = it.size }
                .mapIndexed { index, element ->
                    flow {
                        emit(GlideApp.with(context)
                            .asBitmap()
                            .load(getImageUrlFromElement(element))
                            .submit()
                            .get()?.let { bitmap ->
                                val fos = FileOutputStream(File(dir, "${index + 1}.png"))
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                                fos.flush()
                                fos.close()
                            })
                    }.catch { Timber.e(it, "At archive image ${index+1}") }
                }.merge()
                .collectIndexed { index, _ ->
                    emit(ProgressStatus.SetProgress(index + 1, max))
                }
        } catch (e: Exception) {
            Timber.e(e, "While downloading archive images")
        }
    }

    override suspend fun getRedditThread(article: Article) =
        redditSearchApi.search(article.title)?.url?.let { Uri.parse(it) }

    override suspend fun setFavorite(number: Int, favorite: Boolean) {
        articleDao.setFavorite(number, favorite)
    }

    override suspend fun setRead(number: Int, read: Boolean) {
        articleDao.setRead(number, read)
    }

    override suspend fun setAllRead() {
        articleDao.setAllRead()
    }

    override suspend fun setAllUnread() {
        articleDao.setAllUnread()
    }

    override suspend fun searchArticles(query: String): List<Article> = articleDao.searchArticlesByTitle(query)

    override suspend fun loadArticle(number: Int): LoadedArticle? {
        val article = articleDao.getArticle(number) ?: return null
        articleDao.setRead(number, true)

        val doc = withContext(Dispatchers.IO) {
            if (!settings.fullOfflineWhatIf) {
                Jsoup.parse(okHttpClient.newCall(
                    Request.Builder()
                    .url("https://what-if.xkcd.com/$number")
                    .build()
                ).await().body?.string())
            } else {
                val dir = File(settings.getOfflinePath(context).absolutePath
                        + OFFLINE_WHATIF_PATH + number)
                val file = File(dir, "$number.html")
                Jsoup.parse(file, "UTF-8")
            }
        }

        //append custom css
        doc.head().getElementsByTag("link").remove()
        if (appTheme.amoledThemeEnabled()) {
            doc.head().appendElement("link").attr("rel", "stylesheet").attr("type", "text/css")
                .attr("href", "amoled.css")
        } else if (appTheme.nightThemeEnabled) {
            if (appTheme.invertColors) {
                doc.head().appendElement("link").attr("rel", "stylesheet").attr("type", "text/css")
                    .attr("href", "night_invert.css")
            } else {
                doc.head().appendElement("link").attr("rel", "stylesheet").attr("type", "text/css")
                    .attr("href", "night.css")
            }
        } else {
            doc.head().appendElement("link").attr("rel", "stylesheet").attr("type", "text/css")
                .attr("href", "style.css")
        }

        //fix the image links
        var count = 1
        val base = settings.getOfflinePath(context).absolutePath
        for (e in doc.select(".illustration")) {
            if (!settings.fullOfflineWhatIf) {
                e.attr("src", getImageUrlFromElement(e))
            } else {
                val path = "file://$base/what if/$number/$count.png"
                e.attr("src", path)
            }
            e.attr("onclick", "img.performClick(title);")
            count++
        }

        //fix footnotes and math scripts
        if (settings.fullOfflineWhatIf) {
            doc.select("script[src]").first().attr("src", "MathJax.js")
        }

        //remove header, footer, nav buttons
        doc.body().children().filter {
            it.id() != "entry-wrapper" && it.tagName() != "script"
        }.map { it.remove() }

        doc.body().getElementById("entry-wrapper")?.children()?.filter { it.id() != "entry" }
            ?.map { it.remove() }

        val refs = doc.select(".ref").map { it.select(".refbody").html() }

        doc.select(".ref").mapIndexed { n, element ->
            element.select(".refnum")
                .attr("onclick", "ref.performClick(\"${n}\")")
            element.select(".refbody").remove()
        }

        return LoadedArticle(article, doc.html(), refs)
    }

    override suspend fun deleteAllOfflineArticles() {
        File(settings.getOfflinePath(context).absolutePath + OFFLINE_WHATIF_PATH).deleteRecursively()
    }
}

@Module
@InstallIn(ViewModelComponent::class, SingletonComponent::class)
class ArticleRepositoryModule {
    @Provides
    fun provideArticleRepository(
        @ApplicationContext context: Context,
        sharedPrefs: SharedPrefManager,
        settings: AppSettings,
        appTheme: AppTheme,
        articleDao: ArticleDao,
        okHttpClient: OkHttpClient,
        redditSearchApi: RedditSearchApi,
    ): ArticleRepository = ArticleRepositoryImpl(context, sharedPrefs, settings, appTheme, articleDao, okHttpClient, redditSearchApi)
}
