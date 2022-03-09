package de.tap.easy_xkcd.settings

import android.content.Context
import androidx.lifecycle.*
import androidx.work.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.database.NewComicNotificationHandler
import de.tap.easy_xkcd.database.comics.ComicRepository
import de.tap.easy_xkcd.database.comics.OfflineModeDownloadWorker
import de.tap.easy_xkcd.database.whatif.ArticleRepository
import de.tap.easy_xkcd.database.whatif.WhatIfOfflineModeDownloadWorker
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.ViewModelWithFlowHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class OfflineAndNotificationViewModel @Inject constructor(
    private val comicRepository: ComicRepository,
    private val articleRepository: ArticleRepository,
    @ApplicationContext private val context: Context,
    private val prefHelper: PrefHelper,
    private val newComicNotificationHandler: NewComicNotificationHandler,
) : ViewModelWithFlowHelper() {

    companion object {
        const val offlineDownloadTag = "offlineDownload"
        const val whatifOfflineDownloadTag = "whatifOfflineDownload"
        const val moveOfflineDataTag = "moveOfflineData"
    }

    fun onNotificationIntervalChanged(newValue: String) =
        newComicNotificationHandler.changeNotificationIntervalTo(newValue.toLong())

    private val offlineDownloadActive =
        WorkManager.getInstance(context).getWorkInfosByTagLiveData(offlineDownloadTag)
            .asFlow()
            .map { workInfos ->
                workInfos.any { workInfo ->
                    workInfo.state == WorkInfo.State.ENQUEUED ||
                            workInfo.state == WorkInfo.State.RUNNING
                }
            }
            .asLazyStateFlow(initialValue = false)

    private val whatifOfflineDownloadActive =
        WorkManager.getInstance(context).getWorkInfosByTagLiveData(whatifOfflineDownloadTag)
            .asFlow()
            .map { workInfos ->
                workInfos.any { workInfo ->
                    workInfo.state == WorkInfo.State.ENQUEUED ||
                            workInfo.state == WorkInfo.State.RUNNING
                }
            }
            .asLazyStateFlow(initialValue = false)

    private val offlineRemovalActive = MutableStateFlow(false)

    private val whatifOfflineRemovalActive = MutableStateFlow(false)

    val disableOfflineModeButton = combine(offlineDownloadActive, offlineRemovalActive) {
        downloadActive, removalActive -> downloadActive || removalActive }

    fun onOfflineModeEnabled() {
        WorkManager.getInstance(context).enqueueUniqueWork(offlineDownloadTag, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequest.Builder(OfflineModeDownloadWorker::class.java)
                .addTag(offlineDownloadTag)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        )
    }

    fun onOfflineModeDisabled() {
        viewModelScope.launch {
            offlineRemovalActive.value = true
            comicRepository.removeOfflineBitmaps()
            offlineRemovalActive.value = false
            Timber.i("Removed offline bitmaps!")
        }
    }

    val disableWhatifOfflineModeButton = combine(whatifOfflineDownloadActive, whatifOfflineRemovalActive) {
            downloadActive, removalActive -> downloadActive || removalActive }

    fun onWhatIfOfflineModeEnabled() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            whatifOfflineDownloadTag, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequest.Builder(WhatIfOfflineModeDownloadWorker::class.java)
                .addTag(whatifOfflineDownloadTag)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        )
    }

    fun onWhatIfOfflineModeDisabled() {
        viewModelScope.launch {
            whatifOfflineRemovalActive.value = true
            articleRepository.deleteAllOfflineArticles()
            whatifOfflineRemovalActive.value = false
            Timber.i("Removed offline articles!")
        }
    }

    val moveOfflineDataInProgress =
        WorkManager.getInstance(context).getWorkInfosByTagLiveData(moveOfflineDataTag)
            .asFlow()
            .map { workInfos ->
                workInfos.any { workInfo ->
                    workInfo.state == WorkInfo.State.ENQUEUED ||
                            workInfo.state == WorkInfo.State.RUNNING
                }
            }
            .asLazyStateFlow(initialValue = false)

    fun onOfflinePathSelected(newValue: String) {
        val oldPath = prefHelper.getOfflinePath(context)
        val newPath = prefHelper.getOfflinePathForValue(context, newValue)

        WorkManager.getInstance(context).enqueueUniqueWork(
            moveOfflineDataTag, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequest.Builder(MoveOfflinePathWorker::class.java)
                .addTag(moveOfflineDataTag)
                .setInputData(Data.Builder()
                    .putString(MoveOfflinePathWorker.PARAM_OLD_PATH, oldPath.absolutePath)
                    .putString(MoveOfflinePathWorker.PARAM_NEW_PATH, newPath.absolutePath)
                    .build()
                )
                .build()
        )
    }
}