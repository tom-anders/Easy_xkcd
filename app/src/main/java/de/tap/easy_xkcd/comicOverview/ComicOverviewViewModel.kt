package de.tap.easy_xkcd.comicOverview

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.comicBrowsing.ComicDatabaseModel
import de.tap.easy_xkcd.database.RealmComic
import de.tap.easy_xkcd.utils.PrefHelper
import javax.inject.Inject

@HiltViewModel
class ComicOverviewViewModel @Inject constructor(
    private val model: ComicDatabaseModel,
    @ApplicationContext context: Context
) : ViewModel() {
    private val _comics = MutableLiveData<List<RealmComic>>()
    val comics: LiveData<List<RealmComic>> = _comics

    val prefHelper = PrefHelper(context)

    private val _bookmark = MutableLiveData<Int>()
    val bookmark: LiveData<Int> = _bookmark

    private val _overviewStyle = MutableLiveData<Int>()
    val overviewStyle: LiveData<Int> = _overviewStyle

    private fun updateComicsToShow() {
        _comics.value = when {
            prefHelper.overviewFav() -> model.getFavoriteComics()
            prefHelper.hideRead() -> model.getUnreadComics()
            else -> model.getAllComics()
        }.reversed()
    }

    init {
        updateComicsToShow()
        _bookmark.value = prefHelper.bookmark

        _overviewStyle.value = prefHelper.overviewStyle
    }

    fun overviewStyleSelected(style: Int) {
        prefHelper.overviewStyle = style
        _overviewStyle.value = style
    }

    fun setBookmark(number: Int) {
        prefHelper.bookmark = number
        _bookmark.value = number
    }

    private val _hideRead = MutableLiveData(prefHelper.hideRead())
    val hideRead: LiveData<Boolean> = _hideRead
    fun toggleHideRead() {
        prefHelper.setHideRead(!prefHelper.hideRead())
        updateComicsToShow()
        _hideRead.value = prefHelper.hideRead()
    }

    fun getNextUnreadComic(): Int? {
        val unreadComics = model.getUnreadComics()
        if (unreadComics.isNotEmpty()) {
            return unreadComics.find { it.comicNumber > prefHelper.lastComic }?.comicNumber
        }
        return 0
    }

    private val _onlyFavorites = MutableLiveData(prefHelper.overviewFav())
    val onlyFavorites: LiveData<Boolean> = _onlyFavorites
    fun toggleOnlyFavorites() {
        prefHelper.setOverviewFav(!prefHelper.overviewFav())
        updateComicsToShow()
        _onlyFavorites.value = prefHelper.overviewFav()
    }
}
