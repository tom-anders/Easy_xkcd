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
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;

import com.tap.xkcd_reader.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class PrefHelper {
    private static SharedPreferences sharedPrefs;
    private static SharedPreferences prefs;
    private static List<Integer> randList;
    private static int randIndex = 0;

    private static final String FULL_OFFLINE = "pref_offline";
    private static final String COMIC_TITLES = "comic_titles";
    private static final String COMIC_TRANS = "comic_trans";
    private static final String COMIC_URLS = "comic_urls";
    private static final String SUBTITLE_ENABLED = "pref_subtitle";
    private static final String TITLES_LOADED = "titles_loaded";
    private static final String URLS_LOADED = "urls_loaded";
    private static final String HIGHEST_COMIC_TITLE = "highest_comic_title";
    private static final String HIGHEST_COMIC_URL = "highest_comic_url";
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
    private static final String TUESDAY_UPDATE = "tuesday_update";
    private static final String ALT_DEFAULT = "pref_show_alt";
    private static final String LAST_WHATIF = "last_whatif";
    private static final String NIGHT_MODE = "night_mode";
    private static final String WHATIF_READ = "whatif_read";
    private static final String NEWEST_WHATIF = "whatif_newest";
    private static final String HIDE_READ = "hide_read";
    private static final String SWIPE_ENABLED = "whatif_swipe";
    private static final String RATE_SNACKBAR = "rate_snackbar";
    private static final String THEME = "pref_theme";
    private static final String WHATIF_OFFLINE = "pref_offline_whatif";
    private static final String WHATIF_TITLES = "whatif_titles";
    private static final String NOMEDIA_CREATED = "nomedia_created";
    private static final String SHARE_ALT = "pref_share_alt";
    private static final String PREF_ZOOM = "pref_zoom";
    private static final String PREF_DONATE = "pref_hide_donate";
    private static final String DATABSE_LOADED = "database_loaded";
    private static final String ALT_STYLE = "pref_alt_style";
    private static final String ALT_OPTIONS = "pref_alt_options";
    private static final String ALT_ACTIVATION = "pref_alt_activation";
    private static final String SURVEY_SNACKBAR = "survey_snackbar";
    private static final String NIGHT_THEME = "pref_night";
    private static final String INVERT_COLORS = "pref_invert";


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

    public static String getComicUrls() {
        return sharedPrefs.getString(COMIC_URLS, "");
    }

    public static boolean fabEnabled(String prefTag) {
        return prefs.getStringSet("pref_random", new HashSet<String>()).contains(prefTag);
    }

    public static boolean subtitleEnabled() {
        return prefs.getBoolean(SUBTITLE_ENABLED, true);
    }

    public static boolean classicAltStyle() {
        return Integer.parseInt(prefs.getString(ALT_STYLE, "0"))==0;
    }

    public static boolean databaseLoaded() {
        return sharedPrefs.getBoolean(DATABSE_LOADED, false);
    }

    public static void setDatabaseLoaded() {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putBoolean(DATABSE_LOADED, true);
        editor.apply();
    }

    public static void setTitles(String titles) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(COMIC_TITLES, titles);
        editor.commit();
    }

    public static void setUrls(String urls, int highest) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(COMIC_URLS, urls);
        editor.putInt(HIGHEST_COMIC_URL, highest);
        editor.commit();
    }

    public static void setTrans(String trans) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(COMIC_TRANS, trans);
        editor.commit();
    }

    public static int getHighestUrls() {
        return sharedPrefs.getInt(HIGHEST_COMIC_URL, 0);
    }

    public static void addTitle(String title, int i) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(OFFLINE_TITLE + String.valueOf(i), title);
        editor.commit();
    }

    public static String getTitle(int number) {
        return sharedPrefs.getString(OFFLINE_TITLE + String.valueOf(number), "");
    }

    public static void addAlt(String alt, int i) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(OFFLINE_ALT + String.valueOf(i), alt);
        editor.commit();
    }

    public static String getAlt(int number) {
        return sharedPrefs.getString(OFFLINE_ALT + String.valueOf(number), "");
    }

    public static void setHighestOffline(int number) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putInt(OFFLINE_HIGHEST, number);
        editor.commit();
    }

    public static int getHighestOffline() {
        return sharedPrefs.getInt(OFFLINE_HIGHEST, 0);
    }

    public static int getNewest() {
        return sharedPrefs.getInt(NEWEST_COMIC, 0);
    }

    public static void deleteTitleAndAlt(int newest, Activity activity) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        for (int i = 1; i <= newest; i++) {
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
        return prefs.getStringSet(ALT_OPTIONS, new HashSet<String>()).contains(ALT_VIBRATION);
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
        return Integer.parseInt(hours) * 60 * 60 * 1000;
    }

    public static boolean checkUpdated(int day) {
        switch (day) {
            case Calendar.MONDAY:
                return sharedPrefs.getBoolean(MONDAY_UPDATE, false);
            case Calendar.WEDNESDAY:
                return sharedPrefs.getBoolean(WEDNESDAY_UPDATE, false);
            case Calendar.FRIDAY:
                return sharedPrefs.getBoolean(FRIDAY_UPDATE, false);

            case Calendar.TUESDAY:
                sharedPrefs.getBoolean(TUESDAY_UPDATE, false);
        }
        return true;
    }

    public static void setUpdated(int day, boolean found) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        switch (day) {
            case Calendar.MONDAY:
                editor.putBoolean(MONDAY_UPDATE, found);
                editor.putBoolean(WEDNESDAY_UPDATE, false);
                editor.putBoolean(FRIDAY_UPDATE,false);
                break;
            case Calendar.WEDNESDAY:
                editor.putBoolean(WEDNESDAY_UPDATE, found);
                editor.putBoolean(FRIDAY_UPDATE, false);
                editor.putBoolean(MONDAY_UPDATE,false);
                editor.putBoolean(TUESDAY_UPDATE, false);
                break;
            case Calendar.FRIDAY:
                editor.putBoolean(FRIDAY_UPDATE, found);
                editor.putBoolean(MONDAY_UPDATE, false);
                editor.putBoolean(WEDNESDAY_UPDATE, false);
                editor.putBoolean(TUESDAY_UPDATE, false);
                break;
            case Calendar.TUESDAY:
                editor.putBoolean(TUESDAY_UPDATE, found);
        }
        editor.apply();
        Log.d("Update Status:", String.valueOf(sharedPrefs.getBoolean(MONDAY_UPDATE,false))
                +String.valueOf(sharedPrefs.getBoolean(TUESDAY_UPDATE,false))
                +String.valueOf(sharedPrefs.getBoolean(WEDNESDAY_UPDATE,false))
                +String.valueOf(sharedPrefs.getBoolean(FRIDAY_UPDATE,false)));
    }

    public static boolean altByDefault() {
        return prefs.getStringSet(ALT_OPTIONS, new HashSet<String>()).contains(ALT_DEFAULT);
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

    public static void setWhatifRead(String added) {
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

    public static boolean checkRead(int number) {
        String read = sharedPrefs.getString(WHATIF_READ, "");
        if (read.equals("")) {
            return false;
        }
        String[] readList = Favorites.sortArray(read.split(","));
        int[] readInt = new int[readList.length];
        for (int i = 0; i < readInt.length; i++) {
            readInt[i] = Integer.parseInt(readList[i]);
        }
        int a = Arrays.binarySearch(readInt, number);
        return (a >= 0);
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

    public static boolean swipeEnabled() {
        return sharedPrefs.getBoolean(SWIPE_ENABLED, false);
    }

    public static void setSwipeEnabled(boolean value) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putBoolean(SWIPE_ENABLED, value);
        editor.apply();
    }

    public static boolean showRateDialog() {
        int n = sharedPrefs.getInt(RATE_SNACKBAR, 0);
        if (n < 31) {
            SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putInt(RATE_SNACKBAR, n + 1);
            editor.apply();
        }
        return (n == 12 | n == 30);
    }

    public static int getTheme() {
        if (nightThemeEnabled())
            return R.style.NightTheme;

        int n = Integer.parseInt(prefs.getString(THEME, "1"));
        switch (n) {
            case 1:
                return R.style.DefaultTheme;
            case 2:
                return R.style.RedTheme;
            case 3:
                return R.style.BlueTheme;
            case 4:
                return R.style.BlackTheme;
            case 5:
                return R.style.PurpleTheme;
            case 6:
                return R.style.LimeTheme;
            case 7:
                return R.style.GreenTheme;
            default:
                return R.style.DefaultTheme;
        }
    }

    public static int getDialogTheme() {
        if (nightThemeEnabled())
            return R.style.AlertDialogNight;
        int n = Integer.parseInt(prefs.getString(THEME, "1"));
        switch (n) {
            case 1:
                return R.style.AlertDialog;
            case 2:
                return R.style.AlertDialogRed;
            case 3:
                return R.style.AlertDialogBlue;
            case 4:
                return R.style.AlertDialogBlack;
            case 5:
                return R.style.AlertDialogPurple;
            case 6:
                return R.style.AlertDialogLime;
            case 7:
                return R.style.AlertDialogGreen;
            default:
                return R.style.AlertDialog;
        }
    }

    public static int getNewestWhatIf() {
        return sharedPrefs.getInt(NEWEST_WHATIF, 1);
    }

    public static void setNewestWhatif(int number) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putInt(NEWEST_WHATIF, number);
        editor.apply();
    }

    public static void setFullOfflineWhatIf(boolean value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(WHATIF_OFFLINE, value);
        editor.commit();
    }

    public static boolean fullOfflineWhatIf() {
        return prefs.getBoolean(WHATIF_OFFLINE, false);
    }

    public static void setWhatIfTitles(String titles) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(WHATIF_TITLES, titles);
        editor.commit();
    }

    public static ArrayList<String> getWhatIfTitles() {
        String titles = sharedPrefs.getString(WHATIF_TITLES, "");
        return new ArrayList<>(Arrays.asList(titles.split("&&")));
    }

    public static boolean nomediaCreated() {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        boolean created = sharedPrefs.getBoolean(NOMEDIA_CREATED, false);
        editor.putBoolean(NOMEDIA_CREATED, true);
        editor.apply();
        return created;
    }

    public static boolean shareAlt() {
        return prefs.getStringSet(ALT_OPTIONS, new HashSet<String>()).contains(SHARE_ALT);
    }

    public static int getZoom(int webDefault) {
        int i;
        try {
            i = Integer.parseInt(prefs.getString(PREF_ZOOM, String.valueOf(webDefault)));
        } catch (Exception e) {
            i = webDefault;
        }
        return i;
    }

    public static boolean hideDonate() {
        return prefs.getBoolean(PREF_DONATE, false);
    }

    public static void setHideDonate(boolean value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PREF_DONATE, value);
        editor.apply();
    }

    public static boolean altLongTap() {
        return Integer.parseInt(prefs.getString(ALT_ACTIVATION, "1"))==1;
    }

    public static void showSurveySnackbar(final Context context, FloatingActionButton fab) {
        if (!sharedPrefs.getBoolean(SURVEY_SNACKBAR, false)) {
            View.OnClickListener oc = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Uri uri = Uri.parse("https://docs.google.com/forms/d/1c9hb80NU67CImMKAlOH4p9-F63_duC7qAL30FhJv9Xg/viewform?usp=send_form");
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                     context.startActivity(intent);
                }
            };
            SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putBoolean(SURVEY_SNACKBAR, true);
            editor.apply();
            Snackbar.make(fab, R.string.snackbar_survey, Snackbar.LENGTH_LONG)
                    .setAction(R.string.snackbar_survey_oc, oc)
                    .show();
        }
    }

    public static boolean nightThemeEnabled() {
        return prefs.getBoolean(NIGHT_THEME, false);
    }

    public static boolean invertColors() {
        return prefs.getBoolean(INVERT_COLORS, true)&&nightThemeEnabled();
    }

    public static int getRandomNumber(int current) {
        if (randList==null) {
            randList = new ArrayList<>();
            for (int i=1; i<getNewest(); i++) {
                if (i!=current)
                    randList.add(i);
            }
            Collections.shuffle(randList);
            randList.add(0, current);
        }
        int result;
        if (randIndex==0) {
            result = randList.get(randIndex+1);
            randIndex++;
        } else {
            randIndex++;
            result = randList.get(randIndex);
        }
        return result;
    }

    public static int getPreviousRandom(int i) {
        if (randList!=null && randIndex>0) {
            int result;
            if (randIndex == 1) {
                randIndex--;
                result = randList.get(randIndex);
            } else {
                result = randList.get(randIndex-1);
                randIndex--;
            }
            return result;
        }
        return i;
    }

}



























