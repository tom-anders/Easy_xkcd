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
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.RelativeLayout;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.tap.xkcd_reader.R;

import java.io.BufferedReader;
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
    private Toolbar mToolbar;
    private static DrawerLayout sDrawer;
    public ActionBarDrawerToggle mDrawerToggle;
    private MenuItem searchMenuItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (isOnline() && savedInstanceState == null) {
            new updateComicTitles().execute();
            new updateComicTranscripts().execute();
        }

        if (getIntent().getAction().equals(Intent.ACTION_VIEW)) {
            ComicBrowserFragment.sLastComicNumber = (getNumberFromUrl(getIntent().getDataString()));
        }
        if (("de.tap.easy_xkcd.ACTION_COMIC").equals(getIntent().getAction())) {
            ComicBrowserFragment.sLastComicNumber = getIntent().getIntExtra("number", 0);
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
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        sDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        sDrawer.setDrawerListener(mDrawerToggle);
        mDrawerToggle = setupDrawerToggle();
        sNavView = (NavigationView) findViewById(R.id.nvView);
        setupDrawerContent(sNavView);

        mFab = (FloatingActionButton) findViewById(R.id.fab);
        setupFab(mFab);

        //Load fragment
        if (isOnline()) {
            MenuItem item;
            if (savedInstanceState != null) {
                //Get last loaded fragment
                sCurrentFragment = savedInstanceState.getInt("CurrentFragment");
                item = sNavView.getMenu().findItem(sCurrentFragment);
            } else {
                //Load ComicBrowserFragment by default
                sProgress = ProgressDialog.show(this, "", this.getResources().getString(R.string.loading_comics), true);
                item = sNavView.getMenu().findItem(R.id.nav_browser);
            }
            selectDrawerItem(item);
        } else if (sCurrentFragment != R.id.nav_favorites) { //Don't show the dialog if the user is currently browsing his favorites
            Log.d("test", "test");
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
                        ((ComicBrowserFragment) fragmentManager.findFragmentByTag("browser")).getRandomComic();
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
                if (!isOnline()) {
                    Toast toast = Toast.makeText(this, R.string.no_connection, Toast.LENGTH_SHORT);
                    toast.show();
                    MenuItem m = sNavView.getMenu().findItem(R.id.nav_favorites);
                    m.setChecked(true);
                    sDrawer.closeDrawers();
                    return;
                }
                //showComicBrowserFragment
                showFragment("pref_random_comics", menuItem.getItemId(), "Comics", "browser", "favorites");
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
                showFragment("pref_random_favorites", menuItem.getItemId(), getResources().getString(R.string.nv_favorites), "favorites", "browser");
                break;

            case R.id.nav_settings:
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

    private void showFragment(String prefTag, int itemId, String actionBarTitle, String fragmentTagShow, String fragmentTagHide) {
        android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();
        assert getSupportActionBar() != null;  //We always have an ActionBar available, so this stops Android Studio from complaining about possible NullPointerExceptions
        //Setup alt text view
        TextView tvAlt = (TextView) findViewById(R.id.tvAlt);
        tvAlt.setVisibility(View.GONE);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) tvAlt.getLayoutParams();
        int dpMargin = 5;
        float d = this.getResources().getDisplayMetrics().density;
        int margin = (int) (dpMargin * d);

        //Setup FAB
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(prefTag, false)) {
            params.setMargins(margin, margin, margin, margin);
            mFab.setVisibility(View.GONE);
        } else {
            int dpMarginRight = 50;
            int marginRight = (int) (dpMarginRight * d);
            params.setMargins(margin, margin, marginRight, margin);
            mFab.setVisibility(View.VISIBLE);
        }

        getSupportActionBar().setTitle(actionBarTitle);
        if (fragmentManager.findFragmentByTag(fragmentTagShow) != null) {
            //if the fragment exists, show it.
            fragmentManager.beginTransaction().show(fragmentManager.findFragmentByTag(fragmentTagShow)).commitAllowingStateLoss();
            //Update action bar
            if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_subtitle", true)) {
                switch (itemId) {
                    //Update Action Bar title
                    case R.id.nav_favorites: {
                        getSupportActionBar().setSubtitle(String.valueOf(FavoritesFragment.sFavorites[FavoritesFragment.sFavoriteIndex]));
                        break;
                    }
                    case R.id.nav_browser: {
                        getSupportActionBar().setSubtitle(String.valueOf(ComicBrowserFragment.sLastComicNumber));
                        break;
                    }
                }
            }
        } else {
            //if the fragment does not exist, add it to fragment manager.
            switch (itemId) {
                case R.id.nav_favorites:
                    fragmentManager.beginTransaction().add(R.id.flContent, new FavoritesFragment(), fragmentTagShow).commitAllowingStateLoss();
                    break;
                case R.id.nav_browser:
                    fragmentManager.beginTransaction().add(R.id.flContent, new ComicBrowserFragment(), fragmentTagShow).commitAllowingStateLoss();
                    break;
            }
        }
        //if the other fragment is visible, hide it.
        if (fragmentManager.findFragmentByTag(fragmentTagHide) != null) {
            fragmentManager.beginTransaction().hide(fragmentManager.findFragmentByTag(fragmentTagHide)).commitAllowingStateLoss();
        }
    }

    private class updateComicTitles extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            Log.d("start", "task started");
            SharedPreferences preferences = getPreferences(Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            if (!preferences.getBoolean("titles_loaded", false)) {
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
                editor.putBoolean("titles_loaded", true);
                editor.putString("comic_titles", sb.toString());
                editor.putInt("highest_comic_title", 1551);
                editor.commit();
                Log.d("...", "comic titles updated first time");
            }
            try {
                int newest = new Comic(0, getApplicationContext()).getComicNumber();
                StringBuilder sb = new StringBuilder();
                sb.append(preferences.getString("comic_titles", ""));
                int n = preferences.getInt("highest_comic_title", 0);
                while (n < newest) {
                    String s = new Comic(n + 1, getApplicationContext()).getComicData()[0];
                    sb.append("&&");
                    sb.append(s);
                    editor.putInt("highest_comic_title", n + 1);
                    n++;
                    Log.d("n", String.valueOf(n));
                }
                editor.putString("comic_titles", sb.toString());
            } catch (IOException e) {
                e.printStackTrace();
                editor.putBoolean("titles_loaded", false);
            }
            editor.commit();
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            SharedPreferences preferences = getPreferences(Activity.MODE_PRIVATE);
            Log.d("done", "Comic titles updated");
            Log.d("boolean", String.valueOf(preferences.getBoolean("titles_loaded", false)));
            Log.d("highest", String.valueOf(preferences.getInt("highest_comic_title", 0)));
            sComicTitles = preferences.getString("comic_titles", "");
        }

        @Override
        protected void onCancelled() {
            SharedPreferences preferences = getPreferences(Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("comic_titles", "");
            editor.putBoolean("titles_loaded", false);
            editor.commit();
        }
    }

    private class updateComicTranscripts extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            Log.d("start", "task started");
            SharedPreferences preferences = getPreferences(Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            if (!preferences.getBoolean("trans_loaded", false)) {
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
                editor.putBoolean("trans_loaded", true);
                editor.putString("comic_trans", sb.toString());
                editor.putInt("highest_comic_trans", 1551);
                editor.commit();
                Log.d("...", "comic trans updated first time");
            }
            try {
                int newest = new Comic(0, getApplicationContext()).getComicNumber();
                StringBuilder sb = new StringBuilder();
                sb.append(preferences.getString("comic_trans", ""));
                int n = preferences.getInt("highest_comic_trans", 0);
                while (n < newest) {
                    String s = new Comic(n + 1).getTranscript();
                    sb.append("&&");
                    if (!s.equals("")) {
                        sb.append(s);
                    } else {
                        sb.append("N.A.");
                    }
                    editor.putInt("highest_comic_trans", n + 1);
                    n++;
                    Log.d("n", String.valueOf(n));
                }
                editor.putString("comic_trans", sb.toString());
            } catch (IOException e) {
                e.printStackTrace();
                editor.putBoolean("trans_loaded", false);
            }
            editor.commit();
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            SharedPreferences preferences = getPreferences(Activity.MODE_PRIVATE);
            Log.d("done", "Comic trans updated");
            Log.d("boolean", String.valueOf(preferences.getBoolean("trans_loaded", false)));
            Log.d("highest", String.valueOf(preferences.getInt("highest_comic_trans", 0)));
            sComicTrans = preferences.getString("comic_trans", "");
        }

        @Override
        protected void onCancelled() {
            SharedPreferences preferences = getPreferences(Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("comic_trans", "");
            editor.putBoolean("titles_trans", false);
            editor.commit();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        //Called when users open xkcd.com or m.xkcd.com links
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            //Get the ComicBrowserFragment and update it
            android.support.v4.app.FragmentManager fm = getSupportFragmentManager();
            ComicBrowserFragment fragment = (ComicBrowserFragment) fm.findFragmentByTag("browser");
            ComicBrowserFragment.sLastComicNumber = getNumberFromUrl(intent.getDataString());
            fragment.new pagerUpdate().execute(ComicBrowserFragment.sLastComicNumber);
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
        return new ActionBarDrawerToggle(this, sDrawer, mToolbar, R.string.drawer_open, R.string.drawer_close);
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
        assert getSupportActionBar() != null;
        //Check if ActionBar subtitle should be displayed
        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_subtitle", true)) {
            getSupportActionBar().setSubtitle(null);
        }
        //Reselect the current fragment in order to update action bar and floating action button
        if (isOnline()) {
            MenuItem m = sNavView.getMenu().findItem(sCurrentFragment);
            selectDrawerItem(m);
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

