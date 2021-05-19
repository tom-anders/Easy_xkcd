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

import android.content.Context;
import android.content.res.Resources;
import android.util.JsonReader;
import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.Ignore;
import io.realm.annotations.PrimaryKey;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import timber.log.Timber;

public class Article extends RealmObject {
    @PrimaryKey
    private int number;

    private String title;

    private String thumbnail;

    private boolean favorite;

    private boolean read;

    private boolean offline;

    private static final String OFFLINE_WHATIF_PATH = "/easy xkcd/what if/";
    private static final String OFFLINE_WHATIF_OVERVIEW_PATH = "/easy xkcd/what if/overview/";

    public static boolean hasOfflineFilesForArticle(int number, PrefHelper prefHelper) {
        File sdCard = prefHelper.getOfflinePath();

        File thumbnail = new File(sdCard.getAbsolutePath() + OFFLINE_WHATIF_OVERVIEW_PATH + number + ".png");
        File doc = new File(sdCard.getAbsolutePath() + OFFLINE_WHATIF_PATH + number + "/" + number + ".html");

        return thumbnail.exists() && doc.exists();
    }

    public static boolean downloadThumbnails(RealmResults<Article> articles, PrefHelper prefHelper) {
        File sdCard = prefHelper.getOfflinePath();
        File dir = new File(sdCard.getAbsolutePath() + OFFLINE_WHATIF_OVERVIEW_PATH);
        if (!dir.exists()) dir.mkdirs();

        try {
            for (Article article : articles) {
                File file = new File(dir, article.getNumber() + ".png");

                if (file.exists()) break;

                Response response = JsonParser.getNewHttpClient().newCall(
                        new Request.Builder().url(article.getThumbnail()).build()).execute();

                BufferedSink sink = Okio.buffer(Okio.sink(file));
                sink.writeAll(response.body().source());

                sink.close();
                response.body().close();
            }

        } catch (IOException e) {
            Timber.e(e);
            return false;
        }

        return true;
    }

    public static boolean downloadArticle(int number, PrefHelper prefHelper) {
        if (hasOfflineFilesForArticle(number, prefHelper)) return true;

        Timber.d("downloading %d...", number);
        
        try {
            OkHttpClient client = JsonParser.getNewHttpClient();

            Document doc = Jsoup.parse(
                    Objects.requireNonNull(client.newCall(
                            new Request.Builder()
                                    .url("https://what-if.xkcd.com/" + number)
                                    .build())
                            .execute().body()).string());

            
            File dir = new File(prefHelper.getOfflinePath().getAbsolutePath() + OFFLINE_WHATIF_PATH + number);
            if (!dir.exists()) dir.mkdirs();
            
            File file = new File(dir, number + ".html");
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(doc.outerHtml());
            writer.close();
            
            //download images
            int count = 1;
            for (Element e : doc.select(".illustration")) {
                try {
                    String url = "https://what-if.xkcd.com" + e.attr("src");
                    Request request = new Request.Builder()
                            .url(url)
                            .build();
                    Response response = client.newCall(request).execute();
                    file = new File(dir, String.valueOf(count) + ".png");
                    BufferedSink sink = Okio.buffer(Okio.sink(file));
                    sink.writeAll(response.body().source());
                    sink.close();
                    response.body().close();
                    count++;
                } catch (Exception e2) {
                    Timber.e(e2, "article %d", number);
                    return false;
                }
            }
        } catch (Exception e) {
            Timber.e(e);
            return false;
        }

        return true;
    }

    public static ArrayList<String> generateRefs(Document doc) {
        ArrayList<String> refs = new ArrayList<>();
        int count = 0;
        for (Element e : doc.select(".ref")) {
            refs.add((e.select(".refbody").html()));
            String n = "\"" + count + "\"" ;
            e.select(".refnum").attr("onclick", "ref.performClick(" + n + ")");
            e.select(".refbody").remove();
            count++;
        }
        return refs;
    }

    // Can't be "getDocument", since then Realm would complain
    public static Document generateDocument(int number, PrefHelper prefHelper, ThemePrefs themePrefs) throws IOException {
        Document doc;
        if (!prefHelper.fullOfflineWhatIf()) {
            OkHttpClient okHttpClient = JsonParser.getNewHttpClient();
            Request request = new Request.Builder()
                    .url("https://what-if.xkcd.com/" + number)
                    .build();
            Response response = okHttpClient.newCall(request).execute();
            String body = response.body().string();
            doc = Jsoup.parse(body);
            //doc = Jsoup.connect("http://what-if.xkcd.com/" + String.valueOf(mNumber)).get();
        } else {
            File sdCard = prefHelper.getOfflinePath();
            File dir = new File(sdCard.getAbsolutePath() + OFFLINE_WHATIF_PATH + number);
            File file = new File(dir, number + ".html");
            doc = Jsoup.parse(file, "UTF-8");
        }
        //append custom css
        doc.head().getElementsByTag("link").remove();
        if (themePrefs.amoledThemeEnabled()) {
            if (themePrefs.invertColors(false)) {
                doc.head().appendElement("link").attr("rel", "stylesheet").attr("type", "text/css").attr("href", "amoled_invert.css");
            } else {
                doc.head().appendElement("link").attr("rel", "stylesheet").attr("type", "text/css").attr("href", "amoled.css");
            }
            Log.d("info","amoled night theme enabled: " + themePrefs.amoledThemeEnabled());
        } else if (themePrefs.nightThemeEnabled()) {
            if (themePrefs.invertColors(false)) {
                doc.head().appendElement("link").attr("rel", "stylesheet").attr("type", "text/css").attr("href", "night_invert.css");
            } else {
                doc.head().appendElement("link").attr("rel", "stylesheet").attr("type", "text/css").attr("href", "night.css");
            }
        } else {
            doc.head().appendElement("link").attr("rel", "stylesheet").attr("type", "text/css").attr("href", "style.css");
        }

        //fix the image links
        int count = 1;
        String base = prefHelper.getOfflinePath().getAbsolutePath();
        for (org.jsoup.nodes.Element e : doc.select(".illustration")) {
            if (!prefHelper.fullOfflineWhatIf()) {
                String src = e.attr("src");
                e.attr("src", "https://what-if.xkcd.com" + src);
            } else {
                String path = "file://" + base + "/easy xkcd/what if/" + number + "/" + count + ".png";
                e.attr("src", path);
            }
            e.attr("onclick", "img.performClick(title);");
            count++;
        }

        //fix footnotes and math scripts
        if (!prefHelper.fullOfflineWhatIf()) {
            //doc.select("script[src]").last().attr("src", "http://aja" +
            //"x.googleapis.com/ajax/libs/jquery/1.10.1/jquery.min.js");
            doc.select("script[src]").first().attr("src", "https://cdn.mathjax.org/mathjax/latest/MathJax.js");
        } else {
            //doc.select("script[src]").last().attr("src", "footnotes.js");
            doc.select("script[src]").first().attr("src", "MathJax.js");
        }

        //remove header, footer, nav buttons
        doc.getElementById("header-wrapper").remove();
        doc.select("nav").remove();
        doc.getElementById("footer-wrapper").remove();

        //remove title
        doc.select("h1").remove();

        return doc;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public int getNumber() {
        return number;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public boolean isOffline() {
        return offline;
    }

    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }
}
