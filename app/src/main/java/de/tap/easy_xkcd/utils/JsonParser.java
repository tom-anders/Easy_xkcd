package de.tap.easy_xkcd.utils;

/**
 * Used to get the JSON Objects from https://xkcd.com/json.html
 */

import java.io.IOException;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class JsonParser {

    private static OkHttpClient client;

    public static JSONObject getJSONFromUrl(String url) throws IOException{
        JSONObject jObj = null;
        try{
            if (client == null)
                client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            String json = client.newCall(request).execute().body().string();
            jObj = new JSONObject(json);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jObj;
    }

}
