package de.tap.easy_xkcd.database

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tap.xkcd_reader.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.utils.PrefHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import timber.log.Timber

//TODO Probably this doesn't even need to be a worker...
@HiltWorker
class OfflineModeDownloadWorker @AssistedInject constructor (
    @Assisted private val context: Context,
    @Assisted parameters: WorkerParameters,
    private val repository: ComicRepository,
    private val prefHelper: PrefHelper,
) : CoroutineWorker(context, parameters) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager

    companion object {
        const val channelId = "download"
        const val notificationId = 1
    }

    private lateinit var notification: NotificationCompat.Builder

    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel()
        }



        notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.resources.getString(R.string.loading_offline))
            .setProgress(1, 0, false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, context.getString(R.string.dialog_cancel),
                WorkManager.getInstance(context).createCancelPendingIntent(id))
        setForeground(ForegroundInfo(notificationId, notification.build()))
        notificationManager.notify(notificationId, notification.build())

        notification.setSilent(true)
        val collector = { status: ProgressStatus ->
            if (status is ProgressStatus.SetProgress) {
                notification.setProgress(status.max, status.value, false)
                notificationManager.notify(notificationId, notification.build())
            }
        }

        repository.cacheAllComics(cacheMissingTranscripts = false).collect(collector)

        repository.saveOfflineBitmaps.collect(collector)

        notificationManager.cancel(notificationId)
        prefHelper.setFullOffline(true)

        return Result.success()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                channelId,
                context.resources.getString(R.string.notification_channel_download),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

}