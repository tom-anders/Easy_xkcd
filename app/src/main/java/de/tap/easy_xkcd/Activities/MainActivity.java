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

import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.net.Uri;
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
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.util.Log;
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

import java.util.Random;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnLongClick;
import de.tap.easy_xkcd.fragments.ComicBrowserFragment;
import de.tap.easy_xkcd.fragments.ComicFragment;
import de.tap.easy_xkcd.fragments.OverviewBaseFragment;
import de.tap.easy_xkcd.fragments.OverviewCardsFragment;
import de.tap.easy_xkcd.fragments.OverviewListFragment;
import de.tap.easy_xkcd.notifications.ComicListener;
import de.tap.easy_xkcd.utils.Favorites;
import de.tap.easy_xkcd.fragments.FavoritesFragment;
import de.tap.easy_xkcd.fragments.OfflineFragment;
import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.fragments.WhatIfFragment;
import de.tap.easy_xkcd.fragments.WhatIfOverviewFragment;
import de.tap.easy_xkcd.CustomTabHelpers.CustomTabActivityHelper;
import de.tap.easy_xkcd.utils.ThemePrefs;


public class MainActivity extends BaseActivity {
    @Bind(R.id.fab)
    FloatingActionButton mFab;
    @Bind(R.id.nvView)
    NavigationView mNavView;
    @Bind(R.id.drawer_layout)
    DrawerLayout mDrawer;
    @Bind(R.id.toolbar)
    Toolbar toolbar;

    public static boolean fullOffline = false;
    public static boolean fullOfflineWhatIf = false;
    public static boolean fromSearch = false;
    public static boolean overviewLaunch;

    public ActionBarDrawerToggle drawerToggle;
    private MenuItem searchMenuItem;
    private CustomTabActivityHelper customTabActivityHelper;
    private int currentFragment;
    private ProgressDialog progress;

    private static final String COMIC_INTENT = "de.tap.easy_xkcd.ACTION_COMIC";
    private static final String WHATIF_INTENT = "de.tap.easy_xkcd.ACTION_WHAT_IF";
    private static final String SAVED_INSTANCE_CURRENT_FRAGMENT = "CurrentFragment";
    private static final String BROWSER_TAG = "browser";
    private static final String FAV_TAG = "favorites";
    private static final String WHATIF_TAG = "whatif";
    private static final String OVERVIEW_TAG = "overview";
    public static final int UPDATE_ALARM = 2;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefHelper.moveFavorites(this);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        PreferenceManager.setDefaultValues(this, R.xml.pref_alt_sharing, false);

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
        boolean whatIfIntent = false;

        //Check for intents
        switch (getIntent().getAction()) {
            case Intent.ACTION_VIEW:
                if (getIntent().getDataString().contains("what")) {
                    WhatIfActivity.WhatIfIndex = (getNumberFromUrl(getIntent().getDataString(), 1));
                    prefHelper.setLastWhatIf(WhatIfActivity.WhatIfIndex);
                    whatIfIntent = true;
                    WhatIfFragment.newIntent = true;
                } else {
                    prefHelper.setLastComic(getNumberFromUrl(getIntent().getDataString(), 0));
                }
                break;
            case COMIC_INTENT:
                int number = getIntent().getIntExtra("number", 0);
                prefHelper.setLastComic(number);
                break;
            case WHATIF_INTENT:
                WhatIfActivity.WhatIfIndex = getIntent().getIntExtra("number", 0);
                prefHelper.setLastWhatIf(WhatIfActivity.WhatIfIndex);
                whatIfIntent = true;
                WhatIfFragment.newIntent = true;
                break;
        }

        setupToolbar(toolbar);
        if (savedInstanceState == null)
            toolbar.setAlpha(0);

