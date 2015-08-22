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

package de.tap.easy_xkcd;

import android.app.Activity;
import android.content.SharedPreferences;

import java.util.Arrays;


public class Favorites {

    /**
     * Adapted from http://androidopentutorials.com/android-how-to-store-list-of-values-in-sharedpreferences/
     */

    public static boolean addFavoriteItem(Activity activity, String favoriteItem) {
        //Get previous favorite items
        String favoriteList = getStringFromPreferences(activity, null, "favorites");
        // Append new Favorite item
        if (favoriteList != null) {
            favoriteList = favoriteList + "," + favoriteItem;
        } else {
            favoriteList = favoriteItem;
        }
        // Save in Shared Preferences
        return putStringInPreferences(activity, favoriteList, "favorites");
    }

    public static boolean removeFavoriteItem(Activity activity, String favoriteItem) {
        String[] old = getFavoriteList(activity);
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
            return putStringInPreferences(activity, sb.toString(), "favorites");
        } else {
            return putStringInPreferences(activity, null, "favorites");
        }
    }

    public static String[] getFavoriteList(Activity activity) {
        String favoriteList = getStringFromPreferences(activity, null, "favorites");
        return sortArray(convertStringToArray(favoriteList));
    }

    public static boolean checkFavorite(Activity activity, int number) {
        String[] favoriteList = sortArray(getFavoriteList(activity));
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

    public static boolean putStringInPreferences(Activity activity, String nick, String key) {
        SharedPreferences sharedPreferences = activity.getPreferences(Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, nick);
        editor.commit();
        return true;
    }

    private static String getStringFromPreferences(Activity activity, String defaultValue, String key) {
        SharedPreferences sharedPreferences = activity.getPreferences(Activity.MODE_PRIVATE);
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
