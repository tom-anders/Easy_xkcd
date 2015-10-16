package de.tap.easy_xkcd.utils;

/**
 * Used to get the JSON Objects from https://xkcd.com/json.html
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONException;
import org.json.JSONObject;

public class JsonParser {

    static JSONObject jObj = null;

    public static JSONObject getJSONFromUrl(String url) throws IOException{
        try{
            String json = HTTPGetCall(url);
            jObj = new JSONObject(json);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jObj;
    }

    protected static String HTTPGetCall(String WebMethodURL) throws IOException
    {
        StringBuilder response = new StringBuilder();

        //Prepare the URL and the connection
        URL u = new URL(WebMethodURL);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
            //Get the Stream reader ready
            BufferedReader input = new BufferedReader(new InputStreamReader(conn.getInputStream()), 8192);

            //Loop through the return data and copy it over to the response object to be processed
            String line;
            while ((line = input.readLine()) != null) {
                response.append(line);
            }

            input.close();
        }
        return response.toString();
    }

}
