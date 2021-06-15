package de.tap.easy_xkcd.comicBrowsing

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.database.RealmComic
import de.tap.easy_xkcd.utils.PrefHelper
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

    private val _selectedComic = MutableLiveData<RealmComic>()
    val selectedComic: LiveData<RealmComic> = _selectedComic

    init {
        _selectedComic.value = comics.getOrNull(prefHelper.lastComic - 1)
    }

    private fun getComic(number: Int) = comics.getOrNull(number - 1)

    private var comicBeforeLastRandom: Int? = null
    fun getNextRandomComic(): Int {
        comicBeforeLastRandom = selectedComic.value?.comicNumber
        return model.getRandomComic()
    }

    fun getPreviousRandomComic(): Int? {
        return comicBeforeLastRandom
    }

    fun toggleFavorite() {
        _selectedComic.value?.let { comic ->
            model.toggleFavorite(comic.comicNumber)
            _isFavorite.value = model.isFavorite(comic.comicNumber)
        }
    }

    fun comicSelected(number: Int) {
        _selectedComic.value = getComic(number)
        prefHelper.lastComic = number
        _isFavorite.value = model.isFavorite(number)
        model.setRead(number, true)
    }
}