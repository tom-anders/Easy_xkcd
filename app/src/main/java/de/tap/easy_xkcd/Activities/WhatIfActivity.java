package de.tap.easy_xkcd.Activities;

import android.annotation.SuppressLint;
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
import java.util.ArrayList;
import java.util.Random;

import androidx.core.view.MenuCompat;
import butterknife.Bind;
import butterknife.ButterKnife;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.misc.OnSwipeTouchListener;
import de.tap.easy_xkcd.utils.Article;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.realm.Realm;
import timber.log.Timber;

public class WhatIfActivity extends BaseActivity {
    public static final String INTENT_NUMBER = "number";

    @Bind(R.id.wv)
    WebView web;
    private ProgressDialog mProgress;
    private Article loadedArticle;
    private int numArticles;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_what_if);
        ButterKnife.bind(this);

        androidx.appcompat.widget.Toolbar toolbar = (androidx.appcompat.widget.Toolbar) findViewById(R.id.toolbar);
        setupToolbar(toolbar);

        web.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void performClick(String alt) {
                androidx.appcompat.app.AlertDialog.Builder mDialog = new androidx.appcompat.app.AlertDialog.Builder(WhatIfActivity.this);
                mDialog.setMessage(alt);
                mDialog.show();
            }
        }, "img");

        web.getSettings().setBuiltInZoomControls(true);
        web.getSettings().setUseWideViewPort(true);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDisplayZoomControls(false);
        web.getSettings().setLoadWithOverviewMode(true);
        web.getSettings().setTextZoom(prefHelper.getZoom(web.getSettings().getTextZoom()));

        web.setOnTouchListener(new OnSwipeTouchListener(WhatIfActivity.this) {
            @Override
            public void onSwipeRight() {
                if (prefHelper.swipeEnabled() && loadedArticle.getNumber() != 1) {
                    nextWhatIf(true);
                }
            }

            @Override
            public void onSwipeLeft() {
                if (prefHelper.swipeEnabled() && loadedArticle.getNumber() != numArticles) {
                    nextWhatIf(false);
                }

            }
        });

        Realm realm = Realm.getDefaultInstance();
        numArticles = realm.where(Article.class).findAll().size();
        realm.close();

        if (!getIntent().hasExtra(INTENT_NUMBER)) {
            Timber.w("WhatIfActivity started without valid number given in intent.");
        }

        loadWhatIf(getIntent().getIntExtra(INTENT_NUMBER, 1), null);
    }

    private void loadWhatIf(int articleNumber, Animation animationOnLoaded) {
        lockRotation();
        if (!prefHelper.fullOfflineWhatIf()) {
            mProgress = new ProgressDialog(WhatIfActivity.this);
            mProgress.setTitle(getResources().getString(R.string.loading_article));
            mProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgress.setCancelable(false);
            mProgress.show();
        }

        prefHelper.setLastWhatIf(articleNumber);

        Realm realm = Realm.getDefaultInstance();
        loadedArticle = realm.copyFromRealm(realm.where(Article.class).equalTo("number", articleNumber).findFirst());
        realm.beginTransaction();
        loadedArticle.setRead(true);
        realm.copyToRealmOrUpdate(loadedArticle);
        realm.commitTransaction();
        realm.close();

        if (loadedArticle == null) {
            Timber.wtf("Could not find article %d in database", articleNumber);
            finish();
        }

        Single.fromCallable(() -> Article.generateDocument(loadedArticle.getNumber(), prefHelper, themePrefs))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError(e -> {
                    Timber.e(e);
                    finish();
                })
                .doOnSuccess(doc -> {
                    final ArrayList<String> refs = Article.generateRefs(doc);
                    web.addJavascriptInterface(new Object() {
                        @JavascriptInterface
                        public void performClick(String n) {
                            if (loadedArticle.getNumber() == 141 && n.equals("2")) { //This footnote contains an image
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
                                    .setMessage(Html.fromHtml(refs.get(Integer.parseInt(n))))
                                    .show()
                                    .findViewById(android.R.id.message))
                                    .setMovementMethod(LinkMovementMethod.getInstance()); //enable hyperlinks
                        }
                    }, "ref");

                    web.loadDataWithBaseURL("file:///android_asset/.", doc.html(), "text/html", "UTF-8", null);
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
                            if (prefHelper.showWhatIfTip()) {
                                Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), R.string.what_if_tip, BaseTransientBottomBar.LENGTH_LONG);
                                snackbar.setAction(R.string.got_it, snackbarView -> prefHelper.setShowWhatIfTip(false));
                                snackbar.show();
                            }
                        }
                    });
                    if (mProgress != null)
                        mProgress.dismiss();

                    if (animationOnLoaded != null) {
                        web.startAnimation(animationOnLoaded);
                        web.setVisibility(View.VISIBLE);
                    }

                    assert getSupportActionBar() != null;
                    getSupportActionBar().setSubtitle(loadedArticle.getTitle());
                    unlockRotation();
                })
                .subscribe();
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
                prefHelper.setSwipeEnabled(!prefHelper.swipeEnabled());

                // Next/Prev buttons are only visible if swipe is disabled, so reload the menu
                invalidateOptionsMenu();
                return true;

            case R.id.action_browser:
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://what-if.xkcd.com/" + loadedArticle.getNumber()));
                startActivity(intent);
                return true;

            case R.id.action_share:
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_SUBJECT, "What if: " + loadedArticle.getTitle());
                share.putExtra(Intent.EXTRA_TEXT, "https://what-if.xkcd.com/" + loadedArticle.getNumber());
                startActivity(share);
                return true;

            case R.id.action_random:
                loadWhatIf(new Random().nextInt(Realm.getDefaultInstance().where(Article.class).findAll().size()), null);
                return true;
            case R.id.action_favorite:
                Realm realm = Realm.getDefaultInstance();
                realm.beginTransaction();
                loadedArticle.setFavorite(!loadedArticle.isFavorite());
                realm.copyToRealmOrUpdate(loadedArticle);
                realm.commitTransaction();

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
        web.startAnimation(AnimationUtils.loadAnimation(this, left ? R.anim.slide_out_right : R.anim.slide_out_left));
        web.setVisibility(View.INVISIBLE);

        int nextNumber = left ? loadedArticle.getNumber() - 1 : loadedArticle.getNumber() + 1;
        loadWhatIf(nextNumber,
                AnimationUtils.loadAnimation(this, left ? R.anim.slide_in_left : R.anim.slide_in_right));

        invalidateOptionsMenu();
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_back).setVisible(loadedArticle.getNumber() != 1);
        menu.findItem(R.id.action_next).setVisible(loadedArticle.getNumber() != numArticles);

        if (menu.findItem(R.id.action_swipe).isChecked()) {
            menu.findItem(R.id.action_back).setVisible(false);
            menu.findItem(R.id.action_next).setVisible(false);
        }

        if (loadedArticle.isFavorite())
            menu.findItem(R.id.action_favorite).setIcon(ContextCompat.getDrawable(this, R.drawable.ic_favorite_on_24dp)).setTitle(R.string.action_favorite_remove);

        return super.onPrepareOptionsMenu(menu);
    }

}