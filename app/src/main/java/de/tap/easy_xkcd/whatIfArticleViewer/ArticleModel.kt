package de.tap.easy_xkcd.whatIfArticleViewer

import android.content.Context
import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.database.DatabaseManager
import de.tap.easy_xkcd.utils.Article
import de.tap.easy_xkcd.utils.JsonParser
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.ThemePrefs
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ru.gildor.coroutines.okhttp.await
import java.io.File
import javax.inject.Inject
import kotlin.random.Random

interface ArticleModel {
    //    fun getLoadedArticle(): Article
    fun getTitle(): String

    fun isArticleFavorite(): Boolean

    fun toggleArticleFavorite()

    suspend fun loadArticle(number: Int): String

    suspend fun getRedditThread(): String

    fun getRef(index: String): String

    fun getNumber(): Int

    fun getRandomArticleNumber(): Int

    fun hasNextArticle(): Boolean

    fun hasPreviousArticle(): Boolean
}

@Module
@InstallIn(ViewModelComponent::class)
abstract class ArticleModelModule {
    @Binds
    abstract fun bindArticleModel(articleModelImpl: ArticleModelImpl): ArticleModel
}

//TODO Inject PrefHelper and ThemePrefs as Singleton via Hilt?!
class ArticleModelImpl @Inject constructor(@ApplicationContext private val context: Context) : ArticleModel {
    private lateinit var loadedArticle: Article
    private lateinit var refs: ArrayList<String?>

    private val OFFLINE_WHATIF_PATH = "/what if/"

    private var prefHelper: PrefHelper = PrefHelper(context)
    private var themePrefs: ThemePrefs = ThemePrefs(context)
    private var databaseManager = DatabaseManager(context)

    override suspend fun loadArticle(number: Int): String {
        val realm = Realm.getDefaultInstance()
        loadedArticle = realm.copyFromRealm(
            realm.where(Article::class.java).equalTo("number", number).findFirst()
        )
        realm.beginTransaction()
        loadedArticle.isRead = true
        realm.copyToRealmOrUpdate(loadedArticle)
        realm.commitTransaction()
        realm.close()

        val doc = generateDocument(number, prefHelper, themePrefs)

        refs = Article.generateRefs(doc)

        prefHelper.lastWhatIf = loadedArticle.number

        return doc.html()
    }

    private suspend fun generateDocument(number: Int, prefHelper: PrefHelper, themePrefs: ThemePrefs): Document {
        val doc = withContext(Dispatchers.IO) {
            if (!prefHelper.fullOfflineWhatIf()) {
                val okHttpClient = JsonParser.getNewHttpClient()
                val request = Request.Builder()
                    .url("https://what-if.xkcd.com/$number")
                    .build()
                val response = okHttpClient.newCall(request).await()
                val body = response.body!!.string()
                Jsoup.parse(body)
                //doc = Jsoup.connect("http://what-if.xkcd.com/" + String.valueOf(mNumber)).get();
            } else {
                val sdCard = prefHelper.getOfflinePath(context)
                val dir = File(sdCard.absolutePath + OFFLINE_WHATIF_PATH + number)
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
        return doc
    }

    override suspend fun getRedditThread(): String {
        return withContext(Dispatchers.IO) {
            "https://www.reddit.com" + JsonParser.getJSONFromUrl(
                "https://www.reddit.com/r/xkcd/search.json?q=${loadedArticle.title}&restrict_sr=on"
            )
                .getJSONObject("data")
                .getJSONArray("children").getJSONObject(0).getJSONObject("data")
                .getString("permalink")
        }
    }

    override fun getTitle(): String {
        return loadedArticle.title
    }

    override fun isArticleFavorite(): Boolean {
        return loadedArticle.isFavorite
    }

    override fun getNumber(): Int {
        return loadedArticle.number
    }

    override fun hasPreviousArticle(): Boolean {
        return loadedArticle.number > 1
    }

    override fun hasNextArticle(): Boolean {
        return loadedArticle.number < databaseManager.numberOfArticles
    }

    override fun getRandomArticleNumber(): Int {
        return Random.nextInt(databaseManager.numberOfArticles) + 1
    }

    override fun toggleArticleFavorite() {
        loadedArticle.isFavorite = !loadedArticle.isFavorite
        val realm = Realm.getDefaultInstance()
        realm.executeTransaction {
            realm.copyToRealmOrUpdate(loadedArticle)
        }
        realm.close()
    }

    override fun getRef(index: String): String {
        //TODO Convert Article.java to Kotlin, then we can get rid of this assert
        return refs[index.toInt()]!!
    }

}
