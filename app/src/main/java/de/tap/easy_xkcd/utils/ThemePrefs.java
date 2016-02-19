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
 * <p/>
 * Inspired by the excellent Reddit App Slide: https://github.com/ccrama/Slide
 */

package de.tap.easy_xkcd.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.support.design.widget.NavigationView;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.tap.xkcd_reader.R;

import java.util.Calendar;

public class ThemePrefs {
    private Context context;

    private static final String THEME = "pref_theme";
    private static final String COLOR_PRIMARY = "pref_color_primary";
    private static final String COLOR_PRIMARY_DARK = "pref_color_primary_dark";
    private static final String COLOR_ACCENT = "pref_color_accent";
    private static final String COLOR_ACCENT_NIGHT = "pref_color_accent_night";
    private static final String NIGHT_THEME = "pref_night";
    private static final String WHATIF_NIGHT_MODE = "night_mode";
    private static final String AUTO_NIGHT = "pref_auto_night";
    private static final String AUTO_NIGHT_START_MIN = "pref_auto_night_start_min";
    private static final String AUTO_NIGHT_START_HOUR = "pref_auto_night_start_hour";
    private static final String AUTO_NIGHT_END_MIN = "pref_auto_night_end_min";
    private static final String AUTO_NIGHT_END_HOUR = "pref_auto_night_end_hour";
    private static final String INVERT_COLORS = "pref_invert";

