package de.tap.easy_xkcd.Activities;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;

import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.os.FileUriExposedException;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.tap.xkcd_reader.R;

import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.Random;

import androidx.core.view.MenuCompat;
import butterknife.Bind;
import butterknife.ButterKnife;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.misc.OnSwipeTouchListener;
import de.tap.easy_xkcd.utils.Article;
import de.tap.easy_xkcd.fragments.whatIf.WhatIfFavoritesFragment;
import de.tap.easy_xkcd.fragments.whatIf.WhatIfFragment;
import timber.log.Timber;

public class WhatIfActivity extends BaseActivity {

    @Bind(R.id.wv)
    WebView web;
    public static int WhatIfIndex;
    private ProgressDialog mProgress;
    private boolean leftSwipe = false;
    private boolean rightSwipe = false;
    private Article loadedArticle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_what_if);
        ButterKnife.bind(this);

        androidx.appcompat.widget.Toolbar toolbar = (androidx.appcompat.widget.Toolbar) findViewById(R.id.toolbar);
        setupToolbar(toolbar);

        web.addJavascriptInterface(new altObject(), "img");
        web.addJavascriptInterface(new refObject(), "ref");
        web.getSettings().setBuiltInZoomControls(true);
        web.getSettings().setUseWideViewPort(true);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDisplayZoomControls(false);
        web.getSettings().setLoadWithOverviewMode(true);
        web.getSettings().setTextZoom(prefHelper.getZoom(web.getSettings().getTextZoom()));

        new LoadWhatIf().execute();
    }

    /**
     * JavaScript Object to display an image's alt text
     */
    private class altObject {
        @JavascriptInterface
        public void performClick(String alt) {
            androidx.appcompat.app.AlertDialog.Builder mDialog = new androidx.appcompat.app.AlertDialog.Builder(WhatIfActivity.this);
            mDialog.setMessage(alt);
            mDialog.show();
        }
    }

    /**
     * JavaScript Object to display the footnotes
     */
    private class refObject {
        @JavascriptInterface
        public void performClick(String n) {
            if (WhatIfIndex == 141 && n.equals("2")) { //This footnote contains an image
                ImageView image = new ImageView(WhatIfActivity.this);
                image.setImageResource(R.mipmap.brda);
                image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                AlertDialog.Builder builder =
                        new AlertDialog.Builder(WhatIfActivity.this).
                                setMessage("Here's an image which is great for annoying a few specific groups of people:")
                                .setView(image);
                builder.create().show();
                return;
            }
            ((TextView) new androidx.appcompat.app.AlertDialog.Builder(WhatIfActivity.this)
                    .setMessage(Html.fromHtml(loadedArticle.getRefs().get(Integer.parseInt(n))))
                    .show()
                    .findViewById(android.R.id.message))
                    .setMovementMethod(LinkMovementMethod.getInstance()); //enable hyperlinks
        }
    }

    private class LoadWhatIf extends AsyncTask<Void, Void, Void> {
        private Document doc;

        @Override
        protected void onPreExecute() {
            lockRotation();
            if (!prefHelper.fullOfflineWhatIf()) {
                mProgress = new ProgressDialog(WhatIfActivity.this);
                mProgress.setTitle(getResources().getString(R.string.loading_article));
                mProgress.setIndeterminate(false);
                mProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgress.setCancelable(false);
                mProgress.show();
            }
        }

        @Override
        protected Void doInBackground(Void... dummy) {
            try {
                loadedArticle = new Article(WhatIfIndex, prefHelper.fullOfflineWhatIf(), WhatIfActivity.this);
                doc = loadedArticle.getWhatIf(WhatIfActivity.this);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            if (doc != null) {
                web.loadDataWithBaseURL("file:///android_asset/.", doc.html(), "text/html", "UTF-8", null);
                web.setWebChromeClient(new WebChromeClient() {
                    public void onProgressChanged(WebView view, int progress) {
                        if (!prefHelper.fullOfflineWhatIf())
                            mProgress.setProgress(progress);
                    }
                });
                web.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            try {
                                startActivity(intent);
                            } catch (FileUriExposedException e) {
                                Timber.e(e);
                            }
                        } else {
                            startActivity(intent);
                        }
                        return true;
                    }

                    public void onPageFinished(WebView view, String url) {
                        WhatIfFragment.getInstance().updateRv();
                        if (prefHelper.showWhatIfTip()) {
                            Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), R.string.what_if_tip, BaseTransientBottomBar.LENGTH_LONG);
                            snackbar.setAction(R.string.got_it, snackbarView -> prefHelper.setShowWhatIfTip(false));
                            snackbar.show();
                        }
                        if (mProgress != null)
                            mProgress.dismiss();

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
                                if (WhatIfIndex != 1 && prefHelper.swipeEnabled()) {
                                    nextWhatIf(true);
                                }
                            }

                            @Override
                            public void onSwipeLeft() {
                                if (WhatIfIndex != WhatIfFragment.mTitles.size() && prefHelper.swipeEnabled()) {
                                    nextWhatIf(false);
                                }

                            }
                        });
                    }
                });
            } else {
                if (mProgress != null)
                    mProgress.dismiss();
            }
            assert getSupportActionBar() != null;
            getSupportActionBar().setSubtitle(loadedArticle.getTitle());
            unlockRotation();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_what_if, menu);
        menu.findItem(R.id.action_night_mode).setChecked(themePrefs.nightThemeEnabled());
        menu.findItem(R.id.action_swipe).setChecked(prefHelper.swipeEnabled());
        MenuCompat.setGroupDividerEnabled(menu, true);
        return true;
    }

    @Override
    protected void onDestroy() {
        if (mProgress != null) {
            mProgress.dismiss();
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_next:
                return nextWhatIf(false);

            case R.id.action_back:
                return nextWhatIf(true);

            case R.id.action_night_mode:
                item.setChecked(!item.isChecked());
                themePrefs.setNightThemeEnabled(item.isChecked());
                setResult(Activity.RESULT_OK);
                finish();
                startActivity(getIntent());
                return true;

            case R.id.action_swipe:
                item.setChecked(!item.isChecked());
                prefHelper.setSwipeEnabled(item.isChecked());
                invalidateOptionsMenu();
                return true;

            case R.id.action_browser:
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://what-if.xkcd.com/" + String.valueOf(WhatIfIndex)));
                startActivity(intent);
                return true;

            case R.id.action_share:
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_SUBJECT, "What if: " + loadedArticle.getTitle());
                share.putExtra(Intent.EXTRA_TEXT, "https://what-if.xkcd.com/" + String.valueOf(WhatIfIndex));
                startActivity(share);
                return true;

            case R.id.action_random:
                Random mRand = new Random();
                WhatIfIndex = mRand.nextInt(prefHelper.getNewestWhatIf());
                prefHelper.setLastWhatIf(WhatIfIndex);
                WhatIfFragment.getInstance().getRv().scrollToPosition(WhatIfFragment.mTitles.size() - WhatIfIndex);

                prefHelper.setWhatifRead(String.valueOf(WhatIfIndex));
                new LoadWhatIf().execute();
                return true;
            case R.id.action_favorite:
                if (!prefHelper.checkWhatIfFav(WhatIfIndex)) {
                    prefHelper.setWhatIfFavorite(String.valueOf(WhatIfIndex));
                } else {
                    prefHelper.removeWhatifFav(WhatIfIndex);
                }
                WhatIfFavoritesFragment.getInstance().updateFavorites();
                invalidateOptionsMenu();
                return true;

            case R.id.action_thread:
                return DatabaseManager.showThread(loadedArticle.getTitle(), this, true);

        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Loads the next what-if
     *
     * @param left true if the user chose the previous what-if
     */
    private boolean nextWhatIf(boolean left) {
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
        prefHelper.setLastWhatIf(WhatIfIndex);
        new LoadWhatIf().execute();
        invalidateOptionsMenu();

        try {
            WhatIfFragment.getInstance().getRv().scrollToPosition(WhatIfFragment.mTitles.size() - WhatIfIndex);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        prefHelper.setWhatifRead(String.valueOf(WhatIfIndex));
        invalidateOptionsMenu();
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (WhatIfIndex == 1)
            menu.findItem(R.id.action_back).setVisible(false);
        else
            menu.findItem(R.id.action_back).setVisible(true);

        if (WhatIfIndex == WhatIfFragment.mTitles.size())
            menu.findItem(R.id.action_next).setVisible(false);
        else
            menu.findItem(R.id.action_next).setVisible(true);

        if (menu.findItem(R.id.action_swipe).isChecked()) {
            menu.findItem(R.id.action_back).setVisible(false);
            menu.findItem(R.id.action_next).setVisible(false);
        }
        if (prefHelper.checkWhatIfFav(WhatIfIndex))
            menu.findItem(R.id.action_favorite).setIcon(ContextCompat.getDrawable(this, R.drawable.ic_favorite_on_24dp)).setTitle(R.string.action_favorite_remove);

        return super.onPrepareOptionsMenu(menu);
    }

}