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

package de.tap.easy_xkcd.Activities;

import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.transition.Transition;
import android.transition.TransitionInflater;
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
import butterknife.OnClick;
import butterknife.OnLongClick;
import de.tap.easy_xkcd.fragments.ComicBrowserFragment;
import de.tap.easy_xkcd.fragments.OverviewListFragment;
import de.tap.easy_xkcd.notifications.ComicListener;
import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.utils.Favorites;
import de.tap.easy_xkcd.fragments.FavoritesFragment;
import de.tap.easy_xkcd.fragments.OfflineFragment;
import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.fragments.WhatIfFragment;
import de.tap.easy_xkcd.fragments.WhatIfOverviewFragment;
import de.tap.easy_xkcd.CustomTabHelpers.CustomTabActivityHelper;


public class MainActivity extends AppCompatActivity {
    @Bind(R.id.fab)
    FloatingActionButton mFab;
    @Bind(R.id.nvView)
    NavigationView mNavView;
    @Bind(R.id.toolbar)
    Toolbar toolbar;
    @Bind(R.id.drawer_layout)
    DrawerLayout mDrawer;

    private static MainActivity instance;

    public static boolean fullOffline = false;
    public static boolean fullOfflineWhatIf = false;
    public static boolean fromSearch = false;

    public ActionBarDrawerToggle mDrawerToggle;
    private MenuItem searchMenuItem;
    private CustomTabActivityHelper customTabActivityHelper;
    private int mCurrentFragment;
    private ProgressDialog mProgress;
    private PrefHelper prefHelper;

    private static final String COMIC_INTENT = "de.tap.easy_xkcd.ACTION_COMIC";
    private static final String WHATIF_INTENT = "de.tap.easy_xkcd.ACTION_WHAT_IF";
    private static final String SAVED_INSTANCE_CURRENT_FRAGMENT = "CurrentFragment";
    private static final String BROWSER_TAG = "browser";
    private static final String FAV_TAG = "favorites";
    private static final String WHATIF_TAG = "whatif";
    private static final String OVERVIEW_TAG = "overview";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefHelper = new PrefHelper(this);
        setTheme(prefHelper.getTheme());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        instance = this;
        customTabActivityHelper = new CustomTabActivityHelper();

        if (savedInstanceState == null) {
            if (prefHelper.getNotificationInterval() != 0) {
                WakefulIntentService.scheduleAlarms(new ComicListener(), this, true);
            } else {
                WakefulIntentService.cancelAlarms(this);
            }
        }

        fullOffline = prefHelper.fullOfflineEnabled();
        fullOfflineWhatIf = prefHelper.fullOfflineWhatIf();
        boolean showProgress = true;
        boolean whatIfIntent = false;

