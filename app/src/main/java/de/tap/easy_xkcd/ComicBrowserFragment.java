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
import android.content.ActivityNotFoundException;
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
import android.support.customtabs.CustomTabsIntent;
import android.support.design.widget.Snackbar;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.tap.xkcd_reader.R;

import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import de.tap.easy_xkcd.CustomTabHelpers.BrowserFallback;
import de.tap.easy_xkcd.CustomTabHelpers.CustomTabActivityHelper;
import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;

public class ComicBrowserFragment extends android.support.v4.app.Fragment {

    public static int sLastComicNumber = 0;
    public static Comic sLoadedComic;
    public static int sNewestComicNumber = 0;
    public static SparseArray<Comic> sComicMap = new SparseArray<>();
    private Comic[] sComics;
    private HackyViewPager sPager;
    private ComicBrowserPagerAdapter sPagerAdapter;
    private ActionBar mActionBar;
    private int pagerState;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.pager_layout, container, false);
        setHasOptionsMenu(true);

        if (savedInstanceState != null) {
            sLastComicNumber = savedInstanceState.getInt("Last Comic");
        } else if (sLastComicNumber == 0) {
            //sLastComicNumber = mSharedPreferences.getInt("Last Comic", 0);
            sLastComicNumber = PrefHelper.getLastComic();
        }

        mActionBar = ((MainActivity) getActivity()).getSupportActionBar();
        assert mActionBar != null;

        //Setup ViewPager, alt TextView
        sPagerAdapter = new ComicBrowserPagerAdapter(getActivity());
        sPager = (HackyViewPager) v.findViewById(R.id.pager);
        setupPager(sPager);
        /*tvAlt = (TextView) getActivity().findViewById(R.id.tvAlt);
        tvAlt.setVisibility(View.GONE);*/
        //Update the pager
        new pagerUpdate().execute(sLastComicNumber);

        return v;
    }

    private void setupPager(final ViewPager pager) {
        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                //tvAlt.setVisibility(View.GONE);
                try {
                    //This updates the favorite icon
                    getActivity().invalidateOptionsMenu();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }

                if (!isOnline()) { //Don't update if the device is not online
                    Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
                    return;
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
                pagerState = state;
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
            //Get the most recent comic if the app is started for the first time
            if (sNewestComicNumber == 0) {
                try {
                    JSONObject json = JsonParser.getJSONFromUrl("http://xkcd.com/info.0.json");
                    sNewestComicNumber = Integer.parseInt(json.getString("num"));
                    if (sLastComicNumber == 0) {
                        sLastComicNumber = sNewestComicNumber;
                        pos[0] = sNewestComicNumber;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            PrefHelper.setNewestComic(sNewestComicNumber);

            //Update comic array
            try {
                sComics = GetComic(pos[0]);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (sLastComicNumber != 1 && sLastComicNumber != 2 && sLastComicNumber != sNewestComicNumber && sLastComicNumber != sNewestComicNumber - 1) {
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
        }
    }

    private class ComicBrowserPagerAdapter extends PagerAdapter {
        Context mContext;
        LayoutInflater mLayoutInflater;
        Boolean fingerLifted = true;

        public ComicBrowserPagerAdapter(Context context) {
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
            View itemView = mLayoutInflater.inflate(R.layout.pager_item, container, false);
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
                    if (sLastComicNumber - 1 + position == 1572) {
                        String url = "https://docs.google.com/forms/d/1e8htNa3bn5OZIgv83dodjZAHcQ424pgQPcFqWz2xSG4/viewform?c=0&w=1";
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(url));
                        startActivity(intent);
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
            //Setup the title text view
            TextView tvTitle = (TextView) itemView.findViewById(R.id.tvTitle);
            tvTitle.setText(sComics[position].getComicData()[0]);
            //load the image
            Glide.with(mContext)
                    .load(sComics[position].getComicData()[2])
                    .asBitmap()
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                            //dismiss the progress dialog if the last image finished loading
                            if (position == 1 && MainActivity.sProgress != null) {
                                MainActivity.sProgress.dismiss();
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
                            pvComic.setImageBitmap(resource);
                            if (position == sPagerAdapter.getCount()-1) {
                                switch (Integer.parseInt(PrefHelper.getOrientation())) {
                                    case 1: getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
                                        break;
                                    case 2: getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                                        break;
                                    case 3: getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                                        break;
                                }
                            }
                        }
                    });
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

            case R.id.action_random:
                return getRandomComic();

            case R.id.action_explain:
                return explainComic(sLastComicNumber);

        }
        return super.onOptionsItemSelected(item);
    }

    private boolean explainComic(int number) {
        String url = "http://explainxkcd.com/" + String.valueOf(number);
        /*Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);*/
        CustomTabsIntent.Builder intentBuilder = new CustomTabsIntent.Builder();
        TypedValue typedValue = new TypedValue();
        getActivity().getTheme().resolveAttribute(R.attr.colorPrimary, typedValue, true);
        intentBuilder.setToolbarColor(typedValue.data);
        CustomTabActivityHelper.openCustomTab(getActivity(), intentBuilder.build(), Uri.parse(url), new BrowserFallback());
        return true;
    }

    private Comic[] GetComic(Integer number) throws IOException {
        //try to get the Comics from sComicMap in order to save bandwidth & memory
        Comic newComic = sComicMap.get(number);
        Comic newComic0 = sComicMap.get(number - 1);
        Comic newComic2 = sComicMap.get(number + 1);

        //Create new Comic objects if they are not already in our ComicMap
        if (newComic == null) {
            newComic = new Comic(number, getActivity());
            sComicMap.put(number, newComic);
        }
        if (newComic0 == null) {
            newComic0 = new Comic(number - 1, getActivity());
            sComicMap.put(number - 1, newComic0);
        }
        if (newComic2 == null) {
            newComic2 = new Comic(number + 1, getActivity());
            sComicMap.put(number + 1, newComic2);
        }
        sLoadedComic = newComic;

        //Return the comics depending on the View Pager's current position
        if (number == 1) { //this is only true when the user has reached comic 1
            Comic[] result = new Comic[2];
            result[0] = newComic;
            result[1] = newComic2;
            return result;
        }
        if (number != sNewestComicNumber) {
            Comic[] result = new Comic[3];
            result[0] = newComic0;
            result[1] = newComic;
            result[2] = newComic2;
            return result;
        } else { //this is true when the user has reached latest comic
            Comic[] result = new Comic[2];
            result[0] = newComic0;
            result[1] = newComic;
            return result;
        }
    }

    private boolean getLatestComic() {
        if (!isOnline()) {
            MainActivity.sProgress.dismiss();
            Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
            return true;
        }
        //update pager
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
        if (PrefHelper.shareAlt()) {
            share.putExtra(Intent.EXTRA_TEXT, sLoadedComic.getComicData()[1]);
        }
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

    private boolean deleteAllFavorites() {
        new android.support.v7.app.AlertDialog.Builder(getActivity())
                .setMessage(R.string.delete_favorites_dialog)
                .setPositiveButton(R.string.dialog_yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        final String[] fav = Favorites.getFavoriteList(getActivity());
                        Favorites.putStringInPreferences(getActivity(), null, "favorites");
                        //Remove the FavoritesFragment
                        android.support.v4.app.FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                        FavoritesFragment f = (FavoritesFragment) fragmentManager.findFragmentByTag("favorites");
                        if (f != null) {
                            fragmentManager.beginTransaction().remove(f).commit();
                        }
                        getActivity().invalidateOptionsMenu();
                        //Delete all saved images
                        for (String i : fav) {
                            getActivity().deleteFile(i);
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

    private boolean ModifyFavorites(MenuItem item) {
        if (!isOnline()) {
            Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
            return true;
        }
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

    private class SaveComicImageTask extends AsyncTask<Void, Void, Void> {
        private Bitmap mBitmap;
        private int mAddedNumber = sLastComicNumber;
        private Comic mAddedComic = sLoadedComic;

        @Override
        protected Void doInBackground(Void... params) {
            //Add the comics number to the favorite list
            Favorites.addFavoriteItem(getActivity(), String.valueOf(mAddedNumber));
            try {
                //Download the image
                String url = sLoadedComic.getComicData()[2];
                mBitmap = Glide
                        .with(getActivity())
                        .load(url)
                        .asBitmap()
                        .into(-1, -1)
                        .get();
            } catch (Exception e) {
                Favorites.removeFavoriteItem(getActivity(), String.valueOf(mAddedNumber));
                Log.e("Saving Image failed!", e.toString());
            }
            //save title and alt text
            /*SharedPreferences.Editor mEditor = mSharedPreferences.edit();
            mEditor.putString(("title" + String.valueOf(mAddedNumber)), mAddedComic.getComicData()[0]);
            mEditor.putString(("alt" + String.valueOf(mAddedNumber)), mAddedComic.getComicData()[1]);
            mEditor.apply();*/
            PrefHelper.addTitle(mAddedComic.getComicData()[0], mAddedNumber);
            PrefHelper.addAlt(mAddedComic.getComicData()[1], mAddedNumber);
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            //the full bitmap should be available here
            try {
                File sdCard = Environment.getExternalStorageDirectory();
                File dir = new File (sdCard.getAbsolutePath() + "/easy xkcd");
                dir.mkdirs();
                File file = new File(dir, String.valueOf(mAddedNumber)+".png");
                FileOutputStream fos = new FileOutputStream(file);
                mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.flush();
                fos.close();
            } catch (Exception e) {
                Log.e("Error", "Saving to external storage failed");
                try {
                    FileOutputStream fos = getActivity().openFileOutput(String.valueOf(mAddedNumber), Context.MODE_PRIVATE);
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.close();
                } catch (Exception e2) {
                    e2.printStackTrace();
                }
            }

            //refresh the FavoritesFragmentco
            FavoritesFragment f = (FavoritesFragment) getActivity().getSupportFragmentManager().findFragmentByTag("favorites");
            if (f != null) {
                f.refresh();
            }

            //Sometimes the floating action button does not animate back to the bottom when the snackbar is dismissed, so force it to its original position
            ((MainActivity) getActivity()).mFab.forceLayout();
        }
    }

    private class DeleteComicImageTask extends AsyncTask<Integer, Void, Void> {
        private int mRemovedNumber = sLastComicNumber;
        private View.OnClickListener oc;

        @Override
        protected Void doInBackground(Integer... pos) {
            //delete the image from internal storage
            getActivity().deleteFile(String.valueOf(mRemovedNumber));
            //delete from external storage
            File sdCard = Environment.getExternalStorageDirectory();
            File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
            File file = new File(dir, String.valueOf(mRemovedNumber) + ".png");
            file.delete();

            //remove the number from the favorites list
            Favorites.removeFavoriteItem(getActivity(), String.valueOf(mRemovedNumber));
            //clear alt text and title
            /*SharedPreferences.Editor mEditor = mSharedPreferences.edit();
            mEditor.putString("title" + String.valueOf(mRemovedNumber), null);
            mEditor.putString("alt" + String.valueOf(mRemovedNumber), null);
            mEditor.apply();*/
            PrefHelper.addTitle("", mRemovedNumber);
            PrefHelper.addAlt("", mRemovedNumber);


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

    private void showRateSnackbar() {
        if(PrefHelper.showRateDialog()) {
            View.OnClickListener oc = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Uri uri = Uri.parse("market://details?id=" + getActivity().getPackageName());
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + getActivity().getPackageName())));
                    }
                }
            };
            Snackbar.make(((MainActivity) getActivity()).mFab, R.string.snackbar_rate, Snackbar.LENGTH_LONG)
                    .setAction(R.string.snackbar_rate_action, oc)
                    .show();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putInt("Last Comic", sLastComicNumber);
        super.onSaveInstanceState(savedInstanceState);
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

    public boolean getRandomComic() {
        if (isOnline() && sNewestComicNumber != 0) {
            MainActivity.sProgress = ProgressDialog.show(getActivity(), "", this.getResources().getString(R.string.loading_random), true);
            //get a random number and update the pager
            Random mRand = new Random();
            Integer mNumber = mRand.nextInt(sNewestComicNumber) + 1;
            sLastComicNumber = mNumber;
            new pagerUpdate().execute(mNumber);
        } else {
            Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private boolean isOnline() {
        //Checks if the device is currently online
        ConnectivityManager cm =
                (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();

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
