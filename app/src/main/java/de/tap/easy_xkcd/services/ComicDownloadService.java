package de.tap.easy_xkcd.services;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.tap.xkcd_reader.BuildConfig;
import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileOutputStream;

import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.utils.PrefHelper;

public class ComicDownloadService extends IntentService {

    private static final String OFFLINE_PATH = "/easy xkcd";

    public ComicDownloadService() {
        super("ComicDownloadService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        PrefHelper prefHelper = new PrefHelper(getApplicationContext());
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(getApplicationContext())
                        .setSmallIcon(R.drawable.ic_notification)
                        .setProgress(100, 0 , false)
                        .setContentTitle(getResources().getString(R.string.loading_offline))
                        .setOngoing(true)
                        .setAutoCancel(true);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotificationManager.notify(0, mBuilder.build());

        if (!BuildConfig.DEBUG) {
            for (int i = 1; i <= prefHelper.getNewest(); i++) {
                Log.d("i", String.valueOf(i));
                try {
                    Comic comic = new Comic(i, getApplicationContext());
                    String url = comic.getComicData()[2];
                    Bitmap mBitmap = Glide.with(getApplicationContext())
                            .load(url)
                            .asBitmap()
                            .into(-1, -1)
                            .get();
                    try {
                        File sdCard = prefHelper.getOfflinePath();
                        File dir = new File(sdCard.getAbsolutePath() + OFFLINE_PATH);
                        dir.mkdirs();
                        File file = new File(dir, String.valueOf(i) + ".png");
                        FileOutputStream fos = new FileOutputStream(file);
                        mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.flush();
                        fos.close();
                    } catch (Exception e) {
                        Log.e("Error", "Saving to external storage failed");
                        try {
                            FileOutputStream fos = getApplicationContext().openFileOutput(String.valueOf(i), Context.MODE_PRIVATE);
                            mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                            fos.close();
                        } catch (Exception e2) {
                            e2.printStackTrace();
                        }
                    }

                    prefHelper.addTitle(comic.getComicData()[0], i);
                    prefHelper.addAlt(comic.getComicData()[1], i);
                    int p = (int) (i / ((float) prefHelper.getNewest()) * 100);
                    mBuilder.setProgress(100, p, false);
                    mBuilder.setContentText(i + "/" + prefHelper.getNewest());
                    mNotificationManager.notify(0, mBuilder.build());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        prefHelper.setHighestOffline(prefHelper.getNewest());
        Intent restart = new Intent("de.tap.easy_xkcd.ACTION_COMIC");
        restart.putExtra("number", prefHelper.getLastComic());
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 1, restart, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(pendingIntent);
        mBuilder.setContentText(getResources().getString(R.string.not_restart));
        mNotificationManager.notify(0, mBuilder.build());

    }

}
