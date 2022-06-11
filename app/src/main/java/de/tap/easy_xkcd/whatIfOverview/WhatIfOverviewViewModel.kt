package de.tap.easy_xkcd.whatIfOverview

import android.content.Context
import androidx.lifecycle.*
import com.tap.xkcd_reader.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.database.ArticleRoomDatabase
import de.tap.easy_xkcd.database.whatif.ArticleRepository
import de.tap.easy_xkcd.database.whatif.Article
import de.tap.easy_xkcd.utils.SharedPrefManager
import de.tap.easy_xkcd.utils.ViewModelWithFlowHelper
import io.realm.RealmResults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.internal.wait
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class WhatIfOverviewViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: ArticleRepository,
    private val sharedPrefs: SharedPrefManager,
) : ViewModelWithFlowHelper() {
    private val _hideRead = MutableStateFlow(sharedPrefs.hideReadWhatif)
    val hideRead: StateFlow<Boolean> = _hideRead

    private val _onlyFavorites = MutableStateFlow(false)
    val onlyFavorites: StateFlow<Boolean> = _onlyFavorites

    fun toggleOnlyFavorites(): Boolean {
        _onlyFavorites.value = !_onlyFavorites.value
        return true
    }

    fun toggleHideRead(): Boolean {
        _hideRead.value = !_hideRead.value
        sharedPrefs.hideReadWhatif = _hideRead.value
        return true
    }

    private val _searchResults = MutableStateFlow(emptyList<Article>())
    fun setSearchQuery(newQuery: String) {
        if (newQuery.isEmpty()) {
            _searchResults.value = emptyList()
        } else {
            viewModelScope.launch {
                _searchResults.value = repository.searchArticles(newQuery)
            }
        }
    }

    val articles = combine(repository.articles, _hideRead, _onlyFavorites, _searchResults) {
        articles, hideRead, onlyFavorites, searchResults ->
        when {
            searchResults.isNotEmpty() -> searchResults
            onlyFavorites -> articles.filter { it.favorite }
            hideRead -> articles.filter { !it.read }
            else -> articles
        }.asReversed()
    }.asLazyStateFlow(emptyList())

    init {
        // TODO Decide what happens when we're not online.
        // Maybe display an What-if stickfigure character and a retry button below it?
        // Also, have a text that says one should try out offline mode next time

        viewModelScope.launch {
            repository.updateDatabase()
        }
    }

    fun toggleArticleFavorite(article: Article) {
        viewModelScope.launch {
            repository.setFavorite(article.number, !article.favorite)
        }
    }

    fun setAllArticlesRead(): Boolean {
        viewModelScope.launch {
            repository.setAllRead()
        }
        return true
    }

    fun setAllArticlesUnread(): Boolean {
        viewModelScope.launch {
            repository.setAllUnread()
        }
        return true
    }
}
