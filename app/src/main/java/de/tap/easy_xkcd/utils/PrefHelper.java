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
package de.tap.easy_xkcd.utils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;

import com.tap.xkcd_reader.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import de.tap.easy_xkcd.Activities.NestedSettingsActivity;
import de.tap.easy_xkcd.database.DatabaseManager;

public class PrefHelper {
    private SharedPreferences sharedPrefs;
    private SharedPreferences prefs;
    private List<Integer> randList;
    private int randIndex = 0;

    private static final String FULL_OFFLINE = "pref_offline";
    private static final String COMIC_TITLES = "comic_titles";
    private static final String COMIC_TRANS = "comic_trans";
    private static final String COMIC_URLS = "comic_urls";
    private static final String SUBTITLE_ENABLED = "pref_subtitle";
    private static final String HIGHEST_COMIC_URL = "highest_comic_url";
    private static final String OFFLINE_TITLE = "title";
    private static final String OFFLINE_ALT = "alt";
    private static final String OFFLINE_HIGHEST = "highest_offline";
    private static final String NEWEST_COMIC = "Newest Comic";
    private static final String LAST_COMIC = "Last Comic";
    private static final String ALT_VIBRATION = "pref_alt";
    private static final String ALT_BACK = "pref_alt_back";
    private static final String ALT_TIP = "alt_tip";
    private static final String WHAT_IF_TIP = "whatif_tip";
    private static final String RANDOM_TIP = "random_tip";
    private static final String SHARE_IMAGE = "pref_share";
    private static final String SHARE_MOBILE = "pref_mobile";
    private static final String NOTIFICATIONS_INTERVAL = "pref_notifications";
    private static final String MONDAY_UPDATE = "monday_update";
    private static final String WEDNESDAY_UPDATE = "wednesday_update";
    private static final String FRIDAY_UPDATE = "friday_update";
    private static final String TUESDAY_UPDATE_WHATIF = "tuesday_update_whatif";
    private static final String WEDNESDAY_UPDATE_WHATIF = "wednesday_update_whatif";
    private static final String THURSDAY_UPDATE_WHATIF = "thursday_update_whatif";
    private static final String ALT_DEFAULT = "pref_show_alt";
    private static final String LAST_WHATIF = "last_whatif";
    private static final String WHATIF_READ = "whatif_read";
    private static final String WHATIF_FAV = "whatif_fav";
    private static final String NEWEST_WHATIF = "whatif_newest";
    private static final String HIDE_READ_WHATIF = "hide_read";
    private static final String HIDE_READ_OVERVIEW = "hide_read_overview";
    private static final String SWIPE_ENABLED = "whatif_swipe";
    private static final String RATE_SNACKBAR = "rate_snackbar";
    private static final String WHATIF_OFFLINE = "pref_offline_whatif";
    private static final String WHATIF_TITLES = "whatif_titles";
    private static final String NOMEDIA_CREATED = "nomedia_created";
    private static final String SHARE_ALT = "pref_share_alt";
    private static final String PREF_ZOOM = "pref_zoom";
    private static final String PREF_DONATE = "pref_hide_donate";
    private static final String DATABASE_LOADED = "database_loaded";
    private static final String ALT_STYLE = "pref_alt_style";
    private static final String ALT_OPTIONS = "pref_alt_options";
    private static final String ALT_ACTIVATION = "pref_alt_activation";
    private static final String SURVEY_SNACKBAR = "survey_snackbar";
    private static final String CUSTOM_THEMES_SNACKBAR = "custom_themes_snackbar";
    private static final String COLORED_NAVBAR = "pref_navbar";
    private static final String MOBILE_ENABLED = "pref_update_mobile";
    private static final String OFFLINE_PATH = "pref_offline_path";
    private static final String ZOOM_SCROLL = "pref_zoom_scroll";
    private static final String OVERVIEW_FAV = "overview_fav";
    private static final String DEFAULT_ZOOM = "pref_default_zoom";
    private static final String OVERVIEW_STYLE = "overview_style";
    private static final String BOOKMARK = "bookmark";
    private static final String WHAT_IF_SUNBEAM_LOADED = "sun_beam";
    private static final String LAUNCH_TO_OVERVIEW = "pref_overview_default";
    private static final String INCLUDE_LINK = "pref_include_link";
    private static final String WIDGET_ALT = "widget_alt";
    private static final String WIDGET_COMIC_NUMBER = "widget_comicNumber";

