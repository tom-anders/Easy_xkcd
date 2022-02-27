package de.tap.easy_xkcd.database.whatif

import android.content.Context
import android.net.Uri
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.tap.easy_xkcd.utils.JsonParser
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.ThemePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import ru.gildor.coroutines.okhttp.await
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class LoadedArticle(
    val article: Article,
    val html: String,

    //TODO Handle the image ref no.2 for number 141...
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
}

@Singleton
class ArticleRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefHelper: PrefHelper,
    private val themePrefs: ThemePrefs,
    private val articleDao: ArticleDao,
    val okHttpClient: OkHttpClient,
) : ArticleRepository {
    companion object {
        const val OFFLINE_WHATIF_PATH = "/what if/"
    }

    override val articles: Flow<List<Article>> = articleDao.getArticles()

    override suspend fun updateDatabase() = withContext(Dispatchers.IO) {
        try {
            val document =
                Jsoup.parse(
                    JsonParser.getNewHttpClient().newCall(
                        Request.Builder()
                            .url("https://what-if.xkcd.com/archive/")
                            .build()
                    ).await().body?.string()
                )
            if (document != null) {
                val titles = document.select("h1")
                val thumbnails = document.select("img.archive-image")

                val previousNumberOfArticles = articleDao.getArticlesSuspend().size
                val migrateLegacyDatabase = (previousNumberOfArticles == 0)

                val articles = (previousNumberOfArticles + 1..titles.size).map { number ->
                    Article(
                        number = number,
                        title = titles[number - 1].text(),
                        thumbnail = "https://what-if.xkcd.com/" + thumbnails[number - 1].attr("src"), // -1 cause articles a 1-based indexed
                    ).also { article ->
                        if (migrateLegacyDatabase) {
                            article.read = prefHelper.checkRead(number)
                            article.favorite = prefHelper.checkWhatIfFav(number)
                        }

                        if (prefHelper.fullOfflineWhatIf() && prefHelper.mayDownloadDataForOfflineMode(context)) {
                            if (number > previousNumberOfArticles) {
                                //TODO download new article here for offline mode
                            }
                        }
                    }
                }
                articleDao.insert(articles)
            }
        } catch (e: IOException) {
            Timber.e(e)
        }
    }

    //TODO Use retrofit for API
    override suspend fun getRedditThread(article: Article): Uri? = withContext(Dispatchers.IO) {
        Uri.parse("https://www.reddit.com" + JsonParser.getJSONFromUrl(
            "https://www.reddit.com/r/xkcd/search.json?q=${article.title}&restrict_sr=on"
        )
            .getJSONObject("data")
            .getJSONArray("children").getJSONObject(0).getJSONObject("data")
            .getString("permalink"))
    }

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
            if (!prefHelper.fullOfflineWhatIf()) {
                Jsoup.parse(okHttpClient.newCall(
                    Request.Builder()
                    .url("https://what-if.xkcd.com/$number")
                    .build()
                ).await().body?.string())
            } else {
                val dir = File(prefHelper.getOfflinePath(context).absolutePath
                        + OFFLINE_WHATIF_PATH + number)
                val file = File(dir, "$number.html")
                Jsoup.parse(file, "UTF-8")
            }
        }

        //append custom css
        doc.head().getElementsByTag("link").remove()
        if (themePrefs.amoledThemeEnabled()) {
            if (themePrefs.invertColors(false)) {
                doc.head().appendElement("link").attr("rel", "stylesheet").attr("type", "text/css")
                    .attr("href", "amoled_invert.css")
            } else {
                doc.head().appendElement("link").attr("rel", "stylesheet").attr("type", "text/css")
                    .attr("href", "amoled.css")
            }
        } else if (themePrefs.nightThemeEnabled()) {
            if (themePrefs.invertColors(false)) {
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
        val base = prefHelper.getOfflinePath(context).absolutePath
        for (e in doc.select(".illustration")) {
            if (!prefHelper.fullOfflineWhatIf()) {
                val src = e.attr("src")
                e.attr("src", "https://what-if.xkcd.com$src")
            } else {
                val path = "file://$base/what if/$number/$count.png"
                e.attr("src", path)
            }
            e.attr("onclick", "img.performClick(title);")
            count++
        }

        //fix footnotes and math scripts
        if (!prefHelper.fullOfflineWhatIf()) {
            doc.select("script[src]").first()
                .attr("src", "https://cdn.mathjax.org/mathjax/latest/MathJax.js")
        } else {
            doc.select("script[src]").first().attr("src", "MathJax.js")
        }

        //remove header, footer, nav buttons
        doc.getElementById("header-wrapper").remove()
        doc.select("nav").remove()
        doc.getElementById("footer-wrapper").remove()

        //remove title
        doc.select("h1").remove()

        val refs = doc.select(".ref").mapIndexed { n, element ->
            element.select(".refnum")
                .attr("onclick", "ref.performClick(\"${n}\")")
            element.select(".refbody").remove()

            element.select(".refbody").html()
        }

        return LoadedArticle(article, doc.html(), refs)
    }
}

@Module
@InstallIn(ViewModelComponent::class, SingletonComponent::class)
class ArticleRepositoryModule {
    @Provides
    fun provideArticleRepository(
        @ApplicationContext context: Context,
        prefHelper: PrefHelper,
        themePrefs: ThemePrefs,
        articleDao: ArticleDao,
        okHttpClient: OkHttpClient,
    ): ArticleRepository = ArticleRepositoryImpl(context, prefHelper, themePrefs, articleDao, okHttpClient)
}
