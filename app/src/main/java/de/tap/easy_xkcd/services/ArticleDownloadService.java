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

package de.tap.easy_xkcd.services;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import android.os.Build;
import android.util.Log;

import com.tap.xkcd_reader.BuildConfig;
import com.tap.xkcd_reader.R;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import de.tap.easy_xkcd.utils.Article;
import de.tap.easy_xkcd.utils.PrefHelper;
import io.realm.Realm;
import io.realm.RealmResults;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import timber.log.Timber;

public class ArticleDownloadService extends IntentService {

    private static final String OFFLINE_WHATIF_PATH = "/easy xkcd/what if/";
    private static final String OFFLINE_WHATIF_OVERVIEW_PATH = "/easy xkcd/what if/overview";

    public ArticleDownloadService() {
        super("ArticleDownloadService");
    }

    NotificationCompat.Builder getNotificationBuilder(String channel) {
        return new NotificationCompat.Builder(this, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setProgress(100, 0, false)
                .setContentTitle(getResources().getString(R.string.loading_articles))
                .setOngoing(true)
                .setAutoCancel(true);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
/*
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel("comic");
            notificationManager.deleteNotificationChannel("download");
            notificationManager.createNotificationChannel(new NotificationChannel("comic", getResources().getString(R.string.notification_channel_comic), NotificationManager.IMPORTANCE_HIGH));
            notificationManager.createNotificationChannel(new NotificationChannel("download", getResources().getString(R.string.notification_channel_download), NotificationManager.IMPORTANCE_LOW));
        }
        notificationManager.notify(1, getNotificationBuilder("comic").build());

        PrefHelper prefHelper = new PrefHelper(getApplicationContext());

        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();

        RealmResults<Article> articles = realm.where(Article.class).findAll();

        Article.downloadThumbnails(articles, prefHelper);
        for (int i = 0; i < articles.size(); i++) {
            Timber.d("Downloading %d...", i + 1);
            Article.downloadArticle(articles.get(i).getNumber(), prefHelper);
            articles.get(i).setOffline(true);

            int p = (int) (i / ((float) articles.size()) * 100);
            NotificationCompat.Builder builder = getNotificationBuilder("download");
            builder.setProgress(100, p, false);
            builder.setContentText(i + "/" + articles.size());
            notificationManager.notify(1, builder.build());
        }
        Timber.d("Done! ...");
        realm.copyToRealmOrUpdate(articles);
        realm.commitTransaction();
        realm.close();

        Intent restart = new Intent("de.tap.easy_xkcd.ACTION_COMIC");
        restart.putExtra("number", prefHelper.getLastComic());
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 1, restart, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = getNotificationBuilder("comic");
        builder.setContentIntent(pendingIntent)
                .setContentText(getResources().getString(R.string.not_restart));
        notificationManager.notify(1, builder.build());*/
    }

}
