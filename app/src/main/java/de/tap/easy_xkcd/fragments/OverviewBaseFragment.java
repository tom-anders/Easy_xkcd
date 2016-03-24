package de.tap.easy_xkcd.fragments;

import android.content.DialogInterface;
import android.os.Build;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.tap.xkcd_reader.R;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.database.RealmComic;
import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.utils.ThemePrefs;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

public abstract class OverviewBaseFragment extends android.support.v4.app.Fragment {
    protected static RealmResults<RealmComic> comics;
    protected PrefHelper prefHelper;
    protected ThemePrefs themePrefs;
    protected DatabaseManager databaseManager;
    public static int bookmark;
    protected static final String BROWSER_TAG = "browser";
    protected static final String OVERVIEW_TAG = "overview";

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
        ComicFragment fragment = (ComicFragment) fragmentManager.findFragmentByTag(BROWSER_TAG);
        fragment.scrollTo(number, false);


        getFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                .hide(fragmentManager.findFragmentByTag(OVERVIEW_TAG))
                .show(fragment)
                .commit();


        if (prefHelper.subtitleEnabled())
            ((MainActivity) getActivity()).getToolbar().setSubtitle(String.valueOf(fragment.lastComicNumber));
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        /*for (int i = 0; i < menu.size() - 2; i++)
            menu.getItem(i).setVisible(false);

        menu.findItem(R.id.action_boomark).setVisible(prefHelper.getBookmark() != 0).setTitle(R.string.open_bookmark);
        menu.findItem(R.id.action_hide_read).setVisible(true).setChecked(prefHelper.hideRead());

        MenuItem item = menu.findItem(R.id.action_favorite);
        item.setVisible(true);
        if (!prefHelper.overviewFav()) {
            item.setIcon(R.drawable.ic_favorite_outline);
            item.setTitle(R.string.nv_favorites);
        } else {
            item.setIcon(R.drawable.ic_action_favorite);
            item.setTitle(R.string.action_overview);
        }

        if (prefHelper.hideDonate())
            menu.findItem(R.id.action_donate).setVisible(false);

        menu.findItem(R.id.action_unread).setVisible(true);
        menu.findItem(R.id.action_overview_style).setVisible(true);
        menu.findItem(R.id.action_earliest_unread).setVisible(true); */

        inflater.inflate(R.menu.menu_overview_fragment, menu);
        menu.findItem(R.id.action_boomark).setVisible(prefHelper.getBookmark() != 0).setTitle(R.string.open_bookmark);
        menu.findItem(R.id.action_hide_read).setChecked(prefHelper.hideRead());
        menu.findItem(R.id.action_search).setVisible(false);
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

    private void animateToolbar() {
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


    public void updateDatabasePostExecute() {
        animateToolbar();
    }

}