    public PrefHelper(Context context) {
        sharedPrefs = context.getSharedPreferences("MainActivity", Activity.MODE_PRIVATE);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public boolean fullOfflineEnabled() {
        return prefs.getBoolean(FULL_OFFLINE, false);
    }

    public void setFullOffline(boolean value) {
        prefs.edit().putBoolean(FULL_OFFLINE, value).apply();
    }

    public boolean fabEnabled(String prefTag) {
        return prefs.getStringSet("pref_random", new HashSet<String>()).contains(prefTag);
    }

    public boolean subtitleEnabled() {
        return prefs.getBoolean(SUBTITLE_ENABLED, true);
    }

    public boolean classicAltStyle() {
        return Integer.parseInt(prefs.getString(ALT_STYLE, "0")) == 0;
    }

    public boolean databaseLoaded() {
        return sharedPrefs.getBoolean(DATABASE_LOADED, false);
    }

    public void setDatabaseLoaded() {
        sharedPrefs.edit().putBoolean(DATABASE_LOADED, true).apply();
    }

    public void setTitles(String titles) {
        sharedPrefs.edit().putString(COMIC_TITLES, titles).apply();
    }

    public void setUrls(String urls, int highest) {
        sharedPrefs.edit().putString(COMIC_URLS, urls).putInt(HIGHEST_COMIC_URL, highest).apply();
    }

    public void setTrans(String trans) {
        sharedPrefs.edit().putString(COMIC_TRANS, trans).apply();
    }

    public int getHighestUrls() {
        return sharedPrefs.getInt(HIGHEST_COMIC_URL, 0);
    }

    public void addTitle(String title, int i) {
        sharedPrefs.edit().putString(OFFLINE_TITLE + String.valueOf(i), title).apply();
    }

    public String getTitle(int number) {
        return sharedPrefs.getString(OFFLINE_TITLE + String.valueOf(number), "");
    }

    public void addAlt(String alt, int i) {
        sharedPrefs.edit().putString(OFFLINE_ALT + String.valueOf(i), alt).apply();
    }

    public String getAlt(int number) {
        return sharedPrefs.getString(OFFLINE_ALT + String.valueOf(number), "");
    }

    public void setHighestOffline(int number) {
        sharedPrefs.edit().putInt(OFFLINE_HIGHEST, number).apply();
    }

    public int getHighestOffline() {
        return sharedPrefs.getInt(OFFLINE_HIGHEST, 0);
    }

    public int getNewest() {
        return sharedPrefs.getInt(NEWEST_COMIC, 0);
    }

    public void deleteTitleAndAlt(int newest, DatabaseManager databaseManager) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        for (int i = 1; i <= newest; i++) {
            if (databaseManager.checkFavorite(i)) {
                editor.putString(OFFLINE_TITLE, "");
                editor.putString(OFFLINE_ALT, "");
            }
        }
        editor.apply();
    }

    public void setLastComic(int number) {
        sharedPrefs.edit().putInt(LAST_COMIC, number).apply();
    }

    public int getLastComic() {
        return sharedPrefs.getInt(LAST_COMIC, 0);
    }

    public void setNewestComic(int number) {
        sharedPrefs.edit().putInt(NEWEST_COMIC, number).apply();
    }

    public boolean altVibration() {
        return prefs.getStringSet(ALT_OPTIONS, new HashSet<String>()).contains(ALT_VIBRATION);
    }

    public boolean altBackButton() {
        return prefs.getStringSet(ALT_OPTIONS, new HashSet<String>()).contains(ALT_BACK);
    }

    public boolean showAltTip() {
        return sharedPrefs.getBoolean(ALT_TIP, true);
    }

