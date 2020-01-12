package de.tap.easy_xkcd.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.view.View;
import android.widget.RemoteViews;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.AppWidgetTarget;
import com.bumptech.glide.request.transition.Transition;
import com.tap.xkcd_reader.R;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.tap.easy_xkcd.GlideApp;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.database.RealmComic;
import de.tap.easy_xkcd.utils.PrefHelper;
import timber.log.Timber;


public class WidgetRandomProvider extends AppWidgetProvider {

    private PrefHelper prefHelper;
    private int lastComicNumber;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {

        if (prefHelper == null)
            prefHelper = new PrefHelper(context);
        if (lastComicNumber == 0)
            lastComicNumber = prefHelper.getRandomNumber(prefHelper.getLastComic());
        else
            lastComicNumber = prefHelper.getRandomNumber(lastComicNumber);

        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget_random_layout);

        Intent intent = new Intent(context, WidgetRandomProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.tvAlt, pendingIntent); //Use tvAlt instead of shuffle if available

        Intent intent2 = new Intent("de.tap.easy_xkcd.ACTION_COMIC");
        intent2.putExtra("number", lastComicNumber);
        PendingIntent openInApp = PendingIntent.getActivity(context, 1, intent2, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.ivComic, openInApp);

        appWidgetManager.updateAppWidget(appWidgetIds, remoteViews);

        RealmComic comic = (new DatabaseManager(context)).getRealmComic(lastComicNumber);

        AppWidgetTarget appWidgetTarget = new AppWidgetTarget(context, R.id.ivComic, remoteViews, appWidgetIds) {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                try {
                    super.onResourceReady(resource, transition);
                } catch (IllegalArgumentException e) {
                    Timber.e(e, "Loading image failed for %d", comic.getComicNumber());
                }
            }
        };

        if (comic != null) {
            if (prefHelper.fullOfflineEnabled()) {
                remoteViews.setImageViewBitmap(R.id.ivComic, RealmComic.getOfflineBitmap(lastComicNumber, context, prefHelper));
            } else {
                GlideApp.with(context)
                        .asBitmap()
                        .load(comic.getUrl())
                        .into(appWidgetTarget);
            }

            String title = prefHelper.widgetShowComicNumber() ? (lastComicNumber + ": ") : "";
            remoteViews.setTextViewText(R.id.tvTitle, title + comic.getTitle());
            remoteViews.setTextViewText(R.id.tvAlt, comic.getAltText());
            if (prefHelper.widgetShowAlt())
                remoteViews.setViewVisibility(R.id.tvAlt, View.VISIBLE);
        }
    }


}
