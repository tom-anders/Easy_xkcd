package de.tap.easy_xkcd.fragments;


import android.Manifest;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ProgressDialog;
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
import android.os.Environment;
import android.os.Vibrator;
import android.support.customtabs.CustomTabsIntent;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
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
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.kogitune.activity_transition.ActivityTransition;
import com.tap.xkcd_reader.R;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.CustomTabHelpers.BrowserFallback;
import de.tap.easy_xkcd.CustomTabHelpers.CustomTabActivityHelper;
import de.tap.easy_xkcd.misc.HackyViewPager;
import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.utils.Favorites;
import de.tap.easy_xkcd.utils.JsonParser;
import de.tap.easy_xkcd.utils.PrefHelper;
import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;

public class ComicBrowserFragment extends android.support.v4.app.Fragment {
    public int sLastComicNumber;
    public int sNewestComicNumber;
    private static boolean newestUpdated = false;
    public SparseArray<Comic> sComicMap = new SparseArray<>();
    public HackyViewPager mPager;
    public static boolean fromSearch = false;
    private static boolean loadingImages;

    private ComicBrowserPagerAdapter adapter;
    private PrefHelper prefHelper;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.pager_layout, container, false);
        setHasOptionsMenu(true);
        prefHelper = ((MainActivity) getActivity()).getPrefHelper();

        if (savedInstanceState != null) {
            sLastComicNumber = savedInstanceState.getInt("Last Comic");
        } else if (sLastComicNumber == 0) {
            sLastComicNumber = prefHelper.getLastComic();
        }
        if (((MainActivity) getActivity()).getCurrentFragment() == R.id.nav_browser && prefHelper.subtitleEnabled())
            ((MainActivity) getActivity()).getToolbar().setSubtitle(String.valueOf(sLastComicNumber));

        mPager = (HackyViewPager) v.findViewById(R.id.pager);
        mPager.setOffscreenPageLimit(3);
        loadingImages = true;

        if (savedInstanceState == null && !newestUpdated) {
            newestUpdated = true;
            new updateNewest(true).execute();
        } else {
            sNewestComicNumber = prefHelper.getNewest();
            if (sLastComicNumber != 0) {
                try {
                    Field field = ViewPager.class.getDeclaredField("mRestoredCurItem");
                    field.setAccessible(true);
                    field.set(mPager, sLastComicNumber - 1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            adapter = new ComicBrowserPagerAdapter(getActivity());
            mPager.setAdapter(adapter);
        }

        mPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                try {
                    getActivity().invalidateOptionsMenu();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
                prefHelper.setComicRead(String.valueOf(position + 1));
                if (getActivity().getSupportFragmentManager().findFragmentByTag("overview") != null)
                    ((OverviewListFragment) getActivity().getSupportFragmentManager().findFragmentByTag("overview")).notifyAdapter();
                if (!prefHelper.isOnline(getActivity()))  //Don't update if the device is not online
                    Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();

                sLastComicNumber = position + 1;
                if (((MainActivity) getActivity()).getCurrentFragment() == R.id.nav_browser && prefHelper.subtitleEnabled())
                    ((MainActivity) getActivity()).getToolbar().setSubtitle(String.valueOf(sLastComicNumber));
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
        return v;
    }

    @Override
    public void onStop() {
        prefHelper.setLastComic(sLastComicNumber);
        super.onStop();
    }

    public void updatePager() {
        new updateNewest(false).execute();
    }

    private class updateNewest extends AsyncTask<Void, Void, Boolean> {
        private ProgressDialog progress;
        private boolean showProgress;
        private boolean updatePager;

        public updateNewest(boolean showProgress) {
            super();
            this.showProgress = showProgress;
        }

        @Override
        protected void onPreExecute() {
            if (showProgress) {
                progress = new ProgressDialog(getActivity());
                progress.setTitle(getResources().getString(R.string.loading_comics));
                progress.setCancelable(false);
                progress.show();
            }
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                JSONObject json = JsonParser.getJSONFromUrl("http://xkcd.com/info.0.json");
                sNewestComicNumber = Integer.parseInt(json.getString("num"));
                if (sLastComicNumber == 0)
                    sLastComicNumber = sNewestComicNumber;
            } catch (Exception e) {
                e.printStackTrace();
            }
            boolean showSnackbar = sNewestComicNumber > prefHelper.getNewest() && sLastComicNumber != sNewestComicNumber && (prefHelper.getNotificationInterval() == 0);
            updatePager = showProgress || sNewestComicNumber > prefHelper.getNewest();

            prefHelper.setNewestComic(sNewestComicNumber);
            prefHelper.setLastComic(sLastComicNumber);
            return showSnackbar;
        }

        @Override
        protected void onPostExecute(Boolean showSnackbar) {
            if (updatePager) {
                if (sLastComicNumber != 0) {
                    try {
                        Field field = ViewPager.class.getDeclaredField("mRestoredCurItem");
                        field.setAccessible(true);
                        field.set(mPager, sLastComicNumber - 1);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                adapter = new ComicBrowserPagerAdapter(getActivity());
                mPager.setAdapter(adapter);
                mPager.setOffscreenPageLimit(3);
            }
            if (showSnackbar) {
                View.OnClickListener oc = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getLatestComic();
                    }
                };
                FloatingActionButton fab = (FloatingActionButton) getActivity().findViewById(R.id.fab);
                Snackbar.make(fab, getActivity().getResources().getString(R.string.new_comic), Snackbar.LENGTH_LONG)
                        .setAction(getActivity().getResources().getString(R.string.new_comic_view), oc)
                        .show();
            }
            if (showProgress)
                progress.dismiss();
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

            if (!prefHelper.defaultZoom()) {
                pvComic.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                pvComic.setMaximumScale(10f);
            }


            if (position == sLastComicNumber - 1 && fromSearch) {
                fromSearch = false;
                ActivityTransition.with(getActivity().getIntent()).duration(300).to(tvTitle).to(pvComic).start(null);
            }

            if (prefHelper.altByDefault())
                tvAlt.setVisibility(View.VISIBLE);

            class loadComic extends AsyncTask<Void, Void, Void> {
                private Comic comic;

                @Override
                protected Void doInBackground(Void... dummy) {
                    try {
                        comic = new Comic(position + 1, getActivity());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void dummy) {
                    if (comic != null && getActivity() != null) {
                        tvAlt.setText(comic.getComicData()[1]);
                        //Setup the title text view
                        tvTitle.setText(comic.getComicData()[0]);
                        Glide.with(getActivity())
                                .load(comic.getComicData()[2])
                                .asBitmap()
                                .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                                .into(new SimpleTarget<Bitmap>() {
                                    @Override
                                    public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                                        pvComic.setImageBitmap(resource);
                                        if (position == sLastComicNumber - 1) {
                                            if (((MainActivity) getActivity()).getProgressDialog() != null)
                                                ((MainActivity) getActivity()).getProgressDialog().dismiss();
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
                                        if (position == sLastComicNumber + 2) {
                                            switch (Integer.parseInt(prefHelper.getOrientation())) {
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
                                        if (position == sLastComicNumber + 2
                                                | (position == sLastComicNumber - 1 && sLastComicNumber == sNewestComicNumber)
                                                | (position == sLastComicNumber && sLastComicNumber == sNewestComicNumber - 1)
                                                | (position == sLastComicNumber + 1 && sLastComicNumber == sNewestComicNumber - 2)) {
                                            loadingImages = false;
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
                    if (!prefHelper.altLongTap()) {
                        if (prefHelper.classicAltStyle()) {
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
                    if (fingerLifted && prefHelper.altLongTap()) {
                        if (prefHelper.altVibration()) {
                            Vibrator vi = (Vibrator) getActivity().getSystemService(MainActivity.VIBRATOR_SERVICE);
                            vi.vibrate(10);
                        }
                        if (prefHelper.classicAltStyle()) {
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
            if (prefHelper.invertColors()) {
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
            if (prefHelper.scrollDisabledWhileZoom() && prefHelper.defaultZoom())
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
                return ((MainActivity) getActivity()).explainComic(sLastComicNumber);

            case R.id.action_browser:
                return ((MainActivity) getActivity()).openComicInBrowser(sLastComicNumber);

            case R.id.action_trans:
                return ((MainActivity) getActivity()).showTranscript(sComicMap.get(sLastComicNumber).getTranscript());
        }
        return super.onOptionsItemSelected(item);
    }

    public boolean getRandomComic() {
        if (prefHelper.isOnline(getActivity()) && sNewestComicNumber != 0) {
            ((MainActivity) getActivity()).setProgressDialog(getActivity().getResources().getString(R.string.loading_random), false);
            sLastComicNumber = prefHelper.getRandomNumber(sLastComicNumber);
            mPager.setCurrentItem(sLastComicNumber - 1, false);
        } else {
            Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    public void getPreviousRandom() {
        if (prefHelper.isOnline(getActivity()) && sNewestComicNumber != 0) {
            int n = sLastComicNumber;
            sLastComicNumber = prefHelper.getPreviousRandom(sLastComicNumber);
            if (sLastComicNumber != n) {
                ((MainActivity) getActivity()).setProgressDialog(this.getResources().getString(R.string.loading_random), false);
            }
            mPager.setCurrentItem(sLastComicNumber - 1, false);
        } else {
            Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean getLatestComic() {
        if (sNewestComicNumber - sLastComicNumber > 4) {
            ((MainActivity) getActivity()).setProgressDialog(this.getResources().getString(R.string.loading_latest), false);
        }
        mPager.setCurrentItem(sNewestComicNumber - 1, false);
        return true;
    }

    private boolean setAltText() {
        //If the user selected the menu item for the first time, show the toast
        if (prefHelper.showAltTip()) {
            Toast toast = Toast.makeText(getActivity(), R.string.action_alt_tip, Toast.LENGTH_LONG);
            toast.show();
            prefHelper.setAltTip(false);
        }
        //Show alt text
        TextView tvAlt = (TextView) mPager.findViewWithTag(sLastComicNumber - 1).findViewById(R.id.tvAlt);
        if (prefHelper.classicAltStyle()) {
            toggleVisibility(tvAlt);
        } else {
            android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity());
            mDialog.setMessage(tvAlt.getText());
            mDialog.show();
        }
        return true;
    }

    private boolean shareComic() {
        if (prefHelper.shareImage()) {
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
        if (prefHelper.shareMobile()) {
            share.putExtra(Intent.EXTRA_TEXT, "http://m.xkcd.com/" + String.valueOf(sLastComicNumber));
        } else {
            share.putExtra(Intent.EXTRA_TEXT, "http://xkcd.com/" + String.valueOf(sLastComicNumber));
        }
        startActivity(Intent.createChooser(share, this.getResources().getString(R.string.share_url)));
    }

    public void shareComicImage() {
       if (!(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return;
        }
        new ShareImageTask().execute(sComicMap.get(sLastComicNumber).getComicData()[2]);
    }

    private class ShareImageTask extends AsyncTask<String, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(String... params) {
            String url = params[0];
            try {
                return Glide
                        .with(getActivity())
                        .load(url)
                        .asBitmap()
                        .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                        .into(-1, -1)
                        .get();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Override
        protected void onPostExecute(Bitmap result) {
            if (result == null) {
                return;
            }
            try {
                String cachePath = Environment.getExternalStorageDirectory() +"/easy xkcd";
                File dir = new File(cachePath);
                dir.mkdirs();
                File file = new File(dir, String.valueOf(sLastComicNumber)+".png");
                FileOutputStream stream = new FileOutputStream(file);
                result.compress(Bitmap.CompressFormat.PNG, 100, stream);
                stream.close();
                Uri uri = Uri.fromFile(file);
                share(uri);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void share(Uri result) {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/*");
            share.putExtra(Intent.EXTRA_STREAM, result);
            share.putExtra(Intent.EXTRA_SUBJECT, sComicMap.get(sLastComicNumber).getComicData()[0]);
            if (prefHelper.shareAlt()) {
                share.putExtra(Intent.EXTRA_TEXT, sComicMap.get(sLastComicNumber).getComicData()[1]);
            }
            startActivity(Intent.createChooser(share, getResources().getString(R.string.share_image)));
        }
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

    private boolean ModifyFavorites(MenuItem item) {
        if (!prefHelper.isOnline(getActivity())) {
            Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
            return true;
        }
        if (Favorites.checkFavorite(getActivity(), sLastComicNumber)) {
            new DeleteComicImageTask().execute();
            item.setIcon(R.drawable.ic_favorite_outline);
            return true;
        } else {
            new SaveComicImageTask().execute();
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
            Favorites.addFavoriteItem(getActivity(), String.valueOf(mAddedNumber));
            try {
                String url = mAddedComic.getComicData()[2];
                mBitmap = Glide
                        .with(getActivity())
                        .load(url)
                        .asBitmap()
                        .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                        .into(-1, -1)
                        .get();
            } catch (Exception e) {
                Favorites.removeFavoriteItem(getActivity(), String.valueOf(mAddedNumber));
                Log.e("Saving Image failed!", e.toString());
            }
            prefHelper.addTitle(mAddedComic.getComicData()[0], mAddedNumber);
            prefHelper.addAlt(mAddedComic.getComicData()[1], mAddedNumber);
            return null;
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Override
        protected void onPostExecute(Void dummy) {
            try {
                File sdCard = prefHelper.getOfflinePath();
                File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                dir.mkdirs();
                File file = new File(dir, String.valueOf(mAddedNumber) + ".png");
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
            ((MainActivity) getActivity()).getFab().forceLayout();
        }
    }

    private class DeleteComicImageTask extends AsyncTask<Integer, Void, Void> {
        private int mRemovedNumber = sLastComicNumber;
        private View.OnClickListener oc;

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Override
        protected Void doInBackground(Integer... pos) {
            //delete the image from internal storage
            getActivity().deleteFile(String.valueOf(mRemovedNumber));
            //delete from external storage
            File sdCard = prefHelper.getOfflinePath();
            File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
            File file = new File(dir, String.valueOf(mRemovedNumber) + ".png");
            file.delete();

            Favorites.removeFavoriteItem(getActivity(), String.valueOf(mRemovedNumber));

            prefHelper.addTitle("", mRemovedNumber);
            prefHelper.addAlt("", mRemovedNumber);

            oc = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new SaveComicImageTask().execute();
                }
            };

            Snackbar.make(((MainActivity) getActivity()).getFab(), R.string.snackbar_remove, Snackbar.LENGTH_LONG)
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
        if (((MainActivity) getActivity()).getFab().getVisibility() == View.GONE) {
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

    public void scrollTo(int pos, boolean smooth) {
        mPager.setCurrentItem(pos, smooth);
    }

    public boolean zoomReset() {
        if (loadingImages) {
            return true;
        }
        PhotoView pv = (PhotoView) mPager.findViewWithTag(sLastComicNumber - 1).findViewById(R.id.ivComic);
        float scale = pv.getScale();
        if (scale != 1f) {
            pv.setScale(1f, true);
            return true;
        } else {
            return false;
        }
    }
}
















































