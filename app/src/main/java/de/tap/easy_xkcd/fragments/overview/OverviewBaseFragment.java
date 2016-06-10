/**
 * *******************************************************************************
 * Copyright 2016 Tom Praschan
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

package de.tap.easy_xkcd.fragments.overview;

import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.database.RealmComic;
import de.tap.easy_xkcd.fragments.comics.ComicFragment;
import de.tap.easy_xkcd.fragments.comics.FavoritesFragment;
import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.utils.ThemePrefs;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

public abstract class OverviewBaseFragment extends android.support.v4.app.Fragment {
    protected RealmResults<RealmComic> comics;
    protected PrefHelper prefHelper;
    protected ThemePrefs themePrefs;
    protected DatabaseManager databaseManager;
    public static int bookmark;
    protected static final String BROWSER_TAG = "browser";
    protected static final String OVERVIEW_TAG = "overview";
    private static final String FAV_TAG = "favorites";

    protected void setupVariables() {
        prefHelper = ((MainActivity) getActivity()).getPrefHelper();
        themePrefs = ((MainActivity) getActivity()).getThemePrefs();
        databaseManager = ((MainActivity) getActivity()).getDatabaseManager();
        bookmark = prefHelper.getBookmark();
        setHasOptionsMenu(true);
    }

    protected void updateBookmark(int i) {
        bookmark = comics.get(i).getComicNumber();
        prefHelper.setBookmark(bookmark);
    }

    public void showComic(final int pos) {
        goToComic(comics.get(pos).getComicNumber() - 1);
    }

    public void showRandomComic(final int number) {
        goToComic(number - 1);
    }

    public void goToComic(final int number) {
        android.support.v4.app.FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
        ComicFragment fragment;
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        int subtitle;
        if (!prefHelper.overviewFav()) {
            FavoritesFragment favorites = (FavoritesFragment) fragmentManager.findFragmentByTag(FAV_TAG);
            if (favorites != null)
                transaction.hide(favorites);
            fragment = (ComicFragment) fragmentManager.findFragmentByTag(BROWSER_TAG);
            fragment.scrollTo(number, false);
            transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                    .hide(fragmentManager.findFragmentByTag(OVERVIEW_TAG))
                    .show(fragment)
                    .commit();
            subtitle = fragment.lastComicNumber;
        } else {
            transaction
                    .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                    .hide(fragmentManager.findFragmentByTag(BROWSER_TAG))
                    .hide(fragmentManager.findFragmentByTag(OVERVIEW_TAG));
            fragment = (FavoritesFragment) fragmentManager.findFragmentByTag(FAV_TAG);
            int index = Arrays.binarySearch(databaseManager.getFavComics(), number + 1);
            if (fragment == null || index < 0) {
                if (index < 0) { // If the comic for some reason is in realm, but not in shared prefs, add it now
                    index = -index - 1;
                    databaseManager.setFavorite(number + 1, true);
                }
                fragment = new FavoritesFragment();
                transaction.add(R.id.flContent, fragment, FAV_TAG);
            } else {
                transaction.show(fragment);
                fragment.scrollTo(index, false);
            }
            fragment.favoriteIndex = index;
            subtitle = databaseManager.getFavComics()[fragment.favoriteIndex];
            transaction.commit();

            ((MainActivity) getActivity()).setCurrentFragment(R.id.nav_favorites);
            ((MainActivity) getActivity()).getSupportActionBar().setTitle(getActivity().getResources().getString(R.string.nv_favorites));
            ((MainActivity) getActivity()).getNavView().getMenu().findItem(R.id.nav_favorites).setChecked(true);
        }


        if (prefHelper.subtitleEnabled())
            ((MainActivity) getActivity()).getToolbar().setSubtitle(String.valueOf(subtitle));
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_overview_fragment, menu);
        menu.findItem(R.id.action_boomark).setVisible(prefHelper.getBookmark() != 0).setTitle(R.string.open_bookmark);
        menu.findItem(R.id.action_hide_read).setChecked(prefHelper.hideRead());
        //menu.findItem(R.id.action_search).setVisible(false);
        menu.findItem(R.id.action_boomark).setVisible(bookmark != 0);

        MenuItem fav = menu.findItem(R.id.action_favorite);
        if (!prefHelper.overviewFav()) {
            fav.setIcon(R.drawable.ic_favorite_outline);
            fav.setTitle(R.string.nv_favorites);
        } else {
            fav.setIcon(R.drawable.ic_action_favorite);
            fav.setTitle(R.string.action_overview);
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_overview_style:
                android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity());
                mDialog.setTitle(R.string.overview_style_title)
                        .setSingleChoiceItems(R.array.overview_style, prefHelper.getOverviewStyle(), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                prefHelper.setOverviewStyle(i);
                                dialogInterface.dismiss();
                                FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
                                transaction.detach(getActivity().getSupportFragmentManager().findFragmentByTag(OVERVIEW_TAG));
                                switch (i) {
                                    case 0:
                                        transaction.add(R.id.flContent, new OverviewListFragment(), OVERVIEW_TAG);
                                        break;
                                    case 1:
                                        transaction.add(R.id.flContent, new OverviewCardsFragment(), OVERVIEW_TAG);
                                        break;
                                    case 2:
                                        transaction.add(R.id.flContent, new OverviewStaggeredGridFragment(), OVERVIEW_TAG);
                                }
                                transaction.commit();
                            }
                        }).show();
                break;
            case R.id.action_earliest_unread:
                if (prefHelper.hideRead()) {
                    int n = comics.size() - 1;
                    if (n > 0)
                        showComic(n);
                } else {
                    RealmComic comic = comics.where().equalTo("isRead", false).findAllSorted("comicNumber", Sort.ASCENDING).first();
                    if (comic != null)
                        goToComic(comic.getComicNumber() - 1);
                }
        }
        return super.onOptionsItemSelected(item);
    }

    public abstract void notifyAdapter(int number);

    protected void setupAdapter() {
        Realm realm = databaseManager.realm;
        if (prefHelper.overviewFav()) {
            comics = realm.where(RealmComic.class).equalTo("isFavorite", true).findAll();
        } else if (prefHelper.hideRead()) {
            comics = realm.where(RealmComic.class).equalTo("isRead", false).findAll();
        } else {
            comics = realm.where(RealmComic.class).findAll();
        }
        comics.sort("comicNumber", Sort.DESCENDING);
    }

    protected void animateToolbar() {
        Toolbar toolbar = ((MainActivity) getActivity()).getToolbar();
        if (toolbar.getAlpha() == 0) {
            toolbar.setTranslationY(-300);
            toolbar.animate().setDuration(300).translationY(0).alpha(1);
            View view;
            for (int i = 0; i < toolbar.getChildCount(); i++) {
                view = toolbar.getChildAt(i);
                view.setTranslationY(-300);
                view.animate().setStartDelay(50 * (i + 1)).setDuration(70 * (i + 1)).translationY(0);
            }
        }
    }

    public boolean overViewFav() {
        return comics == null || comics.size() != prefHelper.getNewest();
    }

    public void updateDatabasePostExecute() {
        animateToolbar();
    }

}
