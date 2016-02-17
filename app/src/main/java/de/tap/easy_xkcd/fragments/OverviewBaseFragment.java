package de.tap.easy_xkcd.fragments;

import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.util.Log;
import android.util.SparseArray;
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
import java.util.Random;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.utils.Favorites;
import de.tap.easy_xkcd.utils.PrefHelper;

public abstract class OverviewBaseFragment extends android.support.v4.app.Fragment {
    protected static SparseArray<String> titles = new SparseArray<>();
    protected static SparseArray<String> urls = new SparseArray<>();
    protected static int[] read;
    protected PrefHelper prefHelper;
    public static int bookmark;
    protected static final String BROWSER_TAG = "browser";
    protected static final String OVERVIEW_TAG = "overview";

    protected void setupVariables() {
        prefHelper = ((MainActivity) getActivity()).getPrefHelper();
        bookmark = prefHelper.getBookmark();
        setHasOptionsMenu(true);
    }

    protected void updateBookmark(int i) {
        prefHelper.setBookmark(titles.keyAt(i));
        bookmark = titles.keyAt(i);
    }

    public void showComic(final int pos) {
        android.support.v4.app.FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
        ComicFragment fragment = (ComicFragment) fragmentManager.findFragmentByTag(BROWSER_TAG);
        fragment.scrollTo(titles.keyAt(pos) - 1, false);

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

    protected boolean checkComicRead(int number) {
        return read != null && Arrays.binarySearch(read, number) >= 0;
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
            case R.id.action_overview_style: //TODO apply dialog style
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

    public void notifyAdapter(int pos) {
        if (prefHelper == null)
            return;
        read = prefHelper.getComicRead();
    }

    protected void setupAdapter() {
        read = prefHelper.getComicRead();
        String[] t = prefHelper.getComicTitles().split("&&");
        String[] u = prefHelper.getComicUrls().split("&&");
        boolean hideRead = prefHelper.hideRead();
        titles.clear();
        urls.clear();
        for (int i = 1; i <= t.length; i++) {
            if (prefHelper.overviewFav()) {
                if (Favorites.checkFavorite(getActivity(), i)) {
                    titles.put(i, t[i - 1]);
                    urls.put(i, u[i - 1]);
                }
                continue;
            }
            if (read == null || (!hideRead) || ((Arrays.binarySearch(read, i) < 0) || i == bookmark)) {
                titles.put(i, t[i - 1]);
                urls.put(i, u[i - 1]);
            }
        }
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

    protected abstract class updateDatabase extends AsyncTask<Void, Integer, Void> {
        private ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(getActivity());
            progress.setTitle(getResources().getString(R.string.update_database));
            progress.setIndeterminate(false);
            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (!prefHelper.databaseLoaded()) {
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
                prefHelper.setTitles(sb.toString());
                publishProgress(15);
                Log.d("info", "titles loaded");

                is = getResources().openRawResource(R.raw.comic_trans);
                br = new BufferedReader(new InputStreamReader(is));
                sb = new StringBuilder();
                try {
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                } catch (IOException e) {
                    Log.e("error:", e.getMessage());
                }
                prefHelper.setTrans(sb.toString());
                publishProgress(30);
                Log.d("info", "trans loaded");

                is = getResources().openRawResource(R.raw.comic_urls);
                br = new BufferedReader(new InputStreamReader(is));
                sb = new StringBuilder();
                try {
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                } catch (IOException e) {
                    Log.e("error:", e.getMessage());
                }
                prefHelper.setUrls(sb.toString(), 1579);
                Log.d("info", "urls loaded");
                prefHelper.setDatabaseLoaded();
            }
            publishProgress(50);
            if (prefHelper.isOnline(getActivity())) {
                int newest;
                try {
                    newest = new Comic(0).getComicNumber();
                } catch (IOException e) {
                    newest = prefHelper.getNewest();
                }
                StringBuilder sbTitle = new StringBuilder();
                sbTitle.append(prefHelper.getComicTitles());
                StringBuilder sbTrans = new StringBuilder();
                sbTrans.append(prefHelper.getComicTrans());
                StringBuilder sbUrl = new StringBuilder();
                sbUrl.append(prefHelper.getComicUrls());
                String title;
                String trans;
                String url;
                Comic comic;
                int highestUrls = prefHelper.getHighestUrls();
                for (int i = highestUrls; i < newest; i++) {
                    try {
                        comic = new Comic(i + 1);
                        title = comic.getComicData()[0];
                        trans = comic.getTranscript();
                        url = comic.getComicData()[2];
                    } catch (IOException e) {
                        title = "";
                        trans = "";
                        url = "";
                    }
                    sbTitle.append("&&");
                    sbTitle.append(title);
                    sbUrl.append("&&");
                    sbUrl.append(url);
                    sbTrans.append("&&");
                    if (!trans.equals("")) {
                        sbTrans.append(trans);
                    } else {
                        sbTrans.append("n.a.");
                    }
                    float x = newest - highestUrls;
                    int y = i - highestUrls;
                    int p = (int) ((y / x) * 50);
                    publishProgress(p + 50);
                }
                prefHelper.setTitles(sbTitle.toString());
                prefHelper.setTrans(sbTrans.toString());
                prefHelper.setUrls(sbUrl.toString(), newest);
            }
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
