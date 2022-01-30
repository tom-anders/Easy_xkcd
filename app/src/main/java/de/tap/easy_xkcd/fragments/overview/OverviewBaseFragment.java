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
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.widget.Toolbar;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.request.RequestOptions;
import com.tap.xkcd_reader.R;

import java.util.Random;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.GlideApp;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.database.RealmComic;
import de.tap.easy_xkcd.fragments.comics.ComicBrowserFragment;
import de.tap.easy_xkcd.fragments.comics.ComicFragment;
import de.tap.easy_xkcd.fragments.comics.FavoritesFragment;
import de.tap.easy_xkcd.fragments.comics.OfflineFragment;
import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.utils.ThemePrefs;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;
import timber.log.Timber;

public abstract class OverviewBaseFragment extends Fragment {
    protected RealmResults<RealmComic> comics;
    protected PrefHelper prefHelper;
    protected ThemePrefs themePrefs;
    protected DatabaseManager databaseManager;
    public static int bookmark;
    /*protected static final String BROWSER_TAG = "browser";
    protected static final String OVERVIEW_TAG = "overview";
    private static final String FAV_TAG = "favorites";*/

    protected static final String LAST_COMIC = "lastcomic";

    protected int lastComicNumber = 1;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            lastComicNumber = savedInstanceState.getInt(LAST_COMIC);
        } else {
            lastComicNumber = getArguments().getInt(LAST_COMIC);
        }
        //We don't need override(Target.SIZE_ORIGINAL) here, so set empty defaultRequestOptions for Glide
        GlideApp.with(this).setDefaultRequestOptions(new RequestOptions());
        super.onCreate(savedInstanceState);
    }

    static public OverviewBaseFragment getOverviewFragment(PrefHelper prefHelper, int lastComic) {
        OverviewBaseFragment overviewBaseFragment;
        switch (prefHelper.getOverviewStyle()) {
            case 0:
                overviewBaseFragment = new OverviewListFragment();
                break;
            case 2:
                overviewBaseFragment = new OverviewStaggeredGridFragment();
                break;
            case 1:
            default:
                overviewBaseFragment = new OverviewCardsFragment();
                break;
        }
        Bundle args = new Bundle();
        args.putInt(LAST_COMIC, lastComic);
        overviewBaseFragment.setArguments(args);

        return overviewBaseFragment;
    }

    protected void setupVariables() {
        prefHelper = new PrefHelper(getContext());
        themePrefs = new ThemePrefs(getContext());
        databaseManager = ((MainActivity) getActivity()).getDatabaseManager();
        bookmark = prefHelper.getBookmark();
        setHasOptionsMenu(true);
    }

    protected void updateBookmark(int i) {
        if (bookmark == 0)
            Toast.makeText(getActivity(), R.string.bookmark_toast_2, Toast.LENGTH_LONG).show();
        bookmark = comics.get(i).getComicNumber();
        prefHelper.setBookmark(bookmark);
        getActivity().invalidateOptionsMenu();
    }

    public void showComic(final int pos) {
        goToComic(comics.get(pos).getComicNumber(), pos);
    }

    public void showRandomComic() {
        goToComic(comics.get(new Random().nextInt(comics.size())).getComicNumber(), -1); //Pass a negative position here cause we don't need a shared element transition
    }

    abstract protected TextView getCurrentTitleTextView(int position);

    abstract protected ImageView getCurrentThumbnail(int position);

    public void goToComic(final int number, final int position) {
        //TODO add shared elements, maybe?
        databaseManager.setRead(number, true);
        Timber.d("number: %d", number);
        ((MainActivity) getActivity()).lastComicNumber = number;
        FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
        transaction.setReorderingAllowed(true);
        TextView title = getCurrentTitleTextView(position);
        if (title != null) {
            transaction.addSharedElement(title, title.getTransitionName());
            Timber.d("selected title %s", title.getText());
        }
        ImageView thumbnail = getCurrentThumbnail(position);
        if (thumbnail != null) {
            transaction.addSharedElement(thumbnail, thumbnail.getTransitionName());
        }

        ComicFragment comicFragment;
        if (prefHelper.overviewFav()) {
            comicFragment = new FavoritesFragment();
        } else if (prefHelper.fullOfflineEnabled()) {
            comicFragment = new OfflineFragment();
        } else {
            comicFragment = new ComicBrowserFragment();
        }
        comicFragment.lastComicNumber = number;
        comicFragment.transitionPending = true;
        transaction.replace(R.id.flContent, comicFragment, MainActivity.FRAGMENT_TAG)
                .commitAllowingStateLoss();
        ((MainActivity) getActivity()).setCurrentFragment(prefHelper.overviewFav() ? MainActivity.CurrentFragment.Favorites : MainActivity.CurrentFragment.Browser);
        /*android.support.v4.app.FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
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
            //int index = Arrays.binarySearch(databaseManager.getFavComicsLegacy(), number + 1);
            RealmResults<RealmComic> favorites = databaseManager.getFavComics();
            int index = 0;
            for (int i = 0; i < favorites.size(); i++) {
                if (favorites.get(i).getComicNumber() == number + 1) {
                    index = i;
                }
            }
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
            subtitle = number + 1;
            transaction.commit();

            ((MainActivity) getActivity()).setCurrentFragment(MainActivity.CurrentFragment.Favorites);
            ((MainActivity) getActivity()).getSupportActionBar().setTitle(getActivity().getResources().getString(R.string.nv_favorites));
            ((MainActivity) getActivity()).getNavView().getMenu().findItem(R.id.nav_favorites).setChecked(true);
        }


        if (prefHelper.subtitleEnabled())
            ((MainActivity) getActivity()).getToolbar().setSubtitle(String.valueOf(subtitle)); */
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
            fav.setIcon(R.drawable.ic_favorite_off_24dp);
            fav.setTitle(R.string.nv_favorites);
        } else {
            fav.setIcon(R.drawable.ic_favorite_on_24dp);
            fav.setTitle(R.string.action_overview);
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_overview_style:
                androidx.appcompat.app.AlertDialog.Builder mDialog = new androidx.appcompat.app.AlertDialog.Builder(getActivity());
                mDialog.setTitle(R.string.overview_style_title)
                        .setSingleChoiceItems(R.array.overview_style, prefHelper.getOverviewStyle(), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                prefHelper.setOverviewStyle(i);
                                dialogInterface.dismiss();
                                ((MainActivity) getActivity()).showOverview(true);
                            }
                        }).show();
                break;
            case R.id.action_earliest_unread:
                if (prefHelper.hideRead()) {
                    int n = comics.size() - 1;
                    if (n > 0)
                        showComic(n);
                } else {
                    try {
                        RealmComic comic = comics.where().equalTo("isRead", false).findAllSorted("comicNumber", Sort.ASCENDING).first();
                        if (comic != null)
                            goToComic(comic.getComicNumber(), -1);
                    } catch (IndexOutOfBoundsException e) {
                        e.printStackTrace();
                    }
                }
        }
        return super.onOptionsItemSelected(item);
    }

    public abstract void notifyAdapter(int number);

    protected int getIndexForNumber(int number) {
        if (prefHelper.overviewFav()) {
            for (int i = 0; i < comics.size(); i++) {
                if (comics.get(i).getComicNumber() == lastComicNumber) {
                    return i;
                }
            }
            return 0;
        } else {
            if (lastComicNumber <= comics.size()) {
                return (comics.size() - lastComicNumber);
            }
            return 0;
        }

    }

    protected void setupAdapter() {
        Realm realm = Realm.getDefaultInstance();
        if (prefHelper.overviewFav()) {
            comics = realm.where(RealmComic.class).equalTo("isFavorite", true).findAll();
        } else if (prefHelper.hideRead()) {
            comics = realm.where(RealmComic.class).equalTo("isRead", false).findAll();
        } else {
            comics = realm.where(RealmComic.class).findAll();
        }
        comics.sort("comicNumber", Sort.DESCENDING);
        realm.close();
    }

    protected void animateToolbar() {
        Toolbar toolbar = ((MainActivity) getActivity()).getToolbar();
        if (toolbar.getAlpha() == 0) {
            toolbar.setTranslationY(-300);
            toolbar.animate().setDuration(300).translationY(0).alpha(1);
            View view;
            for (int i = 2; i < toolbar.getChildCount(); i++) {
                view = toolbar.getChildAt(i);
                view.setTranslationY(-300);
                view.animate().setStartDelay(50 * (i + 1)).setDuration(70 * (i + 1)).translationY(0);
            }
        }
    }

    public boolean overViewFav() {
        return comics == null || comics.size() != prefHelper.getNewest();
    }

}
