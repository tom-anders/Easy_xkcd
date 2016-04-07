/**
 * *******************************************************************************
 * Copyright 2016 Tom Praschan
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ******************************************************************************
 */

package de.tap.easy_xkcd.notifications;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.tap.xkcd_reader.R;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.Calendar;

import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.utils.PrefHelper;

public class ComicNotifier extends WakefulIntentService {

    private PrefHelper prefHelper;

    public ComicNotifier() {
        super("NewComicNotifier");
    }

    @Override
    public void doWakefulWork(Intent intent) {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        prefHelper = new PrefHelper(getApplicationContext());
        if (!prefHelper.checkComicUpdated(day)) {
            Log.e("Info", "Comic task " + "executed");
            updateComics();
        }
        if (!prefHelper.checkWhatIfUpdated(day)) {
            Log.e("Info", "WhatIf task executed");
            updateWhatIf();
        }
    }

    void updateComics() {
        boolean found = false;
        Comic comic = null;
        try {
            comic = new Comic(0);
            if (comic.getComicNumber() > prefHelper.getNewest())
                found = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (found) {
            NotificationCompat.Builder mBuilder =
                    new NotificationCompat.Builder(getApplicationContext())
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle(getResources().getString(R.string.new_comic))
                            .setContentText(String.valueOf(comic.getComicNumber()) + ": " + comic.getComicData()[0])
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

}
