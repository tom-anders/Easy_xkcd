package de.tap.easy_xkcd.comicOverview

import android.content.Context
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.comicBrowsing.ComicDatabaseModel
import de.tap.easy_xkcd.database.Comic
import de.tap.easy_xkcd.database.ComicContainer
import de.tap.easy_xkcd.database.ComicRepository
import de.tap.easy_xkcd.database.RealmComic
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.ViewModelWithFlowHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
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
            hideRead -> newComics.filter { it.comic == null || !it.comic.read }
            onlyFavs -> newComics.filter { it.comic?.favorite == true }
            else -> newComics.toMutableList()
        }
    }.asEagerStateFlow(emptyList())

    val comicCached = repository.comicCached.receiveAsFlow()

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
        withContext(Dispatchers.IO) {
            repository.setRead(number, read)
        }
    }

    fun toggleOnlyFavorites() {
        prefHelper.setOverviewFav(!prefHelper.overviewFav())
        _onlyFavorites.value = prefHelper.overviewFav()
    }
}
