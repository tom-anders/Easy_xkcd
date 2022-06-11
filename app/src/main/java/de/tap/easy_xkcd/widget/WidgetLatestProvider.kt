package de.tap.easy_xkcd.widget

import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetManager
import android.widget.RemoteViews
import com.tap.xkcd_reader.R
import android.content.Intent
import android.app.PendingIntent
import de.tap.easy_xkcd.database.RealmComic
import de.tap.easy_xkcd.database.DatabaseManager
import timber.log.Timber
import android.content.ComponentName
import android.content.Context
import de.tap.easy_xkcd.widget.WidgetLatestProvider
import com.bumptech.glide.request.target.AppWidgetTarget
import android.graphics.Bitmap
import android.view.View
import com.bumptech.glide.request.transition.Transition
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.GlideApp
import de.tap.easy_xkcd.database.comics.Comic
import de.tap.easy_xkcd.database.comics.ComicDao
import de.tap.easy_xkcd.database.comics.ComicRepository
import de.tap.easy_xkcd.database.comics.XkcdApi
import de.tap.easy_xkcd.utils.AppSettings
import de.tap.easy_xkcd.utils.SharedPrefManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException
import java.io.IOException
import java.lang.Exception
import java.lang.IllegalArgumentException
import javax.inject.Inject

@AndroidEntryPoint
class WidgetLatestProvider : AppWidgetProvider() {
    @Inject
    lateinit var xkcdApi: XkcdApi
    @Inject
    lateinit var comicDao: ComicDao
    @Inject
    lateinit var sharedPrefManager: SharedPrefManager
    @Inject
    lateinit var settings: AppSettings

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Comic(xkcdApi.getNewestComic(), context)
            } catch (e: Exception) {
                Timber.e(e)
                comicDao.getComic(sharedPrefManager.newestComic)
            }?.let { newestComic ->
                val remoteViews = RemoteViews(context.packageName, R.layout.widget_latest_layout)
                GlideApp.with(context)
                    .asBitmap()
                    .load(newestComic.url)
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
                                    Timber.e(e, "Loading image failed for ${newestComic.number}")
                                }
                            }
                        }
                    )

                val titlePrefix =
                    if (settings.widgetShowComicNumber) "${newestComic.number}: " else ""
                remoteViews.setTextViewText(R.id.tvTitle, "${titlePrefix}${newestComic.title}")

                if (settings.widgetShowAlt) {
                    remoteViews.setViewVisibility(
                        R.id.tvAlt,
                        View.VISIBLE
                    )
                    remoteViews.setTextViewText(R.id.tvAlt, newestComic.altText)
                }

                PendingIntent.getActivity(
                    context, 1,
                    Intent("de.tap.easy_xkcd.ACTION_COMIC").apply {
                        putExtra("number", newestComic.number)
                        // We might have found a new comic, so make sure main app is updated
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ).let {
                    remoteViews.setOnClickPendingIntent(
                        R.id.ivComic, it
                    )
                    remoteViews.setOnClickPendingIntent(
                        R.id.tvAlt, it
                    )
                }
                appWidgetManager.updateAppWidget(appWidgetIds, remoteViews)
            }
        }
    }
}