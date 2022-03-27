package de.tap.easy_xkcd.database.whatif

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tap.xkcd_reader.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.tap.easy_xkcd.database.BaseDownloadWorker
import de.tap.easy_xkcd.database.ProgressStatus
import de.tap.easy_xkcd.utils.PrefHelper
import kotlinx.coroutines.flow.collect

@HiltWorker
class WhatIfOfflineModeDownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted parameters: WorkerParameters,
    private val repository: ArticleRepository,
    private val prefHelper: PrefHelper,
) : BaseDownloadWorker(context, parameters, WhatIfOfflineModeDownloadWorker::class.hashCode()) {

    override suspend fun onDoWork(): Result {
        notification.setContentTitle(context.resources.getString(R.string.loading_offline_whatif))

        setForeground(ForegroundInfo(notificationId, notification.build()))
        notificationManager.notify(notificationId, notification.build())

        notification.setSilent(true)

        repository.downloadAllArticles.collect(progressCollector)
        repository.downloadArchiveImages.collect(progressCollector)

        notificationManager.cancel(notificationId)
        prefHelper.setFullOfflineWhatIf(true)

        return Result.success()
    }
}