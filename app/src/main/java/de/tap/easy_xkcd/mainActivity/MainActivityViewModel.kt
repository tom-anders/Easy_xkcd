package de.tap.easy_xkcd.mainActivity

import android.app.Application
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import de.tap.easy_xkcd.comicBrowsing.ComicDatabaseModel
import de.tap.easy_xkcd.database.ComicRepository
import de.tap.easy_xkcd.database.ProgressStatus
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.SingleLiveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    app: Application,
    private val repository: ComicRepository,
    private val prefHelper: PrefHelper,
) : AndroidViewModel(app) {

    private val _progress: MutableLiveData<ProgressStatus> = MutableLiveData(ProgressStatus.Finished)
    var progress: LiveData<ProgressStatus> = _progress

    private val _initialized = MutableLiveData(false)
    val initialized: LiveData<Boolean> = _initialized

    init {
        viewModelScope.launch {
            repository.migrateRealmDatabase()
//            if (prefHelper.isOnline(app.applicationContext)) {
//                withContext(Dispatchers.IO) {
//                    repository.saveOfflineBitmaps().collect {
//                        _progress.postValue(it)
//                    }

//                    val newestComic = repository.findNewestComic()
//
//                    if (newestComic > prefHelper.newest) {
//                        prefHelper.setNewestComic(newestComic)
//
//                        //TODO this will also fire the very first time the app is started I think?
//                        foundNewComic.postValue(true)
//                    }
//                }
//            }

        }
        _initialized.value = true
    }
}