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

import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.SearchView;
import android.widget.Toast;

import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.tap.xkcd_reader.R;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;
import butterknife.OnLongClick;
import de.tap.easy_xkcd.CustomTabHelpers.CustomTabActivityHelper;


public class MainActivity extends AppCompatActivity {
    //public FloatingActionButton mFab;
    @Bind(R.id.fab) FloatingActionButton mFab;
    @Bind(R.id.nvView) NavigationView mNavView;
    @Bind(R.id.toolbar) Toolbar toolbar;
    public static int sCurrentFragment;
    public static ProgressDialog sProgress;
    //public static NavigationView mNavView;
    //public Toolbar toolbar;
    private static DrawerLayout sDrawer;
    public ActionBarDrawerToggle mDrawerToggle;
    private MenuItem searchMenuItem;
    public static boolean fullOffline = false;
    public static boolean fullOfflineWhatIf = false;
    private boolean settingsOpened = false;
    private static MainActivity instance;
    private CustomTabActivityHelper customTabActivityHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        PrefHelper.getPrefs(this);
        setTheme(PrefHelper.getTheme());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        instance = this;
        customTabActivityHelper = new CustomTabActivityHelper();

        if (savedInstanceState == null) {
            if (PrefHelper.getNotificationInterval() != 0) {
                WakefulIntentService.scheduleAlarms(new ComicListener(), this, true);
            } else {
                WakefulIntentService.cancelAlarms(this);
            }
        }

        fullOffline = PrefHelper.fullOfflineEnabled();
        fullOfflineWhatIf = PrefHelper.fullOfflineWhatIf();

