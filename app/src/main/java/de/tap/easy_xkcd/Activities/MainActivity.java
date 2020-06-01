/*
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

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.transition.Explode;
import android.transition.Fade;
import android.transition.Slide;
import android.transition.Transition;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.nbsp.materialfilepicker.ui.FilePickerActivity;
import com.tap.xkcd_reader.R;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnLongClick;
import de.tap.easy_xkcd.CustomTabHelpers.CustomTabActivityHelper;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.database.updateComicDatabase;
import de.tap.easy_xkcd.fragments.comics.ComicBrowserFragment;
import de.tap.easy_xkcd.fragments.comics.ComicFragment;
import de.tap.easy_xkcd.fragments.comics.FavoritesFragment;
import de.tap.easy_xkcd.fragments.comics.OfflineFragment;
import de.tap.easy_xkcd.fragments.overview.OverviewBaseFragment;
import de.tap.easy_xkcd.fragments.whatIf.WhatIfFragment;
import de.tap.easy_xkcd.fragments.whatIf.WhatIfOverviewFragment;
import de.tap.easy_xkcd.notifications.ComicNotifierJob;
import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.utils.ThemePrefs;
import timber.log.Timber;


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

    public int lastComicNumber = 0;

    private static final int UPDATE_JOB_ID = 1;

    public ActionBarDrawerToggle drawerToggle;
    private MenuItem searchMenuItem;
    private CustomTabActivityHelper customTabActivityHelper;
    private ProgressDialog progress;
    private DatabaseManager databaseManager;

    private static final String COMIC_NOTIFICATION_INTENT = "de.tap.easy_xkcd.ACTION_COMIC_NOTIFICATION";
    private static final String COMIC_INTENT = "de.tap.easy_xkcd.ACTION_COMIC";
    private static final String WHATIF_INTENT = "de.tap.easy_xkcd.ACTION_WHAT_IF";
    private static final String FAVORITE_INTENT = "de.tap.easy_xkcd.ACTION_FAVORITE";
    private static final String SAVED_INSTANCE_CURRENT_FRAGMENT = "CurrentFragment";

    public static final int UPDATE_ALARM = 2;

    public static final String FRAGMENT_TAG = "MainActivityFragments";

    public enum CurrentFragment {Browser, Favorites, Overview, WhatIf}

    private CurrentFragment currentFragment = null;

    private boolean updateTaskRunning = false;

    private boolean fullscreenEnabled = false;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        PreferenceManager.setDefaultValues(this, R.xml.pref_alt_sharing, false);

        customTabActivityHelper = new CustomTabActivityHelper();
        databaseManager = new DatabaseManager(this);

        if (savedInstanceState == null) {
            if (fromSearch) {
                postponeEnterTransition();
                Timber.d("posponing transition...");
            }
            Timber.d("savedInstanceState is null.");
        }

        if (currentFragment == null) {
            if (savedInstanceState != null) {
                currentFragment = (CurrentFragment) savedInstanceState.getSerializable(SAVED_INSTANCE_CURRENT_FRAGMENT);
            } else {
                currentFragment = CurrentFragment.Browser; //Open Browser by default
            }
        }

        fullOffline = prefHelper.fullOfflineEnabled();
        fullOfflineWhatIf = prefHelper.fullOfflineWhatIf();
        boolean whatIfIntent = false;

        //Check for intents
        try {
            switch (Objects.requireNonNull(getIntent().getAction())) {
                case Intent.ACTION_VIEW:
                    if (Objects.requireNonNull(getIntent().getDataString()).contains("what")) {
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
                case COMIC_NOTIFICATION_INTENT:
                    prefHelper.setLastComic(0); // In updateComicDatabase this will lead to prefHelper.setLastComic(newest)
                    Timber.d("started from Comic Notification Intent");
                    break;
                case WHATIF_INTENT:
                    WhatIfActivity.WhatIfIndex = getIntent().getIntExtra("number", 0);
                    prefHelper.setLastWhatIf(WhatIfActivity.WhatIfIndex);
                    whatIfIntent = true;
                    WhatIfFragment.newIntent = true;
                    break;
                case FAVORITE_INTENT:
                    if (databaseManager.noFavorites()) {
                        showDrawerErrorToast(R.string.no_favorites);
                    } else {
                        showFavoritesFragment(false);
                    }
                    break;
            }
        } catch (NullPointerException e) {
            Timber.e("Null Pointer exception when checking intent: %s", e.getMessage());
        }

        setupToolbar(toolbar);
        if (savedInstanceState == null && !SearchResultsActivity.isOpen)
            toolbar.setAlpha(0);

        //Align FAB left if the user enabled this
        if (prefHelper.fabLeft()) {
            CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) mFab.getLayoutParams();
            params.gravity = Gravity.BOTTOM | Gravity.LEFT;
            mFab.setLayoutParams(params);
        }

        mDrawer.addDrawerListener(drawerToggle);
        mDrawer.setStatusBarBackgroundColor(themePrefs.getPrimaryDarkColor());
        drawerToggle = setupDrawerToggle();
        if (themePrefs.amoledThemeEnabled()) {
            mNavView.setBackgroundColor(Color.BLACK);
        } else if (themePrefs.nightThemeEnabled()) {
            mNavView.setBackgroundColor(ContextCompat.getColor(this, R.color.background_material_dark));
            toolbar.setPopupTheme(R.style.ThemeOverlay_AppCompat);
        }
        setupDrawerContent(mNavView);

        if(!prefHelper.navDrawerSwipe()) {
            mDrawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            toolbar.setNavigationOnClickListener(v -> {
                if(v.getId() == -1)
                    mDrawer.openDrawer(mNavView, true);
                Log.d("test", String.valueOf(v.getId()));
            });
        }

        if (savedInstanceState == null) {
            new Handler().postDelayed(() -> {
                prefHelper.showRateSnackbar(MainActivity.this.getPackageName(), MainActivity.this, mFab);
                prefHelper.showSurveySnackbar(MainActivity.this, mFab);
                //prefHelper.showFeatureSnackbar(MainActivity.this, mFab);
            }, 1500);
        }

        // Show/hide toolbar when fullscreen is exited/entered
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(visibility -> {
            fullscreenEnabled = (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
            new Handler().postDelayed(() -> {
                toolbar.setTranslationY(fullscreenEnabled ? 0f : - toolbar.getHeight());
                toolbar.animate().translationY(fullscreenEnabled ? - toolbar.getHeight() : 0f);
                toolbar.setVisibility(fullscreenEnabled ? View.GONE : View.VISIBLE);
            }, 180);
        });

        if (prefHelper.showUpdateMessage()) {
            new AlertDialog.Builder(this).setMessage(R.string.update_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.got_it, (dialogInterface, i) -> { }).show();
        }

        //Load fragment
        if (fullOffline || prefHelper.isOnline(this) || fullOfflineWhatIf) { //Do we have internet or are in offline mode?
            updateComicsTask task = new updateComicsTask(prefHelper, this, savedInstanceState, whatIfIntent, savedInstanceState == null, false);
            if (savedInstanceState == null) {
                task.execute();
            } else {
                task.onPostExecute(null);
            }
        } else if ((currentFragment != CurrentFragment.Favorites)) { //Don't show the dialog if the user is currently browsing his favorites or full offline is enabled
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setMessage(R.string.no_connection)
                    .setPositiveButton(R.string.no_connection_retry, (dialog1, which) -> {
                        finish();
                        startActivity(getIntent());
                    })
                    .setCancelable(false);
            if (!databaseManager.noFavorites()) {
                //We have favorites, so let give the user the option to view them
                dialog.setNegativeButton(R.string.no_connection_favorites, (dialog12, which) -> {
                    MenuItem m = mNavView.getMenu().findItem(R.id.nav_favorites);
                    selectDrawerItem(m, false, false, true, savedInstanceState == null);
                });
            }
            dialog.show();
        }
    }

    public void toggleFullscreen() {
        mDrawer.setFitsSystemWindows(fullscreenEnabled);
        if (!fullscreenEnabled) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            // | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            // | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            // Hide the nav bar and status bar
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        } else {
            getWindow().getDecorView().setSystemUiVisibility(defaultVisibility);
        }
        fullscreenEnabled = !fullscreenEnabled;
    }

    private class updateComicsTask extends updateComicDatabase {
        private Bundle savedInstanceState;
        private boolean whatIfIntent;
        private boolean fromOnRestart;

        @Override
        protected void onPreExecute() {
            updateTaskRunning = true;
            super.onPreExecute();
        }

        //TODO Splashscreen?
        public updateComicsTask(PrefHelper prefHelper, Context context, Bundle savedInstanceState, boolean whatIfIntent, boolean showProgress, boolean fromOnRestart) {
            super(prefHelper, context);
            lockRotation();
            this.savedInstanceState = savedInstanceState;
            this.whatIfIntent = whatIfIntent;
            this.showProgress = showProgress;
            this.fromOnRestart = fromOnRestart;
        }

        @Override
        public void onPostExecute(Void dummy) {
            super.onPostExecute(dummy);
            if (!fromOnRestart && savedInstanceState == null && prefHelper.launchToOverview()) {
                currentFragment = CurrentFragment.Overview;
                showOverview(false);
            } else if (getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG) == null || newComicFound) {
                Timber.d("Creating a new Fragment...");
                switch (currentFragment) {
                    case Browser:
                        showBrowserFragment(false);
                        break;
                    case Favorites:
                        showFavoritesFragment(false);
                        break;
                    case Overview:
                        showOverview(false);
                        break;
                }
            }
            updateToolbarTitle();
            unlockRotation();
            //Setup the notifications in case the device was restarted
            Timber.d("interval: %d", prefHelper.getNotificationInterval());
            if (!fromOnRestart && savedInstanceState == null && prefHelper.getNotificationInterval() != 0) {
                JobScheduler jobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
                jobScheduler.schedule(new JobInfo.Builder(UPDATE_JOB_ID, new ComponentName(MainActivity.this, ComicNotifierJob.class))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPeriodic(prefHelper.getNotificationInterval())
                        .setPersisted(true)
                        .build()
                );
                Timber.d("job scheduled...");
            }
            updateTaskRunning = false;
        }
    }

    public void updateToolbarTitle() {
        Timber.d("Current fragment: %s", String.valueOf(currentFragment));
        if (getSupportActionBar() == null) {
            return;
        }
        switch (currentFragment) {
            case Browser:
                getSupportActionBar().setTitle(getResources().getString(R.string.comicbrowser_title));
                break;
            case Favorites:
                getSupportActionBar().setTitle(getResources().getString(R.string.nv_favorites));
                break;
            case Overview:
                if (prefHelper.overviewFav()) {
                    getSupportActionBar().setTitle(getResources().getString(R.string.nv_favorites));
                } else {
                    getSupportActionBar().setTitle(getResources().getString(R.string.comicbrowser_title));
                }
                break;
            case WhatIf:
                getSupportActionBar().setTitle(getResources().getString(R.string.nv_whatif));
                break;
        }
    }

    @SuppressWarnings("unused") // it's actually used, just injected by Butter Knife
    @OnClick(R.id.fab)
    void onClick() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
        if (fragment instanceof OverviewBaseFragment) {
            ((OverviewBaseFragment) fragment).showRandomComic();
        } else if (fragment instanceof ComicFragment) {
            if (prefHelper.showRandomTip()) {
                Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), R.string.random_tip, BaseTransientBottomBar.LENGTH_LONG);
                snackbar.setAction(R.string.got_it, view -> prefHelper.setShowRandomTip(false));
                snackbar.show();
            }
            ((ComicFragment) fragment).getRandomComic();
        }
    }

    @SuppressWarnings("unused")
    @OnLongClick(R.id.fab)
    boolean onLongClick() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
        if (currentFragment == CurrentFragment.Browser && fragment instanceof ComicFragment) {
            ((ComicFragment) fragment).getPreviousRandom();
        }
        return true;
    }

    void closeDrawer() {
        new Handler().postDelayed(() -> mDrawer.closeDrawers(), 1);
    }

    /**
     * Adds the listener for the navigationView and adjusts the colors according to our theme
     */
    private void setupDrawerContent(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(
                menuItem -> {
                    closeDrawer();
                    //mDrawer.closeDrawers();
                    prepareToolbarAnimation(-300);
                    //updateToolbarTitle();
                    animateToolbar(-300);
                    selectDrawerItem(menuItem, false, false, false, true);
                    return false;
                });
        themePrefs.setupNavdrawerColor(navigationView);
    }



    public void showOverview(boolean animate) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle("");
        }

        prefHelper.setOverviewFav(currentFragment == CurrentFragment.Favorites);

        Timber.d("last comic: %d", lastComicNumber);
        OverviewBaseFragment overviewBaseFragment = OverviewBaseFragment.getOverviewFragment(prefHelper, lastComicNumber != 0 ? lastComicNumber : prefHelper.getLastComic());

        Fragment oldFragment = fragmentManager.findFragmentByTag(FRAGMENT_TAG);
        final TextView title;
        final PhotoView image;
        if (currentFragment == CurrentFragment.Browser || currentFragment == CurrentFragment.Favorites) {
            title = ((ComicFragment) oldFragment).getCurrentTitleTextView();
            image = ((ComicFragment) oldFragment).getCurrentPhotoView();
            Timber.d("Current title: %s name %s", title.getText(), title.getTransitionName());

            /*oldFragment.setExitSharedElementCallback(new SharedElementCallback() {
                @Override
                public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
                    Timber.d("shared element exit");
                    sharedElements.put(names.get(0), image);
                    sharedElements.put(names.get(1), title);
                }
            });*/
            transaction.setReorderingAllowed(true);
            transaction.addSharedElement(title, title.getTransitionName());
            transaction.addSharedElement(image, image.getTransitionName());

            //overviewBaseFragment.setEnterTransition(new Slide(Gravity.LEFT));
            Timber.d("added shared element");
            //oldFragment.setSharedElementEnterTransition(R.transition.image_shared_element_transition);
        } else if (animate) {
            if (oldFragment != null) {
                Explode explode = new Explode();
                explode.setInterpolator(new AccelerateInterpolator(2.0f));
                oldFragment.setExitTransition(explode);
            }
            Transition enterTransition;
            if (currentFragment == CurrentFragment.Overview) { //If we are just changing to a different overview style, let the new fragment fade in
                enterTransition = new Fade();
            } else { //If we come from Browser or Favorites, the overview slides in from the left
                enterTransition = new Slide(Gravity.LEFT);
                enterTransition.setInterpolator(new OvershootInterpolator(1.5f));
            }
            overviewBaseFragment.setEnterTransition(enterTransition);
            overviewBaseFragment.setAllowEnterTransitionOverlap(false);
        }
        transaction.replace(R.id.flContent, overviewBaseFragment, FRAGMENT_TAG)
                .commitNowAllowingStateLoss();

        currentFragment = CurrentFragment.Overview;
    }


    void showFavoritesFragment(boolean animate) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        //mFab.setVisibility(prefHelper.fabDisabledFavorites() ? View.GONE : View.VISIBLE);

        FavoritesFragment favoritesFragment = new FavoritesFragment();
        final FragmentTransaction transaction = fragmentManager.beginTransaction();
        if (animate) {
            Fragment oldFragment = fragmentManager.findFragmentByTag(FRAGMENT_TAG);
            if (oldFragment != null) {
                Slide slideOut = new Slide(currentFragment == CurrentFragment.Browser ? Gravity.TOP : Gravity.BOTTOM);
                slideOut.setInterpolator(new AccelerateInterpolator(2.0f));
                oldFragment.setExitTransition(slideOut);
            }
            Slide slideIn = new Slide(currentFragment == CurrentFragment.Browser ? Gravity.BOTTOM : Gravity.TOP);
            slideIn.setInterpolator(new OvershootInterpolator(1.5f));
            favoritesFragment.setEnterTransition(slideIn);
            favoritesFragment.setAllowEnterTransitionOverlap(false);
        }
        transaction
                .replace(R.id.flContent, favoritesFragment, FRAGMENT_TAG);
        currentFragment = CurrentFragment.Favorites;
        transaction.commitNowAllowingStateLoss();
    }

    void showWhatifFragment(boolean animate) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        //mFab.setVisibility(View.GONE);
        mFab.hide(); //WhatIf Fragment has its own FAB

        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle("");
        }

        WhatIfOverviewFragment whatIfFragment = new WhatIfOverviewFragment();
        final FragmentTransaction transaction = fragmentManager.beginTransaction();
        Fragment oldFragment = fragmentManager.findFragmentByTag(FRAGMENT_TAG);
        if (oldFragment != null) {
            Slide slideOut = new Slide(Gravity.TOP);
            slideOut.setInterpolator(new AccelerateInterpolator(2.0f));
            oldFragment.setExitTransition(slideOut);
        }

        Slide slideIn = new Slide(Gravity.BOTTOM);
        slideIn.setInterpolator(new OvershootInterpolator(1.5f));
        //For some reason we have to add an empty listener here, so that the listener in WhatIfOverviewFragment works ¯\_(ツ)_/¯
        slideIn.addListener(new Transition.TransitionListener() {
            @Override
            public void onTransitionStart(@NonNull Transition transition) {

            }

            @Override
            public void onTransitionEnd(@NonNull Transition transition) {

            }

            @Override
            public void onTransitionCancel(@NonNull Transition transition) {

            }

            @Override
            public void onTransitionPause(@NonNull Transition transition) {

            }

            @Override
            public void onTransitionResume(@NonNull Transition transition) {

            }
        });
        whatIfFragment.setEnterTransition(slideIn);
        whatIfFragment.setAllowEnterTransitionOverlap(false);
        transaction.replace(R.id.flContent, whatIfFragment, FRAGMENT_TAG);
        currentFragment = CurrentFragment.WhatIf;
        transaction.commitNowAllowingStateLoss();
    }

    void showBrowserFragment(boolean animate) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (prefHelper.fabDisabledComicBrowser()) mFab.hide(); else mFab.show();

        ComicFragment comicFragment = fullOffline ? new OfflineFragment() : new ComicBrowserFragment();
        final FragmentTransaction transaction = fragmentManager.beginTransaction();
        if (animate) {
            Fragment oldFragment = fragmentManager.findFragmentByTag(FRAGMENT_TAG);
            if (oldFragment != null) {
                Slide slideOut = new Slide(Gravity.BOTTOM);
                slideOut.setInterpolator(new AccelerateInterpolator(2.0f));
                oldFragment.setExitTransition(slideOut);
            }

            Slide slideIn = new Slide(Gravity.TOP);
            slideIn.setInterpolator(new OvershootInterpolator(1.5f));
            comicFragment.setEnterTransition(slideIn);
            comicFragment.setAllowEnterTransitionOverlap(false);
        }
        transaction.replace(R.id.flContent, comicFragment, FRAGMENT_TAG);
        currentFragment = CurrentFragment.Browser;
        transaction.commitNowAllowingStateLoss();
    }

    /**
     * Selects a item from the navigation Drawer
     *
     * @param menuItem     the pressed menu item
     * @param showOverview should be true when the user selected "Launch to Overview Mode" in the settings
     * @param animateOverview  should be false when the device was rotated and the app showed overview mode before the rotation
     * @param shouldAnimateToolbar wether to animate the toolbar, should be false when coming from onRestart()
     */
    public void selectDrawerItem(final MenuItem menuItem, final boolean showOverview, final boolean animateOverview, final boolean shouldAnimateToolbar, final boolean animateTransition) {
        switch (menuItem.getItemId()) {
            case R.id.nav_browser:
                if (!prefHelper.isOnline(this) && !fullOffline) {
                    showDrawerErrorToast(R.string.no_connection); //No connection, so show Error toast and return
                    break;
                }
                if (shouldAnimateToolbar) animateToolbar(-300);
                showBrowserFragment(animateTransition);
                break;
            case R.id.nav_favorites:
                if (databaseManager.noFavorites()) {
                    showDrawerErrorToast(R.string.no_favorites); //No favorites, so show Error Toast and return
                    break;
                }
                if (shouldAnimateToolbar) animateToolbar(300);
                showFavoritesFragment(animateTransition);
                break;
            case R.id.nav_whatif:
                if (!prefHelper.isOnline(this) && !fullOfflineWhatIf) {
                    showDrawerErrorToast(R.string.no_connection); //No connection, so show Error toast and return
                    break;
                }
                if (shouldAnimateToolbar) animateToolbar(300);
                showWhatifFragment(animateTransition);
                break;

            case R.id.nav_settings:
                new Handler().postDelayed(() -> {
                    mDrawer.closeDrawers();
                    startActivityForResult(new Intent(MainActivity.this, SettingsActivity.class), 1);
                }, 1);
                return;

            case R.id.nav_feedback:
                new Handler().postDelayed(() -> {
                    mDrawer.closeDrawers();
                    Intent i = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "easyxkcd@gmail.com", null));
                    startActivity(Intent.createChooser(i, getResources().getString(R.string.nav_feedback_send)));
                }, 1);
                return;

            case R.id.nav_about:
                closeDrawer();
                startActivity(new Intent(MainActivity.this, AboutActivity.class));
                return;
        }
        mNavView.setCheckedItem(currentFragmentToNavId());
        updateToolbarTitle();
        updateToolbarElevation();
        invalidateOptionsMenu();
    }

    void updateToolbarElevation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { //Setup the toolbar elevation for WhatIf overview
            if (currentFragment == CurrentFragment.WhatIf)
                toolbar.setElevation(0);
            else {
                Resources r = getResources();
                float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, r.getDisplayMetrics());
                toolbar.setElevation(px);
            }
        }
    }

    /**
     * Shows an error toast and resets the navigationDrawer to the previous item
     *
     * @param errorId the string resource to be shown
     */
    private void showDrawerErrorToast(int errorId) {
        Toast.makeText(this, errorId, Toast.LENGTH_SHORT).show();
        /*MenuItem m = mNavView.getMenu().findItem(itemId);
        m.setChecked(true);*/ //TODO figure out here how to leave the last item checked
        closeDrawer();
    }

    private void prepareToolbarAnimation(int translation) {
        //View view;
        for (int i = 2; i < toolbar.getChildCount(); i++) {
            View view = toolbar.getChildAt(i);
            view.setTranslationY(-300);
        }
        toolbar.getChildAt(0).setAlpha(0);
    }

    /**
     * Animates the toolbar and its childs
     *
     * @param translation The initial vertical translation of the menu items
     */
    private void animateToolbar(int translation) {
        for (int i = 2; i < toolbar.getChildCount(); i++) {
            View view = toolbar.getChildAt(i);
            //view.setTranslationY(translation);
            view.animate().setStartDelay(50 * (i - 2)).setDuration(150 * (i + 1)).translationY(0);
        }
        //toolbar.getChildAt(0).setAlpha(0);
        toolbar.getChildAt(0).animate().alpha(1).setDuration(200).setInterpolator(new AccelerateInterpolator());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Timber.d("received new intent %s", intent.getAction());
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        switch (intent.getAction()) {
            case Intent.ACTION_VIEW:
                if (intent.getDataString().contains("what")) {
                    MenuItem item = mNavView.getMenu().findItem(R.id.nav_whatif);
                    selectDrawerItem(item, false, false, false, false);
                    WhatIfActivity.WhatIfIndex = getNumberFromUrl(intent.getDataString(), 1);
                    Intent whatIf = new Intent(MainActivity.this, WhatIfActivity.class);
                    prefHelper.setLastWhatIf(WhatIfActivity.WhatIfIndex);
                    startActivity(whatIf);
                } else {
                    MenuItem item = mNavView.getMenu().findItem(R.id.nav_browser);
                    selectDrawerItem(item, false, false, true, false);
                    ComicFragment comicFragment = (ComicFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
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
                selectDrawerItem(item, false, false, true, false);
                ComicFragment comicFragment = (ComicFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
                comicFragment.lastComicNumber = intent.getIntExtra("number", 1);
                comicFragment.scrollTo(comicFragment.lastComicNumber - 1, false);
                break;
            case COMIC_NOTIFICATION_INTENT:
                databaseManager.setHighestInDatabase(databaseManager.getHighestInDatabase() - 3);
                finish();
                startActivity(getIntent());
                Timber.d("Notification intent while activity was running");
                break;
            case WHATIF_INTENT:
                item = mNavView.getMenu().findItem(R.id.nav_whatif);
                selectDrawerItem(item, false, false, false, false);
                WhatIfActivity.WhatIfIndex = intent.getIntExtra("number", 1);
                Intent whatIf = new Intent(MainActivity.this, WhatIfActivity.class);
                prefHelper.setLastWhatIf(WhatIfActivity.WhatIfIndex);
                startActivity(whatIf);
                break;
            case FAVORITE_INTENT:
                if (databaseManager.noFavorites()) {
                    showDrawerErrorToast(R.string.no_favorites);
                } else {
                    showFavoritesFragment(false);
                }
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
        //searchView.setIconified(false); // Workaround for the keyboard to appear when the search item is pressed, see https://stackoverflow.com/a/47287337

        searchMenuItem = menu.findItem(R.id.action_search);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                getSearchMenuItem().collapseActionView();
                searchView.setQuery("", false);
                hideKeyboard();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });


        searchMenuItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                //Hide the other menu items to the right
                for (int i = 0; i < menu.size(); ++i) {
                    menu.getItem(i).setVisible(menu.getItem(i) == searchMenuItem);
                }
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                invalidateOptionsMenu(); // Brings back the hidden menu items in onMenuItemActionExpand()

                hideKeyboard();
                return true;
            }
        });

        menu.findItem(R.id.action_donate).setVisible(!prefHelper.hideDonate());
        menu.findItem(R.id.action_night_mode).setChecked(themePrefs.nightEnabledThemeIgnoreAutoNight());
        menu.findItem(R.id.action_night_mode).setVisible(!themePrefs.autoNightEnabled() && !themePrefs.useSystemNightTheme());
        return true;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
                imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
        }
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
        if (currentFragment == CurrentFragment.Browser || currentFragment == CurrentFragment.Favorites) {
            prefHelper.setLastComic(lastComicNumber);
        }

        Intent intent = getIntent();
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        overridePendingTransition(0, 0);
        finish();
        overridePendingTransition(0, 0);
        startActivity(intent);
        return true;
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        //Save the current fragment
        savedInstanceState.putSerializable(SAVED_INSTANCE_CURRENT_FRAGMENT, currentFragment);
        Timber.d("instanceState saved");
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
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (getSearchMenuItem().isActionViewExpanded()) {
            getSearchMenuItem().collapseActionView();
        } else if (mDrawer != null && mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.closeDrawer(GravityCompat.START);
        } else if (currentFragment == CurrentFragment.Browser || currentFragment == CurrentFragment.Favorites) {
            boolean zoomReset;
            zoomReset = ((ComicFragment) fragmentManager.findFragmentByTag(FRAGMENT_TAG)).zoomReset(); //Reset the zoom level of the current image
            if (!zoomReset) {
                if (!SearchResultsActivity.isOpen && !getIntent().getAction().equals(Intent.ACTION_VIEW)) {
                    prefHelper.setOverviewFav(currentFragment == CurrentFragment.Favorites);
                    if (currentFragment == CurrentFragment.Favorites && (prefHelper.isOnline(this) || prefHelper.fullOfflineEnabled())) {
                        showOverview(true);
                        //currentFragment = CurrentFragment.Browser;
                        getSupportActionBar().setTitle("Comics");
                        mNavView.getMenu().findItem(R.id.nav_browser).setChecked(true);
                    } else {
                        showOverview(true);
                    }
                } else {
                    super.onBackPressed();
                }
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

    public int currentFragmentToNavId() {
        switch (currentFragment) {
            case Browser:
                return R.id.nav_browser;
            case Favorites:
                return R.id.nav_favorites;
            case Overview:
                return prefHelper.overviewFav() ? R.id.nav_favorites : R.id.nav_browser;
            case WhatIf:
                return R.id.nav_whatif;
            default:
                return R.id.nav_browser;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Timber.d("onResume called");
        mNavView.setCheckedItem(currentFragmentToNavId());
    }

    @Override
    protected void onRestart() {
        Timber.d("onRestart called");
        if (!updateTaskRunning) {
            new updateComicsTask(prefHelper, this, null, false, false, true).execute();
        } else {
            Timber.d("update Task is already running!");
        }
        if (fromSearch)
            fromSearch = false;
        super.onRestart();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Timber.d( "received result" +  resultCode + "from request" + requestCode);
        if (requestCode == 1) {
            switch (resultCode) {
                case RESULT_OK: //restart the activity when something major was changed in the settings
                    updateTaskRunning = true; // Prevents creation of a new updateTask in onRestart()
                    finish();
                    startActivity(getIntent());
                    break;
                case UPDATE_ALARM:
                    JobScheduler jobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
                    if (prefHelper.getNotificationInterval() != 0) {
                        jobScheduler.cancel(UPDATE_JOB_ID);
                        jobScheduler.schedule(new JobInfo.Builder(UPDATE_JOB_ID, new ComponentName(this, ComicNotifierJob.class))
                                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                                .setPeriodic(prefHelper.getNotificationInterval())
                                .setPersisted(true)
                                .build()
                        );
                        Timber.d("Job rescheduled!");
                    } else {
                        jobScheduler.cancel(UPDATE_JOB_ID);
                        Timber.d("Job canceled!");
                    }
                    break;
            }
        } else if (requestCode == 2 && resultCode == FilePickerActivity.RESULT_OK) {
            ((FavoritesFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG)).importFavorites(data); //The import can only be started when FavoritesFragment is visible, so this cast should never fail
        } else if (requestCode == 3 && resultCode == Activity.RESULT_OK) {
            finish();
            startActivity(getIntent());
            //TODO select drawer item here, do this after merge
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    if (currentFragment == CurrentFragment.Favorites) {
                        ((FavoritesFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG)).shareComic(true);
                    } else {
                        ((ComicBrowserFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG)).shareComicImage();
                    }
                break;
            case 2:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //((ComicBrowserFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG)).new SaveComicImageTask(lastComicNumber).execute(true);
                    ((ComicBrowserFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG)).addFavorite(lastComicNumber, true);
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

    public CurrentFragment getCurrentFragment() {
        return currentFragment;
    }

    public void setCurrentFragment(CurrentFragment currentFragment) {this.currentFragment = currentFragment;}

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

    public void setNavSwipe() {

    }

}

