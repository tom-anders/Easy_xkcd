package de.tap.easy_xkcd.comicBrowsing

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.database.RealmComic
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.SingleLiveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.min
import kotlin.random.Random

abstract class ComicBrowserBaseViewModel constructor(
    protected val model: ComicDatabaseModel,
    @ApplicationContext context: Context
) : ViewModel() {
    protected val prefHelper = PrefHelper(context)

    abstract fun comicSelected(index: Int)

    fun setBookmark() {
        selectedComic.value?.let {
            model.setBookmark(it.comicNumber)
        }
    }

    abstract fun toggleFavorite()

    protected val _selectedComic = MutableLiveData<RealmComic>()
    val selectedComic: LiveData<RealmComic> = _selectedComic
}

@HiltViewModel
class FavoriteComicsViewModel @Inject constructor(
    model: ComicDatabaseModel,
    @ApplicationContext context: Context
) : ComicBrowserBaseViewModel(model, context) {

    private val _favorites = MutableLiveData<List<RealmComic>>()
    val favorites: LiveData<List<RealmComic>> = _favorites

    val scrollToPage = SingleLiveEvent<Int>()

    private var currentIndex: Int = 0
        set(value) {
            _selectedComic.value = _favorites.value?.getOrNull(value)
            field = value
        }

    init {
        _favorites.value = model.getFavoriteComics()
        currentIndex = 0
    }

    override fun comicSelected(index: Int) {
        currentIndex = index
    }

    override fun toggleFavorite() {
        _selectedComic.value?.let { comic ->
            viewModelScope.launch {
                model.toggleFavorite(comic.comicNumber)
                _favorites.value = model.getFavoriteComics()

                currentIndex = min(currentIndex, _favorites.value!!.size - 1)
                comicSelected(currentIndex)
                scrollToPage.value = currentIndex
            }
        }
    }

    fun getRandomFavoriteIndex(): Int {
        _favorites.value?.let {
            return Random.nextInt(it.size)
        }
        return 0
    }

    fun removeAllFavorites() = model.removeAllFavorites()
}

@HiltViewModel
class ComicBrowserViewModel @Inject constructor(
    model: ComicDatabaseModel,
    @ApplicationContext context: Context
) : ComicBrowserBaseViewModel(model, context) {

    val comics = model.getAllComics()

    private val _isFavorite = MutableLiveData<Boolean>()
    val isFavorite: LiveData<Boolean> = _isFavorite

    init {
        _selectedComic.value = comics.getOrNull(if (prefHelper.lastComic != 0) {
            prefHelper.lastComic
        } else {
            prefHelper.newest
        } - 1)
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

    override fun toggleFavorite() {
        _selectedComic.value?.let { comic ->
            viewModelScope.launch {
                model.toggleFavorite(comic.comicNumber)
                _isFavorite.value = model.isFavorite(comic.comicNumber)
            }
        }
    }

    override fun comicSelected(index: Int) {
        val number = index + 1

        _selectedComic.value = getComic(number)
        prefHelper.lastComic = number
        _isFavorite.value = model.isFavorite(number)
        model.setRead(number, true)
    }
}