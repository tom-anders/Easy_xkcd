package de.tap.easy_xkcd.mainActivity

import android.app.Application
import android.text.Html
import androidx.core.text.HtmlCompat
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import de.tap.easy_xkcd.comicBrowsing.ComicDatabaseModel
import de.tap.easy_xkcd.database.ComicRepository
import de.tap.easy_xkcd.database.ProgressStatus
import de.tap.easy_xkcd.explainXkcd.ExplainXkcdApi
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.SingleLiveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
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