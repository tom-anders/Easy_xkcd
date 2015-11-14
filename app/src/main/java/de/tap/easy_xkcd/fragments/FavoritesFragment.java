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

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import de.tap.easy_xkcd.utils.Favorites;
import de.tap.easy_xkcd.misc.HackyViewPager;
import de.tap.easy_xkcd.utils.OfflineComic;
import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.Activities.MainActivity;
import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;


public class FavoritesFragment extends android.support.v4.app.Fragment {

    public SparseArray<OfflineComic> mComicMap = new SparseArray<>();
    static final String LAST_FAV = "last fav";
    public static Integer sFavoriteIndex = 0;
    private static HackyViewPager sPager;
    private FavoritesPagerAdapter mPagerAdapter = null;
    private String[] mFav;
    public static int[] sFavorites;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, final Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.pager_layout, container, false);
        //Get Favorites &&
        mFav = Favorites.getFavoriteList(this.getActivity());
        //mSharedPreferences = getActivity().getPreferences(Activity.MODE_PRIVATE);
        setHasOptionsMenu(true);
        if (((MainActivity) getActivity()).getProgressDialog() != null) {
            ((MainActivity) getActivity()).getProgressDialog().dismiss();
        }

        mPagerAdapter = new FavoritesPagerAdapter(getActivity());
        sPager = (HackyViewPager) v.findViewById(R.id.pager);
        setupPager(sPager);
        if (savedInstanceState != null) {
            sFavoriteIndex = savedInstanceState.getInt(LAST_FAV);
            getActivity().invalidateOptionsMenu();
        }

        if (mFav != null) {
            new pagerUpdate().execute();
        }
        return v;
    }

    private void setupPager(ViewPager pager) {
        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                sFavoriteIndex = position;
                try {
                    //Update the ActionBar Subtitle
                    if (PrefHelper.subtitleEnabled() && ((MainActivity) getActivity()).getCurrentFragment() == R.id.nav_favorites)
                        ((MainActivity) getActivity()).getToolbar().setSubtitle(String.valueOf(sFavorites[position]));

                    getActivity().invalidateOptionsMenu();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
                switch (Integer.parseInt(PrefHelper.getOrientation())) {
                    case 1:
                        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
                        break;
                    case 2:
                        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        break;
                    case 3:
                        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        break;
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }
        });
    }

    private class FavoritesPagerAdapter extends PagerAdapter {
        Context mContext;
        LayoutInflater mLayoutInflater;
        Boolean fingerLifted = true;

        public FavoritesPagerAdapter(Context context) {
            mContext = context;
            mLayoutInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return mComicMap.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public Object instantiateItem(ViewGroup container, final int position) {
            View itemView = mLayoutInflater.inflate(R.layout.pager_item, container, false);
            final PhotoView pvComic = (PhotoView) itemView.findViewById(R.id.ivComic);
            final TextView tvAlt = (TextView) itemView.findViewById(R.id.tvAlt);
            itemView.setTag(position);

            if (PrefHelper.altByDefault())
                tvAlt.setVisibility(View.VISIBLE);
            tvAlt.setText(PrefHelper.getAlt(sFavorites[position]));

            //fix for issue #2
            pvComic.setOnDoubleTapListener(new GestureDetector.OnDoubleTapListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (pvComic.getScale() < 0.5f * pvComic.getMaximumScale()) {
                        pvComic.setScale(0.5f * pvComic.getMaximumScale(), true);
                    } else if (pvComic.getScale() < pvComic.getMaximumScale()) {
                        pvComic.setScale(pvComic.getMaximumScale(), true);
                    } else {
                        pvComic.setScale(1.0f, true);
                    }
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    if (!PrefHelper.altLongTap()) {
                        if (PrefHelper.classicAltStyle()) {
                            toggleVisibility(tvAlt);
                        } else {
                            android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity());
                            mDialog.setMessage(tvAlt.getText());
                            mDialog.show();
                        }
                    }
                    return false;
                }

                @Override
                public boolean onDoubleTapEvent(MotionEvent e) {
                    if (e.getAction() == MotionEvent.ACTION_UP) {
                        fingerLifted = true;
                    }
                    if (e.getAction() == MotionEvent.ACTION_DOWN) {
                        fingerLifted = false;
                    }
                    return false;
                }
            });

            pvComic.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (fingerLifted && PrefHelper.altLongTap()) {
                        if (PrefHelper.altVibration()) {
                            Vibrator vi = (Vibrator) getActivity().getSystemService(MainActivity.VIBRATOR_SERVICE);
                            vi.vibrate(10);
                        }
                        if (PrefHelper.classicAltStyle()) {
                            toggleVisibility(tvAlt);
                        } else {
                            android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity());
                            mDialog.setMessage(tvAlt.getText());
                            mDialog.show();
                        }
                    }
                    return true;
                }
            });

            TextView tvTitle = (TextView) itemView.findViewById(R.id.tvTitle);
            tvTitle.setText(PrefHelper.getTitle(sFavorites[position]));

            if (PrefHelper.invertColors()) {
                float[] colorMatrix_Negative = {
                        -1.0f, 0, 0, 0, 255, //red
                        0, -1.0f, 0, 0, 255, //green
                        0, 0, -1.0f, 0, 255, //blue
                        0, 0, 0, 1.0f, 0 //alpha
                };
                ColorFilter cf = new ColorMatrixColorFilter(colorMatrix_Negative);
                pvComic.setColorFilter(cf);
            }

            pvComic.setImageBitmap(mComicMap.get(position).getBitmap());
            if (Arrays.binarySearch(mContext.getResources().getIntArray(R.array.large_comics), sFavorites[sFavoriteIndex]) >= 0) {
                pvComic.setMaximumScale(7.0f);
            }
            //Disable ViewPager scrolling when the user zooms into an image
            pvComic.setOnMatrixChangeListener(new PhotoViewAttacher.OnMatrixChangedListener() {
                @Override
                public void onMatrixChanged(RectF rectF) {
                    if (pvComic.getScale() > 1.4) {
                        sPager.setLocked(true);
                    } else {
                        sPager.setLocked(false);
                    }
                }
            });
            container.addView(itemView);
            return itemView;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((RelativeLayout) object);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_alt: {
                return setAltText();
            }
            case R.id.delete_favorites: {
                return deleteAllFavorites();
            }
            case R.id.action_favorite: {
                return modifyFavorites();
            }
            case R.id.action_share: {
                return shareComic();
            }
            case R.id.action_random: {
                return getRandomComic();
            }

            case R.id.action_trans:
                return showTranscript();
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean showTranscript() {
        String trans = mComicMap.get(sFavoriteIndex).getTranscript();
        android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity());
        mDialog.setMessage(trans);
        mDialog.show();
        return true;
    }

    private boolean setAltText() {
        if (PrefHelper.showAltTip()) {
            Toast toast = Toast.makeText(getActivity(), R.string.action_alt_tip, Toast.LENGTH_LONG);
            toast.show();
            PrefHelper.setAltTip(false);
        }
        TextView tvAlt = (TextView) sPager.findViewWithTag(sFavoriteIndex).findViewById(R.id.tvAlt);
        if (PrefHelper.classicAltStyle()) {
            try {
                toggleVisibility(tvAlt);
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        } else {
            android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity());
            mDialog.setMessage(tvAlt.getText());
            mDialog.show();
        }
        return true;
    }

    private boolean deleteAllFavorites() {
        new android.support.v7.app.AlertDialog.Builder(getActivity())
                .setMessage(R.string.delete_favorites_dialog)
                .setPositiveButton(R.string.dialog_yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        final String[] fav = Favorites.getFavoriteList(getActivity());

                        Favorites.putStringInPreferences(getActivity(), null, "favorites");

                        MenuItem mBrowser = ((MainActivity) getActivity()).getNavView().getMenu().findItem(R.id.nav_browser);
                        ((MainActivity) getActivity()).selectDrawerItem(mBrowser);

                        if (!PrefHelper.fullOfflineEnabled())
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
                ((MainActivity) getActivity()).selectDrawerItem(mBrowser);
                return;
            }
            refresh();
        }
    }

    private boolean modifyFavorites() {
        final int mRemoved = sFavorites[sFavoriteIndex];
        final Bitmap mRemovedBitmap = mComicMap.get(sFavoriteIndex).getBitmap();
        final String mAlt = PrefHelper.getAlt(sFavorites[sFavoriteIndex]);
        final String mTitle = PrefHelper.getTitle(sFavorites[sFavoriteIndex]);

        new DeleteImageTask().execute(mRemoved);

        View.OnClickListener oc = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Favorites.addFavoriteItem(getActivity(), String.valueOf(mRemoved));
                try {
                    FileOutputStream fos = getActivity().openFileOutput(String.valueOf(mRemoved), Context.MODE_PRIVATE);
                    mRemovedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                PrefHelper.addTitle(mTitle, mRemoved);
                PrefHelper.addAlt(mAlt, mRemoved);
                refresh();
            }
        };

        Snackbar.make(((MainActivity) getActivity()).getFab(), R.string.snackbar_remove, Snackbar.LENGTH_LONG)
                .setAction(R.string.snackbar_undo, oc)
                .show();
        return true;
    }

    private boolean shareComic() {

        if (PrefHelper.shareImage()) {
            shareComicImage();
            return true;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setItems(R.array.share_dialog, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        shareComicImage();
                        break;
                    case 1:
                        shareComicUrl();
                        break;
                }
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
        return true;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void shareComicImage() {
        if (!(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("image/*");
        Bitmap mBitmap = mComicMap.get(sFavoriteIndex).getBitmap();
        try {
            String path = MediaStore.Images.Media.insertImage(getActivity().getContentResolver(),
                    mBitmap, "Image Description", null);
            share.putExtra(Intent.EXTRA_STREAM, Uri.parse(path));
        } catch (Exception e) {
            try {
                File cachePath = new File(getActivity().getCacheDir(), "images");
                cachePath.mkdirs();
                FileOutputStream stream = new FileOutputStream(cachePath + "/image.png");
                mBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                stream.close();
                File imagePath = new File(getActivity().getCacheDir(), "images");
                File newFile = new File(imagePath, "image.png");
                Uri contentUri = FileProvider.getUriForFile(getActivity(), "com.tap.easy_xkcd.fileprovider", newFile);
                share.putExtra(Intent.EXTRA_STREAM, contentUri);
            } catch (IOException e2) {
                e.printStackTrace();
            }
        }
        if (PrefHelper.shareAlt())
            share.putExtra(Intent.EXTRA_TEXT, PrefHelper.getAlt(sFavorites[sFavoriteIndex]));
        startActivity(Intent.createChooser(share, this.getResources().getString(R.string.share_image)));
    }

    private void shareComicUrl() {
        Intent share = new Intent(android.content.Intent.ACTION_SEND);
        share.setType("text/plain");

        share.putExtra(Intent.EXTRA_SUBJECT, PrefHelper.getTitle(sFavorites[sFavoriteIndex]));
        if (PrefHelper.shareMobile()) {
            share.putExtra(Intent.EXTRA_TEXT, "http://m.xkcd.com/" + String.valueOf(sFavorites[sFavoriteIndex]));
        } else {
            share.putExtra(Intent.EXTRA_TEXT, "http://xkcd.com/" + String.valueOf(sFavorites[sFavoriteIndex]));
        }
        startActivity(Intent.createChooser(share, this.getResources().getString(R.string.share_url)));
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_latest).setVisible(false);
        menu.findItem(R.id.action_search).setVisible(false);
        menu.findItem(R.id.action_overview).setVisible(false);
        MenuItem fav = menu.findItem(R.id.action_favorite);
        fav.setIcon(R.drawable.ic_action_favorite);
        //If the FAB is visible, hide the random comic menu item
        if (((MainActivity) getActivity()).getFab().getVisibility() == View.GONE) {
            menu.findItem(R.id.action_random).setVisible(true);
        } else {
            menu.findItem(R.id.action_random).setVisible(false);
        }
        super.onPrepareOptionsMenu(menu);
    }

    public class pagerUpdate extends AsyncTask<Integer, Integer, Void> {

        @Override
        protected Void doInBackground(Integer... pos) {
            sFavorites = new int[mFav.length];

            for (int i = 0; i < sFavorites.length; i++) {
                sFavorites[i] = Integer.parseInt(mFav[i]);
                mComicMap.put(i, new OfflineComic(sFavorites[i], getActivity()));
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            sPager.setAdapter(mPagerAdapter);
            mPagerAdapter.notifyDataSetChanged();
            sPager.setCurrentItem(sFavoriteIndex);

            Toolbar toolbar = ((MainActivity)getActivity()).getToolbar();
            if (PrefHelper.subtitleEnabled() && ((MainActivity) getActivity()).getCurrentFragment() == R.id.nav_favorites)
                toolbar.setSubtitle(String.valueOf(sFavorites[sFavoriteIndex]));

            if (toolbar.getAlpha()==0) {
                toolbar.setTranslationY(-300);
                toolbar.animate().setDuration(300).translationY(0).alpha(1);
                View view;
                for (int i = 0; i<toolbar.getChildCount(); i++) {
                    view = toolbar.getChildAt(i);
                    view.setTranslationY(-300);
                    view.animate().setStartDelay(50*(i+1)).setDuration(70*(i+1)).translationY(0);
                }
            }
        }

    }

    public static boolean zoomReset() {
        PhotoView pv = (PhotoView) sPager.findViewWithTag(sFavoriteIndex).findViewById(R.id.ivComic);
        float scale = pv.getScale();
        if (scale != 1f) {
            pv.setScale(1f, true);
            return true;
        } else {
            return false;
        }
    }

    public boolean getRandomComic() {
        //get a random number and update the pager
        if (mFav.length > 1) {
            Random rand = new Random();
            Integer number = rand.nextInt(mFav.length);
            while (number.equals(sFavoriteIndex)) {
                number = rand.nextInt(mFav.length);
            }
            sPager.setCurrentItem(number);
        }
        return true;
    }

    public void refresh() {
        //Updates favorite list, pager and alt TextView
        mFav = Favorites.getFavoriteList(this.getActivity());
        mComicMap.clear();
        if (mFav.length != 0) {
            if (sFavoriteIndex == mFav.length) {
                sFavoriteIndex--;
            }
            new pagerUpdate().execute();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        if (sFavoriteIndex != null)
            savedInstanceState.putInt(LAST_FAV, sFavoriteIndex);

        super.onSaveInstanceState(savedInstanceState);
    }

    private void toggleVisibility(View view) {
        // Switches a view's visibility between GONE and VISIBLE
        if (view.getVisibility() == View.GONE) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

}
