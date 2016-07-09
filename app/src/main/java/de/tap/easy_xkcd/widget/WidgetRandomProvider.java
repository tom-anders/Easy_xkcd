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
import de.tap.easy_xkcd.utils.OfflineComic;
import de.tap.easy_xkcd.utils.PrefHelper;


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

        if (prefHelper.fullOfflineEnabled()) {
            OfflineComic comic = new OfflineComic(lastComicNumber, context, prefHelper);
            remoteViews.setImageViewBitmap(R.id.ivComic, comic.getBitmap());
            remoteViews.setTextViewText(R.id.tvTitle, lastComicNumber + ": " + comic.getComicData()[0]);
        } else if (prefHelper.isOnline(context)) {
            new LoadComicTask(context).execute();
        }

        Intent intent = new Intent(context, WidgetRandomProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.shuffle, pendingIntent);

        Intent intent2 = new Intent("de.tap.easy_xkcd.ACTION_COMIC");
        intent2.putExtra("number", lastComicNumber);
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
                comic = new Comic(lastComicNumber);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return comic;
        }

        @Override
        protected void onPostExecute(Comic comic) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisAppWidget = new ComponentName(context.getPackageName(), WidgetRandomProvider.class.getName());
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);
            RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget_random_layout);
            AppWidgetTarget appWidgetTarget = new AppWidgetTarget(context, remoteViews, R.id.ivComic, appWidgetIds);

            if (comic != null) {
                Glide.with(context)
                        .load(comic.getComicData()[2])
                        .asBitmap()
                        .into(appWidgetTarget);

                String title = prefHelper.widgetShowComicNumber() ? (lastComicNumber + ": ") : "";
                remoteViews.setTextViewText(R.id.tvTitle, title + comic.getComicData()[0]);
                remoteViews.setTextViewText(R.id.tvAlt, comic.getComicData()[1]);
                if (prefHelper.widgetShowAlt())
                    remoteViews.setViewVisibility(R.id.tvAlt, View.VISIBLE);
            }
        }
    }

}
