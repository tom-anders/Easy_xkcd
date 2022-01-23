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

    val comics = combine(repository.favorites, repository.unreadComics, repository.comics,
                         _hideRead, _onlyFavorites) { favComics, unreadComics, allComics, hideRead, onlyFavs ->
        Timber.d("diff NEW!! ${allComics[0].comic?.read} ${unreadComics.size}")
        when {
            hideRead -> unreadComics
            onlyFavs -> favComics
            else -> allComics
        }
    }.asEagerStateFlow(emptyList())

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
