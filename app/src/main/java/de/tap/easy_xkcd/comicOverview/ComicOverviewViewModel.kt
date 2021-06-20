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

@HiltViewModel
abstract class ComicBrowserBaseViewModel constructor(
    protected val model: ComicDatabaseModel,
    @ApplicationContext context: Context
) : ViewModel() {
    private val _comics = MutableLiveData<List<RealmComic>>()
    val comics: LiveData<List<RealmComic>> = _comics

    val prefHelper = PrefHelper(context)

    private val _bookmark = MutableLiveData<Int>()
    val bookmark: LiveData<Int> = _bookmark

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
    }

    fun setBookmark(number: Int) {
        prefHelper.bookmark = number
        _bookmark.value = number
    }

    fun toggleHideRead() {
        prefHelper.setHideRead(!prefHelper.hideRead())
        updateComicsToShow()
    }

    fun toggleOnlyFavorites() {
        prefHelper.setOverviewFav(!prefHelper.overviewFav())
        updateComicsToShow()
    }
}
