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

package de.tap.easy_xkcd;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.FileProvider;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.util.Log;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;


public class FavoritesFragment extends android.support.v4.app.Fragment {

    public SparseArray<Bitmap> mComicMap = new SparseArray<>();
    static final String LAST_FAV = "last fav";
    public static Integer sFavoriteIndex = 0;
    private HackyViewPager mPager = null;
    private FavoritesPagerAdapter mPagerAdapter = null;
    private String[] mFav;
    public static int[] sFavorites;
    //private SharedPreferences mSharedPreferences;
    private TextView tvAlt;
    private ActionBar mActionBar;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, final Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.pager_layout, container, false);
        //Get Favorites &&
        mFav = Favorites.getFavoriteList(this.getActivity());
        //mSharedPreferences = getActivity().getPreferences(Activity.MODE_PRIVATE);
        setHasOptionsMenu(true);
        if (MainActivity.sProgress != null) {
            MainActivity.sProgress.dismiss();
            MainActivity.sProgress = null;
        }
        mActionBar = ((MainActivity) getActivity()).getSupportActionBar();
        assert mActionBar != null;

        //Setup ViewPager, alt TextView
        tvAlt = (TextView) getActivity().findViewById(R.id.tvAlt);
        mPagerAdapter = new FavoritesPagerAdapter(getActivity());
        mPager = (HackyViewPager) v.findViewById(R.id.pager);
        setupPager(mPager);
        if (savedInstanceState != null) {
            sFavoriteIndex = savedInstanceState.getInt(LAST_FAV);
            getActivity().invalidateOptionsMenu();
        }
        //Update the pager
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
                    tvAlt.setVisibility(View.GONE);
                    //Update the ActionBar Subtitle
                    if (PrefHelper.subtitleEnabled() && MainActivity.sCurrentFragment == R.id.nav_favorites) {
                        mActionBar.setSubtitle(String.valueOf(sFavorites[position]));
                    }
                    //This updates the favorite icon
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
            itemView.setTag(position);

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


            //Setup alt text and LongClickListener
            pvComic.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (fingerLifted) {
                        if (PrefHelper.altVibration()) {
                            Vibrator vi = (Vibrator) getActivity().getSystemService(MainActivity.VIBRATOR_SERVICE);
                            vi.vibrate(10);
                        }
                        //tvAlt.setText(mSharedPreferences.getString(("alt" + String.valueOf(sFavorites[sFavoriteIndex])), ""));
                        tvAlt.setText(PrefHelper.getAlt(sFavorites[sFavoriteIndex]));
                        toggleVisibility(tvAlt);
                    }
                    return true;
                }
            });
            //setup the title text view
            TextView tvTitle = (TextView) itemView.findViewById(R.id.tvTitle);
            //tvTitle.setText(mSharedPreferences.getString(("title" + String.valueOf(sFavorites[position])), ""));
            tvTitle.setText(PrefHelper.getTitle(sFavorites[position]));
            //load the image
            pvComic.setImageBitmap(mComicMap.get(position));
            if (Arrays.binarySearch(mContext.getResources().getIntArray(R.array.large_comics), sFavorites[sFavoriteIndex]) >= 0) {
                pvComic.setMaximumScale(7.0f);
            }
            //Disable ViewPager scrolling when the user zooms into an image
            pvComic.setOnMatrixChangeListener(new PhotoViewAttacher.OnMatrixChangedListener() {
                @Override
                public void onMatrixChanged(RectF rectF) {
                    if (pvComic.getScale() > 1.4) {
                        mPager.setLocked(true);
                    } else {
                        mPager.setLocked(false);
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
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean setAltText() {
        //If the user selected the menu item for the first time, show the toast
        if (PrefHelper.showAltTip()) {
            Toast toast = Toast.makeText(getActivity(), R.string.action_alt_tip, Toast.LENGTH_LONG);
            toast.show();
            /*SharedPreferences.Editor mEditor = mSharedPreferences.edit();
            mEditor.putBoolean("alt_tip", false);
            mEditor.apply();*/
            PrefHelper.setAltTip(false);
        }
        //tvAlt.setText(mSharedPreferences.getString(("alt" + String.valueOf(sFavorites[sFavoriteIndex])), ""));
        tvAlt.setText(PrefHelper.getAlt(sFavorites[sFavoriteIndex]));
        toggleVisibility(tvAlt);
        return true;
    }

    private boolean deleteAllFavorites() {
        new android.support.v7.app.AlertDialog.Builder(getActivity())
                .setMessage(R.string.delete_favorites_dialog)
                .setPositiveButton(R.string.dialog_yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        final String[] fav = Favorites.getFavoriteList(getActivity());

                        //Clear favorites
                        Favorites.putStringInPreferences(getActivity(), null, "favorites");

                        //Show ComicBrowserFragment
                        MenuItem mBrowser = MainActivity.sNavView.getMenu().findItem(R.id.nav_browser);
                        ((MainActivity) getActivity()).selectDrawerItem(mBrowser);

                        //Delete all saved images
                        if (!((MainActivity) getActivity()).fullOffline) {
                            for (String i : fav) {
                                getActivity().deleteFile(i);
                            }
                        }

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
            if (!MainActivity.fullOffline) {
                getActivity().deleteFile(String.valueOf(pos[0]));
            }
            Favorites.removeFavoriteItem(getActivity(), String.valueOf(pos[0]));
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            String[] fav = Favorites.getFavoriteList(getActivity());
            if (fav.length == 0) {
                //If there are no favorites left, show ComicBrowserFragment
                MenuItem mBrowser = MainActivity.sNavView.getMenu().findItem(R.id.nav_browser);
                ((MainActivity) getActivity()).selectDrawerItem(mBrowser);
                return;
            }
            refresh();
        }
    }

    private boolean modifyFavorites() {
        final int mRemoved = sFavorites[sFavoriteIndex];
        final Bitmap mRemovedBitmap = mComicMap.get(sFavoriteIndex);
        //final String mAlt = mSharedPreferences.getString(("alt" + String.valueOf(sFavorites[sFavoriteIndex])), "");
        //final String mTitle = mSharedPreferences.getString(("title" + String.valueOf(sFavorites[sFavoriteIndex])), "");

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
                /*SharedPreferences sharedPreferences = getActivity().getPreferences(Activity.MODE_PRIVATE);
                SharedPreferences.Editor mEditor = sharedPreferences.edit();

                mEditor.putString(("title" + String.valueOf(mRemoved)), mTitle);
                mEditor.putString(("alt" + String.valueOf(mRemoved)), mAlt);
                mEditor.apply();*/

                PrefHelper.addTitle(mTitle, mRemoved);
                PrefHelper.addAlt(mAlt, mRemoved);

                refresh();
            }
        };

        FloatingActionButton fab = (FloatingActionButton) getActivity().findViewById(R.id.fab);
        Snackbar.make(fab, R.string.snackbar_remove, Snackbar.LENGTH_LONG)
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

    private void shareComicImage() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("image/*");
        Bitmap mBitmap = mComicMap.get(sFavoriteIndex);
        try {
            String path = MediaStore.Images.Media.insertImage(getActivity().getContentResolver(),
                    mBitmap, "Image Description", null);
            share.putExtra(Intent.EXTRA_STREAM, Uri.parse(path));
        } catch (Exception e) {
            try {
                File cachePath = new File(getActivity().getCacheDir(), "images");
                cachePath.mkdirs(); // don't forget to make the directory
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
        startActivity(Intent.createChooser(share, this.getResources().getString(R.string.share_image)));
    }

    private void shareComicUrl() {
        Intent share = new Intent(android.content.Intent.ACTION_SEND);
        share.setType("text/plain");

        //share.putExtra(Intent.EXTRA_SUBJECT, mSharedPreferences.getString(("title" + String.valueOf(sFavorites[sFavoriteIndex])), ""));
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
        //setup the favorites icon

        menu.findItem(R.id.action_latest).setVisible(false);
        menu.findItem(R.id.action_search).setVisible(false);
        MenuItem fav = menu.findItem(R.id.action_favorite);
        fav.setIcon(R.drawable.ic_action_favorite);

        //If the FAB is visible, hide the random comic menu item
        if (((MainActivity) getActivity()).mFab.getVisibility() == View.GONE) {
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
                try {
                    FileInputStream fis = getActivity().getApplicationContext().openFileInput(mFav[i]);
                    Bitmap mBitmap = BitmapFactory.decodeStream(fis);
                    fis.close();
                    mComicMap.put(i, mBitmap);
                } catch (Exception e) {
                    Log.e("Error", "Image not found, looking in external storage");
                    try {
                        File sdCard = Environment.getExternalStorageDirectory();
                        File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                        File file = new File(dir, mFav[i] + ".png");
                        FileInputStream fis = new FileInputStream(file);
                        Bitmap mBitmap = BitmapFactory.decodeStream(fis);
                        fis.close();
                        mComicMap.put(i, mBitmap);
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            mPager.setAdapter(mPagerAdapter);
            mPagerAdapter.notifyDataSetChanged();
            mPager.setCurrentItem(sFavoriteIndex);

            if (PrefHelper.subtitleEnabled() && MainActivity.sCurrentFragment == R.id.nav_favorites) {
                mActionBar.setSubtitle(String.valueOf(sFavorites[sFavoriteIndex]));
            }
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
            mPager.setCurrentItem(number);
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
        //tvAlt.setText(mSharedPreferences.getString(("title" + String.valueOf(sFavorites[sFavoriteIndex])), ""));
        tvAlt.setText(PrefHelper.getAlt(sFavorites[sFavoriteIndex]));
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        if (sFavoriteIndex != null) {
            savedInstanceState.putInt(LAST_FAV, sFavoriteIndex);
        }

        // Always call the superclass so it can save the view hierarchy state
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
