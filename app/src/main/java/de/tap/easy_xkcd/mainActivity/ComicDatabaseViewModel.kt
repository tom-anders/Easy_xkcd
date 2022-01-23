package de.tap.easy_xkcd.mainActivity

import android.app.Application
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import de.tap.easy_xkcd.comicBrowsing.ComicDatabaseModel
import de.tap.easy_xkcd.database.Comic
import de.tap.easy_xkcd.database.ComicRepository
import de.tap.easy_xkcd.database.ProgressStatus
import de.tap.easy_xkcd.database.RealmComic
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.SingleLiveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ComicDatabaseViewModel @Inject constructor(
    app: Application,
    private val model: ComicDatabaseModel,
    private val repository: ComicRepository,
    private val prefHelper: PrefHelper,
) : AndroidViewModel(app) {

    private val _progress: MutableLiveData<ProgressStatus> = MutableLiveData(ProgressStatus.Finished)
    var progress: LiveData<ProgressStatus> = _progress

    var progressMax: Int = 0
        private set

    val foundNewComic = SingleLiveEvent<Boolean>()

    private val _databaseLoaded = MutableLiveData(false)
    val databaseLoaded: LiveData<Boolean> = _databaseLoaded

    init {
        viewModelScope.launch {
            if (prefHelper.isOnline(app.applicationContext)) {
                withContext(Dispatchers.IO) {
                    val newestComic = repository.findNewestComic()

                    progressMax = newestComic

//                    repository.migrateRealmDatabase().collect {
//                        _progress.postValue(it)
//                    }
                    //TODO remove, only for debugging here  
                    repository.cacheAllComics().collect {
                        _progress.postValue(it)
                    }

                    if (newestComic > prefHelper.newest) {
                        prefHelper.setNewestComic(newestComic)
                        foundNewComic.postValue(true)
                    }
                }
            }

            _progress.value = null

            _databaseLoaded.value = true
        }
    }
}