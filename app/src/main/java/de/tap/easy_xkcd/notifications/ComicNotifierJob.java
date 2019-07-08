package de.tap.easy_xkcd.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import com.tap.xkcd_reader.BuildConfig;
import com.tap.xkcd_reader.R;

import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.Calendar;

import androidx.core.app.NotificationCompat;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.database.RealmComic;
import de.tap.easy_xkcd.utils.PrefHelper;
import io.realm.Realm;
import timber.log.Timber;

public class ComicNotifierJob extends JobService {
    private updateCheck task;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        Timber.d("Job fired at %s!", Calendar.getInstance().getTime().toString());
        task = new updateCheck(new PrefHelper(this), jobParameters);
        task.execute();
        return true;
    }

    class updateCheck extends AsyncTask<Void, Void, Void> {
        PrefHelper prefHelper;
        JobParameters jobParameters;

        boolean newComicFound = false;
        RealmComic newComic;

        boolean newWhatifFound = false;
        int newWhatIfNumber;
        String newWhatIfTitle;

        public updateCheck(PrefHelper prefHelper, JobParameters jobParameters) {
            this.prefHelper = prefHelper;
            this.jobParameters = jobParameters;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            updateComics();
            updateWhatIf();
            return null;
        }

        void updateComics() {
            try {
                newComic = new DatabaseManager(ComicNotifierJob.this).findNewestComic(ComicNotifierJob.this);
                newComicFound = newComic.getComicNumber() > prefHelper.getNewest();
            } catch (Exception e) {
                Timber.e(e);
                newComicFound = false;
            }
        }

        void updateWhatIf() {
            try {
                Document doc = Jsoup.connect("https://what-if.xkcd.com/archive/")
                        .userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/32.0.1700.19 Safari/537.36")
                        .get();
                Elements titles = doc.select("h1");
                Log.d("size|newest", String.valueOf(titles.size() + "|" + String.valueOf(prefHelper.getNewestWhatIf())));
                if (prefHelper.getNewestWhatIf() != 1 && titles.size() > prefHelper.getNewestWhatIf()) {
                    newWhatifFound = true;
                    newWhatIfTitle = titles.last().text();
                    newWhatIfNumber = titles.size();
                }
            } catch (Exception e) {
                Timber.e(e);
                newWhatifFound = false;
            }
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (newComicFound) {
                NotificationCompat.Builder mBuilder =
                        new NotificationCompat.Builder(ComicNotifierJob.this, "comic")
                                .setSmallIcon(R.drawable.ic_notification)
                                .setContentTitle(getResources().getString(R.string.new_comic))
                                .setContentText(String.valueOf(newComic.getComicNumber()) + ": " + newComic.getTitle())
                                .setAutoCancel(true);
                Intent intent = new Intent("de.tap.easy_xkcd.ACTION_COMIC_NOTIFICATION");
                PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);

                mBuilder.setContentIntent(pendingIntent);
                mBuilder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE);

                NotificationManager notificationManager =
                        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (notificationManager != null && newComic.getComicNumber() > prefHelper.getLastNotification()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        notificationManager.deleteNotificationChannel("comic");
                        notificationManager.createNotificationChannel(new NotificationChannel("comic", getResources().getString(R.string.notification_channel_comic), NotificationManager.IMPORTANCE_HIGH));
                    }
                    notificationManager.notify(1, mBuilder.build());

                    //Make sure the notification is not shown again in case the user dimisses it
                    prefHelper.setLastNotification(newComic.getComicNumber());
                }
            } else {
                Timber.d("ComicNotifier found no new comic...");
            }

            if (newWhatifFound) {
                NotificationCompat.Builder mBuilder = //TODO provide a notification channel to make this show up on oreo
                        new NotificationCompat.Builder(ComicNotifierJob.this)
                                .setSmallIcon(R.drawable.ic_notification)
                                .setContentTitle(getResources().getString(R.string.new_whatif))
                                .setContentText(newWhatIfTitle)
                                .setAutoCancel(true);

                Intent intent = new Intent("de.tap.easy_xkcd.ACTION_WHAT_IF");
                intent.putExtra("number", newWhatIfNumber);
                PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);

                mBuilder.setContentIntent(pendingIntent);
                mBuilder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE);

                NotificationManager mNotificationManager =
                        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (mNotificationManager != null) {
                    mNotificationManager.notify(0, mBuilder.build());
                }
            }

            Timber.d("Job finished at %s!", Calendar.getInstance().getTime().toString());
            jobFinished(jobParameters, false);
        }
    }


    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Timber.d("Job stopped!");
        if (task != null) {
            task.cancel(true);
            task = null;
            Timber.d("canceled task");
        }
        return true;
    }
}
