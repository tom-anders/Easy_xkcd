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
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
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
import com.nbsp.materialfilepicker.ui.FilePickerActivity;
import com.tap.xkcd_reader.R;

import java.util.Random;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnLongClick;
import de.tap.easy_xkcd.CustomTabHelpers.CustomTabActivityHelper;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.fragments.comics.ComicBrowserFragment;
import de.tap.easy_xkcd.fragments.comics.ComicFragment;
import de.tap.easy_xkcd.fragments.comics.FavoritesFragment;
import de.tap.easy_xkcd.fragments.comics.OfflineFragment;
import de.tap.easy_xkcd.fragments.overview.OverviewBaseFragment;
import de.tap.easy_xkcd.fragments.overview.OverviewCardsFragment;
import de.tap.easy_xkcd.fragments.overview.OverviewListFragment;
import de.tap.easy_xkcd.fragments.overview.OverviewStaggeredGridFragment;
import de.tap.easy_xkcd.fragments.whatIf.WhatIfFragment;
import de.tap.easy_xkcd.fragments.whatIf.WhatIfOverviewFragment;
import de.tap.easy_xkcd.notifications.ComicListener;
import de.tap.easy_xkcd.utils.PrefHelper;
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
    private DatabaseManager databaseManager;

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
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        PreferenceManager.setDefaultValues(this, R.xml.pref_alt_sharing, false);

        customTabActivityHelper = new CustomTabActivityHelper();
        databaseManager = new DatabaseManager(this);
        databaseManager.moveFavorites(this);

        if (savedInstanceState == null) {
            //Setup the notifications in case the device was restarted
            if (prefHelper.getNotificationInterval() != 0)
                WakefulIntentService.scheduleAlarms(new ComicListener(), this, true);
            else
                WakefulIntentService.cancelAlarms(this);
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
                } else
                    prefHelper.setLastComic(getNumberFromUrl(getIntent().getDataString(), prefHelper.getLastComic()));
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
        if (savedInstanceState == null && !SearchResultsActivity.isOpen)
            toolbar.setAlpha(0);

        mDrawer.addDrawerListener(drawerToggle);
        mDrawer.setStatusBarBackgroundColor(themePrefs.getPrimaryDarkColor());
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
                    //prefHelper.showFeatureSnackbar(MainActivity.this, mFab);
                }
            }, 1500);
        }

        //Load fragment
        if (fullOffline || prefHelper.isOnline(this) || fullOfflineWhatIf) { //Do we have internet or are in offline mode?
            MenuItem item;
            boolean showOverview = false;
            if (savedInstanceState != null) { //Show the current Fragment
                currentFragment = savedInstanceState.getInt(SAVED_INSTANCE_CURRENT_FRAGMENT);
                item = mNavView.getMenu().findItem(currentFragment);
            } else {
                if (!whatIfIntent && fullOffline | prefHelper.isOnline(this))
                    item = mNavView.getMenu().findItem(R.id.nav_browser);
                else
                    item = mNavView.getMenu().findItem(R.id.nav_whatif);
            }
            if (savedInstanceState != null)
                showOverview = savedInstanceState.getBoolean(OVERVIEW_TAG, false); //Check if overview mode was active before the device was rotated
            else
                overviewLaunch = prefHelper.launchToOverview() && !getIntent().getAction().equals(Intent.ACTION_VIEW); //Check if the user chose overview to be shown by default
            selectDrawerItem(item, showOverview, !showOverview);
        } else if ((currentFragment != R.id.nav_favorites)) { //Don't show the dialog if the user is currently browsing his favorites or full offline is enabled
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setMessage(R.string.no_connection)
                    .setPositiveButton(R.string.no_connection_retry, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                            startActivity(getIntent());
                        }
                    })
                    .setCancelable(false);
            if (!databaseManager.noFavorites()) {
                dialog.setNegativeButton(R.string.no_connection_favorites, new DialogInterface.OnClickListener() { //We have favorites, so let give the user the option to view them
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MenuItem m = mNavView.getMenu().findItem(R.id.nav_favorites);
                        selectDrawerItem(m, false, false);
                    }
                });
            }
            dialog.show();
        }
    }

    @SuppressWarnings("unused") // it's actually used, just injected by Butter Knife
    @OnClick(R.id.fab)
    void onClick() {
        android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();
        OverviewBaseFragment overviewBaseFragment = (OverviewBaseFragment) fragmentManager.findFragmentByTag(OVERVIEW_TAG);

        if (overviewBaseFragment != null && overviewBaseFragment.isVisible()) { //The user is in overview mode
            ComicFragment comicFragment = (ComicFragment) fragmentManager.findFragmentByTag(BROWSER_TAG);
            if (!prefHelper.overviewFav()) //Only favorites?
                if (!prefHelper.hideRead())
                    overviewBaseFragment.showRandomComic(prefHelper.getRandomNumber(comicFragment.lastComicNumber));
                else
                    overviewBaseFragment.showRandomComic(databaseManager.getRandomUnread());
            else
                overviewBaseFragment.showComic(new Random().nextInt(databaseManager.getFavComics().length));
        } else { // The user is browsing comics or favorites
            switch (currentFragment) {
                case R.id.nav_browser: {
                    ((ComicFragment) fragmentManager.findFragmentByTag(BROWSER_TAG)).getRandomComic();
                    if (prefHelper.showRandomTip()) {
                        Toast.makeText(this, getResources().getString(R.string.random_tip), Toast.LENGTH_LONG).show();
                        prefHelper.setRandomTip(false);
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
        OverviewBaseFragment overviewBaseFragment = (OverviewBaseFragment) fragmentManager.findFragmentByTag(OVERVIEW_TAG);
        if (overviewBaseFragment != null && overviewBaseFragment.isVisible())
            return false; // Long click does not work in overview
        else if (currentFragment == R.id.nav_browser)
            ((ComicFragment) fragmentManager.findFragmentByTag(BROWSER_TAG)).getPreviousRandom();
        return true;
    }

    /**
     * Adds the listener for the navigationView and adjusts the colors according to our theme
     */
    private void setupDrawerContent(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        selectDrawerItem(menuItem, false, false);
                        return true;
                    }
                });
        themePrefs.setupNavdrawerColor(navigationView);
    }

    /**
     * Selects a item from the navigation Drawer
     *
     * @param menuItem     the pressed menu item
     * @param showOverview should be true when the user selected "Launch to Overview Mode" in the settings
     * @param animateOverview  should be false when the device was rotated and the app showed overview mode before the rotation
     */
    public void selectDrawerItem(final MenuItem menuItem, final boolean showOverview, final boolean animateOverview) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { //Setup the toolbar elevation for WhatIf overview
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
                    showDrawerErrorToast(R.string.no_connection); //No connection, so show Error toast and return
                    return;
                }
                animateToolbar(-300);
                showFragment("pref_random_comics", menuItem.getItemId(), "Comics", BROWSER_TAG, FAV_TAG, WHATIF_TAG, showOverview, animateOverview);
                break;
            case R.id.nav_favorites:
                if (databaseManager.noFavorites()) {
                    showDrawerErrorToast(R.string.no_favorites); //No favorites, so show Error Toast and return
                    return;
                }
                animateToolbar(300);
                showFragment("pref_random_favorites", menuItem.getItemId(), getResources().getString(R.string.nv_favorites), FAV_TAG, BROWSER_TAG, WHATIF_TAG, showOverview, animateOverview);
                break;
            case R.id.nav_whatif:
                if (!prefHelper.isOnline(this) && !fullOfflineWhatIf) {
                    showDrawerErrorToast(R.string.no_connection); //No connection, so show Error toast and return
                    return;
                }
                animateToolbar(300);
                if (getSupportFragmentManager().findFragmentByTag(WHATIF_TAG) == null) {
                    mDrawer.closeDrawers();
                    new Handler().postDelayed(new Runnable() { //If the fragment is not added yet, add a small delay to avoid lag
                        @Override
                        public void run() {
                            showFragment("", menuItem.getItemId(), "What if?", WHATIF_TAG, FAV_TAG, BROWSER_TAG, showOverview, animateOverview);
                        }
                    }, 150);
                } else
                    showFragment("", menuItem.getItemId(), "What if?", WHATIF_TAG, FAV_TAG, BROWSER_TAG, showOverview, animateOverview);
                break;

            case R.id.nav_settings:
                mDrawer.closeDrawers();
                new Handler().postDelayed(new Runnable() { //Wait for the drawer to be closed to avoid lag
                    @Override
                    public void run() {
                        startActivityForResult(new Intent(MainActivity.this, SettingsActivity.class), 1);
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
                        startActivity(new Intent(MainActivity.this, AboutActivity.class));
                    }
                }, 250);
                return;
        }
        menuItem.setChecked(true);
        mDrawer.closeDrawers();
        currentFragment = menuItem.getItemId();
        invalidateOptionsMenu();
    }

    /**
     * Shows an error toast and resets the navigationDrawer to the previous item
     *
     * @param errorId the string resource to be shown
     */
    private void showDrawerErrorToast(int errorId) {
        Toast.makeText(this, errorId, Toast.LENGTH_SHORT).show();
        MenuItem m = mNavView.getMenu().findItem(currentFragment);
        m.setChecked(true);
        mDrawer.closeDrawers();
    }

    /**
     * Animates the toolbar and its childs
     *
     * @param translation The initial vertical translation of the menu items
     */
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

    /**
     * Shows a new fragment and adjusts toolbar and FAB accordingly
     *
     * @param prefTag          the preference tag that specifies whether the FAB should be hidden (Only in ComicBrowser or Favorites)
     * @param itemId           the id of the pressed menu item
     * @param toolbarTitle     the new title of the toolbar
     * @param fragmentTagShow  the tag of the fragment to be shown (Comic Browser, Favorites, WhatIf)
     * @param fragmentTagHide  the tag of the fragment to be hidden
     * @param fragmentTagHide2 the tag of the second fragment to be hidden
     * @param showOverview     should be true when the user selected "Launch to Overview Mode" in the settings
     * @param animateOverview  should be false when the device was rotated and the app showed overview mode before the rotation
     */

    private void showFragment(String prefTag, int itemId, String toolbarTitle, String fragmentTagShow, String fragmentTagHide, String fragmentTagHide2, boolean showOverview, boolean animateOverview) {
        android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();
        assert getSupportActionBar() != null;  //We always have an ActionBar available, so this stops Android Studio from complaining about possible NullPointerExceptions
        //Setup FAB
        if (prefHelper.fabEnabled(prefTag) || fragmentTagShow.equals(WHATIF_TAG))
            mFab.setVisibility(View.GONE); //User chose to hide fab or is is viewing WhatIf
        else
            mFab.setVisibility(View.VISIBLE);

        getSupportActionBar().setTitle(toolbarTitle);
        if (fragmentManager.findFragmentByTag(fragmentTagShow) != null) {
            //if the fragment exists, show it.
            android.support.v4.app.FragmentTransaction ft = fragmentManager.beginTransaction();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                fragmentManager.findFragmentByTag(fragmentTagShow).setEnterTransition(null);
            if (fragmentTagShow.equals(BROWSER_TAG))
                ft.setCustomAnimations(R.anim.abc_slide_in_top, R.anim.abc_slide_in_top); //ComicBrowser slide in from the top
            else
                ft.setCustomAnimations(R.anim.abc_slide_in_bottom, R.anim.abc_slide_in_bottom); //Favorites & WhatIf from the bottom

            ft.show(fragmentManager.findFragmentByTag(fragmentTagShow));
            ft.commitAllowingStateLoss();
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

        //Hide the other fragments
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        if (fragmentManager.findFragmentByTag(fragmentTagHide) != null)
            fragmentTransaction.hide(fragmentManager.findFragmentByTag(fragmentTagHide));
        if (fragmentManager.findFragmentByTag(fragmentTagHide2) != null)
            fragmentTransaction.hide(fragmentManager.findFragmentByTag(fragmentTagHide2));
        if (fragmentManager.findFragmentByTag(OVERVIEW_TAG) != null)
            fragmentTransaction.hide(fragmentManager.findFragmentByTag(OVERVIEW_TAG));
        fragmentTransaction.commitAllowingStateLoss();

        if (showOverview)
            showOverview(animateOverview);
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
                if (intent.getDataString().contains("what")) {
                    MenuItem item = mNavView.getMenu().findItem(R.id.nav_whatif);
                    selectDrawerItem(item, false, false);
                    WhatIfActivity.WhatIfIndex = getNumberFromUrl(intent.getDataString(), 1);
                    Intent whatIf = new Intent(MainActivity.this, WhatIfActivity.class);
                    prefHelper.setLastWhatIf(WhatIfActivity.WhatIfIndex);
                    startActivity(whatIf);
                } else {
                    MenuItem item = mNavView.getMenu().findItem(R.id.nav_browser);
                    selectDrawerItem(item, false, false);
                    ComicFragment comicFragment = (ComicFragment) getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);
                    comicFragment.lastComicNumber = getNumberFromUrl(intent.getDataString(), comicFragment.lastComicNumber);
                    comicFragment.scrollTo(comicFragment.lastComicNumber - 1, false);
                }
                break;
            case COMIC_INTENT:
                /*int number = getIntent().getIntExtra("number", 0);
                ComicFragment fragment = (ComicFragment) fm.findFragmentByTag(BROWSER_TAG);
                fragment.lastComicNumber = number;
                if (fragment instanceof ComicBrowserFragment && fragment.isVisible())
                    progress = ProgressDialog.show(this, "", this.getResources().getString(R.string.loading_comics), true);
                fragment.updatePager();*/
                MenuItem item = mNavView.getMenu().findItem(R.id.nav_browser);
                selectDrawerItem(item, false, false);
                ComicFragment comicFragment = (ComicFragment) getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);
                comicFragment.lastComicNumber = intent.getIntExtra("number", 1);
                comicFragment.scrollTo(comicFragment.lastComicNumber - 1, false);
                break;
            case WHATIF_INTENT:
                item = mNavView.getMenu().findItem(R.id.nav_whatif);
                selectDrawerItem(item, false, false);
                WhatIfActivity.WhatIfIndex = intent.getIntExtra("number", 1);
                Intent whatIf = new Intent(MainActivity.this, WhatIfActivity.class);
                prefHelper.setLastWhatIf(WhatIfActivity.WhatIfIndex);
                startActivity(whatIf);
                break;
        }
    }

    /**
     * Extracts the number from xkcd links
     *
     * @param url           the xkcd.com or m.xkcd.com url
     * @param defaultNumber the number to be returned when something went wrong (usually lastComicNumber)
     * @return the number of the comic that the url links to
     */
    private int getNumberFromUrl(String url, int defaultNumber) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c >= '0' && c <= '9')
                sb.append(c);
            else if (c == '/' && sb.length() > 0) //Fix for comic 1663
                break;
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
                //Show keyboard
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
                //Hide keyboard
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
        menu.findItem(R.id.action_night_mode).setChecked(themePrefs.nightEnabledThemeIgnoreAutoNight());
        menu.findItem(R.id.action_night_mode).setVisible(!themePrefs.autoNightEnabled());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_donate:
                startActivity(new Intent(MainActivity.this, DonateActivity.class));
                return true;

            case R.id.action_overview:
                showOverview(true);
                return true;

            case R.id.action_night_mode:
                return toggleNightMode(item);
        }
        return super.onOptionsItemSelected(item);
    }

    protected boolean toggleNightMode(MenuItem item) {
        item.setChecked(!item.isChecked());
        themePrefs.setNightThemeEnabled(item.isChecked());
        prefHelper.setLastComic(((ComicFragment) getSupportFragmentManager().findFragmentByTag(BROWSER_TAG)).lastComicNumber);

        Intent intent = getIntent();
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        overridePendingTransition(0, 0);
        finish();
        overridePendingTransition(0, 0);
        startActivity(intent);
        return true;
    }

    public void showOverview(boolean animate) {
        android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        if (fragmentManager.findFragmentByTag(OVERVIEW_TAG) != null) { //Scroll to the current comic
            int pos = ((ComicFragment) fragmentManager.findFragmentByTag(BROWSER_TAG)).lastComicNumber;
            ((OverviewBaseFragment) fragmentManager.findFragmentByTag(OVERVIEW_TAG)).notifyAdapter(pos);
        }

        if (animate)
            transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
        OverviewBaseFragment overviewBaseFragment = (OverviewBaseFragment) fragmentManager.findFragmentByTag(OVERVIEW_TAG);
        if (overviewBaseFragment == null || (overviewBaseFragment.overViewFav() != (currentFragment == R.id.nav_favorites)) ) {
            switch (prefHelper.getOverviewStyle()) {
                case 0:
                    overviewBaseFragment = new OverviewListFragment();
                    break;
                case 1:
                    overviewBaseFragment = new OverviewCardsFragment();
                    break;
                case 2:
                    overviewBaseFragment = new OverviewStaggeredGridFragment();
                    break;
            }
            transaction.add(R.id.flContent, overviewBaseFragment, OVERVIEW_TAG);
        } else
            transaction.show(fragmentManager.findFragmentByTag(OVERVIEW_TAG));
        if (fragmentManager.findFragmentByTag(BROWSER_TAG) != null)
            transaction.hide(fragmentManager.findFragmentByTag(BROWSER_TAG));
        if (fragmentManager.findFragmentByTag(FAV_TAG) != null)
            transaction.hide(fragmentManager.findFragmentByTag(FAV_TAG));
        transaction.commitAllowingStateLoss();

        assert getSupportActionBar() != null;
        getSupportActionBar().setSubtitle("");
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        //Save the current fragment
        savedInstanceState.putInt(SAVED_INSTANCE_CURRENT_FRAGMENT, currentFragment);
        //Remember if overview is currently visible
        if (getSupportFragmentManager().findFragmentByTag(OVERVIEW_TAG) != null)
            savedInstanceState.putBoolean(OVERVIEW_TAG, getSupportFragmentManager().findFragmentByTag(OVERVIEW_TAG).isVisible());
        else
            savedInstanceState.putBoolean(OVERVIEW_TAG, false);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggles
        drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        drawerToggle.syncState();
    }

    @Override
    public void onBackPressed() {
        android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();
        if (getSearchMenuItem().isActionViewExpanded()) {
            getSearchMenuItem().collapseActionView();
        } else if (mDrawer != null && mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.closeDrawer(GravityCompat.START);
        } else if ((currentFragment == R.id.nav_browser || currentFragment == R.id.nav_favorites) && (fragmentManager.findFragmentByTag(OVERVIEW_TAG) == null || !fragmentManager.findFragmentByTag(OVERVIEW_TAG).isVisible())) {
            boolean zoomReset;
            zoomReset = ((ComicFragment) fragmentManager.findFragmentByTag(BROWSER_TAG)).zoomReset(); //Reset the zoom level of the current image
            if (!zoomReset) {
                if (!SearchResultsActivity.isOpen && !getIntent().getAction().equals(Intent.ACTION_VIEW)) {
                    prefHelper.setOverviewFav(currentFragment == R.id.nav_favorites);
                    if (currentFragment == R.id.nav_favorites && (prefHelper.isOnline(this) || prefHelper.fullOfflineEnabled())) {
                        showOverview(true);
                        currentFragment = R.id.nav_browser;
                        getSupportActionBar().setTitle("Comics");
                        mNavView.getMenu().findItem(R.id.nav_browser).setChecked(true);
                    } else {
                        showOverview(true);
                    }
                } else {
                    if (((ComicFragment) fragmentManager.findFragmentByTag(BROWSER_TAG)).transition != null)
                        ((ComicFragment) fragmentManager.findFragmentByTag(BROWSER_TAG)).transition.exit(MainActivity.this); //return to the SearchResultsActivity
                    else
                        super.onBackPressed();
                }
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
            if (fullOffline || (prefHelper.isWifi(this) || prefHelper.mobileEnabled()))
                fragment.updatePager(); //Update the pager in case a new comic has ben posted while the app was still active in the background

        if (fromSearch)
            fromSearch = false;
        super.onRestart();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            switch (resultCode) {
                case RESULT_OK: //restart the activity when something major was changed in the settings
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
        } else if (requestCode == 2 && resultCode == FilePickerActivity.RESULT_OK) {
            ((FavoritesFragment) getSupportFragmentManager().findFragmentByTag(FAV_TAG)).importFavorites(data);
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

    // Getters/Setters

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

    public void setCurrentFragment(int id) {currentFragment = id;}

    public ProgressDialog getProgressDialog() {
        return progress;
    }

    public PrefHelper getPrefHelper() {
        return prefHelper;
    }

    public ThemePrefs getThemePrefs() {
        return themePrefs;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public void setProgressDialog(String message, boolean cancel) {
        progress = ProgressDialog.show(this, "", message, cancel);
    }

    private ActionBarDrawerToggle setupDrawerToggle() {
        return new ActionBarDrawerToggle(this, mDrawer, toolbar, R.string.drawer_open, R.string.drawer_close);
    }

}

