package de.tap.easy_xkcd;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.tap.xkcd_reader.R;

import java.io.IOException;
import java.util.Calendar;

public class ComicNotifier extends WakefulIntentService {

    public ComicNotifier() {
        super("NewComicNotifier");
    }

    @Override
    public void doWakefulWork(Intent intent) {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        //int day = Calendar.FRIDAY;
        PrefHelper.getPrefs(getApplicationContext());
        if (!PrefHelper.checkUpdated(day)) {
            new updateComicTitles().execute();
            Log.e("Info", "task executed");
        } else {
           Log.e("Info", "notification already sent or wrong day");
        }
    }

    private class updateComicTitles extends AsyncTask<Void, Void, Void> {
        private boolean found = false;
        private Comic comic;

        @Override
        protected Void doInBackground(Void... params) {
            try {
                comic = new Comic(0);
                if (comic.getComicNumber() > PrefHelper.getNewest()) {
                    found = true;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
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
                mNotificationManager.notify(0, mBuilder.build());

                PrefHelper.setUpdated(Calendar.getInstance().get(Calendar.DAY_OF_WEEK));
                //PrefHelper.setUpdated(Calendar.FRIDAY);
            } /*else {
                NotificationCompat.Builder mBuilder =
                        new NotificationCompat.Builder(getApplicationContext())
                                .setSmallIcon(R.drawable.ic_notification)
                                .setContentTitle("No comic found")
                                .setContentText(String.valueOf(Calendar.getInstance().get(Calendar.HOUR))+":"+String.valueOf(Calendar.getInstance().get(Calendar.MINUTE)))
                                .setAutoCancel(true);

                mBuilder.setDefaults(Notification.DEFAULT_SOUND|Notification.DEFAULT_LIGHTS| Notification.DEFAULT_VIBRATE);

                NotificationManager mNotificationManager =
                        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                mNotificationManager.notify(Calendar.getInstance().get(Calendar.HOUR) + Calendar.getInstance().get(Calendar.MINUTE), mBuilder.build());
            }*/
        }

    }

}
