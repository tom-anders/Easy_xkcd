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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ComicOverviewViewModel @Inject constructor(
    private val repository: ComicRepository,
    @ApplicationContext context: Context,
) : ViewModel() {

    val comics = MediatorLiveData<List<ComicContainer>>()

    val prefHelper = PrefHelper(context)

    private val _bookmark = MutableLiveData<Int>()
    val bookmark: LiveData<Int> = _bookmark

    private val _overviewStyle = MutableLiveData<Int>()
    val overviewStyle: LiveData<Int> = _overviewStyle

    private val _hideRead = MutableLiveData(prefHelper.hideRead())
    val hideRead: LiveData<Boolean> = _hideRead

    private val favorites = repository.favorites.asLiveData()
    private val unreadComics = repository.unreadComics.asLiveData()
    private val allComics = repository.comics.asLiveData()

    private fun updateComicsToShow() {
        comics.removeSource(favorites)
        comics.removeSource(unreadComics)
        comics.removeSource(allComics)

        comics.addSource(when {
            prefHelper.overviewFav() -> favorites
            prefHelper.hideRead() -> unreadComics
            else -> allComics
        }) { comics.value = it }
    }

    private val _onlyFavorites = MutableLiveData(prefHelper.overviewFav())
    val onlyFavorites: LiveData<Boolean> = _onlyFavorites

    init {
        _bookmark.value = prefHelper.bookmark

        _overviewStyle.value = prefHelper.overviewStyle

        updateComicsToShow()
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
        updateComicsToShow()
    }

    fun getNextUnreadComic(): Int? {
        return unreadComics.value?.first { it.number > prefHelper.lastComic }?.number
    }

    fun toggleOnlyFavorites() {
        prefHelper.setOverviewFav(!prefHelper.overviewFav())
        _onlyFavorites.value = prefHelper.overviewFav()
        updateComicsToShow()
    }
}
