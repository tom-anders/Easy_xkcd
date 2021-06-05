package de.tap.easy_xkcd.whatIfOverview

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.tap.xkcd_reader.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.utils.Article
import de.tap.easy_xkcd.utils.PrefHelper
import io.realm.RealmResults
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class WhatIfOverviewViewModel @Inject constructor(
    private val model: OverviewModel,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context
) : ViewModel() {
    private val _articles: MutableLiveData<List<Article>> = MutableLiveData()
    val articles: LiveData<List<Article>> = _articles

    val prefHelper = PrefHelper(context)

    // If null, means that there's no progress to be shown.
    // If non-null, corresponds to the resource id of the progress to show
    private val _progressTextId: MutableLiveData<Int> = MutableLiveData()
    val progressTextId: LiveData<Int> = _progressTextId

    private val _progress: MutableLiveData<Int> = MutableLiveData()
    val progress: MutableLiveData<Int> = _progress

    private val _progressMax: MutableLiveData<Int> = MutableLiveData()
    val progressMax: MutableLiveData<Int> = _progressMax

    init {
        Timber.d("Created!")
        // TODO Decide what happens when we're not online.
        // Maybe display an What-if stickfigure character and a retry button below it?
        // Also, have a text that says one should try out offline mode next time

        if (prefHelper.isOnline(context)
            && (!prefHelper.fullOfflineWhatIf() || prefHelper.mayDownloadDataForOfflineMode(context))
        ) {
            model.updateWhatIfDatabase()
                .doOnSubscribe { _progressTextId.value = R.string.loading_articles }
                .subscribe({
                    if (prefHelper.fullOfflineWhatIf()) {
                        _progressMax.value = model.numberOfOfflineArticlesNotDownloaded()
                        _progress.value = 0
                        model.updateWhatIfOfflineDatabase()
                            .doOnNext {
                                _progress.value = _progress.value?.inc()
                                Timber.d("value is now ${_progress.value}")
                            }
                            .doFinally {
                                updateArticleData()
                            }
                            .subscribe({}, {
                                Timber.e(it)
                            })
                    } else {
                        updateArticleData()
                    }
                }, {
                    Timber.e(it)

                    updateArticleData()
                })
        } else {
            updateArticleData()
        }
    }

    fun updateArticleData(searchQuery: String? = null) {
        _progressTextId.value = null
        _progressMax.value = null
        _progress.value = null

        // Reverse because we want to display newest articles at the top
        _articles.value = model.getArticles(
            hideRead = _hideRead.value!!,
            onlyFavorites = _onlyFavorites.value!!,
            searchQuery = searchQuery
        ).reversed()
    }

    fun toggleArticleFavorite(article: Article) {
        model.toggleArticleFavorite(article)
        updateArticleData()
    }

    private val _onlyFavorites = MutableLiveData(false)
    val onlyFavorites: LiveData<Boolean> = _onlyFavorites
    fun toggleOnlyFavorites(): Boolean {
        _onlyFavorites.value = !_onlyFavorites.value!!
        updateArticleData()
        return true
    }

    private val _hideRead = MutableLiveData(prefHelper.hideReadWhatIf())
    val hideRead: LiveData<Boolean> = _hideRead
    fun toggleHideRead(): Boolean {
        _hideRead.value = !_hideRead.value!!
        prefHelper.setHideReadWhatIf(_hideRead.value!!)

        updateArticleData()
        return true
    }

    fun setAllArticlesRead(): Boolean {
        model.setAllRead()
        updateArticleData()
        return true
    }

    fun setAllArticlesUnread(): Boolean {
        model.setAllUnread()
        updateArticleData()
        return true
    }
}
