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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.tap.easy_xkcd.GlideApp;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.database.RealmComic;
import de.tap.easy_xkcd.utils.PrefHelper;
import io.realm.Realm;
import timber.log.Timber;

public class WidgetLatestProvider extends AppWidgetProvider {
    private PrefHelper prefHelper;
    private int newestComicNumber;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {

        if (prefHelper == null) {
            prefHelper = new PrefHelper(context);
            newestComicNumber = prefHelper.getNewest();
        }

        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget_latest_layout);

        new LoadComicTask(context).execute();

        Intent intent2 = new Intent("de.tap.easy_xkcd.ACTION_COMIC");
        intent2.putExtra("number", newestComicNumber);
        PendingIntent openInApp = PendingIntent.getActivity(context, 1, intent2, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.ivComic, openInApp);

        appWidgetManager.updateAppWidget(appWidgetIds, remoteViews);

    }

    private class LoadComicTask extends AsyncTask<Void, Void, RealmComic> {
        Context context;

        public LoadComicTask(Context context) {
            this.context = context;
        }

        @Override
        protected RealmComic doInBackground(Void... dummy) {
            RealmComic comic = null;
            try {
                comic = new DatabaseManager(context).findNewestComic(context);
            } catch (IOException | JSONException e) {
                Timber.e(e);
            }
            return comic;
        }

        @Override
        protected void onPostExecute(RealmComic comic) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisAppWidget = new ComponentName(context.getPackageName(), WidgetLatestProvider.class.getName());
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);
            RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget_latest_layout);
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
                newestComicNumber = comic.getComicNumber();
                GlideApp.with(context)
                        .asBitmap()
                        .load(comic.getUrl())
                        .into(appWidgetTarget);
                String title = prefHelper.widgetShowComicNumber() ? (newestComicNumber + ": ") : "";
                remoteViews.setTextViewText(R.id.tvTitle, title + comic.getTitle());
                remoteViews.setTextViewText(R.id.tvAlt, comic.getAltText());
                if (prefHelper.widgetShowAlt())
                    remoteViews.setViewVisibility(R.id.tvAlt, View.VISIBLE);

            }
        }
    }

}