    public boolean showWhatIfTip() {
        return sharedPrefs.getBoolean(WHAT_IF_TIP, true);
    }

    public boolean showRandomTip() {
        return sharedPrefs.getBoolean(RANDOM_TIP, true);
    }

    public void setAltTip(boolean value) {
        sharedPrefs.edit().putBoolean(ALT_TIP, value).apply();
    }

    public void setWhatIfTip(boolean value) {
        sharedPrefs.edit().putBoolean(WHAT_IF_TIP, value).apply();
    }

    public void setRandomTip(boolean value) {
        sharedPrefs.edit().putBoolean(RANDOM_TIP, value).apply();
    }



    public boolean shareImage() {
        return prefs.getBoolean(SHARE_IMAGE, false);
    }

    public boolean shareMobile() {
        return prefs.getBoolean(SHARE_MOBILE, false);
    }

    public int getNotificationInterval() {
        String hours = prefs.getString(NOTIFICATIONS_INTERVAL, "0");
        switch (hours) {
            case "12":
            case "6":
                hours = "5";
                break;
        }
        return Integer.parseInt(hours) * 60 * 60 * 1000;
    }

    public boolean checkComicUpdated(int day) {
        switch (day) {
            case Calendar.MONDAY:
                return sharedPrefs.getBoolean(MONDAY_UPDATE, false);
            case Calendar.WEDNESDAY:
                return sharedPrefs.getBoolean(WEDNESDAY_UPDATE, false);
            case Calendar.FRIDAY:
                return sharedPrefs.getBoolean(FRIDAY_UPDATE, false);
        }
        return true;
    }

    public boolean checkWhatIfUpdated(int day) {
        switch (day) {
            case Calendar.TUESDAY:
                return sharedPrefs.getBoolean(TUESDAY_UPDATE_WHATIF, false);
            case Calendar.WEDNESDAY:
                return sharedPrefs.getBoolean(WEDNESDAY_UPDATE_WHATIF, false);
            case Calendar.THURSDAY:
                return sharedPrefs.getBoolean(THURSDAY_UPDATE_WHATIF, false);
        }
        return true;
    }

