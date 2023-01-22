package de.tap.easy_xkcd.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.bumptech.glide.request.target.AppWidgetTarget
import com.bumptech.glide.request.transition.Transition
import com.tap.xkcd_reader.R
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.GlideApp
import de.tap.easy_xkcd.database.comics.ComicRepository
import de.tap.easy_xkcd.utils.AppSettings
import de.tap.easy_xkcd.utils.SharedPrefManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class WidgetRandomProvider : AppWidgetProvider() {
    @Inject
    lateinit var sharedPrefManager: SharedPrefManager
    @Inject
    lateinit var settings: AppSettings

    @Inject
    lateinit var repository: ComicRepository

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_random_layout)
        remoteViews.setImageViewBitmap(R.id.ivComic, null)

        CoroutineScope(Dispatchers.Main).launch {
            repository.cacheComic(Random.nextInt(1, sharedPrefManager.newestComic))?.let { randomComic ->

                GlideApp.with(context)
                    .asBitmap()
                    .load(if (settings.fullOfflineEnabled) repository.getOfflineUri(randomComic.number) else randomComic.url)
                    .into(
                        object :
                            AppWidgetTarget(context, R.id.ivComic, remoteViews, *appWidgetIds) {
                            override fun onResourceReady(
                                resource: Bitmap,
                                transition: Transition<in Bitmap?>?
                            ) {
                                try {
                                    super.onResourceReady(resource, transition)
                                } catch (e: IllegalArgumentException) {
                                    Timber.e(e, "Loading image failed for ${randomComic.number}")
                                }
                            }
                        }
                    )

                remoteViews.setOnClickPendingIntent(
                    R.id.tvAlt,
                    PendingIntent.getBroadcast(
                        context,
                        0,
                        Intent(context, WidgetRandomProvider::class.java).apply {
                            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )

                remoteViews.setOnClickPendingIntent(
                    R.id.ivComic, PendingIntent.getActivity(
                        context, 1,
                        Intent("de.tap.easy_xkcd.ACTION_COMIC").apply {
                            putExtra("number", randomComic.number)
                            // We might have found a new comic, so make sure main app is updated
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )

                val titlePrefix =
                    if (settings.widgetShowComicNumber) "${randomComic.number}: " else ""
                remoteViews.setTextViewText(R.id.tvTitle, "${titlePrefix}${randomComic.title}")

                if (settings.widgetShowAlt) {
                    remoteViews.setViewVisibility(
                        R.id.tvAlt,
                        View.VISIBLE
                    )
                    remoteViews.setTextViewText(R.id.tvAlt, randomComic.altText)
                }

                appWidgetManager.updateAppWidget(appWidgetIds, remoteViews)
            }
        }
    }
}