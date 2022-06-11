package de.tap.easy_xkcd.comicOverview

import android.content.Context
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.database.comics.ComicRepository
import de.tap.easy_xkcd.utils.SharedPrefManager
import de.tap.easy_xkcd.utils.ViewModelWithFlowHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ComicOverviewViewModel @Inject constructor(
    private val repository: ComicRepository,
    @ApplicationContext context: Context,
) : ViewModelWithFlowHelper() {

    val sharedPrefs = SharedPrefManager(context)

    private val _bookmark = MutableStateFlow(
        if (sharedPrefs.bookmark != 0) sharedPrefs.bookmark else null
    )
    val bookmark: StateFlow<Int?> = _bookmark

    //TODO can we also get a flow from preferences? https://github.com/tfcporciuncula/flow-preferences
    //TODO Should be an enum so that we can get rid of the magic numbers
    private val _overviewStyle = MutableStateFlow(sharedPrefs.overviewStyle)
    val overviewStyle: StateFlow<Int> = _overviewStyle

    private val _hideRead = MutableStateFlow(sharedPrefs.hideReadComics)
    val hideRead: StateFlow<Boolean> = _hideRead

    private val _onlyFavorites = MutableStateFlow(sharedPrefs.showOnlyFavsInOverview)
    val onlyFavorites: StateFlow<Boolean> = _onlyFavorites

    fun downloadMissingOfflineBitmap(number: Int) {
        viewModelScope.launch {
            repository.downloadMissingOfflineBitmap(number)
        }
    }

    val comics = combine(repository.comics, _hideRead, _onlyFavorites) { newComics, hideRead, onlyFavs ->
        when {
            onlyFavs -> newComics.filter { it.comic?.favorite == true }
            hideRead -> newComics.filter { it.comic == null || !it.comic.read }
            else -> newComics.toMutableList()
        }
    }.asEagerStateFlow(emptyList())

    val comicCached = repository.comicCached

    fun getOfflineUri(number: Int) = repository.getOfflineUri(number)

    fun cacheComic(number: Int) = viewModelScope.launch { repository.cacheComic(number) }

    fun overviewStyleSelected(style: Int) {
        sharedPrefs.overviewStyle = style
        _overviewStyle.value = style
    }

    fun setBookmark(number: Int) {
        sharedPrefs.bookmark = number
        _bookmark.value = number
    }

    fun toggleHideRead() {
        sharedPrefs.hideReadComics = !sharedPrefs.hideReadComics
        _hideRead.value = sharedPrefs.hideReadComics
    }

    suspend fun getOldestUnread() = withContext(Dispatchers.IO) {
        repository.oldestUnreadComic()
    }

    // BUG: Entry won't be updated in overview because of some flow weirdness
    fun setRead(number: Int, read: Boolean) = viewModelScope.launch {
        repository.setRead(number, read)
    }

    fun toggleOnlyFavorites() {
        sharedPrefs.showOnlyFavsInOverview = !sharedPrefs.showOnlyFavsInOverview
        _onlyFavorites.value = sharedPrefs.showOnlyFavsInOverview
    }
}