    public ThemePrefs(Context context) {
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

    public int getNewTheme() {
        //TODO import old theme
        int accent = getSharedPrefs().getInt(COLOR_ACCENT, -1);
        if (nightThemeEnabled()) {
            if (accent == ContextCompat.getColor(context, R.color.AccentPurple))
                return R.style.PurpleNightTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentIndigo))
                return R.style.IndigoNightTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentBlue))
                return R.style.BlueNightTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentLightBlue))
                return R.style.LightBlueNightTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentCyan))
                return R.style.CyanNightTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentTeal))
                return R.style.TealNightTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentGreen))
                return R.style.GreenNightTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentLightGreen))
                return R.style.LightBlueNightTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentLime))
                return R.style.LimeNightTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentYellow))
                return R.style.YellowNightTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentAmber))
                return R.style.AmberNightTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentOrange))
                return R.style.OrangeNightTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentDeepOrange))
                return R.style.DeepOrangeNightTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentRed))
                return R.style.RedNightTheme;
            return R.style.LimeNightTheme;
        } else {
            if (accent == ContextCompat.getColor(context, R.color.AccentPurple))
                return R.style.PurpleTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentIndigo))
                return R.style.IndigoTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentBlue))
                return R.style.BlueTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentLightBlue))
                return R.style.LightBlueTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentCyan))
                return R.style.CyanTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentTeal))
                return R.style.TealTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentGreen))
                return R.style.GreenTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentLightGreen))
                return R.style.LightBlueTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentLime))
                return R.style.LimeTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentYellow))
                return R.style.YellowTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentAmber))
                return R.style.AmberTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentOrange))
                return R.style.OrangeTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentDeepOrange))
                return R.style.DeepOrangeTheme;
            if (accent == ContextCompat.getColor(context, R.color.AccentRed))
                return R.style.RedTheme;
            return R.style.LimeTheme;
        }
    }

    public void setNewTheme(int accentColor) {
        editSharedPrefs().putInt(COLOR_ACCENT, accentColor).apply();
    }

    public int getAccentColorNight() {
        return getSharedPrefs().getInt(COLOR_ACCENT_NIGHT, getAccentColor());
    }

    public void setupNavdrawerColor(NavigationView navigationView) {
        int[][] state = new int[][] {
                new int[] {-android.R.attr.state_checked},
                new int[] {}
        };
        int[] color = new int[] {
                getNavDrawerTextColor(),
                getNavDrawerHightlightColor()
        };
        int[] colorIcon = new int[] {
                ContextCompat.getColor(context, android.R.color.tertiary_text_light),
                getNavDrawerHightlightColor()
        };
        navigationView.setItemTextColor(new ColorStateList(state, color));
        navigationView.setItemIconTintList(new ColorStateList(state, colorIcon));
    }

    public int getNavDrawerHightlightColor() {
        if (!nightThemeEnabled())
            return getPrimaryColor();
        return getAccentColorNight();
    }

    public int getNavDrawerTextColor() {
        if (!nightThemeEnabled())
            return Color.BLACK;
        return Color.WHITE;
    }

    public int getPrimaryColor() {
        if (!nightThemeEnabled())
            return getSharedPrefs().getInt(COLOR_PRIMARY, ContextCompat.getColor(context, R.color.PrimaryBlueGrey));
        return Color.BLACK;
    }

    public int getAccentColor() {
        return getSharedPrefs().getInt(COLOR_ACCENT, ContextCompat.getColor(context, R.color.AccentLime));
    }

    public void setPrimaryColor(int color) {
        editSharedPrefs().putInt(COLOR_PRIMARY, color).apply();
        int[] colors = getPrimaryColors();
        for (int i = 0; i < colors.length; i++)
            if (colors[i] == color)
                editSharedPrefs().putInt(COLOR_PRIMARY_DARK, getPrimaryDarkColors()[i]).apply();
    }

    public int getPrimaryDarkColor() {
        if (!nightThemeEnabled())
            return getSharedPrefs().getInt(COLOR_PRIMARY_DARK, ContextCompat.getColor(context, R.color.PrimaryDarkBlueGrey));
        return Color.BLACK;
    }


    public int getOldTheme() {
        return R.style.LimeTheme;
        /*if (nightThemeEnabled())
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
        }*/
    }


    public boolean invertColors() {
        return getPrefs().getBoolean(INVERT_COLORS, true) && nightThemeEnabled();
    }

    public int[] getAccentColors() {
        return new int[]{
                ContextCompat.getColor(context, R.color.AccentPurple),
                ContextCompat.getColor(context, R.color.AccentIndigo),
                ContextCompat.getColor(context, R.color.AccentBlue),
                ContextCompat.getColor(context, R.color.AccentLightBlue),
                ContextCompat.getColor(context, R.color.AccentCyan),
                ContextCompat.getColor(context, R.color.AccentTeal),
                ContextCompat.getColor(context, R.color.AccentGreen),
                ContextCompat.getColor(context, R.color.AccentLightGreen),
                ContextCompat.getColor(context, R.color.AccentLime),
                ContextCompat.getColor(context, R.color.AccentYellow),
                ContextCompat.getColor(context, R.color.AccentAmber),
                ContextCompat.getColor(context, R.color.AccentOrange),
                ContextCompat.getColor(context, R.color.AccentDeepOrange),
                ContextCompat.getColor(context, R.color.AccentRed)
        };
    }

    public int[] getPrimaryColors() {
        return new int[]{
                ContextCompat.getColor(context, R.color.PrimaryPurple),
                ContextCompat.getColor(context, R.color.PrimaryDeepPurple),
                ContextCompat.getColor(context, R.color.PrimaryIndigo),
                ContextCompat.getColor(context, R.color.PrimaryBlue),
                ContextCompat.getColor(context, R.color.PrimaryLightBlue),
                ContextCompat.getColor(context, R.color.PrimaryCyan),
                ContextCompat.getColor(context, R.color.PrimaryTeal),
                ContextCompat.getColor(context, R.color.PrimaryGreen),
                ContextCompat.getColor(context, R.color.PrimaryLightGreen),
                ContextCompat.getColor(context, R.color.PrimaryLime),
                ContextCompat.getColor(context, R.color.PrimaryYellow),
                ContextCompat.getColor(context, R.color.PrimaryAmber),
                ContextCompat.getColor(context, R.color.PrimaryOrange),
                ContextCompat.getColor(context, R.color.PrimaryDeepOrange),
                ContextCompat.getColor(context, R.color.PrimaryRed),
                ContextCompat.getColor(context, R.color.PrimaryBrown),
                ContextCompat.getColor(context, R.color.PrimaryGrey),
                ContextCompat.getColor(context, R.color.PrimaryBlueGrey),
                ContextCompat.getColor(context, R.color.PrimaryBlack)
        };
    }

    public int[] getPrimaryDarkColors() {
        return new int[]{
                ContextCompat.getColor(context, R.color.PrimaryDarkPurple),
                ContextCompat.getColor(context, R.color.PrimaryDarkDeepPurple),
                ContextCompat.getColor(context, R.color.PrimaryDarkIndigo),
                ContextCompat.getColor(context, R.color.PrimaryDarkBlue),
                ContextCompat.getColor(context, R.color.PrimaryDarkLightBlue),
                ContextCompat.getColor(context, R.color.PrimaryDarkCyan),
                ContextCompat.getColor(context, R.color.PrimaryDarkTeal),
                ContextCompat.getColor(context, R.color.PrimaryDarkGreen),
                ContextCompat.getColor(context, R.color.PrimaryDarkLightGreen),
                ContextCompat.getColor(context, R.color.PrimaryDarkLime),
                ContextCompat.getColor(context, R.color.PrimaryDarkYellow),
                ContextCompat.getColor(context, R.color.PrimaryDarkAmber),
                ContextCompat.getColor(context, R.color.PrimaryDarkOrange),
                ContextCompat.getColor(context, R.color.PrimaryDarkDeepOrange),
                ContextCompat.getColor(context, R.color.PrimaryDarkRed),
                ContextCompat.getColor(context, R.color.PrimaryDarkBrown),
                ContextCompat.getColor(context, R.color.PrimaryDarkGrey),
                ContextCompat.getColor(context, R.color.PrimaryDarkBlueGrey),
                ContextCompat.getColor(context, R.color.PrimaryDarkBlack)
        };
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

}

