package de.tap.easy_xkcd.comicBrowsing

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tap.xkcd_reader.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.database.RealmComic
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.SingleLiveEvent
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComicsViewModel @Inject constructor(
    private val model: ComicsModel,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val prefHelper = PrefHelper(context)

    private val _comics = MutableLiveData<List<RealmComic>>()
    val comics: LiveData<List<RealmComic>> = _comics

    private val _favorites = MutableLiveData<List<RealmComic>>()
    val favorites: LiveData<List<RealmComic>> = _favorites

    private val _readComics = MutableLiveData<List<RealmComic>>()
    val readComics: LiveData<List<RealmComic>> = _readComics

    private val _progress: MutableLiveData<Int> = MutableLiveData()
    val progress: LiveData<Int> = _progress

    var progressMax: Int = 0
        private set

    val foundNewComic = SingleLiveEvent<Boolean>()

    init {
        viewModelScope.launch {
            if (prefHelper.isOnline(context)) {
                val newestComic = model.findNewestComic()
                progressMax = newestComic
                _progress.value = 0
                model.updateDatabase(newestComic) {
                    _progress.value?.let {
                        _progress.postValue(it + 1)
                    }
                }

                if (newestComic > prefHelper.newest) {
                    prefHelper.setNewestComic(newestComic)
                    foundNewComic.value = true
                }

            }

            _progress.value = null
        }
    }
}