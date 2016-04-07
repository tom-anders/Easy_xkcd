/**
 * *******************************************************************************
 * Copyright 2016 Tom Praschan
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
 */

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
