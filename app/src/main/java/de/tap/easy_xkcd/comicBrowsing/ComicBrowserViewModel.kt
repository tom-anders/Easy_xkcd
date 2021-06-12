package de.tap.easy_xkcd.comicBrowsing

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.utils.PrefHelper
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ComicBrowserViewModel @Inject constructor(
    private val model: ComicDatabaseModel,
    @ApplicationContext context: Context
) : ViewModel() {
    private val prefHelper = PrefHelper(context)

    val comics = model.getAllComics()

    private val _isFavorite = MutableLiveData<Boolean>()
    val isFavorite: LiveData<Boolean> = _isFavorite

    var selectedComic: Int = prefHelper.lastComic
        private set

    fun toggleFavorite() {
        model.toggleFavorite(selectedComic)
        _isFavorite.value = model.isFavorite(selectedComic)
    }

    fun comicSelected(number: Int) {
        selectedComic = number
        prefHelper.lastComic = number
        _isFavorite.value = model.isFavorite(number)
    }
}