        boolean whatIfIntent = false;
        if (getIntent().getAction().equals(Intent.ACTION_VIEW)) {
            if (getIntent().getDataString().contains("what")) {
                WhatIfActivity.WhatIfIndex = (getNumberFromUrl(getIntent().getDataString()));
                PrefHelper.setLastWhatIf(WhatIfActivity.WhatIfIndex);
                whatIfIntent = true;
                WhatIfFragment.newIntent = true;
                OfflineWhatIfFragment.newIntent = true;
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
        if (("de.tap.easy_xkcd.ACTION_WHAT_IF").equals(getIntent().getAction())) {
            int number = getIntent().getIntExtra("number", 0);
            WhatIfActivity.WhatIfIndex = number;
            PrefHelper.setLastWhatIf(WhatIfActivity.WhatIfIndex);
            whatIfIntent = true;
            WhatIfFragment.newIntent = true;
            OfflineWhatIfFragment.newIntent = true;
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
            if (!PrefHelper.colorNavbar()) {
                getWindow().setNavigationBarColor(getResources().getColor(R.color.ColorPrimaryBlack));
            }
        }
        //Setup Toolbar, NavDrawer, FAB
        //toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        TypedValue typedValue2 = new TypedValue();
        getTheme().resolveAttribute(R.attr.colorPrimary, typedValue2, true);
        toolbar.setBackgroundColor(typedValue2.data);
        if (savedInstanceState == null) {
            toolbar.setAlpha(0);
        }

        sDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        sDrawer.setDrawerListener(mDrawerToggle);
        mDrawerToggle = setupDrawerToggle();
        //mNavView = (NavigationView) findViewById(R.id.nvView);
        if (PrefHelper.nightThemeEnabled()) {
            mNavView.setBackgroundColor(getResources().getColor(R.color.background_material_dark));
            toolbar.setPopupTheme(R.style.ThemeOverlay_AppCompat);
        }
        setupDrawerContent(mNavView);

        //mFab = (FloatingActionButton) findViewById(R.id.fab);
        //setupFab(mFab);
        if (savedInstanceState == null) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    showRateSnackbar();
                    PrefHelper.showSurveySnackbar(MainActivity.this, mFab);
                }
            }, 1500);
        }

        //Load fragment
        if (isOnline() | fullOffline | fullOfflineWhatIf) {
            MenuItem item;
            if (savedInstanceState != null) {
                //Get last loaded fragment
                sCurrentFragment = savedInstanceState.getInt("CurrentFragment");
                item = mNavView.getMenu().findItem(sCurrentFragment);
            } else {
                if (!whatIfIntent && fullOffline | isOnline()) {
                    //Load ComicBrowserFragment by default
                    sProgress = ProgressDialog.show(this, "", this.getResources().getString(R.string.loading_comics), true);
                    item = mNavView.getMenu().findItem(R.id.nav_browser);
                } else {
                    item = mNavView.getMenu().findItem(R.id.nav_whatif);
                }
            }
            selectDrawerItem(item);
        } else if ((sCurrentFragment != R.id.nav_favorites)) { //Don't show the dialog if the user is currently browsing his favorites or full offline is enabled
            android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(this, PrefHelper.getDialogTheme());
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
                        MenuItem m = mNavView.getMenu().findItem(R.id.nav_favorites);
                        selectDrawerItem(m);
                    }
                });
            }
            mDialog.show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        customTabActivityHelper.bindCustomTabsService(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        customTabActivityHelper.unbindCustomTabsService(this);
    }

    public static MainActivity getInstance() {
        return instance;
    }

    /*private void setupFab(FloatingActionButton fab) {
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

        fab.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();
                if (sCurrentFragment == R.id.nav_browser) {
                    if (!fullOffline) {
                        ((ComicBrowserFragment) fragmentManager.findFragmentByTag("browser")).getPreviousRandom();
                    } else {
                        ((OfflineFragment) fragmentManager.findFragmentByTag("browser")).getPreviousRandom();
                    }
                }
                return true;
            }
        });
    }*/
    @OnClick(R.id.fab) void onClick() {
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

    @OnLongClick(R.id.fab) boolean onLongClick() {
        android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();
        if (sCurrentFragment == R.id.nav_browser) {
            if (!fullOffline) {
                ((ComicBrowserFragment) fragmentManager.findFragmentByTag("browser")).getPreviousRandom();
            } else {
                ((OfflineFragment) fragmentManager.findFragmentByTag("browser")).getPreviousRandom();
            }
        }
        return true;
    }

    private void showRateSnackbar() {
        // Thanks to /u/underhound for this great idea! https://www.reddit.com/r/Android/comments/3i6uw0/dev_i_think_ive_mastered_the_art_of_asking_for/
        if (PrefHelper.showRateDialog()) {
            View.OnClickListener oc = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Uri uri = Uri.parse("market://details?id=" + MainActivity.this.getPackageName());
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + MainActivity.this.getPackageName())));
                    }
                }
            };
            Snackbar.make(mFab, R.string.snackbar_rate, Snackbar.LENGTH_LONG)
                    .setAction(R.string.snackbar_rate_action, oc)
                    .show();
        }
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
                    MenuItem m = mNavView.getMenu().findItem(sCurrentFragment);
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
                    MenuItem m = mNavView.getMenu().findItem(R.id.nav_browser);
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
                if (!isOnline() && !fullOfflineWhatIf) {
                    Toast toast = Toast.makeText(this, R.string.no_connection, Toast.LENGTH_SHORT);
                    toast.show();
                    MenuItem m = mNavView.getMenu().findItem(sCurrentFragment);
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
                sDrawer.closeDrawer(mNavView);
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
                sDrawer.closeDrawer(mNavView);
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "easyxkcd@gmail.com", null));
                        startActivity(Intent.createChooser(i, getResources().getString(R.string.nav_feedback_send)));
                    }
                }, 200);
                return;

            case R.id.nav_about:
                sDrawer.closeDrawer(mNavView);
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
                    if (isOnline() && !fullOfflineWhatIf) {
                        fragmentManager.beginTransaction().setCustomAnimations(R.anim.abc_slide_in_bottom, R.anim.abc_slide_in_bottom).add(R.id.flContent, new WhatIfFragment(), fragmentTagShow).commitAllowingStateLoss();
                    } else {
                        fragmentManager.beginTransaction().setCustomAnimations(R.anim.abc_slide_in_bottom, R.anim.abc_slide_in_bottom).add(R.id.flContent, new OfflineWhatIfFragment(), fragmentTagShow).commitAllowingStateLoss();
                    }
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
                MenuItem item = mNavView.getMenu().findItem(R.id.nav_whatif);
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

        if (("de.tap.easy_xkcd.ACTION_WHAT_IF").equals(getIntent().getAction())) {
            MenuItem item = mNavView.getMenu().findItem(R.id.nav_whatif);
            selectDrawerItem(item);
            WhatIfActivity.WhatIfIndex = intent.getIntExtra("number", 1);
            Intent whatIf = new Intent(MainActivity.this, WhatIfActivity.class);
            PrefHelper.setLastWhatIf(WhatIfActivity.WhatIfIndex);
            startActivity(whatIf);
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

        if (PrefHelper.hideDonate()) {
            menu.findItem(R.id.action_donate).setVisible(false);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_donate:
                startActivity(new Intent(MainActivity.this, DonateActivity.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
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
                MenuItem m = mNavView.getMenu().findItem(sCurrentFragment);
                selectDrawerItem(m);
            }

            if (PrefHelper.getNotificationInterval() != 0) {
                WakefulIntentService.scheduleAlarms(new ComicListener(), this, true);
            } else {
                WakefulIntentService.cancelAlarms(this);
            }
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

    @Override
    public void onBackPressed() {
        if (getSearchMenuItem().isActionViewExpanded()) {
            getSearchMenuItem().collapseActionView();
        } else {
            super.onBackPressed();
        }
    }

}

