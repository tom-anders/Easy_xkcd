package de.tap.easy_xkcd.mainActivity

import android.app.Application
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import de.tap.easy_xkcd.database.comics.ComicRepository
import de.tap.easy_xkcd.utils.PrefHelper
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    app: Application,
    private val repository: ComicRepository,
    private val prefHelper: PrefHelper,
) : AndroidViewModel(app) {

    init {
        viewModelScope.launch {
            repository.migrateRealmDatabase()
        }
    }
}