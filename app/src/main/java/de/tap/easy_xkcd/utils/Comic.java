/**********************************************************************************
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
 ********************************************************************************/

package de.tap.easy_xkcd.utils;

import android.content.Context;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import com.tap.xkcd_reader.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.StringTokenizer;


public class Comic {
    private String[] comicData;
    private String jsonUrl;
    protected int comicNumber;
    private JSONObject json;

    public Comic(Integer number, Context context) throws IOException {
        if (number != 0) {
            jsonUrl = "https://xkcd.com/" + number.toString() + "/info.0.json";
        } else {
            jsonUrl = "https://xkcd.com/info.0.json";
        }
        try {
            json = JsonParser.getJSONFromUrl(jsonUrl);
            comicData = loadComicData(context);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (context != null) {
            if (Arrays.binarySearch(context.getResources().getIntArray(R.array.interactive_comics), comicNumber) >= 0) { //Check for interactive comic
                comicData[0] = comicData[0] + " " + context.getResources().getString(R.string.title_interactive);
            }

            int i = Arrays.binarySearch(context.getResources().getIntArray(R.array.large_comics), comicNumber);
            if (i >= 0 && PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_large", true)) { //Check for large comic
                comicData[2] = context.getResources().getStringArray(R.array.large_comics_urls)[i];
            }
        }
    }

    public Comic(int number, JSONObject json) throws IOException {
        try {
            comicData = loadComicData(null);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public Comic(Integer number) throws IOException {
        if (number != 0) {
            jsonUrl = "https://xkcd.com/" + number.toString() + "/info.0.json";
        } else {
            jsonUrl = "https://xkcd.com/info.0.json";
        }
        try {
            json = JsonParser.getJSONFromUrl(jsonUrl);
            comicData = loadComicData(null);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public Comic() {}

    private String[] loadComicData(Context context) throws IOException, JSONException {
        String[] result = new String[3];
        if (json != null) {
            result[0] = new String(json.getString("title").getBytes("ISO-8859-1"), "UTF-8");
            result[1] = new String(json.getString("alt").getBytes("ISO-8859-1"), "UTF-8");
            result[2] = json.getString("img");
            comicNumber = Integer.parseInt(json.getString("num"));
        } else if (comicNumber == 404) { //xkcd.com/404 doesn't have a json object
            result[0] = "404";
            result[1] = "404";
            result[2] = "http://i.imgur.com/p0eKxKs.png";
            comicNumber = 404;

        } else throw new IOException("json not found");

        result[2] = Comic.getDoubleResolutionUrl(result[2], comicNumber, context);

        // some image fixes
        switch (comicNumber) {
            case 1037: result[2] = "http://www.explainxkcd.com/wiki/images/f/ff/umwelt_the_void.jpg";
                break;
            case 1608: result[2] = "http://www.explainxkcd.com/wiki/images/4/41/hoverboard.png";
                break;
            case 1350: result[2] = "http://www.explainxkcd.com/wiki/images/3/3d/lorenz.png";
                break;
            case 104: result[2] = "http://i.imgur.com/dnCNfPo.jpg";
                break;
            case 76: result[2] = "http://i.imgur.com/h3fi2RV.jpg";
                break;
            case 80: result[2] = "http://i.imgur.com/lWmI1lB.jpg";
                break;
            case 1663: result[2] = "http://explainxkcd.com/wiki/images/c/ce/garden.png";
                break;
            case 1193: result[2] = "https://www.explainxkcd.com/wiki/images/0/0b/externalities.png";
                break;
            case 1054: result[0] = "The Bacon";
                break;
            case 1137: result[0] = "RTL";
                break;
        }
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            result[0] = result[0].replaceAll("https", "http");
        }
        return result;
    }

    public String getTranscript() {
        try {
            return json.getString("transcript");
        } catch (JSONException e) {
            return " ";
        }
    }

    public String[] getComicData() {
        return comicData;
    }

    public int getComicNumber() {
        return comicNumber;
    }

    //Thanks to /u/doncajon https://www.reddit.com/r/xkcd/comments/667yaf/xkcd_1826_birdwatching/
    static public String getDoubleResolutionUrl(String url, int number, Context context) {
        boolean largeComic = context != null && Arrays.binarySearch(context.getResources().getIntArray(R.array.large_comics), number) >= 0;
        int no2xVersion[] = {1193, 1446, 1350, 1608, 1663, 1667, 1735, 1739, 1744, 1778};

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) { //https doesn't work on KitKat and lower for some reason...
            url = url.replaceAll("https", "http");
        }
        if(number >= 1084 && Arrays.binarySearch(no2xVersion, number) < 0 && !largeComic && !url.contains("_2x.png"))
            return url.substring(0, url.lastIndexOf('.')) + "_2x.png";
        return url;
    }
}
