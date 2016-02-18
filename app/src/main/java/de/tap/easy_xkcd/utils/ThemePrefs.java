package de.tap.easy_xkcd.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.tap.xkcd_reader.R;

import java.util.Calendar;

public class ThemePrefs {
    private Context context;

    private static final String THEME = "pref_theme";
    private static final String NIGHT_THEME = "pref_night";
    private static final String WHATIF_NIGHT_MODE = "night_mode";
    private static final String AUTO_NIGHT = "pref_auto_night";
    private static final String AUTO_NIGHT_START_MIN = "pref_auto_night_start_min";
    private static final String AUTO_NIGHT_START_HOUR = "pref_auto_night_start_hour";
    private static final String AUTO_NIGHT_END_MIN = "pref_auto_night_end_min";
    private static final String AUTO_NIGHT_END_HOUR = "pref_auto_night_end_hour";
    private static final String INVERT_COLORS = "pref_invert";

    public ThemePrefs (Context context) {
        this.context = context;
    }

    public boolean nightModeEnabled() {
        return getSharedPrefs().getBoolean(WHATIF_NIGHT_MODE, false);
    }

    public void setNightMode(boolean value) {
        getSharedPrefs().edit().putBoolean(WHATIF_NIGHT_MODE, value).apply();
    }

    public int[] getAutoNightStart() {
        return new int[]{
                getSharedPrefs().getInt(AUTO_NIGHT_START_HOUR, 21),
                getSharedPrefs().getInt(AUTO_NIGHT_START_MIN, 0)
        };
    }

    public int[] getAutoNightEnd() {
        return new int[]{
                getSharedPrefs().getInt(AUTO_NIGHT_END_HOUR, 8),
                getSharedPrefs().getInt(AUTO_NIGHT_END_MIN, 0)
        };
    }

    public void setAutoNightStart(int[] time) {
        editSharedPrefs()
                .putInt(AUTO_NIGHT_START_HOUR, time[0])
                .putInt(AUTO_NIGHT_START_MIN, time[1])
                .apply();
    }

    public void setAutoNightEnd(int[] time) {
        editSharedPrefs()
                .putInt(AUTO_NIGHT_END_HOUR, time[0])
                .putInt(AUTO_NIGHT_END_MIN, time[1])
                .apply();
    }

    public String getStartSummary() {
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
    }

    public boolean autoNightEnabled() {
        return getPrefs().getBoolean(AUTO_NIGHT, false);
    }

    public boolean nightThemeEnabled() {
        try {
            if (getPrefs().getBoolean(NIGHT_THEME, false) && autoNightEnabled()) {
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
                if (hour > start[0]) {
                    return end[0] <= start[0] || hour < end[0];
                } else {
                    return hour < end[0];
                }
            } else {
                return getPrefs().getBoolean(NIGHT_THEME, false);
            }
        } catch (NullPointerException e) {
            Log.e("error", "night theme null pointer");
            return false;
        }
    }

    public int getOldTheme() {
        if (nightThemeEnabled())
            return R.style.NightTheme;

        int n = Integer.parseInt(getPrefs().getString(THEME, "1"));
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

    public int getDialogTheme() {
        if (nightThemeEnabled())
            return R.style.AlertDialogNight;
        int n = Integer.parseInt(getPrefs().getString(THEME, "1"));
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

    public boolean invertColors() {
        return getPrefs().getBoolean(INVERT_COLORS, true) && nightThemeEnabled();
    }

    private SharedPreferences getSharedPrefs() {
        return context.getSharedPreferences("MainActivity", Activity.MODE_PRIVATE);
    }

    private SharedPreferences getPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private SharedPreferences.Editor editSharedPrefs() {
        return context.getSharedPreferences("MainActivity", Activity.MODE_PRIVATE).edit();
    }

    private SharedPreferences.Editor editPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(context).edit();
    }

}

