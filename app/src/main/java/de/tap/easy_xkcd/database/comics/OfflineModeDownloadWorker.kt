package de.tap.easy_xkcd.database.comics

import android.app.NotificationManager
import android.content.Context
import android.os.Build
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

@HiltWorker
class OfflineModeDownloadWorker @AssistedInject constructor (
    @Assisted private val context: Context,
    @Assisted parameters: WorkerParameters,
    private val repository: ComicRepository,
    private val prefHelper: PrefHelper,
) : BaseDownloadWorker(context, parameters, OfflineModeDownloadWorker::class.hashCode()) {

    override suspend fun onDoWork(): Result {
        notification.setContentTitle(context.resources.getString(R.string.loading_offline))

        setForeground(ForegroundInfo(notificationId, notification.build()))
        notificationManager.notify(notificationId, notification.build())

        notification.setSilent(true)

        repository.cacheAllComics(cacheMissingTranscripts = false).collect(progressCollector)

        repository.saveOfflineBitmaps.collect(progressCollector)

        notificationManager.cancel(notificationId)
        prefHelper.setFullOffline(true)

        return Result.success()
    }
}