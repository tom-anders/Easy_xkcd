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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ComicOverviewViewModel @Inject constructor(
    private val repository: ComicRepository,
    @ApplicationContext context: Context,
) : ViewModelWithFlowHelper() {

//    val comics = MediatorLiveData<List<ComicContainer>>()

    val prefHelper = PrefHelper(context)

    private val _bookmark = MutableLiveData<Int>()
    val bookmark: LiveData<Int> = _bookmark

    //TODO can we also get a flow from preferences? https://github.com/tfcporciuncula/flow-preferences
    private val _overviewStyle = MutableStateFlow(prefHelper.overviewStyle)
    val overviewStyle: StateFlow<Int> = _overviewStyle

    private val _hideRead = MutableStateFlow(prefHelper.hideRead())
    val hideRead: StateFlow<Boolean> = _hideRead

    private val _onlyFavorites = MutableStateFlow(prefHelper.overviewFav())
    val onlyFavorites: StateFlow<Boolean> = _onlyFavorites

    val comics = combine(repository.favorites, repository.unreadComics, repository.comics,
                         _hideRead, _onlyFavorites) { favComics, unreadComic, allComics, hideRead, onlyFavs ->
        when {
            hideRead -> unreadComic
            onlyFavs -> favComics
            else -> allComics
        }
    }.asEagerStateFlow(emptyList())

    init {
        _bookmark.value = prefHelper.bookmark
    }

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
//        updateComicsToShow()
    }

    fun getNextUnreadComic(): Int? {
        return 0 //TODO add back
//        return unreadComics.value?.first { it.number > prefHelper.lastComic }?.number
    }

    fun toggleOnlyFavorites() {
        prefHelper.setOverviewFav(!prefHelper.overviewFav())
        _onlyFavorites.value = prefHelper.overviewFav()
//        updateComicsToShow()
    }
}
