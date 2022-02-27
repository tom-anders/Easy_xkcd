package de.tap.easy_xkcd.search

import android.content.Context
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.database.comics.ComicContainer
import de.tap.easy_xkcd.database.comics.ComicRepository
import de.tap.easy_xkcd.database.comics.ProgressStatus
import de.tap.easy_xkcd.utils.ViewModelWithFlowHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ComicRepository,
    @ApplicationContext context: Context
) : ViewModelWithFlowHelper() {
    var progress = repository.cacheAllComics(cacheMissingTranscripts = true).asLazyStateFlow(
        ProgressStatus.ResetProgress)

    private var searchJob: Job? = null

    private var _query = MutableStateFlow("")
    var query: StateFlow<String> = _query

    fun setQuery(newQuery: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                var results = emptyList<ComicContainer>()
                _query.value = newQuery
                repository.searchComics(newQuery).collect { newResults ->
                    results = (results + newResults).distinctBy { it.number }
                    // Needed to allow the coroutine being canceled if there's a new query in the meantime
                    delay(1)

                    _results.value = results
                }
            }
        }
    }

    fun getOfflineUri(number: Int) = repository.getOfflineUri(number)

    val _results = MutableStateFlow<List<ComicContainer>>(emptyList())
    val results: StateFlow<List<ComicContainer>> = _results
}