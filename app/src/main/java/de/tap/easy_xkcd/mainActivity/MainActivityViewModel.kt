package de.tap.easy_xkcd.mainActivity

import android.app.Application
import androidx.lifecycle.*
import androidx.work.*
import dagger.hilt.android.lifecycle.HiltViewModel
import de.tap.easy_xkcd.database.NewComicNotificationHandler
import de.tap.easy_xkcd.database.NewComicNotificationWorker
import de.tap.easy_xkcd.database.comics.ComicRepository
import de.tap.easy_xkcd.utils.PrefHelper
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    app: Application,
    private val repository: ComicRepository,
    private val newComicNotificationHandler: NewComicNotificationHandler,
) : AndroidViewModel(app) {

    fun onCreateWithNullSavedInstanceState() {
        viewModelScope.launch {
            repository.migrateRealmDatabase()
        }

        newComicNotificationHandler.initNotifications()
    }
}