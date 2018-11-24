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
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.database.RealmComic;
import de.tap.easy_xkcd.utils.PrefHelper;
import io.realm.RealmResults;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import timber.log.Timber;

import static de.tap.easy_xkcd.utils.JsonParser.getNewHttpClient;

//TODO just restart the app and reload the databse!
public class ComicDownloadService extends IntentService {

    private static final String OFFLINE_PATH = "/easy xkcd";

    public ComicDownloadService() {
        super("ComicDownloadService");
    }

    NotificationCompat.Builder getNotificationBuilder(String channel) {
        return new NotificationCompat.Builder(this, channel)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setProgress(100, 0, false)
                        .setContentTitle(getResources().getString(R.string.loading_offline))
                        .setOngoing(true)
                        .setAutoCancel(true);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final PrefHelper prefHelper = new PrefHelper(getApplicationContext());
        final DatabaseManager databaseManager = new DatabaseManager(getApplicationContext());

        final NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel("comic");
            notificationManager.deleteNotificationChannel("download");
            notificationManager.createNotificationChannel(new NotificationChannel("comic", getResources().getString(R.string.notification_channel_comic), NotificationManager.IMPORTANCE_HIGH));
            notificationManager.createNotificationChannel(new NotificationChannel("download", getResources().getString(R.string.notification_channel_download), NotificationManager.IMPORTANCE_LOW));
        }
        notificationManager.notify(0, getNotificationBuilder("comic").build());

        File sdCard = prefHelper.getOfflinePath();
        final File dir = new File(sdCard.getAbsolutePath() + OFFLINE_PATH);
        OkHttpClient client = getNewHttpClient();
        final RealmResults<RealmComic> comics = databaseManager.getRealmComics();
        if (!dir.exists()) dir.mkdirs();
        final int size = comics.size();
        final CountDownLatch latch = new CountDownLatch(size);
        final ConcurrentHashMap<Integer, BufferedSource> bitmaps = new ConcurrentHashMap<>(size);

        for (int i = 0; i < comics.size(); i++) {
            final int number = comics.get(i).getComicNumber();
            //Timber.d("downloading comic %d", number);
            Request request = new Request.Builder()
                    .url(comics.get(i).getUrl())
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    latch.countDown();
                    Timber.e("Downloading comic %d failed", number);
                    Timber.e(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        File file = new File(dir, String.valueOf(number) + ".png");
                        BufferedSink sink = Okio.buffer(Okio.sink(file));
                        sink.writeAll(response.body().source());
                        sink.close();
                    } catch (IOException e) {
                        Timber.e("Saving Comic %d to external storage failed", number);
                        try {
                            FileOutputStream fos = getApplicationContext().openFileOutput(String.valueOf(number), Context.MODE_PRIVATE);
                            BufferedSink sink = Okio.buffer(Okio.sink(fos));
                            sink.writeAll(response.body().source());
                            //FIRST close the sink, THEN close the FileOutputStream
                            sink.close();
                            fos.close();
                        } catch (IOException e2) {
                            Timber.e("Saving Comic %d to internal storage failed", number);
                        }
                    }
                    response.body().close();
                    int p = (int) (number / ((float) size) * 100);
                    NotificationCompat.Builder builder = getNotificationBuilder("download");
                    builder.setProgress(100, p, false);
                    builder.setContentText(size - latch.getCount() - 1 + "/" + size);
                    notificationManager.notify(0, builder.build());

                    latch.countDown();
                    Timber.d("Latch count: %d", latch.getCount());
                }
            });
        }
        try {
            latch.await();
            Timber.d("latch finished!");
        } catch (InterruptedException e) {
            Timber.e(e);
        }

        prefHelper.setFullOffline(true);
        prefHelper.setHighestOffline(prefHelper.getNewest());
        Intent restart = new Intent("de.tap.easy_xkcd.ACTION_COMIC");
        restart.putExtra("number", prefHelper.getLastComic());
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 1, restart, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = getNotificationBuilder("comic");
        builder.setContentIntent(pendingIntent);
        builder.setContentText(getResources().getString(R.string.not_restart));
        notificationManager.cancel(0);
        notificationManager.notify(1, builder.build()); //New id here to avoid any race conditions
    }

}
