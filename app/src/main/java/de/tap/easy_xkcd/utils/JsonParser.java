package de.tap.easy_xkcd.utils;

/**
 * Used to get the JSON Objects from https://xkcd.com/json.html
 */

import java.io.IOException;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

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
            Response response = client.newCall(request).execute();
            String json = response.body().string();
            jObj = new JSONObject(json);
            response.body().close();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jObj;
    }

}
