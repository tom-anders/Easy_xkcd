package de.tap.easy_xkcd.notifications;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;

public class ConnectivityReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            Log.d("ConnectivityReceiver", "ConnectivityReceiver invoked...");

            boolean noConnectivity = intent.getBooleanExtra(
                    ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);

            if (!noConnectivity) {

                ConnectivityManager cm = (ConnectivityManager) context
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo netInfo = cm.getActiveNetworkInfo();

                // only when connected or while connecting...
                if (netInfo != null && netInfo.isConnectedOrConnecting()) {

                    // if we have mobile or wifi connectivity...
                    if (((netInfo.getType() == ConnectivityManager.TYPE_MOBILE))
                            || (netInfo.getType() == ConnectivityManager.TYPE_WIFI)) {
                        Log.d("ConnectivityReceiver", "We have internet, start update check and disable receiver!");

                        // Start service with wakelock by using WakefulIntentService
                        Intent backgroundIntent = new Intent(context, ComicNotifier.class);
                        WakefulIntentService.sendWakefulWork(context, backgroundIntent);

                        // disable receiver after we started the service
                        disableReceiver(context);
                    }
                }
            }

        }
    }

    public static void enableReceiver(Context context) {
        ComponentName component = new ComponentName(context, ConnectivityReceiver.class);

        context.getPackageManager().setComponentEnabledSetting(component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    }

    public static void disableReceiver(Context context) {
        ComponentName component = new ComponentName(context, ConnectivityReceiver.class);

        context.getPackageManager().setComponentEnabledSetting(component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }
}