        mDrawer.setDrawerListener(drawerToggle);
        drawerToggle = setupDrawerToggle();
        if (themePrefs.nightThemeEnabled()) {
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
        if (fullOffline || prefHelper.isOnline(this) || fullOfflineWhatIf) {
            MenuItem item;
            if (savedInstanceState != null) {
                currentFragment = savedInstanceState.getInt(SAVED_INSTANCE_CURRENT_FRAGMENT);
                item = mNavView.getMenu().findItem(currentFragment);
            } else {
                if (!whatIfIntent && fullOffline | prefHelper.isOnline(this)) {
                    item = mNavView.getMenu().findItem(R.id.nav_browser);
                } else {
                    item = mNavView.getMenu().findItem(R.id.nav_whatif);
                }
            }
            boolean showOverview = false;
            if (savedInstanceState != null)
                showOverview = savedInstanceState.getBoolean(OVERVIEW_TAG, false);
            else {
                overviewLaunch = prefHelper.launchToOverview() && !getIntent().getAction().equals(Intent.ACTION_VIEW);
            }
            selectDrawerItem(item, showOverview);
        } else if ((currentFragment != R.id.nav_favorites)) { //Don't show the dialog if the user is currently browsing his favorites or full offline is enabled
            AlertDialog.Builder mDialog = new AlertDialog.Builder(this, themePrefs.getDialogTheme());
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
        OverviewBaseFragment overviewBaseFragment = (OverviewBaseFragment) fragmentManager.findFragmentByTag(OVERVIEW_TAG);
        if (overviewBaseFragment != null && overviewBaseFragment.isVisible()) {
            ComicFragment comicFragment = (ComicFragment) fragmentManager.findFragmentByTag(BROWSER_TAG);
            if (!prefHelper.overviewFav())
                overviewBaseFragment.showComic(comicFragment.newestComicNumber - prefHelper.getRandomNumber(comicFragment.lastComicNumber));
            else
                overviewBaseFragment.showComic(new Random().nextInt(Favorites.getFavoriteList(MainActivity.this).length));
        } else {
            switch (currentFragment) {
                case R.id.nav_browser: {
                    ((ComicFragment) fragmentManager.findFragmentByTag(BROWSER_TAG)).getRandomComic();
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
        OverviewBaseFragment overviewBaseFragment = (OverviewBaseFragment) fragmentManager.findFragmentByTag(OVERVIEW_TAG);
        if (overviewBaseFragment != null && overviewBaseFragment.isVisible()) {
            return true;
        } else {
            if (currentFragment == R.id.nav_browser) {
                ((ComicFragment) fragmentManager.findFragmentByTag(BROWSER_TAG)).getPreviousRandom();
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
        int[][] state = new int[][] {
                new int[] {-android.R.attr.state_checked},
                new int[] {}
        };
        int[] color = new int[] {
                Color.BLACK,
                themePrefs.getPrimaryColor()
        };
        int[] colorIcon = new int[] {
                ContextCompat.getColor(this, android.R.color.tertiary_text_light),
                themePrefs.getPrimaryColor()
        };

        navigationView.setItemTextColor(new ColorStateList(state, color));
        navigationView.setItemIconTintList(new ColorStateList(state, colorIcon));
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
                        startActivityForResult(i, 1);
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
        currentFragment = menuItem.getItemId();
        invalidateOptionsMenu();
    }

    private void showDrawerErrorToast(int errorId) {
        Toast.makeText(this, errorId, Toast.LENGTH_SHORT).show();
        MenuItem m = mNavView.getMenu().findItem(currentFragment);
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
                    FavoritesFragment favoritesFragment = (FavoritesFragment) getSupportFragmentManager().findFragmentByTag(FAV_TAG);
                    if (favoritesFragment != null && favoritesFragment.favorites != null)
                        getSupportActionBar().setSubtitle(String.valueOf(favoritesFragment.favorites[favoritesFragment.favoriteIndex]));
                    break;
                }
                case R.id.nav_browser: {
                    ComicFragment comicFragment = (ComicFragment) getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);
                    if (comicFragment != null && comicFragment.lastComicNumber != 0)
                        getSupportActionBar().setSubtitle(String.valueOf(comicFragment.lastComicNumber));
                    else
                        getSupportActionBar().setSubtitle(String.valueOf(prefHelper.getLastComic()));
                    break;
                }
            }
        } else if (itemId == R.id.nav_whatif) {
            getSupportActionBar().setSubtitle("");
        }
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        if (fragmentManager.findFragmentByTag(fragmentTagHide) != null)
            fragmentTransaction.hide(fragmentManager.findFragmentByTag(fragmentTagHide));
        if (fragmentManager.findFragmentByTag(fragmentTagHide2) != null)
            fragmentTransaction.hide(fragmentManager.findFragmentByTag(fragmentTagHide2));
        if (fragmentManager.findFragmentByTag(OVERVIEW_TAG) != null)
            fragmentTransaction.hide(fragmentManager.findFragmentByTag(OVERVIEW_TAG));
        fragmentTransaction.commitAllowingStateLoss();

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
        switch (intent.getAction()) {
            case Intent.ACTION_VIEW:
                //Get the ComicBrowserFragment and update it
                if (intent.getDataString().contains("what")) {
                    MenuItem item = mNavView.getMenu().findItem(R.id.nav_whatif);
                    selectDrawerItem(item, false);
                    WhatIfActivity.WhatIfIndex = getNumberFromUrl(intent.getDataString(), 1);
                    Intent whatIf = new Intent(MainActivity.this, WhatIfActivity.class);
                    prefHelper.setLastWhatIf(WhatIfActivity.WhatIfIndex);
                    startActivity(whatIf);
                } else {
                    MenuItem item = mNavView.getMenu().findItem(R.id.nav_browser);
                    selectDrawerItem(item, false);
                    ComicFragment comicFragment = (ComicFragment) getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);
                    comicFragment.lastComicNumber = getNumberFromUrl(intent.getDataString(), 0);
                    comicFragment.scrollTo(comicFragment.lastComicNumber - 1, false);
                }
                break;
            case COMIC_INTENT:
                int number = getIntent().getIntExtra("number", 0);
                ComicFragment fragment = (ComicFragment) fm.findFragmentByTag(BROWSER_TAG);
                fragment.lastComicNumber = number;
                if (fragment instanceof ComicBrowserFragment)
                    progress = ProgressDialog.show(this, "", this.getResources().getString(R.string.loading_comics), true);
                fragment.updatePager();
                break;
            case WHATIF_INTENT:
                MenuItem item = mNavView.getMenu().findItem(R.id.nav_whatif);
                selectDrawerItem(item, false);
                WhatIfActivity.WhatIfIndex = intent.getIntExtra("number", 1);
                Intent whatIf = new Intent(MainActivity.this, WhatIfActivity.class);
                prefHelper.setLastWhatIf(WhatIfActivity.WhatIfIndex);
                startActivity(whatIf);
                break;
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
                showOverview();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void showOverview() {
        android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();

        if (fragmentManager.findFragmentByTag(OVERVIEW_TAG) != null) {
            int pos = ((ComicFragment) fragmentManager.findFragmentByTag(BROWSER_TAG)).lastComicNumber;
            ((OverviewBaseFragment) fragmentManager.findFragmentByTag(OVERVIEW_TAG)).notifyAdapter(pos);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            if (fragmentManager.findFragmentByTag(OVERVIEW_TAG) == null) {
                OverviewBaseFragment overviewBaseFragment = null;
                switch (prefHelper.getOverviewStyle()) {
                    case 0:
                        overviewBaseFragment = new OverviewListFragment();
                        break;
                    case 1:
                        overviewBaseFragment = new OverviewCardsFragment();
                        break;
                }
                fragmentManager.beginTransaction().add(R.id.flContent, overviewBaseFragment, OVERVIEW_TAG).commitAllowingStateLoss();
            } else
                fragmentManager.beginTransaction().show(fragmentManager.findFragmentByTag(OVERVIEW_TAG)).commitAllowingStateLoss();
            fragmentManager.beginTransaction().hide(fragmentManager.findFragmentByTag(BROWSER_TAG)).commitAllowingStateLoss();
        } else {
            Transition left = TransitionInflater.from(this).inflateTransition(android.R.transition.slide_left);
            Transition right = TransitionInflater.from(this).inflateTransition(android.R.transition.fade);
            fragmentManager.findFragmentByTag(BROWSER_TAG).setExitTransition(right);

            if (fragmentManager.findFragmentByTag(OVERVIEW_TAG) == null) {
                OverviewBaseFragment overviewBaseFragment = null;
                switch (prefHelper.getOverviewStyle()) {
                    case 0:
                        overviewBaseFragment = new OverviewListFragment();
                        break;
                    case 1:
                        overviewBaseFragment = new OverviewCardsFragment();
                        break;
                }
                overviewBaseFragment.setEnterTransition(left);
                getSupportFragmentManager().beginTransaction()
                        .hide(fragmentManager.findFragmentByTag(BROWSER_TAG))
                        .add(R.id.flContent, overviewBaseFragment, OVERVIEW_TAG)
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
        savedInstanceState.putInt(SAVED_INSTANCE_CURRENT_FRAGMENT, currentFragment);
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
        return currentFragment;
    }

    public ProgressDialog getProgressDialog() {
        return progress;
    }

    public void setProgressDialog(String message, boolean cancel) {
        progress = ProgressDialog.show(this, "", message, cancel);
    }

    private ActionBarDrawerToggle setupDrawerToggle() {
        return new ActionBarDrawerToggle(this, mDrawer, toolbar, R.string.drawer_open, R.string.drawer_close);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggles
        drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onBackPressed() {
        android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();
        if (getSearchMenuItem().isActionViewExpanded()) {
            getSearchMenuItem().collapseActionView();
        } else if (mDrawer != null && mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.closeDrawer(GravityCompat.START);
        } else if (currentFragment == R.id.nav_browser && (fragmentManager.findFragmentByTag(OVERVIEW_TAG) == null || !fragmentManager.findFragmentByTag(OVERVIEW_TAG).isVisible())) {
            boolean zoomReset;
            zoomReset = ((ComicFragment) fragmentManager.findFragmentByTag(BROWSER_TAG)).zoomReset();
            if (!zoomReset) {
                if (!SearchResultsActivity.isOpen && !getIntent().getAction().equals(Intent.ACTION_VIEW))
                    showOverview();
                else
                    super.onBackPressed();
            }
        } else if (currentFragment == R.id.nav_favorites) {
            FavoritesFragment favoritesFragment = (FavoritesFragment) fragmentManager.findFragmentByTag(FAV_TAG);
            if (!favoritesFragment.zoomReset())
                super.onBackPressed();
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
        ComicFragment fragment = (ComicFragment) getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);
        if (fragment != null && prefHelper.isOnline(this) && !fromSearch)
            if (!fullOffline || (prefHelper.isWifi(this) || prefHelper.mobileEnabled()))
                fragment.updatePager();

        if (fromSearch)
            fromSearch = false;
        super.onRestart();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        drawerToggle.syncState();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            switch (resultCode) {
                case RESULT_OK:
                    finish();
                    startActivity(getIntent());
                    break;
                case UPDATE_ALARM:
                    if (prefHelper.getNotificationInterval() != 0)
                        WakefulIntentService.scheduleAlarms(new ComicListener(), this, true);
                    else
                        WakefulIntentService.cancelAlarms(this);
                    break;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    if (currentFragment == R.id.nav_favorites) {
                        FavoritesFragment fragment = (FavoritesFragment) getSupportFragmentManager().findFragmentByTag(FAV_TAG);
                        fragment.shareComic(true);
                    } else {
                        ComicBrowserFragment fragment = (ComicBrowserFragment) getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);
                        fragment.shareComicImage();
                    }
                break;
            case 2:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    ComicBrowserFragment fragment = (ComicBrowserFragment) getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);
                    fragment.new SaveComicImageTask().execute(true);
                }

        }
    }

    public PrefHelper getPrefHelper() {
        return prefHelper;
    }
    public ThemePrefs getThemePrefs() {return themePrefs;}

}

