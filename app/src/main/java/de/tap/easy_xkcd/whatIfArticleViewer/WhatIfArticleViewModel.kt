package de.tap.easy_xkcd.whatIfArticleViewer

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.*
import com.tap.xkcd_reader.R
import dagger.hilt.android.lifecycle.HiltViewModel
import de.tap.easy_xkcd.utils.JsonParser
import de.tap.easy_xkcd.utils.SingleLiveEvent
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.jsoup.Jsoup
import timber.log.Timber
import java.lang.ClassCastException
import java.util.*
import javax.inject.Inject

@HiltViewModel
class WhatIfArticleViewModel @Inject constructor(
    private val model: ArticleModel,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val title: MutableLiveData<String> = MutableLiveData()
    fun getTitle(): LiveData<String> = title

    private val articleHtml: MutableLiveData<String> = MutableLiveData()
    fun getArticleHtml(): LiveData<String> = articleHtml

    //TODO Maybe the model should have a livedata instead as well?
    private val hasNextArticle: MutableLiveData<Boolean> = MutableLiveData()
    fun hasNextArticle(): LiveData<Boolean> {
        return hasNextArticle
    }

    private val hasPreviousArticle: MutableLiveData<Boolean> = MutableLiveData()
    fun hasPreviousArticle(): LiveData<Boolean> {
        return hasPreviousArticle
    }

    private val isFavorite: MutableLiveData<Boolean> = MutableLiveData()
    fun isFavorite(): LiveData<Boolean> = isFavorite

    // If null, means that there's no progress to be shown.
    // If non-null, corresponds to the resource id of the progress to show
    private val progressTextId: MutableLiveData<Int> = MutableLiveData()
    fun progressTextId(): LiveData<Int> = progressTextId

    init {
        try {
            savedStateHandle.get<Int>(WhatIfActivity.INTENT_NUMBER)?.let {
                Timber.d("Loading article %d", it)
                loadArticle(it)
            }
        } catch (e: ClassCastException) {
            Timber.e(e)
        }
    }

    private fun loadArticle(number: Int) {
        model.loadArticle(number)
            .doOnSubscribe { progressTextId.value = R.string.loading_article }
            .doFinally { progressTextId.value = null }
            .subscribe({
                articleHtml.value = it
                title.value = model.getTitle()

                hasPreviousArticle.value = model.hasPreviousArticle()
                hasNextArticle.value = model.hasNextArticle()
                isFavorite.value = model.isArticleFavorite()
            }, {
                Timber.e(it)
            })
    }

    fun showNextArticle() {
        loadArticle(model.getNumber() + 1)
    }

    fun showPreviousArticle() {
        loadArticle(model.getNumber() - 1)
    }


    fun getRef(index: String): String {
        //TODO Handle the image ref no.2 for number 141...

        return model.getRef(index)
    }

    fun shareArticle(): Intent {
        val share = Intent(Intent.ACTION_SEND)
        share.type = "text/plain"
        share.putExtra(Intent.EXTRA_SUBJECT, "What if: " + model.getTitle())
        share.putExtra(Intent.EXTRA_TEXT, "https://what-if.xkcd.com/" + model.getNumber())
        return share
    }

    fun openArticleInBrowser(): Intent
            = Intent(Intent.ACTION_VIEW, Uri.parse("https://what-if.xkcd.com/" + model.getNumber()))

    val openRedditThreadEvent = SingleLiveEvent<String>()
    fun openRedditThread() {
        model.getRedditThread()
            .doOnSubscribe { progressTextId.value = R.string.loading_thread }
            .doFinally { progressTextId.value = null }
            .subscribe({
                openRedditThreadEvent.value = it.replace("www", "m")
            }, {
                openRedditThreadEvent.value = ""
                Timber.e(it)
            })
    }

    fun toggleArticleFavorite() {
        model.toggleArticleFavorite()
        isFavorite.value = model.isArticleFavorite()
    }

    fun showRandomArticle() {
        loadArticle(model.getRandomArticleNumber())
    }
}

