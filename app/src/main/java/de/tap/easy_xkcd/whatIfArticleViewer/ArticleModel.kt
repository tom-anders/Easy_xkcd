package de.tap.easy_xkcd.whatIfArticleViewer

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import de.tap.easy_xkcd.database.DatabaseManager
import de.tap.easy_xkcd.utils.Article
import de.tap.easy_xkcd.utils.JsonParser
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.ThemePrefs
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.realm.Realm
import org.jsoup.nodes.Document
import javax.inject.Inject
import kotlin.random.Random

interface ArticleModel {
    //    fun getLoadedArticle(): Article
    fun getTitle(): String

    fun isArticleFavorite(): Boolean

    fun toggleArticleFavorite()

    fun loadArticle(number: Int): Single<String>

    fun getRedditThread(): Single<String>

    fun getRef(index: String): String

    fun getNumber(): Int

    fun getRandomArticleNumber(): Int

    fun hasNextArticle(): Boolean

    fun hasPreviousArticle(): Boolean
}

@Module
//@InstallIn(ViewModelComponent::class)
@InstallIn(ViewModelComponent::class)
abstract class ArticleModelModule {
    @Binds
    abstract fun bindArticleModel(articleModelImpl: ArticleModelImpl): ArticleModel
}

//TODO Inject PrefHelper and ThemePrefs as Singleton via Hilt?!
class ArticleModelImpl @Inject constructor(@ApplicationContext context: Context) : ArticleModel {
    //class ArticleModelImpl @Inject constructor() : ArticleModel {
    private lateinit var loadedArticle: Article
    private lateinit var refs: ArrayList<String?>

    private var prefHelper: PrefHelper = PrefHelper(context)
    private var themePrefs: ThemePrefs = ThemePrefs(context)
    private var databaseManager = DatabaseManager(context)

    override fun loadArticle(number: Int): Single<String> {
        val realm = Realm.getDefaultInstance()
        loadedArticle = realm.copyFromRealm(
            realm.where(Article::class.java).equalTo("number", number).findFirst()
        )
        realm.beginTransaction()
        loadedArticle.isRead = true
        realm.copyToRealmOrUpdate(loadedArticle)
        realm.commitTransaction()
        realm.close()

        return Single.fromCallable {
            val doc = Article.generateDocument(number, prefHelper, themePrefs)

            refs = Article.generateRefs(doc)

            prefHelper.lastWhatIf = loadedArticle.number

            doc.html()
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun getRedditThread(): Single<String> {
        return Single.fromCallable {
            "https://www.reddit.com" + JsonParser.getJSONFromUrl(
                "https://www.reddit.com/r/xkcd/search.json?q=${loadedArticle.title}&restrict_sr=on"
            )
                .getJSONObject("data")
                .getJSONArray("children").getJSONObject(0).getJSONObject("data")
                .getString("permalink")
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
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
