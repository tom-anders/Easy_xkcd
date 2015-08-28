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
import android.os.Environment;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.SearchView;

import com.tap.xkcd_reader.R;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.util.ArrayList;

public class WhatIfActivity extends AppCompatActivity {

    private WebView web;
    public static int WhatIfIndex;
    private ProgressDialog mProgress;
    private String title;
    private Document doc;
    private boolean leftSwipe = false;
    private boolean rightSwipe = false;
    private boolean fullOffline = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(PrefHelper.getTheme());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_what_if);

        PrefHelper.getPrefs(getApplicationContext());
        fullOffline = PrefHelper.fullOfflineWhatIf();

        android.support.v7.widget.Toolbar toolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);


        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(R.attr.colorPrimaryDark, typedValue, true);
        TypedValue typedValue2 = new TypedValue();
        getTheme().resolveAttribute(R.attr.colorPrimary, typedValue2, true);
        toolbar.setBackgroundColor(typedValue2.data);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(typedValue.data);
        }


        web = (WebView) findViewById(R.id.wv);
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void performClick(String alt) {
                android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(WhatIfActivity.this);
                mDialog.setMessage(alt);
                mDialog.show();
            }
        }, "ok");

        web.getSettings().setBuiltInZoomControls(true);
        web.getSettings().setUseWideViewPort(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT);
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
                if (!fullOffline) {
                    doc = Jsoup.connect("http://what-if.xkcd.com/" + String.valueOf(WhatIfIndex)).get();
                } else {
                    File sdCard = Environment.getExternalStorageDirectory();
                    File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd/what if/"+String.valueOf(WhatIfIndex));
                    File file = new File(dir, String.valueOf(WhatIfIndex) + ".html");
                    doc = Jsoup.parse(file, "UTF-8");
                }
                //append custom css
                doc.head().getElementsByTag("link").remove();
                if (!PrefHelper.nightModeEnabled()) {
                    doc.head().appendElement("link").attr("rel", "stylesheet").attr("type", "text/css").attr("href", "style.css");
                } else {
                    doc.head().appendElement("link").attr("rel", "stylesheet").attr("type", "text/css").attr("href", "night.css");
                }

                //fix the image links

                int count = 1;
                String base = Environment.getExternalStorageDirectory().getAbsolutePath();
                for (org.jsoup.nodes.Element e : doc.select(".illustration")) {
                    if (!fullOffline) {
                        String src = e.attr("src");
                        e.attr("src", "http://what-if.xkcd.com" + src);
                    } else {
                        String path = "file://"+base+"/easy xkcd/what if/"+String.valueOf(WhatIfIndex)+"/"+String.valueOf(count)+".png";
                        e.attr("src", path);
                        e.attr("onclick", "ok.performClick(title);");
                    }
                    e.attr("onclick", "ok.performClick(title);");
                    count++;
                }

                //fix footnotes and math scripts
                if (!PrefHelper.fullOfflineWhatIf()) {
                doc.select("script[src]").last().attr("src", "http://aja" +
                        "x.googleapis.com/ajax/libs/jquery/1.10.1/jquery.min.js");
                    doc.select("script[src]").first().attr("src", "http://cdn.mathjax.org/mathjax/latest/MathJax.js");
                } else {
                    doc.select("script[src]").last().attr("src", "footnotes.js");
                    doc.select("script[src]").first().attr("src", "MathJax.js");
                }

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
                    if (!fullOffline) {
                        WhatIfFragment.getInstance().updateRv();
                    } else {
                        OfflineWhatIfFragment.getInstance().updateRv();
                    }

                    if (mProgress != null) {
                        mProgress.dismiss();
                    }

                    switch (Integer.parseInt(PrefHelper.getOrientation())) {
                        case 1:
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
                            break;
                        case 2:
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                            break;
                        case 3:
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                            break;
                    }

                    if (leftSwipe) {
                        leftSwipe = false;
                        Animation animation = AnimationUtils.loadAnimation(WhatIfActivity.this, R.anim.slide_in_left);
                        web.startAnimation(animation);
                        web.setVisibility(View.VISIBLE);
                    } else if (rightSwipe) {
                        rightSwipe = false;
                        Animation animation = AnimationUtils.loadAnimation(WhatIfActivity.this, R.anim.slide_in_right);
                        web.startAnimation(animation);
                        web.setVisibility(View.VISIBLE);
                    }

                    web.setOnTouchListener(new OnSwipeTouchListener(WhatIfActivity.this) {
                        @Override
                        public void onSwipeRight() {
                            if (WhatIfIndex != 1 && PrefHelper.swipeEnabled()) {
                                nextWhatIf(true);
                            }
                        }

                        @Override
                        public void onSwipeLeft() {
                            if (!fullOffline) {
                                if (WhatIfIndex != WhatIfFragment.mTitles.size() && PrefHelper.swipeEnabled()) {
                                    nextWhatIf(false);
                                }
                            } else {
                                if (WhatIfIndex != OfflineWhatIfFragment.mTitles.size() && PrefHelper.swipeEnabled()) {
                                    nextWhatIf(false);
                                }
                            }
                        }
                    });

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
        menu.findItem(R.id.action_swipe).setChecked(PrefHelper.swipeEnabled());

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_next:
                if (!fullOffline) {
                    if (WhatIfIndex != WhatIfFragment.mTitles.size()) {
                        return nextWhatIf(false);
                    }
                } else {
                    if (WhatIfIndex != OfflineWhatIfFragment.mTitles.size()) {
                        return nextWhatIf(false);
                    }
                }
            case R.id.action_back:
                if (WhatIfIndex != 1) {
                    return nextWhatIf(true);
                }
            case R.id.action_night_mode:
                item.setChecked(!item.isChecked());
                PrefHelper.setNightMode(item.isChecked());
                new LoadWhatIf().execute();
                return true;

            case R.id.action_swipe:
                item.setChecked(!item.isChecked());
                PrefHelper.setSwipeEnabled(item.isChecked());
                return true;

            case R.id.action_browser:
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://what-if.xkcd.com/" + String.valueOf(WhatIfIndex)));
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

    private boolean nextWhatIf(boolean left) {
        mProgress = ProgressDialog.show(this, "", getResources().getString(R.string.loading_articles), true);
        Animation animation;
        if (left) {
            WhatIfIndex--;
            leftSwipe = true;
            animation = AnimationUtils.loadAnimation(this, R.anim.slide_out_right);
            web.startAnimation(animation);
            web.setVisibility(View.INVISIBLE);
        } else {
            WhatIfIndex++;
            rightSwipe = true;
            animation = AnimationUtils.loadAnimation(this, R.anim.slide_out_left);
            web.startAnimation(animation);
            web.setVisibility(View.INVISIBLE);
        }
        PrefHelper.setLastWhatIf(WhatIfIndex);
        new LoadWhatIf().execute();
        invalidateOptionsMenu();
        if (!fullOffline) {
            WhatIfFragment.rv.scrollToPosition(WhatIfFragment.mTitles.size() - WhatIfIndex);
        } else {
            OfflineWhatIfFragment.rv.scrollToPosition(OfflineWhatIfFragment.mTitles.size() - WhatIfIndex);
        }
        PrefHelper.setWhatifRead(String.valueOf(WhatIfIndex));
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (WhatIfIndex == 1) {
            menu.findItem(R.id.action_back).setVisible(false);
        } else {
            menu.findItem(R.id.action_back).setVisible(true);
        }
        if (!fullOffline) {
            if (WhatIfIndex == WhatIfFragment.mTitles.size()) {
                menu.findItem(R.id.action_next).setVisible(false);
            } else {
                menu.findItem(R.id.action_next).setVisible(true);
            }
        } else {
            if (WhatIfIndex == OfflineWhatIfFragment.mTitles.size()) {
                menu.findItem(R.id.action_next).setVisible(false);
            } else {
                menu.findItem(R.id.action_next).setVisible(true);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

}