    public void setWhatIfUpdated(int day, boolean found) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        switch (day) {
            case Calendar.TUESDAY:
                editor.putBoolean(TUESDAY_UPDATE_WHATIF, found);
                editor.putBoolean(WEDNESDAY_UPDATE_WHATIF, false);
                editor.putBoolean(THURSDAY_UPDATE_WHATIF, false);
                break;
            case Calendar.WEDNESDAY:
                editor.putBoolean(WEDNESDAY_UPDATE_WHATIF, found);
                editor.putBoolean(TUESDAY_UPDATE_WHATIF, false);
                editor.putBoolean(THURSDAY_UPDATE_WHATIF, false);
                break;
            case Calendar.THURSDAY:
                editor.putBoolean(THURSDAY_UPDATE_WHATIF, found);
                editor.putBoolean(TUESDAY_UPDATE_WHATIF, false);
                editor.putBoolean(WEDNESDAY_UPDATE_WHATIF, false);
                break;
        }
        editor.apply();
        Log.d("Update Status WhatIf:", String.valueOf(sharedPrefs.getBoolean(TUESDAY_UPDATE_WHATIF, false))
                + String.valueOf(sharedPrefs.getBoolean(WEDNESDAY_UPDATE_WHATIF, false))
                + String.valueOf(sharedPrefs.getBoolean(THURSDAY_UPDATE_WHATIF, false)));
    }

    public void setUpdated(int day, boolean found) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        switch (day) {
            case Calendar.MONDAY:
                editor.putBoolean(MONDAY_UPDATE, found);
                editor.putBoolean(WEDNESDAY_UPDATE, false);
                editor.putBoolean(FRIDAY_UPDATE, false);
                break;
            case Calendar.WEDNESDAY:
                editor.putBoolean(WEDNESDAY_UPDATE, found);
                editor.putBoolean(FRIDAY_UPDATE, false);
                editor.putBoolean(MONDAY_UPDATE, false);
                break;
            case Calendar.FRIDAY:
                editor.putBoolean(FRIDAY_UPDATE, found);
                editor.putBoolean(MONDAY_UPDATE, false);
                editor.putBoolean(WEDNESDAY_UPDATE, false);
                break;
        }
        editor.apply();
        Log.d("Update Status:", String.valueOf(sharedPrefs.getBoolean(MONDAY_UPDATE, false))
                + String.valueOf(sharedPrefs.getBoolean(WEDNESDAY_UPDATE, false))
                + String.valueOf(sharedPrefs.getBoolean(FRIDAY_UPDATE, false)));
    }

    public boolean altByDefault() {
        return prefs.getStringSet(ALT_OPTIONS, new HashSet<String>()).contains(ALT_DEFAULT);
    }

    public int getLastWhatIf() {
        return sharedPrefs.getInt(LAST_WHATIF, 0);
    }

    public void setLastWhatIf(int number) {
        sharedPrefs.edit().putInt(LAST_WHATIF, number).apply();
    }

    /*public boolean WhatIfNightModeEnabled() {
        return sharedPrefs.getBoolean(WHATIF_NIGHT_MODE, false);
    }

    public void setWhatIfNightMode(boolean value) {
        sharedPrefs.edit().putBoolean(WHATIF_NIGHT_MODE, value).apply();
    }*/

    public void setWhatifRead(String added) {
        String read = sharedPrefs.getString(WHATIF_READ, "");
        if (!read.equals("")) {
            read = read + "," + added;
        } else {
            read = added;
        }
        sharedPrefs.edit().putString(WHATIF_READ, read).apply();
    }

    public void setAllUnread() {
        sharedPrefs.edit().putString(WHATIF_READ, "").apply();
    }

    public void setAllWhatIfRead() {
        for (int i = 1; i<=getNewestWhatIf(); i++)
            setWhatifRead(String.valueOf(i));
    }

    public boolean checkRead(int number) {
        String read = sharedPrefs.getString(WHATIF_READ, "");
        if (read.equals("")) {
            return false;
        }
        String[] readList = read.split(",");
        int[] readInt = new int[readList.length];
        for (int i = 0; i < readInt.length; i++)
            readInt[i] = Integer.parseInt(readList[i]);
        Arrays.sort(readInt);

        int a = Arrays.binarySearch(readInt, number);
        return (a >= 0);
    }

    /*public void setComicRead(String added) {
        String read = sharedPrefs.getString(COMIC_READ, "");
        if (!read.equals("")) {
            read = read + "," + added;
        } else {
            read = added;
        }
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(COMIC_READ, read);
        editor.apply();
    }*/

    /*public int[] getComicRead() {
        String read = sharedPrefs.getString(COMIC_READ, "");
        if (!read.equals("")) {
            String[] readList = Favorites.sortArray(read.split(","));
            int[] readInt = new int[readList.length];
            for (int i = 0; i < readInt.length; i++) {
                readInt[i] = Integer.parseInt(readList[i]);
            }
            return readInt;
        }
        return null;
    } */

    public void setWhatIfFavorite(String added) {
        String fav = sharedPrefs.getString(WHATIF_FAV, "");
        if (!fav.equals("")) {
            fav = fav + "," + added;
        } else {
            fav = added;
        }
        sharedPrefs.edit().putString(WHATIF_FAV, fav).apply();
    }

    public boolean checkWhatIfFav(int number) {
        String fav = sharedPrefs.getString(WHATIF_FAV, "");
        if (fav.equals("")) {
            return false;
        }
        String[] favList = fav.split(",");
        int[] favInt = new int[favList.length];
        for (int i = 0; i < favInt.length; i++)
            favInt[i] = Integer.parseInt(favList[i]);
        Arrays.sort(favInt);

        int a = Arrays.binarySearch(favInt, number);
        return (a >= 0);
    }

    public void removeWhatifFav(int number) {
        String[] old = sharedPrefs.getString(WHATIF_FAV, "").split(",");
        int[] oldInt = new int[old.length];
        for (int i = 0; i < old.length; i++)
            oldInt[i] = Integer.parseInt(old[i]);
        Arrays.sort(oldInt);

        int a = Arrays.binarySearch(oldInt, number);
        String[] out = new String[old.length - 1];
        if (out.length != 0 && a >= 0) {
            System.arraycopy(old, 0, out, 0, a);
            System.arraycopy(old, a + 1, out, a, out.length - a);
            StringBuilder sb = new StringBuilder();
            sb.append(out[0]);
            for (int i = 1; i < out.length; i++) {
                sb.append(",");
                sb.append(out[i]);
            }
            sharedPrefs.edit().putString(WHATIF_FAV, sb.toString()).apply();
        } else {
            sharedPrefs.edit().putString(WHATIF_FAV, "").apply();
        }

    }

    /*public void setComicsUnread() {
        sharedPrefs.edit().putString(COMIC_READ, "").apply();
    }*/

    public boolean hideReadWhatIf() {
        return sharedPrefs.getBoolean(HIDE_READ_WHATIF, false);
    }

    public void setHideReadWhatIf(boolean value) {
        sharedPrefs.edit().putBoolean(HIDE_READ_WHATIF, value).apply();
    }

    public boolean hideRead() {
        return sharedPrefs.getBoolean(HIDE_READ_OVERVIEW, false);
    }

    public void setHideRead(boolean value) {
        sharedPrefs.edit().putBoolean(HIDE_READ_OVERVIEW, value).apply();
    }

    public boolean swipeEnabled() {
        return sharedPrefs.getBoolean(SWIPE_ENABLED, false);
    }

    public void setSwipeEnabled(boolean value) {
        sharedPrefs.edit().putBoolean(SWIPE_ENABLED, value).apply();
    }

    public boolean showRateDialog() {
        int n = sharedPrefs.getInt(RATE_SNACKBAR, 0);
        if (n < 31) {
            SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putInt(RATE_SNACKBAR, n + 1);
            editor.apply();
        }
        return (n == 12 | n == 30);
    }

    public void showRateSnackbar(final String packageName, final Context context, FloatingActionButton mFab) {
        // Thanks to /u/underhound for this great idea! https://www.reddit.com/r/Android/comments/3i6uw0/dev_i_think_ive_mastered_the_art_of_asking_for/
        if (showRateDialog()) {
            View.OnClickListener oc = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Uri uri = Uri.parse("market://details?id=" + packageName);
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                    SharedPreferences.Editor editor = sharedPrefs.edit();
                    editor.putInt(RATE_SNACKBAR, 32);
                    editor.apply();
                    try {
                        context.startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + packageName)));
                    }
                }
            };
            Snackbar.make(mFab, R.string.snackbar_rate, Snackbar.LENGTH_LONG)
                    .setAction(R.string.snackbar_rate_action, oc)
                    .show();
        }
    }

    public int getNewestWhatIf() {
        return sharedPrefs.getInt(NEWEST_WHATIF, 1);
    }

    public void setNewestWhatif(int number) {
        sharedPrefs.edit().putInt(NEWEST_WHATIF, number).apply();
    }

    public void setFullOfflineWhatIf(boolean value) {
        prefs.edit().putBoolean(WHATIF_OFFLINE, value).apply();
    }

    public boolean fullOfflineWhatIf() {
        return prefs.getBoolean(WHATIF_OFFLINE, false);
    }

    public void setWhatIfTitles(String titles) {
        sharedPrefs.edit().putString(WHATIF_TITLES, titles).apply();
    }

    public ArrayList<String> getWhatIfTitles() {
        String titles = sharedPrefs.getString(WHATIF_TITLES, "");
        return new ArrayList<>(Arrays.asList(titles.split("&&")));
    }

    public boolean nomediaCreated() {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        boolean created = sharedPrefs.getBoolean(NOMEDIA_CREATED, false);
        if (!created)
            editor.putBoolean(NOMEDIA_CREATED, true).apply();
        return created;
    }

    public boolean shareAlt() {
        return prefs.getStringSet(ALT_OPTIONS, new HashSet<String>()).contains(SHARE_ALT);
    }

    public int getZoom(int webDefault) {
        int i;
        try {
            i = Integer.parseInt(prefs.getString(PREF_ZOOM, String.valueOf(webDefault)));
        } catch (Exception e) {
            i = webDefault;
        }
        return i;
    }

    public boolean hideDonate() {
        return prefs.getBoolean(PREF_DONATE, false);
    }

    public void setHideDonate(boolean value) {
        prefs.edit().putBoolean(PREF_DONATE, value).apply();
    }

    public boolean altLongTap() {
        return Integer.parseInt(prefs.getString(ALT_ACTIVATION, "1")) == 1;
    }

    public void showSurveySnackbar(final Context context, FloatingActionButton fab) {
        int n = sharedPrefs.getInt("survey count", 0);
        if (!sharedPrefs.getBoolean(SURVEY_SNACKBAR, false) && n == 15) {
            View.OnClickListener oc = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Uri uri = Uri.parse("https://docs.google.com/forms/d/1c9hb80NU67CImMKAlOH4p9-F63_duC7qAL30FhJv9Xg/viewform?usp=send_form");
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    context.startActivity(intent);
                }
            };
            sharedPrefs.edit().putBoolean(SURVEY_SNACKBAR, true).apply();
            Snackbar.make(fab, R.string.snackbar_survey, Snackbar.LENGTH_LONG)
                    .setAction(R.string.snackbar_survey_oc, oc)
                    .show();
        } else if (n < 15) {
            sharedPrefs.edit().putInt("survey count", n + 1).apply();
        }
    }

    public void showFeatureSnackbar(final Activity activity, FloatingActionButton fab) {
        if (!sharedPrefs.getBoolean(CUSTOM_THEMES_SNACKBAR, false)) {
            View.OnClickListener oc = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(activity, NestedSettingsActivity.class);
                    intent.putExtra("key", "appearance");
                    activity.startActivityForResult(intent, 1);
                }
            };
            SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putBoolean(CUSTOM_THEMES_SNACKBAR, true);
            editor.apply();
            Snackbar.make(fab, R.string.snackbar_feature, Snackbar.LENGTH_LONG)
                    .setAction(R.string.snackbar_feature_oc, oc)
                    .show();
        }
    }

    public int getRandomNumber(int current) {
        if (randList == null) {
            randList = new ArrayList<>();
            for (int i = 1; i < getNewest(); i++) {
                if (i != current)
                    randList.add(i);
            }
            Collections.shuffle(randList);
            randList.add(0, current);
        }
        int result;
        if (randIndex == 0) {
            result = randList.get(randIndex + 1);
            randIndex++;
        } else {
            randIndex++;
            result = randList.get(randIndex);
        }
        return result;
    }

    public int getPreviousRandom(int i) {
        if (randList != null && randIndex > 0) {
            int result;
            if (randIndex == 1) {
                randIndex--;
                result = randList.get(randIndex);
            } else {
                result = randList.get(randIndex - 1);
                randIndex--;
            }
            return result;
        }
        return i;
    }

    public boolean colorNavbar() {
        return prefs.getBoolean(COLORED_NAVBAR, true);
    }

    public boolean mobileEnabled() {
        return prefs.getBoolean(MOBILE_ENABLED, true);
    }

    /*public boolean autoNightEnabled() {
        return prefs.getBoolean(AUTO_NIGHT, false);
    }*/

    /*public boolean nightThemeEnabled() {
        try {
            if (prefs.getBoolean(NIGHT_THEME, false) && autoNightEnabled()) {
                int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                int minute = Calendar.getInstance().get(Calendar.MINUTE);
                int[] start = getAutoNightStart();
                int[] end = getAutoNightEnd();
                if (hour == start[0]) {
                    return minute >= start[1];
                }
                if (hour == end[0]) {
                    return minute < end[1];
                }
                if (hour > start[0] && hour > end[0] && end[0] >= start[0]) {
                    return false;
                }
                if (hour > start[0]) {*/
                /*if (end[0] > start[0]) {
                    return hour < end[0];
                } else {
                    return true;
                } */
                /*    return end[0] <= start[0] || hour < end[0];
                } else {
                    return hour < end[0];
                }
            } else {
                return prefs.getBoolean(NIGHT_THEME, false);
            }
        } catch (NullPointerException e) {
            Log.e("error", "night theme null pointer");
            return false;
        }
    }*/

    /*public int[] getAutoNightStart() {
        return new int[]{
                sharedPrefs.getInt(AUTO_NIGHT_START_HOUR, 21),
                sharedPrefs.getInt(AUTO_NIGHT_START_MIN, 0)
        };
    }

    public int[] getAutoNightEnd() {
        return new int[]{
                sharedPrefs.getInt(AUTO_NIGHT_END_HOUR, 8),
                sharedPrefs.getInt(AUTO_NIGHT_END_MIN, 0)
        };
    }

    public void setAutoNightStart(int[] time) {
        sharedPrefs.edit()
                .putInt(AUTO_NIGHT_START_HOUR, time[0])
                .putInt(AUTO_NIGHT_START_MIN, time[1])
                .apply();
    }

    public void setAutoNightEnd(int[] time) {
        sharedPrefs.edit()
                .putInt(AUTO_NIGHT_END_HOUR, time[0])
                .putInt(AUTO_NIGHT_END_MIN, time[1])
                .apply();
    }*/

    /*public String getStartSummary() {
        int[] start = getAutoNightStart();
        String suffix = "";
        String minute = String.valueOf(start[1]);
        if (start[1] < 10) {
            minute = "0" + minute;
        }
        return start[0] + ":" + minute + suffix;
    }

    public String getEndSummary() {
        int[] end = getAutoNightEnd();
        String suffix = "";
        String minute = String.valueOf(end[1]);
        if (end[1] < 10) {
            minute = "0" + minute;
        }
        return end[0] + ":" + minute + suffix;
    } */

    public File getOfflinePath() {
        String path = prefs.getString(OFFLINE_PATH, "default");
        if (path.equals("default"))
            return Environment.getExternalStorageDirectory();
        return new File(path);
    }

    public void setOfflinePath(String path) {
        prefs.edit().putString(OFFLINE_PATH, path).apply();
    }

    public boolean isWifi(Context context) {
        if (context == null) {
            Log.e("error", "context null");
            return true;
        }
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return (info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI);
    }

    public boolean isOnline(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }

    public boolean scrollDisabledWhileZoom() {
        return prefs.getBoolean(ZOOM_SCROLL, true);
    }

    public boolean overviewFav() {
        return sharedPrefs.getBoolean(OVERVIEW_FAV, false);
    }

    public void setOverviewFav(boolean value) {
        sharedPrefs.edit().putBoolean(OVERVIEW_FAV, value).apply();
    }

    public boolean defaultZoom() {
        return prefs.getBoolean(DEFAULT_ZOOM, true);
    }

    public int getOverviewStyle() {
        return sharedPrefs.getInt(OVERVIEW_STYLE, 1);
    }

    public void setOverviewStyle(int style) {
        sharedPrefs.edit().putInt(OVERVIEW_STYLE, style).apply();
    }

    public void setBookmark(int number) {
        sharedPrefs.edit().putInt(BOOKMARK, number).apply();
    }

    public int getBookmark() {
        return sharedPrefs.getInt(BOOKMARK, 0);
    }

    public boolean sunBeamDownloaded() {
        return sharedPrefs.getBoolean(WHAT_IF_SUNBEAM_LOADED, false);
    }

    public void setSunbeamLoaded() {
        sharedPrefs.edit().putBoolean(WHAT_IF_SUNBEAM_LOADED, true).apply();
    }

    public boolean launchToOverview() {
        return prefs.getBoolean(LAUNCH_TO_OVERVIEW, false);
    }

    public boolean includeLink() {return prefs.getBoolean(INCLUDE_LINK, false);}

    public boolean widgetShowAlt() {return prefs.getBoolean(WIDGET_ALT, false);}

    public boolean widgetShowComicNumber()  {return prefs.getBoolean(WIDGET_COMIC_NUMBER, true);}

}



























