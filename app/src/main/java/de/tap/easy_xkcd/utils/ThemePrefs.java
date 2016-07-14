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
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.preference.PreferenceManager;
import android.support.design.widget.NavigationView;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.CircularArray;
import android.support.v7.graphics.Palette;
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

    public boolean WhatIfNightModeEnabled() {
        return getSharedPrefs().getBoolean(WHATIF_NIGHT_MODE, false);
    }

    public void setWhatIfNightMode(boolean value) {
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

    public void setNightThemeEnabled(boolean enabled) {
        getPrefs().edit().putBoolean(NIGHT_THEME, enabled).apply();
        setWhatIfNightMode(enabled);
    }

    public boolean nightEnabledThemeIgnoreAutoNight() {
        return getPrefs().getBoolean(NIGHT_THEME, false);
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
        int accent = getSharedPrefs().getInt(COLOR_ACCENT, -1);
        if (accent == -1)
            accent = importOldTheme();

        if (nightThemeEnabled()) {
            accent = getAccentColorNight();
            if (accent == getColor(R.color.AccentPurple))
                return R.style.PurpleNightTheme;
            if (accent == getColor(R.color.AccentIndigo))
                return R.style.IndigoNightTheme;
            if (accent == getColor(R.color.AccentBlue))
                return R.style.BlueNightTheme;
            if (accent == getColor(R.color.AccentLightBlue))
                return R.style.LightBlueNightTheme;
            if (accent == getColor(R.color.AccentCyan))
                return R.style.CyanNightTheme;
            if (accent == getColor(R.color.AccentTeal))
                return R.style.TealNightTheme;
            if (accent == getColor(R.color.AccentGreen))
                return R.style.GreenNightTheme;
            if (accent == getColor(R.color.AccentLightGreen))
                return R.style.LightBlueNightTheme;
            if (accent == getColor(R.color.AccentLime))
                return R.style.LimeNightTheme;
            if (accent == getColor(R.color.AccentYellow))
                return R.style.YellowNightTheme;
            if (accent == getColor(R.color.AccentAmber))
                return R.style.AmberNightTheme;
            if (accent == getColor(R.color.AccentOrange))
                return R.style.OrangeNightTheme;
            if (accent == getColor(R.color.AccentDeepOrange))
                return R.style.DeepOrangeNightTheme;
            if (accent == getColor(R.color.AccentRed))
                return R.style.RedNightTheme;
            return R.style.LimeNightTheme;
        } else {
            if (accent == getColor(R.color.AccentPurple))
                return R.style.PurpleTheme;
            if (accent == getColor(R.color.AccentIndigo))
                return R.style.IndigoTheme;
            if (accent == getColor(R.color.AccentBlue))
                return R.style.BlueTheme;
            if (accent == getColor(R.color.AccentLightBlue))
                return R.style.LightBlueTheme;
            if (accent == getColor(R.color.AccentCyan))
                return R.style.CyanTheme;
            if (accent == getColor(R.color.AccentTeal))
                return R.style.TealTheme;
            if (accent == getColor(R.color.AccentGreen))
                return R.style.GreenTheme;
            if (accent == getColor(R.color.AccentLightGreen))
                return R.style.LightBlueTheme;
            if (accent == getColor(R.color.AccentLime))
                return R.style.LimeTheme;
            if (accent == getColor(R.color.AccentYellow))
                return R.style.YellowTheme;
            if (accent == getColor(R.color.AccentAmber))
                return R.style.AmberTheme;
            if (accent == getColor(R.color.AccentOrange))
                return R.style.OrangeTheme;
            if (accent == getColor(R.color.AccentDeepOrange))
                return R.style.DeepOrangeTheme;
            if (accent == getColor(R.color.AccentRed))
                return R.style.RedTheme;
            return R.style.LimeTheme;
        }
    }

    public int importOldTheme() {
        int oldTheme = getOldTheme();
        int oldAccent, oldPrimary;
        switch (oldTheme) {
            case 2:
                oldAccent = R.color.AccentBlue;
                oldPrimary = R.color.PrimaryRed;
                break;
            case 3:
                oldAccent = R.color.AccentOrange;
                oldPrimary = R.color.PrimaryBlue;
                break;
            case 4:
                oldAccent = R.color.AccentOrange;
                oldPrimary = R.color.PrimaryBlack;
                break;
            case 5:
                oldAccent = R.color.AccentRed;
                oldPrimary = R.color.PrimaryPurple;
                break;
            case 6:
                oldAccent = R.color.AccentRed;
                oldPrimary = R.color.PrimaryLime;
                break;
            case 7:
                oldAccent = R.color.AccentRed;
                oldPrimary = R.color.PrimaryGreen;
                break;
            default:
                oldAccent = R.color.AccentLime;
                oldPrimary = R.color.PrimaryBlueGrey;
        }
        setNewTheme(getColor(oldAccent));
        setPrimaryColor(getColor(oldPrimary));
        return getColor(oldAccent);
    }

    public int getOldTheme() {
        return Integer.parseInt(getPrefs().getString(THEME, "1"));
    }

    public void setNewTheme(int accentColor) {
        editSharedPrefs().putInt(COLOR_ACCENT, accentColor).apply();
    }

    public int getAccentColorNight() {
        return getSharedPrefs().getInt(COLOR_ACCENT_NIGHT, getAccentColor());
    }

    public void setAccentColorNight(int color) {
        editSharedPrefs().putInt(COLOR_ACCENT_NIGHT, color).apply();
    }

    public void setupNavdrawerColor(NavigationView navigationView) {
        int[][] state = new int[][]{
                new int[]{-android.R.attr.state_checked},
                new int[]{}
        };
        int[] color = new int[]{
                getNavDrawerTextColor(),
                getNavDrawerHightlightColor()
        };
        int[] colorIcon = new int[]{
                getColor(android.R.color.tertiary_text_light),
                getNavDrawerHightlightColor()
        };
        navigationView.setItemTextColor(new ColorStateList(state, color));
        navigationView.setItemIconTintList(new ColorStateList(state, colorIcon));
    }

    public int getNavDrawerHightlightColor() {
        if (!nightThemeEnabled())
            return getPrimaryColor(true);
        return getAccentColorNight();
    }

    public int getNavDrawerTextColor() {
        if (!nightThemeEnabled())
            return Color.BLACK;
        return Color.WHITE;
    }

    public int getPrimaryColor(boolean ignoreNightTheme) {
        if (ignoreNightTheme || !nightThemeEnabled())
            return getSharedPrefs().getInt(COLOR_PRIMARY, getColor(R.color.PrimaryBlueGrey));
        return Color.BLACK;
    }

    public int getAccentColor() {
        return getSharedPrefs().getInt(COLOR_ACCENT, getColor(R.color.AccentLime));
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
            return getSharedPrefs().getInt(COLOR_PRIMARY_DARK, getColor(R.color.PrimaryDarkBlueGrey));
        return Color.BLACK;
    }


    public boolean invertColors() {
        return getPrefs().getBoolean(INVERT_COLORS, true) && nightThemeEnabled();
    }

    public int[] getAccentColors() {
        return new int[]{
                getColor(R.color.AccentPurple),
                getColor(R.color.AccentIndigo),
                getColor(R.color.AccentBlue),
                getColor(R.color.AccentLightBlue),
                getColor(R.color.AccentCyan),
                getColor(R.color.AccentTeal),
                getColor(R.color.AccentGreen),
                //getColor(R.color.AccentLightGreen), this color didn't work for some reason
                getColor(R.color.AccentLime),
                getColor(R.color.AccentYellow),
                getColor(R.color.AccentAmber),
                getColor(R.color.AccentOrange),
                getColor(R.color.AccentDeepOrange),
                getColor(R.color.AccentRed),
        };
    }

    public int[] getPrimaryColors() {
        return new int[]{
                getColor(R.color.PrimaryPurple),
                getColor(R.color.PrimaryDeepPurple),
                getColor(R.color.PrimaryIndigo),
                getColor(R.color.PrimaryBlue),
                getColor(R.color.PrimaryLightBlue),
                getColor(R.color.PrimaryCyan),
                getColor(R.color.PrimaryTeal),
                getColor(R.color.PrimaryGreen),
                getColor(R.color.PrimaryLightGreen),
                getColor(R.color.PrimaryLime),
                getColor(R.color.PrimaryYellow),
                getColor(R.color.PrimaryAmber),
                getColor(R.color.PrimaryOrange),
                getColor(R.color.PrimaryDeepOrange),
                getColor(R.color.PrimaryRed),
                getColor(R.color.PrimaryBrown),
                getColor(R.color.PrimaryGrey),
                getColor(R.color.PrimaryBlueGrey),
                getColor(R.color.PrimaryBlack)
        };
    }

    public int[] getPrimaryDarkColors() {
        return new int[]{
                getColor(R.color.PrimaryDarkPurple),
                getColor(R.color.PrimaryDarkDeepPurple),
                getColor(R.color.PrimaryDarkIndigo),
                getColor(R.color.PrimaryDarkBlue),
                getColor(R.color.PrimaryDarkLightBlue),
                getColor(R.color.PrimaryDarkCyan),
                getColor(R.color.PrimaryDarkTeal),
                getColor(R.color.PrimaryDarkGreen),
                getColor(R.color.PrimaryDarkLightGreen),
                getColor(R.color.PrimaryDarkLime),
                getColor(R.color.PrimaryDarkYellow),
                getColor(R.color.PrimaryDarkAmber),
                getColor(R.color.PrimaryDarkOrange),
                getColor(R.color.PrimaryDarkDeepOrange),
                getColor(R.color.PrimaryDarkRed),
                getColor(R.color.PrimaryDarkBrown),
                getColor(R.color.PrimaryDarkGrey),
                getColor(R.color.PrimaryDarkBlueGrey),
                getColor(R.color.PrimaryDarkBlack)
        };
    }

    public ColorFilter getNegativeColorFilter() {
        float[] colorMatrix_Negative = {
                -1.0f, 0, 0, 0, 255, //red
                0, -1.0f, 0, 0, 255, //green
                0, 0, -1.0f, 0, 255, //blue
                0, 0, 0, 1.0f, 0 //alpha
        };
        return new ColorMatrixColorFilter(colorMatrix_Negative);
    }

    public boolean bitmapContainsColor(Bitmap bitmap) {
        Palette palette = Palette.from(bitmap).generate();
        return palette.getVibrantSwatch() != null;
    }

    private int getColor(int color) {
        return ContextCompat.getColor(context, color);
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

