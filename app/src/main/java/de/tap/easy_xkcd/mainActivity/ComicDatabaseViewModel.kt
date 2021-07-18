package de.tap.easy_xkcd.mainActivity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.tap.easy_xkcd.comicBrowsing.ComicDatabaseModel
import de.tap.easy_xkcd.database.RealmComic
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.SingleLiveEvent
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ComicDatabaseViewModel @Inject constructor(
    app: Application,
    private val model: ComicDatabaseModel,
    private val prefHelper: PrefHelper,
) : AndroidViewModel(app) {

    private val _progress: MutableLiveData<Int> = MutableLiveData()
    val progress: LiveData<Int> = _progress

    var progressMax: Int = 0
        private set

    val foundNewComic = SingleLiveEvent<Boolean>()

    private val _databaseLoaded = MutableLiveData(false)
    val databaseLoaded: LiveData<Boolean> = _databaseLoaded

    init {
        viewModelScope.launch {
            if (prefHelper.isOnline(app.applicationContext)) {
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

            _databaseLoaded.value = true
        }
    }

    suspend fun getUriForSharing(comic: RealmComic) = model.getUriForSharing(comic)

    suspend fun getRedditThread(comic: RealmComic) = model.getRedditThread(comic)
}