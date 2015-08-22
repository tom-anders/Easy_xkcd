/**
 * *******************************************************************************
 * Copyright 2015 Tom Praschan
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

package de.tap.easy_xkcd;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.RelativeLayout;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.tap.xkcd_reader.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


public class MainActivity extends AppCompatActivity {
    public FloatingActionButton mFab;
    public static int sCurrentFragment;
    public static ProgressDialog sProgress;
    public static NavigationView sNavView;
    public static String sComicTitles;
    public static String sComicTrans;
    public Toolbar toolbar;
    private static DrawerLayout sDrawer;
    public ActionBarDrawerToggle mDrawerToggle;
    private MenuItem searchMenuItem;
    public static Boolean fullOffline = false;
    private Boolean settingsOpened = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PrefHelper.getPrefs(this);

        if (savedInstanceState == null) {
            if (PrefHelper.getNotificationInterval() != 0) {
                WakefulIntentService.scheduleAlarms(new ComicListener(), this, true);
            } else {
                WakefulIntentService.cancelAlarms(this);
            }
        }

        fullOffline = PrefHelper.fullOfflineEnabled();
        if ((isOnline()) && savedInstanceState == null) {
            new updateComicTitles().execute();
            new updateComicTranscripts().execute();
        } else {
            sComicTitles = PrefHelper.getComicTitles();
            sComicTrans = PrefHelper.getComicTrans();
        }

        boolean whatIfIntent = false;
        if (getIntent().getAction().equals(Intent.ACTION_VIEW)) {
            if (getIntent().getDataString().contains("what")) {
                WhatIfActivity.WhatIfIndex = (getNumberFromUrl(getIntent().getDataString()));
                PrefHelper.setLastWhatIf(WhatIfActivity.WhatIfIndex);
                whatIfIntent = true;
                WhatIfFragment.newIntent = true;
            } else {
                ComicBrowserFragment.sLastComicNumber = (getNumberFromUrl(getIntent().getDataString()));
                OfflineFragment.sLastComicNumber = (getNumberFromUrl(getIntent().getDataString()));
            }
        }
        if (("de.tap.easy_xkcd.ACTION_COMIC").equals(getIntent().getAction())) {
            int number = getIntent().getIntExtra("number", 0);
            if (isOnline() && !fullOffline) {
                ComicBrowserFragment.sLastComicNumber = number;
            } else {
                OfflineFragment.sLastComicNumber = number;
            }
        }
        //On Lollipop, change the app's icon in the recents app screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !getIntent().getAction().equals(Intent.ACTION_VIEW)) {
            TypedValue typedValue = new TypedValue();
            Resources.Theme theme = getTheme();
            theme.resolveAttribute(R.attr.colorPrimary, typedValue, true);
            int color = typedValue.data;

            Bitmap ic = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_easy_xkcd_recents);
            ActivityManager.TaskDescription description = new ActivityManager.TaskDescription("Easy xkcd", ic, color);
            setTaskDescription(description);
        }
        //Setup Toolbar, NavDrawer, FAB
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (savedInstanceState == null) {
            toolbar.setAlpha(0);
        }

        sDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        sDrawer.setDrawerListener(mDrawerToggle);
        mDrawerToggle = setupDrawerToggle();
        sNavView = (NavigationView) findViewById(R.id.nvView);
        setupDrawerContent(sNavView);

        mFab = (FloatingActionButton) findViewById(R.id.fab);
        setupFab(mFab);

        //Load fragment
        if (isOnline() | fullOffline) {
            MenuItem item;
            if (savedInstanceState != null) {
                //Get last loaded fragment
                sCurrentFragment = savedInstanceState.getInt("CurrentFragment");
                item = sNavView.getMenu().findItem(sCurrentFragment);
            } else {
                if (!whatIfIntent) {
                    //Load ComicBrowserFragment by default
                    sProgress = ProgressDialog.show(this, "", this.getResources().getString(R.string.loading_comics), true);
                    item = sNavView.getMenu().findItem(R.id.nav_browser);
                } else {
                    item = sNavView.getMenu().findItem(R.id.nav_whatif);
                }
            }
            selectDrawerItem(item);
        } else if ((sCurrentFragment != R.id.nav_favorites)) { //Don't show the dialog if the user is currently browsing his favorites or full offline is enabled
            android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(this);
            mDialog.setMessage(R.string.no_connection)
                    .setPositiveButton(R.string.no_connection_retry, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                            startActivity(getIntent());
                        }
                    })
                    .setCancelable(false);
            if (Favorites.getFavoriteList(this).length != 0) {
                mDialog.setNegativeButton(R.string.no_connection_favorites, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MenuItem m = sNavView.getMenu().findItem(R.id.nav_favorites);
                        selectDrawerItem(m);
                    }
                });
            }
            mDialog.show();
        }
    }

    private void setupFab(FloatingActionButton fab) {
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();
                switch (sCurrentFragment) {
                    case R.id.nav_browser: {
                        if (!fullOffline) {
                            ((ComicBrowserFragment) fragmentManager.findFragmentByTag("browser")).getRandomComic();
                        } else {
                            ((OfflineFragment) fragmentManager.findFragmentByTag("browser")).getRandomComic();
                        }
                        break;
                    }
                    case R.id.nav_favorites: {
                        ((FavoritesFragment) fragmentManager.findFragmentByTag("favorites")).getRandomComic();
                        break;
                    }
                }
            }
        });
    }

    private void setupDrawerContent(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        selectDrawerItem(menuItem);
                        return true;
                    }
                });
    }

    public void selectDrawerItem(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.nav_browser:
                //Check if the device is online
                if (!isOnline() && !fullOffline) {
                    Toast toast = Toast.makeText(this, R.string.no_connection, Toast.LENGTH_SHORT);
                    toast.show();
                    MenuItem m = sNavView.getMenu().findItem(R.id.nav_favorites);
                    m.setChecked(true);
                    sDrawer.closeDrawers();
                    return;
                }
                //showComicBrowserFragment
                if (sProgress == null) {
                    /*toolbar.setAlpha(0);
                    toolbar.setTranslationY(-300);
                    toolbar.animate().setDuration(300).translationY(0).alpha(1);*/
                    View view;
                    for (int i = 2; i < toolbar.getChildCount(); i++) {
                        view = toolbar.getChildAt(i);
                        view.setTranslationY(-300);
                        view.animate().setStartDelay(50 * (i + 1)).setDuration(70 * (i + 1)).translationY(0);
                    }
                    toolbar.getChildAt(0).setAlpha(0);
                    toolbar.getChildAt(0).animate().alpha(1).setDuration(200).setInterpolator(new AccelerateInterpolator());
                }
                showFragment("pref_random_comics", menuItem.getItemId(), "Comics", "browser", "favorites", "whatif");
                break;
            case R.id.nav_favorites:
                //Check if there are any Favorites
                if (Favorites.getFavoriteList(this).length == 0) {
                    Toast toast = Toast.makeText(this, R.string.no_favorites, Toast.LENGTH_SHORT);
                    toast.show();
                    MenuItem m = sNavView.getMenu().findItem(R.id.nav_browser);
                    m.setChecked(true);
                    sDrawer.closeDrawers();
                    return;
                }
                //showFavoritesFragment
                View view;
                for (int i = 2; i < toolbar.getChildCount(); i++) {
                    view = toolbar.getChildAt(i);
                    view.setTranslationY(300);
                    view.animate().setStartDelay(50 * (i + 1)).setDuration(70 * (i + 1)).translationY(0);
                }
                toolbar.getChildAt(0).setAlpha(0);
                toolbar.getChildAt(0).animate().alpha(1).setDuration(200).setInterpolator(new AccelerateInterpolator());
                showFragment("pref_random_favorites", menuItem.getItemId(), getResources().getString(R.string.nv_favorites), "favorites", "browser", "whatif");
                break;

            case R.id.nav_whatif:
                if (!isOnline()) {
                    Toast toast = Toast.makeText(this, R.string.no_connection, Toast.LENGTH_SHORT);
                    toast.show();
                    MenuItem m = sNavView.getMenu().findItem(sCurrentFragment);
                    m.setChecked(true);
                    sDrawer.closeDrawers();
                    return;
                }
                for (int i = 2; i < toolbar.getChildCount(); i++) {
                    view = toolbar.getChildAt(i);
                    view.setTranslationY(300);
                    view.animate().setStartDelay(50 * (i + 1)).setDuration(70 * (i + 1)).translationY(0);
                }
                toolbar.getChildAt(0).setAlpha(0);
                toolbar.getChildAt(0).animate().alpha(1).setDuration(200).setInterpolator(new AccelerateInterpolator());
                showFragment("", menuItem.getItemId(), "What if?", "whatif", "favorites", "browser");
                break;

            case R.id.nav_settings:
                settingsOpened = true;
                sDrawer.closeDrawer(sNavView);
                //Add delay so that the Drawer is closed before the Settings Activity is launched
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Intent i = new Intent(MainActivity.this, SettingsActivity.class);
                        startActivity(i);
                    }
                }, 200);
                return;

            case R.id.nav_feedback:
                sDrawer.closeDrawer(sNavView);
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "easyxkcd@gmail.com", null));
                        startActivity(Intent.createChooser(i, getResources().getString(R.string.nav_feedback_send)));
                    }
                }, 200);
                return;

            case R.id.nav_about:
                sDrawer.closeDrawer(sNavView);
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Intent i = new Intent(MainActivity.this, AboutActivity.class);
                        startActivity(i);
                    }
                }, 250);
                return;
        }
        menuItem.setChecked(true);
        sDrawer.closeDrawers();
        sCurrentFragment = menuItem.getItemId();
        invalidateOptionsMenu();
    }

    private void showFragment(String prefTag, int itemId, String actionBarTitle, String fragmentTagShow, String fragmentTagHide, String fragmentTagHide2) {
        android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();
        assert getSupportActionBar() != null;  //We always have an ActionBar available, so this stops Android Studio from complaining about possible NullPointerExceptions
        //Setup alt text view

        /*TextView tvAlt = (TextView) findViewById(R.id.tvAlt);
        tvAlt.setVisibility(View.GONE);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) tvAlt.getLayoutParams();
        int dpMargin = 5;
        float d = this.getResources().getDisplayMetrics().density;
        int margin = (int) (dpMargin * d);*/

        //Setup FAB
        if (PrefHelper.fabEnabled(prefTag)) {
            //params.setMargins(margin, margin, margin, margin);
            mFab.setVisibility(View.GONE);
        } else if (!fragmentTagShow.equals("whatif")) {
            /*int dpMarginRight = 50;
            int marginRight = (int) (dpMarginRight * d);
            params.setMargins(margin, margin, marginRight, margin);*/
            mFab.setVisibility(View.VISIBLE);
        } else {
            mFab.setVisibility(View.GONE);
        }

        getSupportActionBar().setTitle(actionBarTitle);
        if (fragmentManager.findFragmentByTag(fragmentTagShow) != null) {
            //if the fragment exists, show it.
            android.support.v4.app.FragmentTransaction ft = fragmentManager.beginTransaction();
            if (fragmentTagShow.equals("browser")) {
                ft.setCustomAnimations(R.anim.abc_slide_in_top, R.anim.abc_slide_in_top);
            } else {
                ft.setCustomAnimations(R.anim.abc_slide_in_bottom, R.anim.abc_slide_in_bottom);
            }
            ft.show(fragmentManager.findFragmentByTag(fragmentTagShow));
            ft.commitAllowingStateLoss();
            //Update action bar
            if (PrefHelper.subtitleEnabled()) {
                switch (itemId) {
                    //Update Action Bar title
                    case R.id.nav_favorites: {
                        getSupportActionBar().setSubtitle(String.valueOf(FavoritesFragment.sFavorites[FavoritesFragment.sFavoriteIndex]));
                        break;
                    }
                    case R.id.nav_browser: {
                        if (isOnline() && !fullOffline) {
                            getSupportActionBar().setSubtitle(String.valueOf(ComicBrowserFragment.sLastComicNumber));
                        } else {
                            getSupportActionBar().setSubtitle(String.valueOf(OfflineFragment.sLastComicNumber));
                        }
                        break;
                    }
                }
            }
        } else {
            //if the fragment does not exist, add it to fragment manager.
            switch (itemId) {
                case R.id.nav_favorites:
                    fragmentManager.beginTransaction().setCustomAnimations(R.anim.abc_slide_in_bottom, R.anim.abc_slide_in_bottom).add(R.id.flContent, new FavoritesFragment(), fragmentTagShow).commitAllowingStateLoss();
                    break;
                case R.id.nav_browser:
                    if (isOnline() && !fullOffline) {
                        fragmentManager.beginTransaction().add(R.id.flContent, new ComicBrowserFragment(), fragmentTagShow).commitAllowingStateLoss();
                    } else {
                        fragmentManager.beginTransaction().add(R.id.flContent, new OfflineFragment(), fragmentTagShow).commitAllowingStateLoss();
                    }
                    break;
                case R.id.nav_whatif:
                    fragmentManager.beginTransaction().setCustomAnimations(R.anim.abc_slide_in_bottom, R.anim.abc_slide_in_bottom).add(R.id.flContent, new WhatIfFragment(), fragmentTagShow).commitAllowingStateLoss();
                    break;
            }
        }
        if (!PrefHelper.subtitleEnabled() | itemId == R.id.nav_whatif) {
            getSupportActionBar().setSubtitle("");
        }

        //if the other fragment is visible, hide it.
        if (fragmentManager.findFragmentByTag(fragmentTagHide) != null) {
            fragmentManager.beginTransaction().hide(fragmentManager.findFragmentByTag(fragmentTagHide)).commitAllowingStateLoss();
        }
        if (fragmentManager.findFragmentByTag(fragmentTagHide2) != null) {
            fragmentManager.beginTransaction().hide(fragmentManager.findFragmentByTag(fragmentTagHide2)).commitAllowingStateLoss();
        }

    }

    private class updateComicTitles extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            if (!PrefHelper.titlesLoaded()) {
                InputStream is = getResources().openRawResource(R.raw.comic_titles);
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                try {
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                } catch (IOException e) {
                    Log.e("error:", e.getMessage());
                }
                PrefHelper.setTitles(sb.toString(), true, 1551);
                Log.d("...", "comic titles updated first time");
            }
            PrefHelper.setHighestTitle(getApplicationContext());
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            sComicTitles = PrefHelper.getComicTitles();
        }

        @Override
        protected void onCancelled() {
            PrefHelper.setTitles("", false, 0);
        }
    }

    private class updateComicTranscripts extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            if (!PrefHelper.transLoaded()) {
                InputStream is = getResources().openRawResource(R.raw.comic_trans);
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                try {
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                } catch (IOException e) {
                    Log.e("error:", e.getMessage());
                }
                PrefHelper.setTrans(sb.toString(), true, 1551);
                Log.d("...", "comic trans updated first time");
            }
            PrefHelper.setHighestTrans(getApplicationContext());
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            Log.d("done", "Comic trans updated");
            sComicTrans = PrefHelper.getComicTrans();
        }

        @Override
        protected void onCancelled() {
            PrefHelper.setTrans("", false, 0);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        //Called when users open xkcd.com or m.xkcd.com links
        android.support.v4.app.FragmentManager fm = getSupportFragmentManager();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            //Get the ComicBrowserFragment and update it
            if (intent.getDataString().contains("what")) {
                MenuItem item = sNavView.getMenu().findItem(R.id.nav_whatif);
                selectDrawerItem(item);
                WhatIfActivity.WhatIfIndex = getNumberFromUrl(getIntent().getDataString());
                Intent whatIf = new Intent(MainActivity.this, WhatIfActivity.class);
                PrefHelper.setLastWhatIf(WhatIfActivity.WhatIfIndex);
                startActivity(whatIf);
            }
            if (isOnline() && !fullOffline) {
                ComicBrowserFragment fragment = (ComicBrowserFragment) fm.findFragmentByTag("browser");
                ComicBrowserFragment.sLastComicNumber = getNumberFromUrl(intent.getDataString());
                fragment.new pagerUpdate().execute(ComicBrowserFragment.sLastComicNumber);
            } else {
                OfflineFragment fragment = (OfflineFragment) fm.findFragmentByTag("browser");
                OfflineFragment.sLastComicNumber = getNumberFromUrl(intent.getDataString());
                fragment.new pagerUpdate().execute(OfflineFragment.sLastComicNumber);
            }
        }
        if (("de.tap.easy_xkcd.ACTION_COMIC").equals(getIntent().getAction())) {
            int number = getIntent().getIntExtra("number", 0);
            if (isOnline() && !fullOffline) {
                ComicBrowserFragment.sLastComicNumber = number;
                ComicBrowserFragment.sNewestComicNumber = 0;
                sProgress = ProgressDialog.show(this, "", this.getResources().getString(R.string.loading_comics), true);
                ComicBrowserFragment fragment = (ComicBrowserFragment) fm.findFragmentByTag("browser");
                fragment.new pagerUpdate().execute(ComicBrowserFragment.sLastComicNumber);
            } else {
                OfflineFragment.sLastComicNumber = number;
                OfflineFragment fragment = (OfflineFragment) fm.findFragmentByTag("browser");
                //fragment.new pagerUpdate().execute(OfflineFragment.sLastComicNumber);
                fragment.new updateImages().execute();
            }
        }

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        // Get the SearchView and set the searchable configuration
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        final SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setIconifiedByDefault(false);
        searchMenuItem = menu.findItem(R.id.action_search);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                MenuItem searchMenuItem = getSearchMenuItem();
                searchMenuItem.collapseActionView();
                searchView.setQuery("", false);
                //Hide Keyboard
                View view = MainActivity.this.getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }

                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        MenuItemCompat.setOnActionExpandListener(searchMenuItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                View view = getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(view, 0);
                }
                searchView.requestFocus();
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                View view = getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
                return true;
            }
        });

        return true;
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        //Save the current fragment
        savedInstanceState.putInt("CurrentFragment", sCurrentFragment);
        super.onSaveInstanceState(savedInstanceState);
    }

    public MenuItem getSearchMenuItem() {
        return searchMenuItem;
    }

    private ActionBarDrawerToggle setupDrawerToggle() {
        return new ActionBarDrawerToggle(this, sDrawer, toolbar, R.string.drawer_open, R.string.drawer_close);
    }

    private boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (settingsOpened) {
            settingsOpened = false;
            assert getSupportActionBar() != null;
            //Reselect the current fragment in order to update action bar and floating action button
            if (isOnline()) {
                MenuItem m = sNavView.getMenu().findItem(sCurrentFragment);
                selectDrawerItem(m);
            }

            if (PrefHelper.getNotificationInterval() != 0) {
                WakefulIntentService.scheduleAlarms(new ComicListener(), this, true);
            } else {
                WakefulIntentService.cancelAlarms(this);
            }
            Log.d("Info", "Update interval: " + String.valueOf(PrefHelper.getNotificationInterval()));

            if (!fullOffline && PrefHelper.fullOfflineEnabled()) {
                if (isOnline()) {
                    new downloadComicsTask().execute();
                } else {
                    Toast.makeText(this, R.string.no_connection, Toast.LENGTH_SHORT).show();
                    PrefHelper.setFullOffline(false);
                    fullOffline = false;
                }
            }
            if (fullOffline && !PrefHelper.fullOfflineEnabled()) {
                android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(this);
                mDialog.setMessage(R.string.delete_offline_dialog)
                        .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                PrefHelper.setFullOffline(true);
                            }
                        })
                        .setPositiveButton(R.string.dialog_yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                new deleteComicsTask().execute();
                            }
                        })
                        .setCancelable(false);
                mDialog.show();

            }
        }
    }

    public class downloadComicsTask extends AsyncTask<Void, Integer, Void> {
        private ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(MainActivity.this);
            progress.setTitle(getResources().getString(R.string.loading_offline));
            progress.setMessage(getResources().getString(R.string.loading_offline_message));
            progress.setIndeterminate(false);
            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            for (int i = 1; i <= ComicBrowserFragment.sNewestComicNumber; i++) {
                try {
                    Comic comic = new Comic(i, MainActivity.this);
                    String url = comic.getComicData()[2];
                    Bitmap mBitmap = Glide.with(MainActivity.this)
                            .load(url)
                            .asBitmap()
                            .into(-1, -1)
                            .get();
                    try {
                        File sdCard = Environment.getExternalStorageDirectory();
                        File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                        dir.mkdirs();
                        File file = new File(dir, String.valueOf(i) + ".png");
                        FileOutputStream fos = new FileOutputStream(file);
                        mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.flush();
                        fos.close();
                    } catch (Exception e) {
                        Log.e("Error", "Saving to external storage failed");
                        try {
                            FileOutputStream fos = openFileOutput(String.valueOf(i), Context.MODE_PRIVATE);
                            mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                            fos.close();
                        } catch (Exception e2) {
                            e2.printStackTrace();
                        }
                    }

                    PrefHelper.addTitle(comic.getComicData()[0], i);
                    PrefHelper.addAlt(comic.getComicData()[1], i);
                    int p = (int) (i / ((float) ComicBrowserFragment.sNewestComicNumber) * 100);
                    publishProgress(p);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            PrefHelper.setHighestOffline(ComicBrowserFragment.sNewestComicNumber);
            return null;
        }

        protected void onProgressUpdate(Integer... pro) {
            progress.setProgress(pro[0]);
            switch (pro[0]) {
                case 2:
                    progress.setMessage(getResources().getString(R.string.loading_offline_2));
                    break;
                case 20:
                    progress.setMessage(getResources().getString(R.string.loading_offline_20));
                    break;
                case 50:
                    progress.setMessage(getResources().getString(R.string.loading_offline_50));
                    break;
                case 80:
                    progress.setMessage(getResources().getString(R.string.loading_offline_80));
                    break;
                case 95:
                    progress.setMessage(getResources().getString(R.string.loading_offline_95));
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            progress.setMessage(getResources().getString(R.string.loading_offline_96));
                        }
                    }, 1000);
                    break;
                case 97:
                    progress.setMessage(getResources().getString(R.string.loading_offline_97));
                    break;
            }
        }

        @Override
        protected void onPostExecute(Void dummy) {
            fullOffline = true;
            progress.dismiss();
            finish();
            startActivity(getIntent());
        }
    }

    public class deleteComicsTask extends AsyncTask<Void, Integer, Void> {
        private ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(MainActivity.this);
            progress.setTitle(getResources().getString(R.string.delete_offline));
            progress.setMessage(getResources().getString(R.string.loading_offline_message));
            progress.setIndeterminate(false);
            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            SharedPreferences preferences = getPreferences(Activity.MODE_PRIVATE);
            SharedPreferences.Editor mEditor = preferences.edit();
            //int newest = preferences.getInt("Newest Comic", 0);
            int newest = PrefHelper.getNewest();
            for (int i = 1; i <= newest; i++) {
                if (!Favorites.checkFavorite(MainActivity.this, i)) {
                    //delete from internal storage
                    deleteFile(String.valueOf(i));
                    //delete from external storage
                    File sdCard = Environment.getExternalStorageDirectory();
                    File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                    File file = new File(dir, String.valueOf(i) + ".png");
                    file.delete();

                    int p = (int) (i / ((float) newest) * 100);
                    publishProgress(p);
                }
            }
            PrefHelper.deleteTitleAndAlt(newest, MainActivity.this);

            fullOffline = false;
            PrefHelper.setHighestOffline(0);

            return null;
        }

        protected void onProgressUpdate(Integer... pro) {
            progress.setProgress(pro[0]);
        }

        @Override
        protected void onPostExecute(Void dummy) {
            progress.dismiss();
            finish();
            startActivity(getIntent());
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggles
        mDrawerToggle.onConfigurationChanged(newConfig);
    }
}

