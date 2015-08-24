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
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.support.design.widget.Snackbar;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Random;

import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;

public class OfflineFragment extends android.support.v4.app.Fragment {

    public static int sLastComicNumber = 0;
    public static OfflineComic sLoadedComic;
    public static int sNewestComicNumber = 0;
    public static SparseArray<OfflineComic> sComicMap = new SparseArray<>();
    private OfflineComic[] sComics;
    private HackyViewPager sPager;
    private OfflineBrowserPagerAdapter sPagerAdapter;
    private ActionBar mActionBar;
    private Boolean randomSelected=false;
    private int pagerState;
    private Boolean savedInstance=false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.pager_layout, container, false);
        setHasOptionsMenu(true);

        if (MainActivity.sProgress!=null) {
            MainActivity.sProgress.dismiss();
        }

        if (savedInstanceState != null) {
            sLastComicNumber = savedInstanceState.getInt("Last Comic");
            savedInstance=true;
        } else if (sLastComicNumber==0) {
            sLastComicNumber = PrefHelper.getLastComic();
        }

        mActionBar = ((MainActivity) getActivity()).getSupportActionBar();
        assert mActionBar != null;

        //Setup ViewPager, alt TextView
        sPagerAdapter = new OfflineBrowserPagerAdapter(getActivity());
        sPager = (HackyViewPager) v.findViewById(R.id.pager);
        setupPager(sPager);
        /*tvAlt = (TextView) getActivity().findViewById(R.id.tvAlt);
        tvAlt.setVisibility(View.GONE);*/
        //Update the pager
        new updateImages().execute();

        return v;
    }

    private void setupPager(ViewPager pager) {
        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            private int state;
            @Override
            public void onPageSelected(int position) {
                //tvAlt.setVisibility(View.GONE);
                try {
                    //This updates the favorite icon
                    getActivity().invalidateOptionsMenu();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
                //Update ViewPager items
                switch (position) {
                    case 0: {
                        sLastComicNumber--;
                        new pagerUpdate().execute(sLastComicNumber);
                        break;
                    }
                    case 1: {
                        if (sLastComicNumber == 1) {
                            //This is only true if the user scrolled to from comic 1 to comic 2
                            sLastComicNumber++;
                            new pagerUpdate().execute(2);
                        }
                        break;
                    }
                    case 2: {
                        sLastComicNumber++;
                        new pagerUpdate().execute(sLastComicNumber);
                        break;
                    }
                }
                //Update ActionBar Subtitle
                if (PrefHelper.subtitleEnabled()) {
                    mActionBar.setSubtitle(String.valueOf(sLastComicNumber));
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                pagerState=state;
            }

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }
        });
    }

    public class pagerUpdate extends AsyncTask<Integer, Void, Void> {
        @Override
        protected Void doInBackground(Integer... pos) {
            PrefHelper.setLastComic(sLastComicNumber);

            //sNewestComicNumber = mSharedPreferences.getInt("highest_offline",0);
            sNewestComicNumber = PrefHelper.getHighestOffline();
            if (sLastComicNumber==0) {
                sLastComicNumber = sNewestComicNumber;
                pos[0] = sNewestComicNumber;
            }
            //Update comic array
            sComics = GetComic(pos[0]);
            if (sLastComicNumber!=1&&sLastComicNumber!=2&&sLastComicNumber!=sNewestComicNumber&&sLastComicNumber!=sNewestComicNumber-1) {
                while (pagerState == ViewPager.SCROLL_STATE_SETTLING) {
                    //wait for view pager to finish animation
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            //Setup pager
            sPager.setAdapter(sPagerAdapter);
            sPagerAdapter.notifyDataSetChanged();

            if (sLastComicNumber != 1) {
                sPager.setCurrentItem(1, true);
            } else {
                //this only true if the user reached comic 1
                sPager.setCurrentItem(0, true);
            }
            if (MainActivity.sProgress!=null) {
                MainActivity.sProgress.dismiss();
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
    }

    private OfflineComic[] GetComic(int number) {
        OfflineComic newComic = sComicMap.get(number);
        OfflineComic newComic0 = sComicMap.get(number - 1);
        OfflineComic newComic2 = sComicMap.get(number + 1);

        //Create new Comic objects if they are not already in our ComicMap
        if (newComic == null) {
            newComic = new OfflineComic(number, getActivity());
            sComicMap.put(number, newComic);
        }
        if (newComic0 == null) {
            newComic0 = new OfflineComic(number - 1, getActivity());
            sComicMap.put(number - 1, newComic0);
        }
        if (newComic2 == null) {
            newComic2 = new OfflineComic(number + 1, getActivity());
            sComicMap.put(number + 1, newComic2);
        }
        sLoadedComic = newComic;

        //Return the comics depending on the View Pager's current position
        if (number == 1) { //this is only true when the user has reached comic 1
            OfflineComic[] result = new OfflineComic[2];
            result[0] = newComic;
            result[1] = newComic2;
            return result;
        }
        if (number != sNewestComicNumber) {
            OfflineComic[] result = new OfflineComic[3];
            result[0] = newComic0;
            result[1] = newComic;
            result[2] = newComic2;
            return result;
        } else { //this is true when the user has reached latest comic
            OfflineComic[] result = new OfflineComic[2];
            result[0] = newComic0;
            result[1] = newComic;
            return result;
        }

    }

    private class OfflineBrowserPagerAdapter extends PagerAdapter {
        Context mContext;
        LayoutInflater mLayoutInflater;
        Boolean fingerLifted = true;

        public OfflineBrowserPagerAdapter(Context context) {
            mContext = context;
            mLayoutInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return sComics.length;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public Object instantiateItem(ViewGroup container, final int position) {
            final View itemView = mLayoutInflater.inflate(R.layout.pager_item, container, false);
            final PhotoView pvComic = (PhotoView) itemView.findViewById(R.id.ivComic);
            itemView.setTag(position);

            final TextView tvAlt = (TextView) itemView.findViewById(R.id.tvAlt);
            if (PrefHelper.altByDefault()) {
                tvAlt.setVisibility(View.VISIBLE);
            }
            tvAlt.setText(sComicMap.get(sLastComicNumber - 1 + position).getComicData()[1]);

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
                    if (e.getAction()==MotionEvent.ACTION_UP) {
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
                        //tvAlt.setText(sComicMap.get(sLastComicNumber).getComicData()[1]);
                        toggleVisibility(tvAlt);
                    }
                    return true;
                }
            });
            //Setup the title text view
            TextView tvTitle = (TextView) itemView.findViewById(R.id.tvTitle);
            tvTitle.setText(sComics[position].getComicData()[0]);
            //load the image
            pvComic.setImageBitmap(sComics[position].getBitmap());
            if (Arrays.binarySearch(mContext.getResources().getIntArray(R.array.large_comics), sLastComicNumber) >= 0) {
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

            if(randomSelected && position==1) {
                Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), android.R.anim.fade_in);
                itemView.setAnimation(animation);
                randomSelected=false;
            }

            if (position==1) {
                Toolbar toolbar = ((MainActivity)getActivity()).toolbar;
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
            case R.id.action_favorite:
                return ModifyFavorites(item);

            case R.id.delete_favorites:
                return deleteAllFavorites();

            case R.id.action_share:
                return shareComic();

            case R.id.action_alt:
                return setAltText();

            case R.id.action_latest:
                return getLatestComic();
                /*PrefHelper.setNewestComic(1564);
                PrefHelper.setHighestOffline(1564);
                File sdCard = Environment.getExternalStorageDirectory();
                File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                File file = new File(dir, String.valueOf(1564) + ".png");
                file.delete();
                PrefHelper.addAlt("", 1565);
                PrefHelper.addTitle("", 1565);
                return true;*/

            case R.id.action_random:
                return getRandomComic();

            case R.id.action_explain:
                return explainComic(sLastComicNumber);

        }
        return super.onOptionsItemSelected(item);
    }

    private boolean explainComic(int number) {
        String url = "http://explainxkcd.com/" + String.valueOf(number);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);
        return true;
    }

    private boolean getLatestComic() {
        MainActivity.sProgress = ProgressDialog.show(getActivity(), "", this.getResources().getString(R.string.loading_latest), true);
        sLastComicNumber = sNewestComicNumber;
        new pagerUpdate().execute(sNewestComicNumber);
        return true;
    }

    private boolean setAltText() {
        //If the user selected the menu item for the first time, show the toast
        if (PrefHelper.showAltTip()) {
            Toast toast = Toast.makeText(getActivity(), R.string.action_alt_tip, Toast.LENGTH_LONG);
            toast.show();
            PrefHelper.setAltTip(false);
        }
        //Show alt text
        TextView tvAlt = (TextView) sPager.getChildAt(1).findViewById(R.id.tvAlt);
        tvAlt.setText(sComicMap.get(sLastComicNumber).getComicData()[1]);
        toggleVisibility(tvAlt);
        return true;
    }

    private boolean shareComic() {
        if (PrefHelper.shareImage()) {
            shareComicImage();
            return true;
        }

        //Show the alert dialog to choose between sharing image or url
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

    private void shareComicUrl() {
        //shares the comics url along with its title
        Intent share = new Intent(android.content.Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, sLoadedComic.getComicData()[0]);
        if (PrefHelper.shareMobile()) {
            share.putExtra(Intent.EXTRA_TEXT, "http://m.xkcd.com/" + String.valueOf(sLoadedComic.getComicNumber()));
        } else {
            share.putExtra(Intent.EXTRA_TEXT, "http://xkcd.com/" + String.valueOf(sLoadedComic.getComicNumber()));
        }
        startActivity(Intent.createChooser(share, this.getResources().getString(R.string.share_url)));
    }

    private void shareComicImage() {
        //shares the comic's image along with its title
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("image/*");
        share.putExtra(Intent.EXTRA_STREAM, getURI());
        share.putExtra(Intent.EXTRA_SUBJECT, sLoadedComic.getComicData()[0]);
        startActivity(Intent.createChooser(share, this.getResources().getString(R.string.share_image)));
    }

    private Uri getURI() {
        //Gets the URI of the currently loaded image
        View v = sPager.findViewWithTag(1);
        ImageView siv = (ImageView) v.findViewById(R.id.ivComic);
        Drawable mDrawable = siv.getDrawable();
        Bitmap mBitmap = Bitmap.createBitmap(mDrawable.getIntrinsicWidth(),
                mDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(mBitmap);
        mDrawable.draw(canvas);
        String path = MediaStore.Images.Media.insertImage(getActivity().getContentResolver(),
                mBitmap, "Image Description", null);
        return Uri.parse(path);
    }

    public boolean getRandomComic() {
        if (sNewestComicNumber != 0) {
            //get a random number and update the pager
            Random mRand = new Random();
            final Integer mNumber = mRand.nextInt(sNewestComicNumber) + 1;
            sLastComicNumber = mNumber;
            randomSelected=true;
            new pagerUpdate().execute(mNumber);
        }
        return true;
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        //Update the favorites icon
        MenuItem fav = menu.findItem(R.id.action_favorite);
        if (Favorites.checkFavorite(getActivity(), sLastComicNumber)) {
            fav.setIcon(R.drawable.ic_action_favorite);
        } else {
            fav.setIcon(R.drawable.ic_favorite_outline);
        }
        //If the FAB is visible, hide the random comic menu item
        if (((MainActivity) getActivity()).mFab.getVisibility() == View.GONE) {
            menu.findItem(R.id.action_random).setVisible(true);
        } else {
            menu.findItem(R.id.action_random).setVisible(false);
        }
        super.onPrepareOptionsMenu(menu);
    }

    private boolean ModifyFavorites(MenuItem item) {
        if (Favorites.checkFavorite(getActivity(), sLastComicNumber)) {
            //Delete the image
            new DeleteComicImageTask().execute();
            //update the favorites icon
            item.setIcon(R.drawable.ic_favorite_outline);
            return true;
        } else {
            //save image to internal storage
            new SaveComicImageTask().execute();
            //update the favorites icon
            item.setIcon(R.drawable.ic_action_favorite);
            return true;
        }
    }

    private class DeleteComicImageTask extends AsyncTask<Integer, Void, Void> {
        private int mRemovedNumber = sLastComicNumber;
        private View.OnClickListener oc;

        @Override
        protected Void doInBackground(Integer... pos) {
            //remove the number from the favorites list
            Favorites.removeFavoriteItem(getActivity(), String.valueOf(mRemovedNumber));

            //Setup the listener for the snackbar
            oc = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new SaveComicImageTask().execute();
                }
            };
            //attach listener and FAB to snackbar
            Snackbar.make(((MainActivity) getActivity()).mFab, R.string.snackbar_remove, Snackbar.LENGTH_LONG)
                    .setAction(R.string.snackbar_undo, oc)
                    .show();
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            //refresh the favorites fragment
            FavoritesFragment f = (FavoritesFragment) getActivity().getSupportFragmentManager().findFragmentByTag("favorites");
            if (f != null) {
                f.refresh();
            }
        }

    }

    private class SaveComicImageTask extends AsyncTask<Void, Void, Void> {
        private int mAddedNumber = sLastComicNumber;

        @Override
        protected Void doInBackground(Void... params) {
            //Add the comics number to the favorite list
            Favorites.addFavoriteItem(getActivity(), String.valueOf(mAddedNumber));
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            //refresh the FavoritesFragment
            FavoritesFragment f = (FavoritesFragment) getActivity().getSupportFragmentManager().findFragmentByTag("favorites");
            if (f != null) {
                f.refresh();
            }

            //Sometimes the floating action button does not animate back to the bottom when the snackbar is dismissed, so force it to its original position
            ((MainActivity) getActivity()).mFab.forceLayout();
        }
    }

    private boolean deleteAllFavorites() {
        new android.support.v7.app.AlertDialog.Builder(getActivity())
                .setMessage(R.string.delete_favorites_dialog)
                .setPositiveButton(R.string.dialog_yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Favorites.putStringInPreferences(getActivity(), null, "favorites");
                        //Remove the FavoritesFragment
                        android.support.v4.app.FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                        FavoritesFragment f = (FavoritesFragment) fragmentManager.findFragmentByTag("favorites");
                        if (f != null) {
                            fragmentManager.beginTransaction().remove(f).commit();
                        }
                        getActivity().invalidateOptionsMenu();
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

    public class updateImages extends AsyncTask<Void, Void, Void> {
        private ProgressDialog progress;
        @Override
        protected void onPreExecute() {
            if (!savedInstance) {
                progress = new ProgressDialog(getActivity());
                progress.setTitle(getResources().getString(R.string.loading_comics));
                progress.setCancelable(false);
                progress.show();
                savedInstance=false;
            }
        }
        @Override
        protected Void doInBackground(Void... pos) {
            if (isOnline()&&!savedInstance) {
                try {
                    sNewestComicNumber = new Comic(0).getComicNumber();
                    Log.d("test", String.valueOf(sNewestComicNumber)+" "+String.valueOf(PrefHelper.getHighestOffline()));
                    if (sNewestComicNumber > PrefHelper.getHighestOffline()) {
                        for (int i = PrefHelper.getHighestOffline(); i <= sNewestComicNumber; i++) {
                            Log.d("comic added", String.valueOf(i));
                            Comic comic = new Comic(i,getActivity());
                            String url = comic.getComicData()[2];
                            Bitmap mBitmap = Glide.with(getActivity())
                                    .load(url)
                                    .asBitmap()
                                    .into(-1, -1)
                                    .get();
                            try {
                                File sdCard = Environment.getExternalStorageDirectory();
                                File dir = new File (sdCard.getAbsolutePath() + "/easy xkcd");
                                dir.mkdirs();
                                File file = new File(dir, String.valueOf(i)+".png");
                                FileOutputStream fos = new FileOutputStream(file);
                                mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                                fos.flush();
                                fos.close();
                            } catch (Exception e) {
                                Log.e("Error", "Saving to external storage failed");
                                try {
                                    FileOutputStream fos = getActivity().openFileOutput(String.valueOf(i), Context.MODE_PRIVATE);
                                    mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                                    fos.close();
                                } catch (Exception e2) {
                                    e2.printStackTrace();
                                }
                            }PrefHelper.addTitle(comic.getComicData()[0], i);
                            PrefHelper.addAlt(comic.getComicData()[1], i);
                            PrefHelper.setHighestOffline(sNewestComicNumber);
                            PrefHelper.setNewestComic(i);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else {
                sNewestComicNumber = PrefHelper.getHighestOffline();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            if (!savedInstance) {
                progress.dismiss();
            }
            new pagerUpdate().execute(sLastComicNumber);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putInt("Last Comic", sLastComicNumber);
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

    private boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

}
