package de.tap.easy_xkcd.comicOverview

import android.content.Context
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.database.comics.ComicRepository
import de.tap.easy_xkcd.utils.PrefHelper
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

    val prefHelper = PrefHelper(context)

    private val _bookmark = MutableStateFlow(
        if (prefHelper.bookmark != 0) prefHelper.bookmark else null
    )
    val bookmark: StateFlow<Int?> = _bookmark

    //TODO can we also get a flow from preferences? https://github.com/tfcporciuncula/flow-preferences
    private val _overviewStyle = MutableStateFlow(prefHelper.overviewStyle)
    val overviewStyle: StateFlow<Int> = _overviewStyle

    private val _hideRead = MutableStateFlow(prefHelper.hideRead())
    val hideRead: StateFlow<Boolean> = _hideRead

    private val _onlyFavorites = MutableStateFlow(prefHelper.overviewFav())
    val onlyFavorites: StateFlow<Boolean> = _onlyFavorites

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
        prefHelper.overviewStyle = style
        _overviewStyle.value = style
    }

    fun setBookmark(number: Int) {
        prefHelper.bookmark = number
        _bookmark.value = number
    }

    fun toggleHideRead() {
        prefHelper.setHideRead(!prefHelper.hideRead())
        _hideRead.value = prefHelper.hideRead()
    }

    suspend fun getOldestUnread() = withContext(Dispatchers.IO) {
        repository.oldestUnreadComic()
    }

    // BUG: Entry won't be updated in overview because of some flow weirdness
    fun setRead(number: Int, read: Boolean) = viewModelScope.launch {
        repository.setRead(number, read)
    }

    fun toggleOnlyFavorites() {
        prefHelper.setOverviewFav(!prefHelper.overviewFav())
        _onlyFavorites.value = prefHelper.overviewFav()
    }
}
