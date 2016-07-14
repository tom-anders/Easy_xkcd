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
import android.support.design.widget.Snackbar;
import android.support.v4.content.FileProvider;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.GlideDrawableImageViewTarget;
import com.nbsp.materialfilepicker.ui.FilePickerActivity;
import com.tap.xkcd_reader.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Random;
import java.util.Stack;

import de.tap.easy_xkcd.Activities.CustomFilePickerActivity;
import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.utils.OfflineComic;
import uk.co.senab.photoview.PhotoView;


public class FavoritesFragment extends ComicFragment {

    public int[] favorites;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, final Bundle savedInstanceState) {
        View v = inflateLayout(R.layout.pager_layout, inflater, container, savedInstanceState);

        if (((MainActivity) getActivity()).getProgressDialog() != null) {
            ((MainActivity) getActivity()).getProgressDialog().dismiss();
        }

        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                favoriteIndex = position;
                try {
                    //Update the ActionBar Subtitle
                    if (prefHelper.subtitleEnabled() && ((MainActivity) getActivity()).getCurrentFragment() == R.id.nav_favorites)
                        ((MainActivity) getActivity()).getToolbar().setSubtitle(String.valueOf(favorites[position]));

                    getActivity().invalidateOptionsMenu();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
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

        new updateFavorites().execute();
        return v;
    }

    public class updateFavorites extends AsyncTask<Integer, Integer, Void> {
        @Override
        protected Void doInBackground(Integer... pos) {
            favorites = databaseManager.getFavComics();
            for (int i = 0; i < favorites.length; i++)
                comicMap.put(i, new OfflineComic(favorites[i], getActivity(), ((MainActivity) getActivity()).getPrefHelper()));

            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            adapter = new FavoritesPagerAdapter(getActivity(), 0);
            pager.setAdapter(adapter);
            pager.setCurrentItem(favoriteIndex);

            Toolbar toolbar = ((MainActivity) getActivity()).getToolbar();
            if (prefHelper.subtitleEnabled() && ((MainActivity) getActivity()).getCurrentFragment() == R.id.nav_favorites)
                toolbar.setSubtitle(String.valueOf(favorites[favoriteIndex]));

            animateToolbar();
        }
    }

    private class FavoritesPagerAdapter extends ComicAdapter {
        public FavoritesPagerAdapter(Context context, int count) {
            super(context, count);
        }

        @Override
        public int getCount() {
            return comicMap.size();
        }

        @Override
        public Object instantiateItem(ViewGroup container, final int position) {
            View itemView = setupPager(container, position);
            final PhotoView pvComic = (PhotoView) itemView.findViewById(R.id.ivComic);
            final TextView tvAlt = (TextView) itemView.findViewById(R.id.tvAlt);
            final TextView tvTitle = (TextView) itemView.findViewById(R.id.tvTitle);

            tvAlt.setText(prefHelper.getAlt(favorites[position]));
            tvTitle.setText(prefHelper.getTitle(favorites[position]));

            if (getGifId(favorites[position] - 1) != 0)
                Glide.with(getActivity())
                        .load(getGifId(favorites[position] - 1))
                        .into(new GlideDrawableImageViewTarget(pvComic));
            else {
                Bitmap bitmap = ((OfflineComic) comicMap.get(position)).getBitmap();
                if (themePrefs.invertColors() && themePrefs.bitmapContainsColor(bitmap))
                    pvComic.clearColorFilter();
                if (bitmap != null)
                    pvComic.setImageBitmap(bitmap);
                else
                    new RedownloadFavorite().execute(comicMap.get(position).getComicNumber()); // If the image is gone download it and refresh the fragment
            }
            if (Arrays.binarySearch(mContext.getResources().getIntArray(R.array.large_comics), favorites[favoriteIndex]) >= 0)
                pvComic.setMaximumScale(13.0f);

            container.addView(itemView);
            return itemView;
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
                return modifyFavorites();

            case R.id.action_share:
                return shareComic(false);

            case R.id.action_random:
                return getRandomComic();

            case R.id.action_explain:
                return explainComic(favorites[favoriteIndex]);

            case R.id.action_browser:
                return openComicInBrowser(favorites[favoriteIndex]);

            case R.id.action_trans:
                return showTranscript(comicMap.get(favoriteIndex).getTranscript());

            case R.id.export_import_favorites:
                return exportImportFavorites();

            case R.id.action_thread:
                return DatabaseManager.showThread(comicMap.get(favorites[favoriteIndex]).getComicData()[0], getActivity(), false);
        }
        return super.onOptionsItemSelected(item);
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
                if (Arrays.binarySearch(favorites, number) < 0) {
                    newFavorites.push(number);
                    databaseManager.setFavorite(number, true);
                    if (number <= ((MainActivity) getActivity()).getDatabaseManager().getHighestInDatabase())
                        ((MainActivity) getActivity()).getDatabaseManager().setFavorite(number, true);
                }
                if (!prefHelper.fullOfflineEnabled()) {
                    new DownloadImageTask(newFavorites).execute();
                }
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
            while (!favStack.isEmpty()) {
                try {
                    int num = favStack.pop();
                    Comic comic = new Comic(num, getActivity());
                    mBitmap = Glide
                            .with(getActivity())
                            .load(comic.getComicData()[2])
                            .asBitmap()
                            .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                            .into(-1, -1)
                            .get();
                    saveComic(num, mBitmap);
                    prefHelper.addTitle(comic.getComicData()[0], num);
                    prefHelper.addAlt(comic.getComicData()[1], num);
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
        for (int i = 0; i < favorites.length; i++) {
            sb.append(favorites[i]).append(" - ");
            sb.append(prefHelper.getTitle(favorites[i]));
            sb.append(newline);
        }
        try {
            File sdCard = prefHelper.getOfflinePath();
            File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
            File file = new File(dir, "favorites.txt");
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
        new android.support.v7.app.AlertDialog.Builder(getActivity())
                .setMessage(R.string.delete_favorites_dialog)
                .setPositiveButton(R.string.dialog_yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                        final int[] fav = databaseManager.getFavComics();
                        databaseManager.removeAllFavorites();

                        MenuItem mBrowser = ((MainActivity) getActivity()).getNavView().getMenu().findItem(R.id.nav_browser);
                        ((MainActivity) getActivity()).selectDrawerItem(mBrowser, false, false);

                        if (!prefHelper.fullOfflineEnabled())
                            for (int i : fav)
                                getActivity().deleteFile(String.valueOf(i));

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

            databaseManager.setFavorite(pos[0], false);
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            if (databaseManager.noFavorites()) {
                //If there are no favorites left, show ComicBrowserFragment
                MenuItem mBrowser = ((MainActivity) getActivity()).getNavView().getMenu().findItem(R.id.nav_browser);
                ((MainActivity) getActivity()).selectDrawerItem(mBrowser, false, false);
                return;
            }
            refresh();
        }
    }

    public class RedownloadFavorite extends AsyncTask<Integer, Integer, Void> {

        @Override
        protected Void doInBackground(Integer... pos) {
            try {
                Comic comic = new Comic(pos[0], getActivity());
                Bitmap bitmap = Glide
                        .with(getActivity())
                        .load(comic.getComicData()[2])
                        .asBitmap()
                        .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                        .into(-1, -1)
                        .get();
                saveComic(pos[0], bitmap);
                prefHelper.addTitle(comic.getComicData()[0], pos[0]);
                prefHelper.addAlt(comic.getComicData()[1], pos[0]);
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

    private boolean modifyFavorites() {
        final int mRemoved = favorites[favoriteIndex];
        final Bitmap mRemovedBitmap = ((OfflineComic) comicMap.get(favoriteIndex)).getBitmap();
        final String mAlt = prefHelper.getAlt(favorites[favoriteIndex]);
        final String mTitle = prefHelper.getTitle(favorites[favoriteIndex]);

        new DeleteImageTask().execute(mRemoved);

        View.OnClickListener oc = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                databaseManager.setFavorite(mRemoved, true);
                if (mRemoved <= ((MainActivity) getActivity()).getDatabaseManager().getHighestInDatabase())
                    ((MainActivity) getActivity()).getDatabaseManager().setFavorite(mRemoved, true);
                saveComic(mRemoved, mRemovedBitmap);
                prefHelper.addTitle(mTitle, mRemoved);
                prefHelper.addAlt(mAlt, mRemoved);
                refresh();
            }
        };

        Snackbar.make(((MainActivity) getActivity()).getFab(), R.string.snackbar_remove, Snackbar.LENGTH_LONG)
                .setAction(R.string.snackbar_undo, oc)
                .show();
        return true;
    }

    public boolean shareComic(boolean fromPermission) {
        if (fromPermission || prefHelper.shareImage()) {
            shareComicImage(getURI(comicMap.get(favoriteIndex).getComicNumber()), comicMap.get(favoriteIndex));
            return true;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setItems(R.array.share_dialog, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        shareComicImage(getURI(comicMap.get(favoriteIndex).getComicNumber()), comicMap.get(favoriteIndex));
                        break;
                    case 1:
                        shareComicUrl(comicMap.get(favoriteIndex));
                        break;
                }
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
        if (((MainActivity) getActivity()).getFab().getVisibility() == View.GONE) {
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
        if (favorites.length > 1) {
            Random rand = new Random();
            Integer number = rand.nextInt(favorites.length);
            while (number.equals(favoriteIndex)) {
                number = rand.nextInt(favorites.length);
            }
            pager.setCurrentItem(number);
        }
        return true;
    }

    public void refresh() {
        //Updates favorite list, pager and alt TextView
        comicMap.clear();
        if (!databaseManager.noFavorites()) {
            if (favoriteIndex == databaseManager.getFavComics().length)
                favoriteIndex--;

            new updateFavorites().execute();
        }
    }

    public void updatePager() {
    }

}
