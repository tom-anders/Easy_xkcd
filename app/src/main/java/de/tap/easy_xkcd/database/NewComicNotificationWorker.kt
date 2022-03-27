package de.tap.easy_xkcd.database

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.tap.xkcd_reader.BuildConfig
import com.tap.xkcd_reader.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import de.tap.easy_xkcd.database.comics.ComicRepository
import de.tap.easy_xkcd.database.comics.XkcdApi
import de.tap.easy_xkcd.explainXkcd.ExplainXkcdApi
import de.tap.easy_xkcd.utils.PrefHelper
import timber.log.Timber
import java.lang.Exception
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewComicNotificationHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefHelper: PrefHelper,
) {
    private companion object {
        const val TAG = "newComicNotification"
    }

    fun initNotifications() =
        updateNotificationInterval(
            prefHelper.notificationIntervalHours,
            ExistingPeriodicWorkPolicy.KEEP
        )

    fun changeNotificationIntervalTo(intervalHours: Long) =
        updateNotificationInterval(intervalHours, ExistingPeriodicWorkPolicy.REPLACE)

    private fun updateNotificationInterval(intervalHours: Long, existingPeriodicWorkPolicy: ExistingPeriodicWorkPolicy) {
        Timber.i("Update notification interval: ${intervalHours}h")
        WorkManager.getInstance(context).apply {
            if (intervalHours > 0) {
                enqueueUniquePeriodicWork(
                    TAG, existingPeriodicWorkPolicy,
                    PeriodicWorkRequestBuilder<NewComicNotificationWorker>(
                        intervalHours, TimeUnit.HOURS,
                    ).setConstraints(
                        Constraints.Builder()
                            .setRequiresBatteryNotLow(true)
                            .setRequiredNetworkType(NetworkType.CONNECTED).build()
                    ).build()
                )
            } else {
                cancelUniqueWork(TAG)
            }
        }
    }
}

@HiltWorker
class NewComicNotificationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted parameters: WorkerParameters,
    private val comicRepository: ComicRepository,
) : CoroutineWorker(context, parameters) {
    private companion object {
        const val CHANNEL_ID = "comic"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager

    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel()
        }

        comicRepository.updateNewestComic(postToChannel = false)?.let { newFoundComic ->
            notificationManager.notify(
                1, NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.resources.getString(R.string.new_comic))
                    .setContentText("${newFoundComic.number}: ${newFoundComic.title}")
                    .setAutoCancel(true)
                    .setContentIntent(
                        PendingIntent.getActivity(
                            context, 1,
                            Intent("de.tap.easy_xkcd.ACTION_COMIC").apply {
                                putExtra("number", newFoundComic.number)
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    .build()
            )
        }

        return Result.success()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.resources.getString(R.string.notification_channel_comic),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }
}