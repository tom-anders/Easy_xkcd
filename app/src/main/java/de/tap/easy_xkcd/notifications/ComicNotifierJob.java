package de.tap.easy_xkcd.notifications;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.util.Log;

import com.tap.xkcd_reader.R;

import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.Calendar;

import androidx.core.app.NotificationCompat;
import de.tap.easy_xkcd.database.RealmComic;
import de.tap.easy_xkcd.utils.PrefHelper;
import io.realm.Realm;
import timber.log.Timber;

public class ComicNotifierJob extends JobService {
    private PrefHelper prefHelper;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        Timber.i("Job fired!");
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        prefHelper = new PrefHelper(getApplicationContext());
        if (!prefHelper.checkComicUpdated(day)) {
            Timber.i("Comic task executed");
            updateComics();
        }
        if (!prefHelper.checkWhatIfUpdated(day)) {
            Timber.i("WhatIf task executed");
            updateWhatIf();
        }
        return true;
    }

    void updateComics() {
        boolean found = false;
        RealmComic comic = null;
        //prefHelper.setNewestComic(prefHelper.getNewest()-1); //TODO just for debug
        try {
            comic = RealmComic.findNewestComic(Realm.getDefaultInstance(), getApplicationContext());
            if (comic.getComicNumber() > prefHelper.getNewest())
                found = true;
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        if (found) {
            NotificationCompat.Builder mBuilder =
                    new NotificationCompat.Builder(getApplicationContext())
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle(getResources().getString(R.string.new_comic))
                            .setContentText(String.valueOf(comic.getComicNumber()) + ": " + comic.getTitle())
                            .setAutoCancel(true);

            Intent intent = new Intent("de.tap.easy_xkcd.ACTION_COMIC");
            intent.putExtra("number", comic.getComicNumber());
            PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            mBuilder.setContentIntent(pendingIntent);
            mBuilder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE);

            NotificationManager mNotificationManager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            mNotificationManager.notify(1, mBuilder.build());
        }
        prefHelper.setUpdated(Calendar.getInstance().get(Calendar.DAY_OF_WEEK), found);
    }

    void updateWhatIf() {
        boolean found = false;
        String title = "";
        int number = 0;
        try {
            Document doc = Jsoup.connect("https://what-if.xkcd.com/archive/")
                    .userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/32.0.1700.19 Safari/537.36")
                    .get();
            Elements titles = doc.select("h1");
            Log.d("size|newest", String.valueOf(titles.size() + "|" + String.valueOf(prefHelper.getNewestWhatIf())));
            if (titles.size() > prefHelper.getNewestWhatIf()) {
                found = true;
                title = titles.last().text();
                number = titles.size();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (found) {
            NotificationCompat.Builder mBuilder =
                    new NotificationCompat.Builder(getApplicationContext())
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle(getResources().getString(R.string.new_whatif))
                            .setContentText(title)
                            .setAutoCancel(true);

            Intent intent = new Intent("de.tap.easy_xkcd.ACTION_WHAT_IF");
            intent.putExtra("number", number);
            PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            mBuilder.setContentIntent(pendingIntent);
            mBuilder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE);

            NotificationManager mNotificationManager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            mNotificationManager.notify(0, mBuilder.build());
        }
        prefHelper.setWhatIfUpdated(Calendar.getInstance().get(Calendar.DAY_OF_WEEK), found);
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Timber.d("Job stopped!");
        return true;
    }
}
