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

import android.content.Context;
import android.preference.PreferenceManager;

import com.tap.xkcd_reader.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;


public class Comic {
    private String[] mComicData;
    private String mJsonUrl;
    private int mComicNumber;
    private JSONObject json;

    public Comic(Integer number, Context context) throws IOException{
        if (number != 0) {
            mJsonUrl = "http://xkcd.com/" + number.toString() + "/info.0.json";
        } else {
            mJsonUrl = "http://xkcd.com/info.0.json";}
        try {
            mComicData = loadComicData(mJsonUrl);
        } catch (JSONException e){
            e.printStackTrace();
        }
        if (Arrays.binarySearch(context.getResources().getIntArray(R.array.interactive_comics), mComicNumber)>=0) {
            mComicData[0] = mComicData[0] + " " + context.getResources().getString(R.string.title_interactive);
        }

        int i = Arrays.binarySearch(context.getResources().getIntArray(R.array.large_comics), mComicNumber);
        if (i>=0 && PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_large", true)) {
            mComicData[2] = context.getResources().getStringArray(R.array.large_comics_urls)[i];
        }
    }

    public Comic(Integer number) throws IOException{
        if (number != 0) {
            mJsonUrl = "http://xkcd.com/" + number.toString() + "/info.0.json";
        } else {
            mJsonUrl = "http://xkcd.com/info.0.json";}
        try {
            mComicData = loadComicData(mJsonUrl);
        } catch (JSONException e){
            e.printStackTrace();
        }
    }

    private String[] loadComicData(String url) throws IOException, JSONException {
        json = JsonParser.getJSONFromUrl(url);
        String[] result = new String[3];
        if (json != null) {
            result[0] = json.getString("title");
            result[1] = json.getString("alt");
            result[2] = json.getString("img");
            mComicNumber = Integer.parseInt(json.getString("num"));
        }  else {
            result[0] = "404";
            result[1] = "404";
            result[2] = "http://i.imgur.com/p0eKxKs.png";
            mComicNumber = 404;
        }
        if (mComicNumber==712) { //fix for é and û
            result[1] = "Using a ring to bind someone you covet into your dark and twisted world? Wow, just got the subtext there. Also, the apparently eager Beyoncé would've made one badass Nazgȗl.";
        } else if (mComicNumber == 847) {
            result[1] = "Eärendil will patrol the walls of night only until the sun reaches red giant stage, engulfing the Morning Star on his brow. Light and high beauty are passing things as well.";
        } else if (mComicNumber == 1037) {
            result[2] = "http://www.explainxkcd.com/wiki/images/f/ff/umwelt_the_void.jpg";
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
        return mComicData;
    }

    public int getComicNumber() { return mComicNumber;}

}
