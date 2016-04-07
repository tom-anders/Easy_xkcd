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
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.FutureTarget;
import com.bumptech.glide.request.target.Target;
import com.tap.xkcd_reader.BuildConfig;
import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.utils.PrefHelper;
import okhttp3.CacheControl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

public class ComicDownloadService extends IntentService {

    private static final String OFFLINE_PATH = "/easy xkcd";

    public ComicDownloadService() {
        super("ComicDownloadService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final PrefHelper prefHelper = new PrefHelper(getApplicationContext());
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setProgress(100, 0, false)
                        .setContentTitle(getResources().getString(R.string.loading_offline))
                        .setOngoing(true)
                        .setAutoCancel(true);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotificationManager.notify(0, mBuilder.build());

        if (!BuildConfig.DEBUG) {
            File sdCard = prefHelper.getOfflinePath();
            File dir = new File(sdCard.getAbsolutePath() + OFFLINE_PATH);
            OkHttpClient client = new OkHttpClient();
            if (!dir.exists()) dir.mkdirs();
            for (int i = 1; i <= prefHelper.getNewest(); i++) {
                try {
                    Comic comic = new Comic(i, this);
                    Request request = new Request.Builder()
                            .url(comic.getComicData()[2])
                            .build();
                    Response response = client.newCall(request).execute();
                    try {
                        File file = new File(dir, String.valueOf(i) + ".png");
                        BufferedSink sink = Okio.buffer(Okio.sink(file));
                        sink.writeAll(response.body().source());
                        sink.close();
                    } catch (Exception e) {
                        Log.e("Error at comic" + i, "Saving to external storage failed");
                        try {
                            FileOutputStream fos = getApplicationContext().openFileOutput(String.valueOf(i), Context.MODE_PRIVATE);
                            BufferedSink sink = Okio.buffer(Okio.sink(fos));
                            sink.writeAll(response.body().source());
                            fos.close();
                            sink.close();
                        } catch (Exception e2) {
                            e2.printStackTrace();
                        }
                    }
                    response.body().close();
                    prefHelper.addTitle(comic.getComicData()[0], i);
                    prefHelper.addAlt(comic.getComicData()[1], i);
                    int p = (int) (i / ((float) prefHelper.getNewest()) * 100);
                    mBuilder.setProgress(100, p, false);
                    mBuilder.setContentText(i + "/" + prefHelper.getNewest());
                    mNotificationManager.notify(0, mBuilder.build());
                } catch (Exception e) {
                    Log.e("Error at comic" + i, e.getMessage());
                }
            }
        }
        prefHelper.setFullOffline(true);
        prefHelper.setHighestOffline(prefHelper.getNewest());
        Intent restart = new Intent("de.tap.easy_xkcd.ACTION_COMIC");
        restart.putExtra("number", prefHelper.getLastComic());
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 1, restart, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(pendingIntent);
        mBuilder.setContentText(getResources().getString(R.string.not_restart));
        mNotificationManager.notify(0, mBuilder.build());
    }

}
