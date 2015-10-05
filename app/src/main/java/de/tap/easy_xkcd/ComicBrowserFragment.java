package de.tap.easy_xkcd;


import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
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
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.tap.xkcd_reader.R;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;

import butterknife.OnPageChange;
import de.tap.easy_xkcd.CustomTabHelpers.BrowserFallback;
import de.tap.easy_xkcd.CustomTabHelpers.CustomTabActivityHelper;
import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;

public class ComicBrowserFragment extends android.support.v4.app.Fragment {
    public static int sLastComicNumber = 0;
    public static int sNewestComicNumber = 0;
    public static SparseArray<Comic> sComicMap = new SparseArray<>();
    public HackyViewPager sPager;
    private ComicBrowserPagerAdapter adapter;
    private ActionBar mActionBar;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.pager_layout, container, false);
        setHasOptionsMenu(true);

        if (savedInstanceState != null) {
            sLastComicNumber = savedInstanceState.getInt("Last Comic");
        } else if (sLastComicNumber == 0) {
            sLastComicNumber = PrefHelper.getLastComic();
        }
        mActionBar = ((MainActivity) getActivity()).getSupportActionBar();
        assert mActionBar != null;
        mActionBar.setSubtitle(String.valueOf(sLastComicNumber));

        sPager = (HackyViewPager) v.findViewById(R.id.pager);
        sPager.setOffscreenPageLimit(3);

        if (savedInstanceState == null) {
            new updateNewest().execute();
        } else {
            if (sLastComicNumber != 0) {
                try {
                    Field field = ViewPager.class.getDeclaredField("mRestoredCurItem");
                    field.setAccessible(true);
                    field.set(sPager, sLastComicNumber - 1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            adapter = new ComicBrowserPagerAdapter(getActivity());
            sPager.setAdapter(adapter);
        }

        sPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

            @Override
            public void onPageSelected(int position) {
                try {
                    getActivity().invalidateOptionsMenu();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
                if (!isOnline()) { //Don't update if the device is not online
                    Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
                }
                sLastComicNumber = position+1;
                mActionBar.setSubtitle(String.valueOf(sLastComicNumber));

            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
        return v;
    }

    @Override
    public void onStop() {
        PrefHelper.setLastComic(sLastComicNumber);
        super.onDestroy();
    }

    private class updateNewest extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                JSONObject json = JsonParser.getJSONFromUrl("http://xkcd.com/info.0.json");
                sNewestComicNumber = Integer.parseInt(json.getString("num"));
                if (sLastComicNumber == 0) {
                    sLastComicNumber = sNewestComicNumber;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            PrefHelper.setNewestComic(sNewestComicNumber);
            PrefHelper.setLastComic(sLastComicNumber);
            return null;
        }
        @Override
        protected void onPostExecute(Void v) {
            if (sLastComicNumber != 0) {
                try {
                    Field field = ViewPager.class.getDeclaredField("mRestoredCurItem");
                    field.setAccessible(true);
                    field.set(sPager, sLastComicNumber - 1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            adapter = new ComicBrowserPagerAdapter(getActivity());
            sPager.setAdapter(adapter);
            sPager.setOffscreenPageLimit(3);
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
            return sNewestComicNumber;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public Object instantiateItem(final ViewGroup container, final int position) {
            final View itemView = mLayoutInflater.inflate(R.layout.pager_item, container, false);
            itemView.setTag(position);
            final PhotoView pvComic = (PhotoView) itemView.findViewById(R.id.ivComic);
            final TextView tvAlt = (TextView) itemView.findViewById(R.id.tvAlt);
            final TextView tvTitle = (TextView) itemView.findViewById(R.id.tvTitle);

            if (PrefHelper.altByDefault()) {
                tvAlt.setVisibility(View.VISIBLE);
            }

            class loadComic extends AsyncTask<Void, Void, Void> {
                private Comic comic;

                @Override
                protected Void doInBackground(Void... dummy) {
                    try {
                        comic = new Comic(position + 1);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void dummy) {
                    if (comic != null) {
                        tvAlt.setText(comic.getComicData()[1]);
                        //Setup the title text view
                        tvTitle.setText(comic.getComicData()[0]);
                        Glide.with(getActivity())
                                .load(comic.getComicData()[2])
                                .asBitmap()
                                .into(new SimpleTarget<Bitmap>() {
                                    @Override
                                    public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                                        pvComic.setImageBitmap(resource);
                                        if (position == sLastComicNumber - 1 && MainActivity.sProgress != null) {
                                            MainActivity.sProgress.dismiss();
                                            Toolbar toolbar = ((MainActivity) getActivity()).toolbar;
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
                                        if (position == sLastComicNumber + 2) {
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
                                });
                        sComicMap.put(position + 1, comic);
                    }
                }
            }
            new loadComic().execute();
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
            //Setup alt text and LongClickListener
            pvComic.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (fingerLifted && PrefHelper.altLongTap()) {
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
                /*getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.flContent, new OverviewFragment()).commit();
                return true; */

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

            case R.id.action_trans:
                return showTranscript();
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean explainComic(int number) {
        String url = "http://explainxkcd.com/" + String.valueOf(number);
        CustomTabsIntent.Builder intentBuilder = new CustomTabsIntent.Builder();
        TypedValue typedValue = new TypedValue();
        getActivity().getTheme().resolveAttribute(R.attr.colorPrimary, typedValue, true);
        intentBuilder.setToolbarColor(typedValue.data);
        CustomTabActivityHelper.openCustomTab(getActivity(), intentBuilder.build(), Uri.parse(url), new BrowserFallback());
        return true;
    }

    private boolean showTranscript() {
        String trans = sComicMap.get(sLastComicNumber).getTranscript();
        android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity());
        mDialog.setMessage(trans);
        mDialog.show();
        return true;
    }

    public boolean getRandomComic() {
        if (isOnline() && sNewestComicNumber != 0) {
            MainActivity.sProgress = ProgressDialog.show(getActivity(), "", this.getResources().getString(R.string.loading_random), true);
            //get a random number and update the pager
            sLastComicNumber = PrefHelper.getRandomNumber(sLastComicNumber);
            sPager.setCurrentItem(sLastComicNumber - 1, false);
        } else {
            Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    public void getPreviousRandom() {
        if (isOnline() && sNewestComicNumber != 0) {
            int n = sLastComicNumber;
            sLastComicNumber = PrefHelper.getPreviousRandom(sLastComicNumber);
            if (sLastComicNumber != n) {
                MainActivity.sProgress = ProgressDialog.show(getActivity(), "", this.getResources().getString(R.string.loading_random), true);
            }
            sPager.setCurrentItem(sLastComicNumber-1, false);
        } else {
            Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean getLatestComic() {
        if (sNewestComicNumber-sLastComicNumber>4) {
            MainActivity.sProgress = ProgressDialog.show(getActivity(), "", this.getResources().getString(R.string.loading_latest), true);
        }
        sPager.setCurrentItem(sNewestComicNumber - 1, false);
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
        TextView tvAlt = (TextView) sPager.findViewWithTag(sLastComicNumber-1).findViewById(R.id.tvAlt);
        if (PrefHelper.classicAltStyle()) {
            toggleVisibility(tvAlt);
        } else {
            android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity());
            mDialog.setMessage(tvAlt.getText());
            mDialog.show();
        }
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
        share.putExtra(Intent.EXTRA_SUBJECT, sComicMap.get(sLastComicNumber).getComicData()[0]);
        if (PrefHelper.shareMobile()) {
            share.putExtra(Intent.EXTRA_TEXT, "http://m.xkcd.com/" + String.valueOf(sLastComicNumber));
        } else {
            share.putExtra(Intent.EXTRA_TEXT, "http://xkcd.com/" + String.valueOf(sLastComicNumber));
        }
        startActivity(Intent.createChooser(share, this.getResources().getString(R.string.share_url)));
    }

    private void shareComicImage() {
        //shares the comic's image along with its title
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("image/*");
        share.putExtra(Intent.EXTRA_STREAM, getURI());
        share.putExtra(Intent.EXTRA_SUBJECT,sComicMap.get(sLastComicNumber).getComicData()[0]);
        if (PrefHelper.shareAlt()) {
            share.putExtra(Intent.EXTRA_TEXT, sComicMap.get(sLastComicNumber).getComicData()[1]);
        }
        startActivity(Intent.createChooser(share, this.getResources().getString(R.string.share_image)));
    }

    private Uri getURI() {
        //Gets the URI of the currently loaded image
        View v = sPager.findViewWithTag(sLastComicNumber - 1);
        ImageView siv = (ImageView) v.findViewById(R.id.ivComic);
        Bitmap mBitmap = ((BitmapDrawable)siv.getDrawable()).getBitmap();
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
                        if (PrefHelper.fullOfflineEnabled()) {
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
        private Comic mAddedComic = sComicMap.get(sLastComicNumber);
        @Override
        protected Void doInBackground(Void... params) {
            //Add the comics number to the favorite list
            Favorites.addFavoriteItem(getActivity(), String.valueOf(mAddedNumber));
            try {
                //Download the image
                String url = mAddedComic.getComicData()[2];
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

            FavoritesFragment f = (FavoritesFragment) getActivity().getSupportFragmentManager().findFragmentByTag("favorites");
            if (f != null) {
                f.refresh();
            }
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
            //Remove the number from the favorites list
            Favorites.removeFavoriteItem(getActivity(), String.valueOf(mRemovedNumber));
            //clear alt text and title
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
        //Checks if the device is currently online
        ConnectivityManager cm =
                (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();

    }
}
















































