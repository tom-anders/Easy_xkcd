package de.tap.easy_xkcd.database

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tap.xkcd_reader.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.tap.easy_xkcd.database.comics.OfflineModeDownloadWorker


abstract class BaseDownloadWorker constructor(
    private val context: Context,
    parameters: WorkerParameters,
    protected val notificationId: Int,
) : CoroutineWorker(context, parameters) {
    protected val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager

    protected var notification = NotificationCompat.Builder(context, OFFLINE_DOWNLOAD_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setProgress(1, 0, false)
        .setOngoing(true)
        .addAction(android.R.drawable.ic_delete, context.getString(R.string.dialog_cancel),
            WorkManager.getInstance(context).createCancelPendingIntent(id))

    private companion object {
        const val OFFLINE_DOWNLOAD_CHANNEL_ID = "download"
    }

    final override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createDownloadChannel()
        }
        return onDoWork()
    }

    protected val progressCollector = { status: ProgressStatus ->
        if (status is ProgressStatus.SetProgress) {
            notification.setProgress(status.max, status.value, false)
            notificationManager.notify(notificationId, notification.build())
        }
    }

    abstract suspend fun onDoWork(): Result

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createDownloadChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                OFFLINE_DOWNLOAD_CHANNEL_ID,
                context.resources.getString(R.string.notification_channel_download),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }
}

