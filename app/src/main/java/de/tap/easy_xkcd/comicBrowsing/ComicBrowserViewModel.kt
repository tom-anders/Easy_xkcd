package de.tap.easy_xkcd.comicBrowsing

import android.content.Context
import android.net.Uri
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.database.comics.Comic
import de.tap.easy_xkcd.database.comics.ComicRepository
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.SingleLiveEvent
import de.tap.easy_xkcd.utils.ViewModelWithFlowHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.*
import javax.inject.Inject
import kotlin.random.Random

abstract class ComicBrowserBaseViewModel constructor(
    private val repository: ComicRepository,
    @ApplicationContext context: Context
) : ViewModelWithFlowHelper() {
    protected val prefHelper = PrefHelper(context)

    abstract fun comicSelected(index: Int)

    abstract fun selectComic(comicNumber: Int)

    suspend fun setBookmark() {
        selectedComicNumber.value?.let {
            withContext(Dispatchers.IO) {
                repository.setBookmark(it)
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            _selectedComicNumber.value?.let {
                repository.setFavorite(it, !repository.isFavorite(it))
            }
        }
    }

    fun getOfflineUri(number: Int) = repository.getOfflineUri(number)

    suspend fun getUriForSharing(number: Int) = repository.getUriForSharing(number)

    suspend fun getRedditThread() = selectedComic.value?.let { repository.getRedditThread(it) }

    suspend fun getTranscript(comic: Comic) = repository.getOrCacheTranscript(comic)

    protected val _selectedComicNumber = MutableStateFlow<Int?>(null)
    val selectedComicNumber: StateFlow<Int?> = _selectedComicNumber

    abstract val selectedComic: StateFlow<Comic?>
}

@HiltViewModel
class ComicBrowserViewModel @Inject constructor(
    private val repository: ComicRepository,
    @ApplicationContext context: Context
) : ComicBrowserBaseViewModel(repository, context) {

    val comics = repository.comics.asLazyStateFlow(emptyList())

    val comicCached = repository.comicCached

    val foundNewComic = repository.foundNewComic.receiveAsFlow()

    private var nextRandom: Int? = null

    private fun genNextRandom() =
        Random.nextInt(if (prefHelper.newest != 0) prefHelper.newest else 1 ).also {
            cacheComic(it)
        }

    private val _selectedComic = combine(selectedComicNumber, repository.comics) { selectedNumber, comics ->
        selectedNumber?.let {
            comics.getOrNull(selectedNumber - 1)?.comic
        } ?: run { null }
    }
    override val selectedComic = _selectedComic.asEagerStateFlow(null)

    val isFavorite = _selectedComic.map { comic ->
        comic?.favorite ?: false
    }.asEagerStateFlow(false)

    init {
        // Jump to the comic we displayed the last time the app was opened
        if (prefHelper.lastComic != 0) {
            _selectedComicNumber.value = prefHelper.lastComic
        }

        viewModelScope.launch {
            repository.newestComicNumber.collect { newest ->
                // This will only be true when the app is launched the very first time and the
                // newest comic is downloaded the first time. In this case, jump to the newest
                // comic by default.
                if (_selectedComicNumber.value == null && newest != 0) {
                    _selectedComicNumber.value = newest
                }

                if (newest != 0) nextRandom = genNextRandom()
            }
        }
    }

    fun cacheComic(number: Int) {
        viewModelScope.launch {
            repository.cacheComic(number)
        }
    }

    override fun comicSelected(index: Int) {
        val number = index + 1
        _selectedComicNumber.value = number
        prefHelper.lastComic = number

        viewModelScope.launch {
            repository.setRead(number, true)
        }
    }

    override fun selectComic(comicNumber: Int) {
        comicSelected(comicNumber - 1)
    }

    private var comicBeforeLastRandom: Int? = null
    fun getNextRandomComic(): Int? {
        comicBeforeLastRandom = _selectedComicNumber.value

        val random = nextRandom

        nextRandom = genNextRandom()

        return random
    }

    fun getPreviousRandomComic(): Int? {
        return comicBeforeLastRandom
    }

}

@HiltViewModel
class FavoriteComicsViewModel @Inject constructor(
    private val repository: ComicRepository,
    @ApplicationContext private val context: Context
) : ComicBrowserBaseViewModel(repository, context) {

    private val _importingFavorites = MutableLiveData(false)
    val importingFavorites: LiveData<Boolean> = _importingFavorites

    val favorites = repository.favorites.asLazyStateFlow(emptyList())

    val scrollToPage = SingleLiveEvent<Int>()

    private val _selectedComic = MutableStateFlow<Comic?>(null)
    override val selectedComic: StateFlow<Comic?> = _selectedComic

    override fun comicSelected(index: Int) {
        _selectedComicNumber.value = favorites.value.getOrNull(index)?.number
        _selectedComic.value = favorites.value.getOrNull(index)?.comic
    }

    override fun selectComic(comicNumber: Int) {
        _selectedComicNumber.value = comicNumber
        scrollToPage.value = favorites.value.indexOfFirst { it.number == comicNumber }
    }

    fun removeAllFavorites() {
        viewModelScope.launch {
            repository.removeAllFavorites()
        }
    }

    fun importFavorites(uri: Uri) {
        viewModelScope.launch {
            _importingFavorites.value = true
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { bufferedReader ->
                            var line: String
                            while (bufferedReader.readLine().also { line = it } != null) {
                                val numberTitle = line.split(" - ".toRegex()).toTypedArray()
                                val number = numberTitle[0].toInt()

                                repository.setFavorite(number, true)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
            _importingFavorites.value = false
        }
    }

    fun exportFavorites(uri: Uri): Boolean {
        //Export the full favorites list as text
        val sb = StringBuilder()
        val newline = System.getProperty("line.separator")
        for (fav in favorites.value) {
            sb.append(fav.number).append(" - ")
            sb.append(fav.comic?.title)
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

    fun getRandomFavoriteIndex() = Random.nextInt(favorites.value.size)
}
