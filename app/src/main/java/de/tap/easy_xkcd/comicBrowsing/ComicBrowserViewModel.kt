package de.tap.easy_xkcd.comicBrowsing

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nbsp.materialfilepicker.ui.FilePickerActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.database.RealmComic
import de.tap.easy_xkcd.notifications.ComicNotifierJob
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.SingleLiveEvent
import hilt_aggregated_deps._de_tap_easy_xkcd_comicBrowsing_ComicBrowserBaseFragment_GeneratedInjector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.*
import java.util.*
import javax.inject.Inject
import kotlin.math.min
import kotlin.random.Random

abstract class ComicBrowserBaseViewModel constructor(
    protected val model: ComicDatabaseModel,
    @ApplicationContext context: Context
) : ViewModel() {
    protected val prefHelper = PrefHelper(context)

    abstract fun comicSelected(index: Int)

    abstract fun jumpToComic(comicNumber: Int)

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
    @ApplicationContext private val context: Context
) : ComicBrowserBaseViewModel(model, context) {

    private val _favorites = MutableLiveData<List<RealmComic>>()
    val favorites: LiveData<List<RealmComic>> = _favorites

    val scrollToPage = SingleLiveEvent<Int>()

    private val _importingFavorites = MutableLiveData(false)
    val importingFavorites: LiveData<Boolean> = _importingFavorites

    private var currentIndex: Int = 0
        set(value) {
            // Negative value might happen in theory when jumpToComic is called for a
            // comic number that is not actually a favorite
            if (value >= 0) {
                _selectedComic.value = _favorites.value?.getOrNull(value)
                field = value
            }
        }

    init {
        _favorites.value = model.getFavoriteComics()
        currentIndex = 0
    }

    override fun jumpToComic(comicNumber: Int) {
        favorites.value?.indexOfFirst { it.comicNumber == comicNumber }?.let { currentIndex = it }
        scrollToPage.value = currentIndex
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

    fun removeAllFavorites() {
        model.removeAllFavorites()

        _favorites.value = model.getFavoriteComics()
        currentIndex = 0
    }

    fun importFavorites(uri: Uri) {
        viewModelScope.launch {
            _importingFavorites.value = true
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { bufferedReader ->
                            var line: String
                            val newFavorites = Stack<Int>()
                            while (bufferedReader.readLine().also { line = it } != null) {
                                val numberTitle = line.split(" - ".toRegex()).toTypedArray()
                                val number = numberTitle[0].toInt()

                                if (!model.isFavorite(number)) {
                                    model.toggleFavorite(number)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
            _importingFavorites.value = false

            _favorites.value = model.getFavoriteComics()
            currentIndex = 0
        }
    }

    fun exportFavorites(uri: Uri): Boolean {
        if (favorites.value == null) return false

        //Export the full favorites list as text
        val sb = StringBuilder()
        val newline = System.getProperty("line.separator")
        for (fav in favorites.value!!) {
            sb.append(fav.comicNumber).append(" - ")
            sb.append(fav.title)
            sb.append(newline)
        }
        try {
            context.contentResolver.openFileDescriptor(uri, "w")?.use {
                FileOutputStream(it.fileDescriptor).use { stream ->
                    stream.write(sb.toString().toByteArray())
                }
            }
        } catch (e: IOException) {
            Timber.e(e)
            return false
        }
        return true
    }
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

    override fun jumpToComic(comicNumber: Int) = comicSelected(comicNumber - 1)

    override fun comicSelected(index: Int) {
        val number = index + 1

        _selectedComic.value = getComic(number)
        prefHelper.lastComic = number
        _isFavorite.value = model.isFavorite(number)
        model.setRead(number, true)
    }
}