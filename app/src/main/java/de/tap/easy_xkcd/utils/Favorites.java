/**********************************************************************************
 * Copyright 2015 Tom Praschan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ********************************************************************************/

package de.tap.easy_xkcd.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;

import de.tap.easy_xkcd.database.DatabaseManager;


public class Favorites {

    /**
     * Adapted from http://androidopentutorials.com/android-how-to-store-list-of-values-in-sharedpreferences/
     */

    private static final String FAVORITES = "favorites";

    public static boolean addFavoriteItem(Context context, String favoriteItem) {
        //Get previous favorite items
        String favoriteList = getStringFromPreferences(context, null, FAVORITES);
        // Append new Favorite item
        if (favoriteList != null) {
            favoriteList = favoriteList + "," + favoriteItem;
        } else {
            favoriteList = favoriteItem;
        }
        // Save in Shared Preferences
        return putStringInPreferences(context, favoriteList, FAVORITES);
    }

    public static boolean removeFavoriteItem(Context context, String favoriteItem) {
        String[] old = getFavoriteList(context);
        int[] oldInt = new int[old.length];
        for (int i = 0; i < old.length; i++) {
            oldInt[i] = Integer.parseInt(old[i]);
        }

        int a = Arrays.binarySearch(oldInt, Integer.valueOf(favoriteItem));
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
            return putStringInPreferences(context, sb.toString(), FAVORITES);
        } else {
            return putStringInPreferences(context, null, FAVORITES);
        }
    }

    public static String[] getFavoriteList(Context context) {
        String favoriteList = getStringFromPreferences(context, null, FAVORITES);
        return sortArray(convertStringToArray(favoriteList));
    }

    public static boolean checkFavorite(Context context, int number) {
        String[] favoriteList = sortArray(getFavoriteList(context));
        int[] favoriteListInt = new int[favoriteList.length];
        for (int i = 0; i < favoriteList.length; i++) {
            favoriteListInt[i] = Integer.parseInt(favoriteList[i]);
        }
        int a = Arrays.binarySearch(favoriteListInt, number);
        return (a >= 0);
    }

    public static String[] sortArray(String[] array) {
        int temp;
        for (int i = 1; i < array.length; i++) {
            temp = Integer.parseInt(array[i]);
            int j = i;
            while (j > 0 && Integer.parseInt(array[j - 1]) > temp) {
                String temp2 = array[j];
                array[j] = array[j - 1];
                array[j - 1] = temp2;
                j--;
            }
        }
        return array;
    }

    public static boolean putStringInPreferences(Context context, String nick, String key) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("MainActivity", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, nick);
        editor.commit();
        return true;
    }

    private static String getStringFromPreferences(Context context, String defaultValue, String key) {
        //SharedPreferences sharedPreferences = activity.getPreferences(Activity.MODE_PRIVATE);
        SharedPreferences sharedPreferences = context.getSharedPreferences("MainActivity", Activity.MODE_PRIVATE);
        return sharedPreferences.getString(key, defaultValue);
    }

    public static String[] convertStringToArray(String str) {
        if (str != null) {
            return str.split(",");
        } else {
            return new String[0];
        }
    }
}