        //Check for intents
        if (getIntent().getAction().equals(Intent.ACTION_VIEW)) {
            if (getIntent().getDataString().contains("what")) {
                WhatIfActivity.WhatIfIndex = (getNumberFromUrl(getIntent().getDataString(), 1));
                prefHelper.setLastWhatIf(WhatIfActivity.WhatIfIndex);
                whatIfIntent = true;
                WhatIfFragment.newIntent = true;
            } else {
                prefHelper.setLastComic(getNumberFromUrl(getIntent().getDataString(), 0));
                OfflineFragment.sLastComicNumber = (getNumberFromUrl(getIntent().getDataString(), 0));
            }
        }
        if ((COMIC_INTENT).equals(getIntent().getAction())) {
            int number = getIntent().getIntExtra("number", 0);
            showProgress = false;
            if (prefHelper.isOnline(this) && !fullOffline) {
                prefHelper.setLastComic(number);
            } else {
                OfflineFragment.sLastComicNumber = number;
            }
        }
        if ((WHATIF_INTENT).equals(getIntent().getAction())) {
            WhatIfActivity.WhatIfIndex = getIntent().getIntExtra("number", 0);
            prefHelper.setLastWhatIf(WhatIfActivity.WhatIfIndex);
            whatIfIntent = true;
            WhatIfFragment.newIntent = true;
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
            if (!prefHelper.colorNavbar())
                getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.ColorPrimaryBlack));
        }
        setSupportActionBar(toolbar);
        TypedValue typedValue2 = new TypedValue();
        getTheme().resolveAttribute(R.attr.colorPrimary, typedValue2, true);
        toolbar.setBackgroundColor(typedValue2.data);
        if (savedInstanceState == null)
            toolbar.setAlpha(0);

        mDrawer.setDrawerListener(mDrawerToggle);
        mDrawerToggle = setupDrawerToggle();
        if (prefHelper.nightThemeEnabled()) {
            mNavView.setBackgroundColor(ContextCompat.getColor(this, R.color.background_material_dark));
            toolbar.setPopupTheme(R.style.ThemeOverlay_AppCompat);
        }
        setupDrawerContent(mNavView);

        if (savedInstanceState == null) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    prefHelper.showRateSnackbar(MainActivity.this.getPackageName(), MainActivity.this, mFab);
                    prefHelper.showSurveySnackbar(MainActivity.this, mFab);
                    prefHelper.showFeatureSnackbar(MainActivity.this, mFab);
                }
            }, 1500);
        }

        //Load fragment
        if (prefHelper.isOnline(this) | fullOffline | fullOfflineWhatIf) {
            MenuItem item;
            if (savedInstanceState != null) {
                //Get last loaded fragment
                mCurrentFragment = savedInstanceState.getInt(SAVED_INSTANCE_CURRENT_FRAGMENT);
                item = mNavView.getMenu().findItem(mCurrentFragment);
            } else {
                if (!whatIfIntent && fullOffline | prefHelper.isOnline(this)) {
                    //Load ComicBrowserFragment by default
                    if (showProgress) {
                        mProgress = ProgressDialog.show(this, "", this.getResources().getString(R.string.loading_comics), true);
                    }
                    item = mNavView.getMenu().findItem(R.id.nav_browser);
                } else {
                    item = mNavView.getMenu().findItem(R.id.nav_whatif);
                }
            }
            boolean showOverview = false;
            if (savedInstanceState != null)
                showOverview = savedInstanceState.getBoolean(OVERVIEW_TAG, false);
            selectDrawerItem(item, showOverview);
        } else if ((mCurrentFragment != R.id.nav_favorites)) { //Don't show the dialog if the user is currently browsing his favorites or full offline is enabled
            AlertDialog.Builder mDialog = new AlertDialog.Builder(this, prefHelper.getDialogTheme());
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
                        selectDrawerItem(m, false);
                    }
                });
            }
            mDialog.show();
        }
    }

    @SuppressWarnings("unused") // it's actually used, just injected by Butter Knife
    @OnClick(R.id.fab)
    void onClick() {
        android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();
        OverviewListFragment overviewListFragment = (OverviewListFragment) fragmentManager.findFragmentByTag(OVERVIEW_TAG);
        if (overviewListFragment != null && overviewListFragment.isVisible()) {
            if (!fullOffline) {
                ComicBrowserFragment comicBrowserFragment = (ComicBrowserFragment) fragmentManager.findFragmentByTag(BROWSER_TAG);
                overviewListFragment.showComic(comicBrowserFragment.sNewestComicNumber - prefHelper.getRandomNumber(comicBrowserFragment.sLastComicNumber));
            }
            else {
                overviewListFragment.showComic(OfflineFragment.sNewestComicNumber - prefHelper.getRandomNumber(OfflineFragment.sLastComicNumber));
            }

        } else {
            switch (mCurrentFragment) {
                case R.id.nav_browser: {
                    if (!fullOffline) {
                        ((ComicBrowserFragment) fragmentManager.findFragmentByTag(BROWSER_TAG)).getRandomComic();
                    } else {
                        ((OfflineFragment) fragmentManager.findFragmentByTag(BROWSER_TAG)).getRandomComic();
                    }
                    break;
                }
                case R.id.nav_favorites: {
                    ((FavoritesFragment) fragmentManager.findFragmentByTag(FAV_TAG)).getRandomComic();
                    break;
                }
            }
        }
    }

    @SuppressWarnings("unused")
    @OnLongClick(R.id.fab)
    boolean onLongClick() {
        android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();
        OverviewListFragment overviewListFragment = (OverviewListFragment) fragmentManager.findFragmentByTag(OVERVIEW_TAG);
        if (overviewListFragment != null && overviewListFragment.isVisible()) {
            return true;
        } else {
            if (mCurrentFragment == R.id.nav_browser) {
                if (!fullOffline) {
                    ((ComicBrowserFragment) fragmentManager.findFragmentByTag(BROWSER_TAG)).getPreviousRandom();
                } else {
                    ((OfflineFragment) fragmentManager.findFragmentByTag(BROWSER_TAG)).getPreviousRandom();
                }
            }
        }
        return true;
    }

    private void setupDrawerContent(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        selectDrawerItem(menuItem, false);
                        return true;
                    }
                });
    }

    public void selectDrawerItem(final MenuItem menuItem, final boolean showOverview) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (menuItem.getItemId() == R.id.nav_whatif)
                toolbar.setElevation(0);
            else {
                Resources r = getResources();
                float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, r.getDisplayMetrics());
                toolbar.setElevation(px);
            }
        }
        switch (menuItem.getItemId()) {
            case R.id.nav_browser:
                if (!prefHelper.isOnline(this) && !fullOffline) {
                    showDrawerErrorToast(R.string.no_connection);
                    return;
                }
                //showComicBrowserFragment
                animateToolbar(-300);
                showFragment("pref_random_comics", menuItem.getItemId(), "Comics", BROWSER_TAG, FAV_TAG, WHATIF_TAG, showOverview);
                break;
            case R.id.nav_favorites:
                //Check if there are any Favorites
                if (Favorites.getFavoriteList(this).length == 0) {
                    showDrawerErrorToast(R.string.no_favorites);
                    return;
                }
                //showFavoritesFragment
                animateToolbar(300);
                showFragment("pref_random_favorites", menuItem.getItemId(), getResources().getString(R.string.nv_favorites), FAV_TAG, BROWSER_TAG, WHATIF_TAG, showOverview);
                break;
            case R.id.nav_whatif:
                if (!prefHelper.isOnline(this) && !fullOfflineWhatIf) {
                    showDrawerErrorToast(R.string.no_connection);
                    return;
                }
                animateToolbar(300);
                if (getSupportFragmentManager().findFragmentByTag(WHATIF_TAG) == null) {
                    mDrawer.closeDrawers();
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            showFragment("", menuItem.getItemId(), "What if?", WHATIF_TAG, FAV_TAG, BROWSER_TAG, showOverview);
                        }
                    }, 150);
                } else {
                    showFragment("", menuItem.getItemId(), "What if?", WHATIF_TAG, FAV_TAG, BROWSER_TAG, showOverview);
                }
                break;

            case R.id.nav_settings:
                mDrawer.closeDrawers();
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Intent i = new Intent(MainActivity.this, SettingsActivity.class);
                        startActivity(i);
                    }
                }, 200);
                return;

            case R.id.nav_feedback:
                mDrawer.closeDrawers();
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "easyxkcd@gmail.com", null));
                        startActivity(Intent.createChooser(i, getResources().getString(R.string.nav_feedback_send)));
                    }
                }, 200);
                return;

            case R.id.nav_about:
                mDrawer.closeDrawers();
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
        mDrawer.closeDrawers();
        mCurrentFragment = menuItem.getItemId();
        invalidateOptionsMenu();
    }

    private void showDrawerErrorToast(int errorId) {
        Toast.makeText(this, errorId, Toast.LENGTH_SHORT).show();
        MenuItem m = mNavView.getMenu().findItem(mCurrentFragment);
        m.setChecked(true);
        mDrawer.closeDrawers();
    }

    private void animateToolbar(int translation) {
        View view;
        for (int i = 2; i < toolbar.getChildCount(); i++) {
            view = toolbar.getChildAt(i);
            view.setTranslationY(translation);
            view.animate().setStartDelay(50 * (i + 1)).setDuration(70 * (i + 1)).translationY(0);
        }
        toolbar.getChildAt(0).setAlpha(0);
        toolbar.getChildAt(0).animate().alpha(1).setDuration(200).setInterpolator(new AccelerateInterpolator());
    }

    private void showFragment(String prefTag, int itemId, String actionBarTitle, String fragmentTagShow, String fragmentTagHide, String fragmentTagHide2, boolean showOverview) {
        android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();
        assert getSupportActionBar() != null;  //We always have an ActionBar available, so this stops Android Studio from complaining about possible NullPointerExceptions
        //Setup FAB
        if (prefHelper.fabEnabled(prefTag)) {
            mFab.setVisibility(View.GONE);
        } else if (!fragmentTagShow.equals(WHATIF_TAG)) {
            mFab.setVisibility(View.VISIBLE);
        } else {
            mFab.setVisibility(View.GONE);
        }
        getSupportActionBar().setTitle(actionBarTitle);
        if (fragmentManager.findFragmentByTag(fragmentTagShow) != null) {
            //if the fragment exists, show it.
            android.support.v4.app.FragmentTransaction ft = fragmentManager.beginTransaction();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                fragmentManager.findFragmentByTag(fragmentTagShow).setEnterTransition(null);
            if (fragmentTagShow.equals(BROWSER_TAG)) {
                ft.setCustomAnimations(R.anim.abc_slide_in_top, R.anim.abc_slide_in_top);
            } else {
                ft.setCustomAnimations(R.anim.abc_slide_in_bottom, R.anim.abc_slide_in_bottom);
            }
            ft.show(fragmentManager.findFragmentByTag(fragmentTagShow));
            ft.commitAllowingStateLoss();
            //Update action bar
        } else {
            //if the fragment does not exist, add it to fragment manager.
            switch (itemId) {
                case R.id.nav_favorites:
                    fragmentManager.beginTransaction().setCustomAnimations(R.anim.abc_slide_in_bottom, R.anim.abc_slide_in_bottom).add(R.id.flContent, new FavoritesFragment(), fragmentTagShow).commitAllowingStateLoss();
                    break;
                case R.id.nav_browser:
                    if (prefHelper.isOnline(this) && !fullOffline) {
                        fragmentManager.beginTransaction().add(R.id.flContent, new ComicBrowserFragment(), fragmentTagShow).commitAllowingStateLoss();
                    } else {
                        fragmentManager.beginTransaction().add(R.id.flContent, new OfflineFragment(), fragmentTagShow).commitAllowingStateLoss();
                    }
                    break;
                case R.id.nav_whatif:
                    fragmentManager.beginTransaction().add(R.id.flContent, new WhatIfOverviewFragment(), fragmentTagShow).commitAllowingStateLoss();
                    break;
            }
        }
        if (prefHelper.subtitleEnabled() && itemId != R.id.nav_whatif) {
            switch (itemId) {
                //Update Action Bar title
                case R.id.nav_favorites: {
                    if (FavoritesFragment.sFavorites != null)
                        getSupportActionBar().setSubtitle(String.valueOf(FavoritesFragment.sFavorites[FavoritesFragment.sFavoriteIndex]));
                    break;
                }
                case R.id.nav_browser: {
                    if (prefHelper.isOnline(this) && !fullOffline) {
                        getSupportActionBar().setSubtitle(String.valueOf(prefHelper.getLastComic()));
                    } else {
                        getSupportActionBar().setSubtitle(String.valueOf(OfflineFragment.sLastComicNumber));
                    }
                    break;
                }
            }
        } else if (itemId == R.id.nav_whatif) {
            getSupportActionBar().setSubtitle("");
        }
        if (fragmentManager.findFragmentByTag(fragmentTagHide) != null)
            fragmentManager.beginTransaction().hide(fragmentManager.findFragmentByTag(fragmentTagHide)).commitAllowingStateLoss();
        if (fragmentManager.findFragmentByTag(fragmentTagHide2) != null)
            fragmentManager.beginTransaction().hide(fragmentManager.findFragmentByTag(fragmentTagHide2)).commitAllowingStateLoss();
        if (fragmentManager.findFragmentByTag(OVERVIEW_TAG) != null)
            fragmentManager.beginTransaction().hide(fragmentManager.findFragmentByTag(OVERVIEW_TAG)).commitAllowingStateLoss();

        if (showOverview)
            showOverview();
    }


    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        android.support.v4.app.FragmentManager fm = getSupportFragmentManager();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            //Get the ComicBrowserFragment and update it
            if (intent.getDataString().contains("what")) {
                MenuItem item = mNavView.getMenu().findItem(R.id.nav_whatif);
                selectDrawerItem(item, false);
                WhatIfActivity.WhatIfIndex = getNumberFromUrl(getIntent().getDataString(), 1);
                Intent whatIf = new Intent(MainActivity.this, WhatIfActivity.class);
                prefHelper.setLastWhatIf(WhatIfActivity.WhatIfIndex);
                startActivity(whatIf);
            } else if (prefHelper.isOnline(this) && !fullOffline) {
                MenuItem item = mNavView.getMenu().findItem(R.id.nav_browser);
                selectDrawerItem(item, false);
                ComicBrowserFragment comicBrowserFragment = (ComicBrowserFragment) getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);
                comicBrowserFragment.sLastComicNumber = getNumberFromUrl(getIntent().getDataString(), 0);
                comicBrowserFragment.mPager.setCurrentItem(comicBrowserFragment.sLastComicNumber - 1, false);
            } else {
                MenuItem item = mNavView.getMenu().findItem(R.id.nav_browser);
                selectDrawerItem(item, false);
                OfflineFragment.sLastComicNumber = getNumberFromUrl(getIntent().getDataString(), 0);
                ((ComicBrowserFragment)fm.findFragmentByTag(BROWSER_TAG)).mPager.setCurrentItem(OfflineFragment.sLastComicNumber - 1, false);
            }
        }
        if ((COMIC_INTENT).equals(getIntent().getAction())) {
            int number = getIntent().getIntExtra("number", 0);
            if (prefHelper.isOnline(this) && !fullOffline) {
                ComicBrowserFragment fragment = (ComicBrowserFragment) fm.findFragmentByTag(BROWSER_TAG);
                fragment.sLastComicNumber = number;
                fragment.sNewestComicNumber = 0;
                mProgress = ProgressDialog.show(this, "", this.getResources().getString(R.string.loading_comics), true);
                fragment.updatePager();
            } else {
                OfflineFragment fragment = (OfflineFragment) fm.findFragmentByTag(BROWSER_TAG);
                OfflineFragment.sLastComicNumber = number;
                fragment.updatePager();
            }
        }
        if ((WHATIF_INTENT).equals(getIntent().getAction())) {
            MenuItem item = mNavView.getMenu().findItem(R.id.nav_whatif);
            selectDrawerItem(item, false);
            WhatIfActivity.WhatIfIndex = intent.getIntExtra("number", 1);
            Intent whatIf = new Intent(MainActivity.this, WhatIfActivity.class);
            prefHelper.setLastWhatIf(WhatIfActivity.WhatIfIndex);
            startActivity(whatIf);
        }
    }

    private int getNumberFromUrl(String url, int defaultNumber) {
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
            return defaultNumber;
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
        if (prefHelper.hideDonate())
            menu.findItem(R.id.action_donate).setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_donate:
                startActivity(new Intent(MainActivity.this, DonateActivity.class));
                return true;

            case R.id.action_overview:
                /*fromSearch = true;
                startActivity(new Intent(MainActivity.this, OverviewActivity.class));*/
                showOverview();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showOverview() {
        android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            if (fragmentManager.findFragmentByTag(OVERVIEW_TAG) == null)
                fragmentManager.beginTransaction().add(R.id.flContent, new OverviewListFragment(), OVERVIEW_TAG).commitAllowingStateLoss();
            else
                fragmentManager.beginTransaction().show(fragmentManager.findFragmentByTag(OVERVIEW_TAG)).commitAllowingStateLoss();
            fragmentManager.beginTransaction().hide(fragmentManager.findFragmentByTag(BROWSER_TAG)).commitAllowingStateLoss();
        } else {
            Transition left = TransitionInflater.from(this).inflateTransition(android.R.transition.slide_left);
            Transition right = TransitionInflater.from(this).inflateTransition(android.R.transition.fade);
            fragmentManager.findFragmentByTag(BROWSER_TAG).setExitTransition(right);

            if (fragmentManager.findFragmentByTag(OVERVIEW_TAG) == null) {
                OverviewListFragment overviewListFragment = new OverviewListFragment();
                overviewListFragment.setEnterTransition(left);
                getSupportFragmentManager().beginTransaction()
                        .hide(fragmentManager.findFragmentByTag(BROWSER_TAG))
                        .add(R.id.flContent, overviewListFragment, OVERVIEW_TAG)
                        .commit();
            } else {
                fragmentManager.findFragmentByTag(OVERVIEW_TAG).setEnterTransition(left);
                getSupportFragmentManager().beginTransaction()
                        .hide(fragmentManager.findFragmentByTag(BROWSER_TAG))
                        .show(fragmentManager.findFragmentByTag(OVERVIEW_TAG))

                        .commit();
            }
            fragmentManager.findFragmentByTag(BROWSER_TAG).setExitTransition(null);
        }
        assert getSupportActionBar() != null;
        getSupportActionBar().setSubtitle("");
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        //Save the current fragment
        savedInstanceState.putInt(SAVED_INSTANCE_CURRENT_FRAGMENT, mCurrentFragment);
        if (getSupportFragmentManager().findFragmentByTag(OVERVIEW_TAG) != null)
            savedInstanceState.putBoolean(OVERVIEW_TAG, getSupportFragmentManager().findFragmentByTag(OVERVIEW_TAG).isVisible());
        else
            savedInstanceState.putBoolean(OVERVIEW_TAG, false);
        super.onSaveInstanceState(savedInstanceState);
    }

    public MenuItem getSearchMenuItem() {
        return searchMenuItem;
    }

    public FloatingActionButton getFab() {
        return mFab;
    }

    public Toolbar getToolbar() {
        return toolbar;
    }

    public NavigationView getNavView() {
        return mNavView;
    }

    public int getCurrentFragment() {
        return mCurrentFragment;
    }

    public ProgressDialog getProgressDialog() {
        return mProgress;
    }

    public void setProgressDialog(String message, boolean cancel) {
        mProgress = ProgressDialog.show(this, "", message, cancel);
    }

    private ActionBarDrawerToggle setupDrawerToggle() {
        return new ActionBarDrawerToggle(this, mDrawer, toolbar, R.string.drawer_open, R.string.drawer_close);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggles
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onBackPressed() {
        android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();
        if (getSearchMenuItem().isActionViewExpanded()) {
            getSearchMenuItem().collapseActionView();
        } else if (mCurrentFragment == R.id.nav_browser && (fragmentManager.findFragmentByTag(OVERVIEW_TAG) == null || !fragmentManager.findFragmentByTag(OVERVIEW_TAG).isVisible())) {
            boolean zoomReset;
            if (!fullOffline) {
                zoomReset = ((ComicBrowserFragment)fragmentManager.findFragmentByTag(BROWSER_TAG)).zoomReset();
            } else {
                zoomReset = ((OfflineFragment)fragmentManager.findFragmentByTag(BROWSER_TAG)).zoomReset();
            }
            if (!zoomReset) {
                showOverview();
            }
        } else if (mCurrentFragment == R.id.nav_favorites) {
            if (!FavoritesFragment.zoomReset()) {
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
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

    @Override
    protected void onResume() {
        super.onResume();
        try {
            mNavView.getMenu().findItem(getCurrentFragment()).setChecked(true);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onRestart() {
        android.support.v4.app.Fragment fragment = getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);
        if (fragment != null && prefHelper.isOnline(this) && !fromSearch) {
            if (!fullOffline) {
                ((ComicBrowserFragment) fragment).updatePager();
            } else if (prefHelper.isWifi(this) | prefHelper.mobileEnabled()) {
                ((OfflineFragment) fragment).updatePager();
            }
        }
        if (fromSearch)
            fromSearch = false;
        super.onRestart();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    public static MainActivity getInstance() {
        return instance;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    if (mCurrentFragment == R.id.nav_favorites) {
                        FavoritesFragment fragment = (FavoritesFragment) getSupportFragmentManager().findFragmentByTag(FAV_TAG);
                        fragment.shareComicImage();
                    } else {
                        ComicBrowserFragment fragment = (ComicBrowserFragment) getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);
                        fragment.shareComicImage();
                    }

        }
    }

    public PrefHelper getPrefHelper() {
        return prefHelper;
    }

}

