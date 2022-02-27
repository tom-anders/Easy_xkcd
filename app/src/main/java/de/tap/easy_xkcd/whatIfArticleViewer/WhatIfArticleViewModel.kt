package de.tap.easy_xkcd.whatIfArticleViewer

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.*
import com.tap.xkcd_reader.R
import dagger.hilt.android.lifecycle.HiltViewModel
import de.tap.easy_xkcd.database.whatif.Article
import de.tap.easy_xkcd.database.whatif.ArticleRepository
import de.tap.easy_xkcd.database.whatif.LoadedArticle
import de.tap.easy_xkcd.utils.SingleLiveEvent
import de.tap.easy_xkcd.utils.ViewModelWithFlowHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class WhatIfArticleViewModel @Inject constructor(
    private val repository: ArticleRepository,
    savedStateHandle: SavedStateHandle
) : ViewModelWithFlowHelper() {

    private val _loadedArticle = MutableStateFlow(LoadedArticle.none())
    val loadedArticle: StateFlow<LoadedArticle> = _loadedArticle

    private val _loadingArticle = MutableStateFlow(false)
    val loadingArticle: StateFlow<Boolean> = _loadingArticle

    private val articles = repository.articles.asEagerStateFlow(emptyList())

    val hasNextArticle = combine(_loadedArticle, repository.articles) {
        loadedArticle, articles ->
        loadedArticle.number < articles.size
    }.asEagerStateFlow(false)

    val hasPreviousArticle = _loadedArticle.map {
        it.number > 1
    }.asEagerStateFlow(false)

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite

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
        _loadingArticle.value = true

        viewModelScope.launch {
            repository.loadArticle(number) ?.let {
                _loadedArticle.value = it
                _isFavorite.value = _loadedArticle.value.favorite
            }
            _loadingArticle.value = false
        }
    }

    fun showNextArticle() {
        loadArticle(loadedArticle.value.number + 1)
    }

    fun showPreviousArticle() {
        loadArticle(loadedArticle.value.number - 1)
    }

    fun shareArticle(): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "What if: " + loadedArticle.value.title)
            putExtra(Intent.EXTRA_TEXT, "https://what-if.xkcd.com/" + loadedArticle.value.number)
        }
    }

    fun openArticleInBrowser(): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://what-if.xkcd.com/${loadedArticle.value.number}"))
    }

    suspend fun getRedditThread() = loadedArticle.value.article.let { repository.getRedditThread(it) }

    fun toggleArticleFavorite() {
        viewModelScope.launch {
            val wasFavorite = loadedArticle.value.favorite
            _isFavorite.value = !wasFavorite
            repository.setFavorite(loadedArticle.value.number, !wasFavorite)
        }
    }

    fun showRandomArticle() {
        loadArticle(Random.nextInt(1, articles.value.size))
    }
}

