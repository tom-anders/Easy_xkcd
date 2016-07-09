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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class Article {
    private int mNumber;
    private boolean offline;
    private ArrayList<String> ref = new ArrayList<>();
    private String title;
    private static final String OFFLINE_WHATIF_PATH = "/easy xkcd/what if/";
    private PrefHelper prefHelper;
    private ThemePrefs themePrefs;

    public Article (Integer number, boolean offlineArticle, Context context) {
        prefHelper = new PrefHelper(context);
        themePrefs = new ThemePrefs(context);
        mNumber = number;
        offline = offlineArticle;
    }

    public String getTitle() {
        return title;
    }

    public ArrayList<String> getRefs() {
        return ref;
    }

    public Document getWhatIf() throws IOException{
        Document doc;
        if (!offline) {
            doc = Jsoup.connect("http://what-if.xkcd.com/" + String.valueOf(mNumber)).get();
        } else {
            File sdCard = prefHelper.getOfflinePath();
            File dir = new File(sdCard.getAbsolutePath() + OFFLINE_WHATIF_PATH +String.valueOf(mNumber));
            File file = new File(dir, String.valueOf(mNumber) + ".html");
            doc = Jsoup.parse(file, "UTF-8");
        }
        //append custom css
        doc.head().getElementsByTag("link").remove();
        if (!themePrefs.WhatIfNightModeEnabled()) {
            doc.head().appendElement("link").attr("rel", "stylesheet").attr("type", "text/css").attr("href", "style.css");
        } else {
            doc.head().appendElement("link").attr("rel", "stylesheet").attr("type", "text/css").attr("href", "night.css");
        }

        //fix the image links
        int count = 1;
        String base = prefHelper.getOfflinePath().getAbsolutePath();
        for (org.jsoup.nodes.Element e : doc.select(".illustration")) {
            if (!offline) {
                String src = e.attr("src");
                e.attr("src", "http://what-if.xkcd.com" + src);
            } else {
                String path = "file://"+base+"/easy xkcd/what if/"+String.valueOf(mNumber)+"/"+String.valueOf(count)+".png";
                e.attr("src", path);
            }
            e.attr("onclick", "img.performClick(title);");
            count++;
        }

        count = 0;
        ref.clear();
        for (Element e : doc.select(".ref")) {
            ref.add((e.select(".refbody").html()));
            String n = "\"" + String.valueOf(count) + "\"" ;
            e.select(".refnum").attr("onclick", "ref.performClick(" + n + ")");
            e.select(".refbody").remove();
            count++;
        }


        //fix footnotes and math scripts
        if (!prefHelper.fullOfflineWhatIf()) {
            //doc.select("script[src]").last().attr("src", "http://aja" +
            //"x.googleapis.com/ajax/libs/jquery/1.10.1/jquery.min.js");
            doc.select("script[src]").first().attr("src", "http://cdn.mathjax.org/mathjax/latest/MathJax.js");
        } else {
            //doc.select("script[src]").last().attr("src", "footnotes.js");
            doc.select("script[src]").first().attr("src", "MathJax.js");
        }

        //remove header, footer, nav buttons
        doc.getElementById("header-wrapper").remove();
        doc.select("nav").remove();
        doc.getElementById("footer-wrapper").remove();

        //remove title
        title = doc.select("h1").text();
        doc.select("h1").remove();
        return doc;
    }

}
