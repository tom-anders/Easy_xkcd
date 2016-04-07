package de.tap.easy_xkcd.notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import java.util.Calendar;

import de.tap.easy_xkcd.utils.PrefHelper;

public class ComicListener implements WakefulIntentService.AlarmListener {

    private PrefHelper prefHelper;

    public void scheduleAlarms(AlarmManager mgr, PendingIntent pi, Context context) {
        prefHelper = new PrefHelper(context.getApplicationContext());
        Calendar calendar = Calendar.getInstance();
        mgr.setInexactRepeating(AlarmManager.RTC, calendar.getTimeInMillis(), prefHelper.getNotificationInterval(), pi);
        Log.d("intervall:", String.valueOf(prefHelper.getNotificationInterval()));
    }

    public void sendWakefulWork(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();

        Log.d("info", "Wakeful work sent");

        // only when connected or while connecting...
        if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                Log.d("DailyListener", "We have internet, start update check directly now!");
                Intent backgroundIntent = new Intent(context, ComicNotifier.class);
                WakefulIntentService.sendWakefulWork(context, backgroundIntent);
        } else {
            Log.d("DailyListener", "We have no internet, enable ConnectivityReceiver!");

            // enable receiver to schedule update when internet is available!
            ConnectivityReceiver.enableReceiver(context);
        }
    }

    public long getMaxAge() {
        return (prefHelper.getNotificationInterval() + 60 * 1000);
    }
}
