package de.tap.easy_xkcd.fragments;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.tap.xkcd_reader.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.database.RealmComic;
import de.tap.easy_xkcd.utils.Comic;
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
        goToComic(number-1);
    }

    public void goToComic(final int number) {
        android.support.v4.app.FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
        ComicFragment fragment = (ComicFragment) fragmentManager.findFragmentByTag(BROWSER_TAG);
        fragment.scrollTo(number, false);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            fragmentManager.beginTransaction().hide(fragmentManager.findFragmentByTag(OVERVIEW_TAG)).show(fragment).commitAllowingStateLoss();
        } else {
            Transition left = TransitionInflater.from(getActivity()).inflateTransition(android.R.transition.slide_left);
            Transition right = TransitionInflater.from(getActivity()).inflateTransition(android.R.transition.slide_right);

            OverviewBaseFragment.this.setExitTransition(left);

            fragment.setEnterTransition(right);

            getFragmentManager().beginTransaction()
                    .hide(fragmentManager.findFragmentByTag(OVERVIEW_TAG))
                    .show(fragment)
                    .commit();
        }

        if (prefHelper.subtitleEnabled()) {
            ((MainActivity) getActivity()).getToolbar().setSubtitle(String.valueOf(fragment.lastComicNumber));
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        for (int i = 0; i < menu.size() - 2; i++)
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
            case R.id.action_hide_read:
                break;
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

    public abstract class updateDatabase extends AsyncTask<Void, Integer, Void> {
        private ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(getActivity());
            progress.setTitle(getActivity().getResources().getString(R.string.update_database));
            progress.setIndeterminate(false);
            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progress.setCancelable(false);
            progress.show();
        }

        private String getFile(int rawId) {
            InputStream is = getActivity().getResources().openRawResource(rawId);
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
            return sb.toString();
        }

        @Override
        protected Void doInBackground(Void... params) {
            Realm realm = Realm.getInstance(getActivity());
            int[] read = databaseManager.getReadComics();
            int[] fav = databaseManager.getFavComics();
            if (!databaseManager.databaseLoaded()) {
                String[] titles = getFile(R.raw.comic_titles).split("&&");
                String[] trans = getFile(R.raw.comic_trans).split("&&");
                String[] urls = getFile(R.raw.comic_urls).split("&&");
                realm.beginTransaction();
                for (int i = 0; i < 1645; i++) {
                    RealmComic comic = realm.createObject(RealmComic.class);
                    comic.setComicNumber(i + 1);
                    comic.setTitle(titles[i]);
                    comic.setTranscript(trans[i]);
                    comic.setUrl(urls[i]);
                    comic.setRead(Arrays.binarySearch(read, i + 1) >= 0);
                    comic.setFavorite(Arrays.binarySearch(fav, i + 1) >= 0);
                }
                realm.commitTransaction();
                databaseManager.setHighestDatabase(1645);
            }
            if (prefHelper.isOnline(getActivity())) {
                int newest;
                try {
                    Comic comic = new Comic(0);
                    newest = comic.getComicNumber();
                } catch (IOException e) {
                    newest = prefHelper.getNewest();
                }
                realm.beginTransaction();
                int highest = databaseManager.getHighestInDatabase() + 1;
                for (int i = highest; i <= newest; i++) {
                    try {
                        Comic comic = new Comic(i);
                        RealmComic realmComic = realm.createObject(RealmComic.class);
                        realmComic.setComicNumber(i);
                        realmComic.setTitle(comic.getComicData()[0]);
                        realmComic.setTranscript(comic.getTranscript());
                        realmComic.setUrl(comic.getComicData()[2]);
                        realmComic.setRead(Arrays.binarySearch(read, i) >= 0);
                        realmComic.setFavorite(Arrays.binarySearch(fav, i + 1) >= 0);
                        float x = newest - highest;
                        int y = i - highest;
                        int p = (int) ((y / x) * 100);
                        publishProgress(p);
                    } catch (IOException e) {
                        Log.d("error at " + i, e.getMessage());
                    }
                }

                realm.commitTransaction();
                databaseManager.setHighestDatabase(newest);
            }
            databaseManager.setDatabaseLoaded(true);
            realm.close();
            return null;
        }

        protected void onProgressUpdate(Integer... pro) {
            progress.setProgress(pro[0]);
        }

        @Override
        protected void onPostExecute(Void dummy) {
            progress.dismiss();
            animateToolbar();
        }
    }

}
