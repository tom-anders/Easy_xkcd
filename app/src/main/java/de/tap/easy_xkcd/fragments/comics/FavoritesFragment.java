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

package de.tap.easy_xkcd.fragments.comics;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import com.bumptech.glide.request.target.Target;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.snackbar.Snackbar;

import androidx.core.content.FileProvider;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.widget.Toolbar;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.nbsp.materialfilepicker.ui.FilePickerActivity;
import com.tap.xkcd_reader.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Random;
import java.util.Stack;

import de.tap.easy_xkcd.Activities.CustomFilePickerActivity;
import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.database.RealmComic;
import io.realm.RealmResults;
import timber.log.Timber;


public class FavoritesFragment extends ComicFragment {

    public RealmResults<RealmComic> favorites;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, final Bundle savedInstanceState) {
        View v = inflateLayout(R.layout.pager_layout, inflater, container, savedInstanceState);

        if (getMainActivity().getProgressDialog() != null) {
            getMainActivity().getProgressDialog().dismiss();
        }

        if (prefHelper.fabDisabledFavorites()) getMainActivity().getFab().hide(); else getMainActivity().getFab().show();

        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                favoriteIndex = position;
                try {
                    //Update the ActionBar Subtitle
                    if (getMainActivity().getCurrentFragment() == MainActivity.CurrentFragment.Favorites)
                        getMainActivity().getToolbar().setSubtitle(prefHelper.subtitleEnabled() ? String.valueOf(favorites.get(position).getComicNumber()) : "");

                    getActivity().invalidateOptionsMenu();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
                lastComicNumber = favorites.get(favoriteIndex).getComicNumber();
                getMainActivity().lastComicNumber = lastComicNumber;
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }
        });

        if (savedInstanceState != null) {
            favoriteIndex = savedInstanceState.getInt(LAST_FAV);
            getActivity().invalidateOptionsMenu();
        }

        if (getActivity() != null) {
            updateFavorites();
        }

        return v;
    }

    void updateFavorites() {
        favorites = databaseManager.getFavComics();
        /*for (int i = 0; i < favorites.size(); i++)
            comicMap.put(i, new OfflineComic(favorites.get(i).getComicNumber(), getActivity(), ((MainActivity) getActivity()).getPrefHelper()));*/

        if (lastComicNumber != 0) {
            for (int i = 0; i < favorites.size(); i++) {
                if (favorites.get(i).getComicNumber() == lastComicNumber) {
                    favoriteIndex = i;
                }
            }
        }
        
        lastComicNumber = favorites.get(favoriteIndex).getComicNumber();
        getMainActivity().lastComicNumber = lastComicNumber;

        adapter = new FavoritesPagerAdapter(getActivity(), 0);
        pager.setAdapter(adapter);
        pager.setCurrentItem(favoriteIndex);

        Toolbar toolbar = getMainActivity().getToolbar();
        if (getMainActivity().getCurrentFragment() == MainActivity.CurrentFragment.Favorites)
            toolbar.setSubtitle(prefHelper.subtitleEnabled() ? String.valueOf(favorites.get(favoriteIndex).getComicNumber()) : "");

        animateToolbar();
    }

    private class FavoritesPagerAdapter extends ComicAdapter {
        public FavoritesPagerAdapter(Context context, int count) {
            super(context, count);
        }

        @Override
        RealmComic getRealmComic(int position) {
            return favorites.get(position);
        }

        @Override
        void loadComicImage(RealmComic comic, PhotoView pvComic) {
            if (!loadGif(comic.getComicNumber(), pvComic)) {
                Bitmap bitmap = RealmComic.getOfflineBitmap(comic.getComicNumber(), context, prefHelper);
                postImageLoadedSetupPhotoView(pvComic, bitmap, comic);
                if (bitmap != null) {
                    pvComic.setImageBitmap(bitmap);
                } else {
                    new RedownloadFavorite().execute(comic.getComicNumber()); // If the image is gone for some reason download it and refresh the fragment
                }
                postImageLoaded(comic.getComicNumber());
            }
        }

        @Override
        public int getCount() {
            return favorites.size();
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_alt:
                return setAltText(true);

            case R.id.delete_favorites:
                return deleteAllFavorites();

            case R.id.action_favorite:
                return modifyFavorites(item);

            case R.id.action_share:
                return shareComic(false);

            case R.id.action_random:
                return getRandomComic();

            case R.id.action_explain:
                return explainComic(favorites.get(favoriteIndex).getComicNumber());

            case R.id.action_browser:
                return openComicInBrowser(favorites.get(favoriteIndex).getComicNumber());

            case R.id.action_trans:
                return showTranscript(favorites.get(favoriteIndex).getTranscript(), favorites.get(favoriteIndex).getComicNumber());

            case R.id.export_import_favorites:
                return exportImportFavorites();

            case R.id.action_thread:
                return DatabaseManager.showThread(favorites.get(favoriteIndex).getTitle(), getActivity(), false);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public TextView getCurrentTitleTextView() {
        return pager.findViewWithTag(favoriteIndex).findViewById(R.id.tvTitle);
    }

    @Override
    public PhotoView getCurrentPhotoView() {
        return pager.findViewWithTag(favoriteIndex).findViewById(R.id.ivComic);
    }

    private boolean exportImportFavorites() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setItems(R.array.export_import_dialog, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        exportFavorites();
                        break;
                    case 1:
                        if (prefHelper.isOnline(getActivity()) || prefHelper.fullOfflineEnabled())
                            getActivity().startActivityForResult(new Intent(getActivity(), CustomFilePickerActivity.class), 2);
                        else
                            Toast.makeText(getActivity(), getResources().getString(R.string.no_connection), Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        });
        builder.create().show();
        return true;
    }

    public void importFavorites(Intent intent) {
        String filePath = intent.getStringExtra(FilePickerActivity.RESULT_FILE_PATH);
        try {
            File file = new File(filePath);
            InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(file));
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line;

            Stack<Integer> newFavorites = new Stack<>();
            while ((line = bufferedReader.readLine()) != null) {
                String[] numberTitle = line.split(" - ");
                int number = Integer.parseInt(numberTitle[0]);
                //if (Arrays.binarySearch(favorites, number) < 0) {
                if (!databaseManager.isFavorite(number)) {
                    newFavorites.push(number);
                    databaseManager.setFavorite(number, true);
                }
            }
            if (!prefHelper.fullOfflineEnabled()) {
                new DownloadImageTask(newFavorites).execute();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getActivity(), "Import failed", Toast.LENGTH_SHORT).show();
        }
    }

    private class DownloadImageTask extends AsyncTask<Void, Void, Void> {
        private Bitmap mBitmap;
        private Stack<Integer> favStack;
        private ProgressDialog progress;

        public DownloadImageTask(Stack<Integer> stack) {
            favStack = stack;
        }

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(getActivity());
            progress.setTitle(getResources().getString(R.string.loading_comics));
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected Void doInBackground(Void... dummy) {
            DatabaseManager databaseManager = new DatabaseManager(getActivity());
            while (!favStack.isEmpty()) {
                try {
                    //RealmComic comic = RealmComic.findNewestComic(Realm.getDefaultInstance(), getActivity());
                    RealmComic comic = databaseManager.getRealmComic(favStack.pop());
                    mBitmap = Glide
                            .with(getActivity())
                            .asBitmap()
                            .load(comic.getUrl())
                            //.diskCacheStrategy(DiskCacheStrategy.SOURCE)
                            .into(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL) //TODO replace all .into(-1,-1) with the simple target
                            .get();
                    RealmComic.saveOfflineBitmap(mBitmap, prefHelper, comic.getComicNumber(), getActivity());
                    Timber.d("comic %d saved...", comic.getComicNumber());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            progress.dismiss();
            Toast.makeText(getActivity(), getResources().getString(R.string.pref_import_success), Toast.LENGTH_SHORT).show();
            refresh();
        }

    }

    private void exportFavorites() {
        //Export the full favorites list as text
        StringBuilder sb = new StringBuilder();
        String newline = System.getProperty("line.separator");
        for (int i = 0; i < favorites.size(); i++) {
            sb.append(favorites.get(i).getComicNumber()).append(" - ");
            sb.append(favorites.get(i).getTitle());
            sb.append(newline);
        }
        try {
            File file = new File(prefHelper.getOfflinePath(getActivity()), "favorites.txt");
            FileWriter writer = new FileWriter(file);
            writer.append(sb.toString());
            writer.flush();
            writer.close();
            //Provide option to send to any app that accepts text/plain content
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType("text/plain");
            Uri uri = FileProvider.getUriForFile(getActivity(), "de.tap.easy_xkcd.fileProvider", file);
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
            startActivity(Intent.createChooser(sendIntent, getResources().getString(R.string.pref_export)));
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getActivity(), "Export failed", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean deleteAllFavorites() {
        new androidx.appcompat.app.AlertDialog.Builder(getActivity())
                .setMessage(R.string.delete_favorites_dialog)
                .setPositiveButton(R.string.dialog_yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                        if (!prefHelper.fullOfflineEnabled())
                            for (int i = 0; i < favorites.size(); i++)
                                getActivity().deleteFile(String.valueOf(favorites.get(i).getComicNumber()));

                        databaseManager.removeAllFavorites();

                        MenuItem mBrowser = getMainActivity().getNavView().getMenu().findItem(R.id.nav_browser);
                        getMainActivity().selectDrawerItem(mBrowser, false, false, false, true);

                        Toast toast = Toast.makeText(getActivity(), R.string.favorites_cleared, Toast.LENGTH_SHORT);
                        toast.show();
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .setCancelable(true)
                .show();
        return true;
    }

    public class DeleteImageTask extends AsyncTask<Integer, Integer, Void> {
        int number;

        @Override
        protected Void doInBackground(Integer... pos) {
            number = pos[0];
            if (!MainActivity.fullOffline)
                getActivity().deleteFile(String.valueOf(pos[0]));

            (new DatabaseManager(getActivity())).setFavorite(pos[0], false);
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            if (databaseManager.noFavorites()) {
                //If there are no favorites left, show ComicBrowserFragment
                MenuItem mBrowser = getMainActivity().getNavView().getMenu().findItem(R.id.nav_browser);
                getMainActivity().selectDrawerItem(mBrowser, false, false, true, true);
                return;
            }
            refresh();
        }
    }

    public class RedownloadFavorite extends AsyncTask<Integer, Integer, Void> {

        @Override
        protected Void doInBackground(Integer... pos) {
            try {
                Bitmap bitmap = Glide
                        .with(getActivity())
                        .asBitmap()
                        .load((new DatabaseManager(getActivity())).getRealmComic(pos[0]).getUrl())
                        //.diskCacheStrategy(DiskCacheStrategy.SOURCE)
                        .into(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                        .get();
                RealmComic.saveOfflineBitmap(bitmap, prefHelper, pos[0], getActivity());
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            refresh();
        }
    }

    @Override
    protected boolean modifyFavorites(MenuItem item) {
        final int mRemoved = favorites.get(favoriteIndex).getComicNumber();
        final Bitmap mRemovedBitmap = RealmComic.getOfflineBitmap(favorites.get(favoriteIndex).getComicNumber(), getActivity(), prefHelper);

        if (favorites.size() > 1) {
            Snackbar.make(getMainActivity().getFab(), R.string.snackbar_remove, Snackbar.LENGTH_LONG)
                    .setAction(R.string.snackbar_undo, view -> {
                        databaseManager.setFavorite(mRemoved, true);
                        RealmComic.saveOfflineBitmap(mRemovedBitmap, prefHelper, mRemoved, getActivity());
                        refresh();
                    })
                    .show();
        }

        new DeleteImageTask().execute(mRemoved);

        return true;
    }

    public boolean shareComic(boolean fromPermission) {
        if (fromPermission || prefHelper.shareImage()) {
            shareComicImage(getURI(favorites.get(favoriteIndex).getComicNumber()), favorites.get(favoriteIndex));
            return true;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setItems(R.array.share_dialog, (dialog, which) -> {
            switch (which) {
                case 0:
                    shareComicImage(getURI(favorites.get(favoriteIndex).getComicNumber()), favorites.get(favoriteIndex));
                    break;
                case 1:
                    shareComicUrl(favorites.get(favoriteIndex));
                    break;
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
        return true;
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        /*menu.findItem(R.id.action_latest).setVisible(false);
        menu.findItem(R.id.action_search).setVisible(false);
        menu.findItem(R.id.action_boomark).setVisible(false);
        menu.findItem(R.id.action_overview).setVisible(false);
        menu.findItem(R.id.delete_favorites).setVisible(true);
        menu.findItem(R.id.export_import_favorites).setVisible(true);
        MenuItem fav = menu.findItem(R.id.action_favorite);
        fav.setIcon(R.drawable.ic_action_favorite);
        fav.setTitle(R.string.action_favorite_remove);*/

        //If the FAB is visible, hide the random comic menu item
        if (getMainActivity().getFab().getVisibility() == View.GONE) {
            menu.findItem(R.id.action_random).setVisible(true);
        } else {
            menu.findItem(R.id.action_random).setVisible(false);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_favorites_fragment, menu);
        menu.findItem(R.id.action_search).setVisible(false);
        super.onCreateOptionsMenu(menu, inflater);
    }

    public boolean getRandomComic() {
        //get a random number and update the pager
        if (favorites.size() > 1) {
            Random rand = new Random();
            Integer number = rand.nextInt(favorites.size());
            while (number.equals(favoriteIndex)) {
                number = rand.nextInt(favorites.size());
            }
            pager.setCurrentItem(number);
        }
        return true;
    }

    public void refresh() {
        //Updates favorite list, pager and alt TextView
        if (!databaseManager.noFavorites()) {
            if (favoriteIndex == databaseManager.getFavComics().size())
                favoriteIndex--;

            updateFavorites();
        }
    }

    public void updatePager() {
    }

}
