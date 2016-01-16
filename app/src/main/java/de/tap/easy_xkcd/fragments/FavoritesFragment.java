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

package de.tap.easy_xkcd.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.tap.xkcd_reader.R;

import java.util.Arrays;
import java.util.Random;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.utils.Favorites;
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
            String[] fav = Favorites.getFavoriteList(getActivity());
            favorites = new int[fav.length];

            for (int i = 0; i < favorites.length; i++) {
                favorites[i] = Integer.parseInt(fav[i]);
                comicMap.put(i, new OfflineComic(favorites[i], getActivity()));
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            adapter = new FavoritesPagerAdapter(getActivity());
            pager.setAdapter(adapter);
            pager.setCurrentItem(favoriteIndex);

            Toolbar toolbar = ((MainActivity) getActivity()).getToolbar();
            if (prefHelper.subtitleEnabled() && ((MainActivity) getActivity()).getCurrentFragment() == R.id.nav_favorites)
                toolbar.setSubtitle(String.valueOf(favorites[favoriteIndex]));

            animateToolbar();
        }
    }

    private class FavoritesPagerAdapter extends ComicAdapter {
        public FavoritesPagerAdapter(Context context) {
            super(context);
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

            pvComic.setImageBitmap(((OfflineComic) comicMap.get(position)).getBitmap());
            if (Arrays.binarySearch(mContext.getResources().getIntArray(R.array.large_comics), favorites[favoriteIndex]) >= 0)
                pvComic.setMaximumScale(7.0f);

            container.addView(itemView);
            return itemView;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_alt: {
                return setAltText(true);
            }
            case R.id.delete_favorites: {
                return deleteAllFavorites();
            }
            case R.id.action_favorite: {
                return modifyFavorites();
            }
            case R.id.action_share: {
                return shareComic(false);
            }
            case R.id.action_random: {
                return getRandomComic();
            }
            case R.id.action_explain: {
                return explainComic(favorites[favoriteIndex]);
            }
            case R.id.action_browser: {
                return openComicInBrowser(favorites[favoriteIndex]);
            }
            case R.id.action_trans: {
                return showTranscript(comicMap.get(favoriteIndex).getTranscript());
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean deleteAllFavorites() {
        new android.support.v7.app.AlertDialog.Builder(getActivity())
                .setMessage(R.string.delete_favorites_dialog)
                .setPositiveButton(R.string.dialog_yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        final String[] fav = Favorites.getFavoriteList(getActivity());

                        Favorites.putStringInPreferences(getActivity(), null, "favorites");

                        MenuItem mBrowser = ((MainActivity) getActivity()).getNavView().getMenu().findItem(R.id.nav_browser);
                        ((MainActivity) getActivity()).selectDrawerItem(mBrowser, false);

                        if (!prefHelper.fullOfflineEnabled())
                            for (String i : fav)
                                getActivity().deleteFile(i);

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

        @Override
        protected Void doInBackground(Integer... pos) {
            if (!MainActivity.fullOffline)
                getActivity().deleteFile(String.valueOf(pos[0]));

            Favorites.removeFavoriteItem(getActivity(), String.valueOf(pos[0]));
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            String[] fav = Favorites.getFavoriteList(getActivity());
            if (fav.length == 0) {
                //If there are no favorites left, show ComicBrowserFragment
                MenuItem mBrowser = ((MainActivity) getActivity()).getNavView().getMenu().findItem(R.id.nav_browser);
                ((MainActivity) getActivity()).selectDrawerItem(mBrowser, false);
                return;
            }
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
                Favorites.addFavoriteItem(getActivity(), String.valueOf(mRemoved));
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
            shareComicImage(getURI(), comicMap.get(favoriteIndex));
            return true;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setItems(R.array.share_dialog, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        shareComicImage(getURI(), comicMap.get(favoriteIndex));
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
        menu.findItem(R.id.action_latest).setVisible(false);
        menu.findItem(R.id.action_search).setVisible(false);
        menu.findItem(R.id.action_boomark).setVisible(false);
        menu.findItem(R.id.action_overview).setVisible(false);
        menu.findItem(R.id.delete_favorites).setVisible(true);
        MenuItem fav = menu.findItem(R.id.action_favorite);
        fav.setIcon(R.drawable.ic_action_favorite);
        fav.setTitle(R.string.action_favorite_remove);
        //If the FAB is visible, hide the random comic menu item
        if (((MainActivity) getActivity()).getFab().getVisibility() == View.GONE) {
            menu.findItem(R.id.action_random).setVisible(true);
        } else {
            menu.findItem(R.id.action_random).setVisible(false);
        }
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
        String[] fav = Favorites.getFavoriteList(this.getActivity());
        if (fav.length != 0) {
            if (favoriteIndex == fav.length) {
                favoriteIndex--;
            }
            new updateFavorites().execute();
        }
    }

    public void updatePager() {}

}
