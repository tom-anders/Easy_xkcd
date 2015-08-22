/**
 * *******************************************************************************
 * Copyright 2015 Tom Praschan
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
package de.tap.easy_xkcd;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;

public class PrefHelper {
    private static SharedPreferences sharedPrefs;
    private static SharedPreferences prefs;

    private static final String FULL_OFFLINE = "pref_offline";
    private static final String COMIC_TITLES = "comic_titles";
    private static final String COMIC_TRANS = "comic_trans";
    private static final String SUBTITLE_ENABLED = "pref_subtitle";
    private static final String TITLES_LOADED = "titles_loaded";
    private static final String HIGHEST_COMIC_TITLE = "highest_comic_title";
    private static final String TRANS_LOADED = "trans_loaded";
    private static final String HIGHEST_COMIC_TRANS = "highest_comic_trans";
    private static final String OFFLINE_TITLE = "title";
    private static final String OFFLINE_ALT = "alt";
    private static final String OFFLINE_HIGHEST = "highest_offline";
    private static final String NEWEST_COMIC = "Newest Comic";
    private static final String LAST_COMIC = "Last Comic";
    private static final String ALT_VIBRATION = "pref_alt";
    private static final String ORIENTATION = "pref_orientation";
    private static final String ALT_TIP = "alt_tip";
    private static final String SHARE_IMAGE = "pref_share";
    private static final String SHARE_MOBILE = "pref_mobile";
    private static final String NOTIFICATIONS_INTERVAL = "pref_notifications";
    private static final String MONDAY_UPDATE = "monday_update";
    private static final String WEDNESDAY_UPDATE = "wednesday_update";
    private static final String FRIDAY_UPDATE = "friday_update";
    private static final String ALT_DEFAULT = "pref_show_alt";
    private static final String LAST_WHATIF = "last_whatif";
    private static final String NIGHT_MODE = "night_mode";
    private static final String WHATIF_READ = "whatif_read";
    private static final String HIDE_READ = "hide_read";

    public static void getPrefs(Context context) {
        //sharedPrefs = ((MainActivity) context).getPreferences(Activity.MODE_PRIVATE);
        sharedPrefs = context.getSharedPreferences("MainActivity", Activity.MODE_PRIVATE);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static boolean fullOfflineEnabled() {
        return prefs.getBoolean(FULL_OFFLINE, false);
    }

    public static void setFullOffline(boolean value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(FULL_OFFLINE, value);
        editor.commit();
    }

    public static String getComicTitles() {
        return sharedPrefs.getString(COMIC_TITLES, "");
    }

    public static String getComicTrans() {
        return sharedPrefs.getString(COMIC_TRANS, "");
    }

    public static boolean fabEnabled(String prefTag) {
        return prefs.getBoolean(prefTag, false);
    }

    public static boolean subtitleEnabled() {
        return prefs.getBoolean(SUBTITLE_ENABLED, true);
    }

    public static boolean titlesLoaded() {
        return sharedPrefs.getBoolean(TITLES_LOADED, false);
    }

    public static void setTitles(String titles, Boolean value, int highest) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putBoolean(TITLES_LOADED, value);
        editor.putString(COMIC_TITLES, titles);
        editor.putInt(HIGHEST_COMIC_TITLE, highest);
        editor.commit();
    }

    public static int getHighestTitle() {
        return sharedPrefs.getInt(HIGHEST_COMIC_TITLE, 0);
    }

    public static int getHighestTrans() {
        return sharedPrefs.getInt(HIGHEST_COMIC_TRANS, 0);
    }

    public static void setHighestTitle(Context context) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        try {
            int newest = new Comic(0, context).getComicNumber();
            int n = getHighestTitle();
            StringBuilder sb = new StringBuilder();
            sb.append(PrefHelper.getComicTitles());
            while (n < newest) {
                String s = new Comic(n + 1, context).getComicData()[0];
                sb.append("&&");
                sb.append(s);
                editor.putInt(HIGHEST_COMIC_TITLE, n + 1);
                n++;
                Log.d("n", String.valueOf(n));
            }
            editor.putString(COMIC_TITLES, sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
            editor.putBoolean(TITLES_LOADED, false);
        }
        editor.commit();
    }

    public static boolean transLoaded() {
        return sharedPrefs.getBoolean(TRANS_LOADED, false);
    }

    public static void setTrans(String trans, Boolean value, int highest) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putBoolean(TRANS_LOADED, value);
        editor.putString(COMIC_TRANS, trans);
        editor.putInt(HIGHEST_COMIC_TRANS, highest);
        editor.commit();
    }

    public static void setHighestTrans(Context context) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        try {
            int newest = new Comic(0, context).getComicNumber();
            int n = getHighestTrans();
            StringBuilder sb = new StringBuilder();
            sb.append(PrefHelper.getComicTrans());
            while (n < newest) {
                String s = new Comic(n + 1, context).getTranscript();
                sb.append("&&");
                sb.append(s);
                editor.putInt(HIGHEST_COMIC_TRANS, n + 1);
                n++;
                Log.d("n", String.valueOf(n));
            }
            editor.putString(COMIC_TRANS, sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
            editor.putBoolean(TRANS_LOADED, false);
        }
        editor.commit();
    }

    public static void addTitle (String title, int i) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(OFFLINE_TITLE + String.valueOf(i), title);
        editor.commit();
    }

    public static String getTitle (int number) {
        return sharedPrefs.getString(OFFLINE_TITLE+String.valueOf(number), "");
    }

    public static void addAlt (String alt, int i) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(OFFLINE_ALT + String.valueOf(i), alt);
        editor.commit();
    }

    public static String getAlt (int number) {
        return sharedPrefs.getString(OFFLINE_ALT+String.valueOf(number), "");
    }

    public static void setHighestOffline(int number) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putInt(OFFLINE_HIGHEST, number);
        editor.apply();
    }

    public static int getHighestOffline() {
        return sharedPrefs.getInt(OFFLINE_HIGHEST, 0);
    }

    public static int getNewest() {
        return sharedPrefs.getInt(NEWEST_COMIC, 0);
    }

    public static void deleteTitleAndAlt (int newest, Activity activity) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        for (int i = 1; i<=newest; i++) {
            if (!Favorites.checkFavorite(activity, i)) {
                editor.putString(OFFLINE_TITLE, "");
                editor.putString(OFFLINE_ALT, "");
            }
        }
        editor.apply();
    }

    public static void setLastComic(int number) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putInt(LAST_COMIC, number);
        editor.commit();
    }

    public static int getLastComic() {
        return sharedPrefs.getInt(LAST_COMIC, 0);
    }

    public static void setNewestComic(int number) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putInt(NEWEST_COMIC, number);
        editor.commit();
    }

    public static boolean altVibration() {
        return prefs.getBoolean(ALT_VIBRATION, true);
    }

    public static String getOrientation() {
        return prefs.getString(ORIENTATION, "1");
    }

    public static boolean showAltTip() {
        return sharedPrefs.getBoolean(ALT_TIP, true);
    }

    public static void setAltTip(boolean value) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putBoolean(ALT_TIP, value);
        editor.apply();
    }

    public static boolean shareImage() {
        return prefs.getBoolean(SHARE_IMAGE, false);
    }

    public static boolean shareMobile() {
        return prefs.getBoolean(SHARE_MOBILE, false);
    }

    public static int getNotificationInterval() {
        String hours = prefs.getString(NOTIFICATIONS_INTERVAL, "0");
        return Integer.parseInt(hours)*60*60*1000;
    }

    public static boolean checkUpdated (int day) {
        switch (day) {
            case Calendar.MONDAY: return sharedPrefs.getBoolean(MONDAY_UPDATE, false);
            case Calendar.WEDNESDAY: return sharedPrefs.getBoolean(WEDNESDAY_UPDATE, false);
            case Calendar.FRIDAY: return sharedPrefs.getBoolean(FRIDAY_UPDATE, false);
        }
        return true;
    }

    public static void setUpdated (int day) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        switch (day) {
            case Calendar.MONDAY:
                editor.putBoolean(MONDAY_UPDATE, true);
                editor.putBoolean(WEDNESDAY_UPDATE, false);
                break;
            case Calendar.WEDNESDAY:
                editor.putBoolean(WEDNESDAY_UPDATE, true);
                editor.putBoolean(FRIDAY_UPDATE, false);
                break;
            case Calendar.FRIDAY:
                editor.putBoolean(FRIDAY_UPDATE, true);
                editor.putBoolean(MONDAY_UPDATE, false);
        }
        editor.apply();
    }

    public static boolean altByDefault() {
        return prefs.getBoolean(ALT_DEFAULT, false);
    }

    public static int getLastWhatIf() {
        return sharedPrefs.getInt(LAST_WHATIF, 0);
    }

    public static void setLastWhatIf(int number) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putInt(LAST_WHATIF, number);
        editor.apply();
    }

    public static boolean nightModeEnabled() {
        return sharedPrefs.getBoolean(NIGHT_MODE, false);
    }

    public static void setNightMode(boolean value) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putBoolean(NIGHT_MODE, value);
        editor.commit();
    }

    public static void setWhatifRead (String added) {
        String read = sharedPrefs.getString(WHATIF_READ, "");
        if (!read.equals("")) {
            read = read + "," + added;
        } else {
            read = added;
        }
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(WHATIF_READ, read);
        editor.commit();
    }

    public static boolean checkRead (int number) {
        String read = sharedPrefs.getString(WHATIF_READ, "");
        if (read.equals("")) {
            return false;
        }
        String[] readList = Favorites.sortArray(read.split(","));
        int[] readInt = new int[readList.length];
        for (int i=0; i<readInt.length; i++) {
            readInt[i] = Integer.parseInt(readList[i]);
        }
        int a = Arrays.binarySearch(readInt, number);
        return (a>=0);
    }

    public static void setAllUnread() {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(WHATIF_READ, "");
        editor.commit();
    }

    public static boolean hideRead() {
        return sharedPrefs.getBoolean(HIDE_READ, false);
    }

    public static void setHideRead(boolean value) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putBoolean(HIDE_READ, value);
        editor.apply();
    }
}



























