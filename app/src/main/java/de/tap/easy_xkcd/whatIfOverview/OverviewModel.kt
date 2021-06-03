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
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.*
import io.reactivex.rxjava3.schedulers.Schedulers
import io.realm.Case
import io.realm.Realm
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

interface OverviewModel {
    fun updateWhatIfDatabase(): Single<Boolean>

    fun updateWhatIfOfflineDatabase(): Observable<Int>

    fun numberOfOfflineArticlesNotDownloaded(): Int

    fun getArticles(hideRead: Boolean = false, onlyFavorites: Boolean = false, searchQuery: String? = null): List<Article>

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

    override fun getArticles(hideRead: Boolean, onlyFavorites: Boolean, searchQuery: String?): List<Article> {
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

    override fun updateWhatIfDatabase(): Single<Boolean> {
        return Single.fromCallable {
            val realm = Realm.getDefaultInstance()
            realm.beginTransaction()

            try {
                val document = Jsoup.parse(
                    JsonParser.getNewHttpClient().newCall(
                        Request.Builder()
                            .url("https://what-if.xkcd.com/archive/")
                            .build()
                    ).execute().body!!.string()
                )

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
                return@fromCallable false
            } finally {
                realm.commitTransaction()
                realm.close()
            }

            return@fromCallable true
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun updateWhatIfOfflineDatabase(): Observable<Int> {
        val realm = Realm.getDefaultInstance()
        val articlesToDownload = realm.copyFromRealm(
            realm.where(Article::class.java).equalTo("offline", false).findAll()
        )
        realm.close()

        if (articlesToDownload.isEmpty()) {
            return Observable.create { obj: ObservableEmitter<Int> -> obj.onComplete() }
        }

        val client = OkHttpClient()
        return Observable.fromIterable(articlesToDownload).flatMapSingle { article: Article? ->
            Article.downloadArticle(
                article,
                client,
                prefHelper
            )
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
        return Realm.getDefaultInstance().where(Article::class.java).equalTo("offline", false).findAll().size
    }

    override fun setAllRead() {
        databaseManager.setAllArticlesReadStatus(true)
    }

    override fun setAllUnread() {
        databaseManager.setAllArticlesReadStatus(false)
    }
}