package de.tap.easy_xkcd.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.view.View;
import android.widget.RemoteViews;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.AppWidgetTarget;
import com.tap.xkcd_reader.R;

import java.io.IOException;

import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.utils.PrefHelper;

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

    private class LoadComicTask extends AsyncTask<Void, Void, Comic> {
        Context context;

        public LoadComicTask(Context context) {
            this.context = context;
        }

        @Override
        protected Comic doInBackground(Void... dummy) {
            Comic comic = null;
            try {
                comic = new Comic(0);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return comic;
        }

        @Override
        protected void onPostExecute(Comic comic) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisAppWidget = new ComponentName(context.getPackageName(), WidgetLatestProvider.class.getName());
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);
            RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget_latest_layout);
            AppWidgetTarget appWidgetTarget = new AppWidgetTarget(context, remoteViews, R.id.ivComic, appWidgetIds);

            if (comic != null) {
                newestComicNumber = comic.getComicNumber();
                Glide.with(context)
                        .load(comic.getComicData()[2])
                        .asBitmap()
                        .into(appWidgetTarget);
                String title = prefHelper.widgetShowComicNumber() ? (newestComicNumber + ": ") : "";
                remoteViews.setTextViewText(R.id.tvTitle, title + comic.getComicData()[0]);
                remoteViews.setTextViewText(R.id.tvAlt, comic.getComicData()[1]);
                if (prefHelper.widgetShowAlt())
                    remoteViews.setViewVisibility(R.id.tvAlt, View.VISIBLE);

            }
        }
    }

}
