package de.tap.easy_xkcd;

import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.SearchView;

import com.tap.xkcd_reader.R;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.ArrayList;

public class WhatIfActivity extends AppCompatActivity {

    private WebView web;
    public static int WhatIfIndex;
    private ProgressDialog mProgress;
    private String title;
    private Document doc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_what_if);

        //TODO alt text
        //TODO footnotes too narrow

        PrefHelper.getPrefs(getApplicationContext());

        android.support.v7.widget.Toolbar toolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(getResources().getColor(R.color.ColorPrimaryDark));
        }

        web = (WebView) findViewById(R.id.wv);
        web.addJavascriptInterface(new Object()
        {
            @JavascriptInterface
            public void performClick(String alt) {
                Log.e("image clicked", alt);
            }
        }, "ok");

        web.getSettings().setBuiltInZoomControls(true);
        web.getSettings().setUseWideViewPort(true);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDisplayZoomControls(false);
        web.getSettings().setLoadWithOverviewMode(true);

        mProgress = ProgressDialog.show(this, "", getResources().getString(R.string.loading_articles), true);
        new LoadWhatIf().execute();
    }

    private class LoadWhatIf extends AsyncTask<Void, Void, Void> {
        @JavascriptInterface
        @Override
        protected Void doInBackground(Void... dummy) {
            try {
                doc = Jsoup.connect("http://what-if.xkcd.com/" + String.valueOf(WhatIfIndex)).get();
                //append custom css
                doc.head().getElementsByTag("link").remove();
                if (!PrefHelper.nightModeEnabled()) {
                    doc.head().appendElement("link").attr("rel", "stylesheet").attr("type", "text/css").attr("href", "style.css");
                } else {
                    doc.head().appendElement("link").attr("rel", "stylesheet").attr("type", "text/css").attr("href", "night.css");
                }

                //fix the image links
                for (org.jsoup.nodes.Element e : doc.select("img")) {
                    String src = e.attr("src");
                    e.attr("src", "http://what-if.xkcd.com" + src);
                    e.attr("onclick", "ok.performClick(title);");
                }

                //fix footnotes and math scripts
                doc.select("script[src]").last().attr("src", "http://aja" +
                        "x.googleapis.com/ajax/libs/jquery/1.10.1/jquery.min.js");
                doc.select("script[src]").first().attr("src", "http://cdn.mathjax.org/mathjax/latest/MathJax.js");

                //remove header, footer, nav buttons
                doc.getElementById("header-wrapper").remove();
                doc.select("nav").remove();
                doc.getElementById("footer-wrapper").remove();

                //remove title
                title = doc.select("h1").text();
                doc.select("h1").remove();

            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            web.loadDataWithBaseURL("file:///android_asset/.", doc.html(), "text/html", "UTF-8", null);
            web.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                    return true;
                }

                public void onPageFinished(WebView view, String url) {
                    WhatIfFragment.getInstance().updateRv();

                    mProgress.dismiss();
                    switch (Integer.parseInt(PrefHelper.getOrientation())) {
                        case 1: setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
                            break;
                        case 2: setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                            break;
                        case 3: setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                            break;
                    }
                }
            });
            getSupportActionBar().setSubtitle(title);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_what_if, menu);

        menu.findItem(R.id.action_night_mode).setChecked(PrefHelper.nightModeEnabled());

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_next:
                if (WhatIfIndex != WhatIfFragment.mTitles.size()) {
                    mProgress = ProgressDialog.show(this, "", getResources().getString(R.string.loading_articles), true);
                    WhatIfIndex++;
                    PrefHelper.setLastWhatIf(WhatIfIndex);
                    new LoadWhatIf().execute();
                    invalidateOptionsMenu();
                    WhatIfFragment.rv.scrollToPosition(WhatIfFragment.mTitles.size() - WhatIfIndex);
                    PrefHelper.setWhatifRead(String.valueOf(WhatIfIndex));
                }
                return true;
            case R.id.action_back:
                if (WhatIfIndex != 1) {
                    mProgress = ProgressDialog.show(this, "", getResources().getString(R.string.loading_articles), true);
                    WhatIfIndex--;
                    PrefHelper.setLastWhatIf(WhatIfIndex);
                    new LoadWhatIf().execute();
                    invalidateOptionsMenu();
                    WhatIfFragment.rv.scrollToPosition(WhatIfFragment.mTitles.size() - WhatIfIndex);
                    PrefHelper.setWhatifRead(String.valueOf(WhatIfIndex));
                }
                return true;
            case R.id.action_night_mode:
                item.setChecked(!item.isChecked());
                PrefHelper.setNightMode(item.isChecked());
                new LoadWhatIf().execute();
                return true;

            case R.id.action_browser:
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://what-if.xkcd.com/"+String.valueOf(WhatIfIndex)));
                startActivity(intent);
                return true;

            case R.id.action_share:
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_SUBJECT, "What if: " + title);
                share.putExtra(Intent.EXTRA_TEXT, "http://what-if.xkcd.com/" + String.valueOf(WhatIfIndex));
                startActivity(share);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (WhatIfIndex==1) {
            menu.findItem(R.id.action_back).setVisible(false);
        } else {
            menu.findItem(R.id.action_back).setVisible(true);
        }
        if (WhatIfIndex==WhatIfFragment.mTitles.size()) {
            menu.findItem(R.id.action_next).setVisible(false);
        } else {
            menu.findItem(R.id.action_next).setVisible(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private int getNumberFromUrl(String url) {
        //Extracts the comic number from xkcd urls
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        try {
            return Integer.parseInt(sb.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

}