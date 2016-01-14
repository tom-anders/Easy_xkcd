package de.tap.easy_xkcd.fragments;


import android.Manifest;
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
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
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
import java.util.Arrays;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.utils.Favorites;
import de.tap.easy_xkcd.utils.JsonParser;
import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;

public class ComicBrowserFragment extends ComicFragment {


    private static boolean loadingImages;
    private static boolean newestUpdated = false;

    private ComicBrowserPagerAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflateLayout(R.layout.pager_layout, inflater, container, savedInstanceState);

        loadingImages = true;

        if (savedInstanceState == null && !newestUpdated) {
            newestUpdated = true;
            new updateNewest(true).execute();
        } else {
            newestComicNumber = prefHelper.getNewest();
            scrollViewPager();
            adapter = new ComicBrowserPagerAdapter(getActivity());
            mPager.setAdapter(adapter);
        }

        mPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                pageSelected(position);
                if (!prefHelper.isOnline(getActivity()))  //Don't update if the device is not online
                    Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        return v;
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
                newestComicNumber = Integer.parseInt(json.getString("num"));
                if (lastComicNumber == 0)
                    lastComicNumber = newestComicNumber;
            } catch (Exception e) {
                e.printStackTrace();
            }
            boolean showSnackbar = newestComicNumber > prefHelper.getNewest() && lastComicNumber != newestComicNumber && (prefHelper.getNotificationInterval() == 0);
            updatePager = showProgress || newestComicNumber > prefHelper.getNewest();

            prefHelper.setNewestComic(newestComicNumber);
            prefHelper.setLastComic(lastComicNumber);
            return showSnackbar;
        }

        @Override
        protected void onPostExecute(Boolean showSnackbar) {
            if (updatePager) {
                scrollViewPager();
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
                //noinspection ResourceType
                Snackbar.make(fab, getActivity().getResources().getString(R.string.new_comic), 4000)
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
            return newestComicNumber;
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


            if (position == lastComicNumber - 1 && fromSearch) {
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
                                        if (position == lastComicNumber - 1) {
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
                                        if (position == lastComicNumber + 2) {
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
                                        if (position == lastComicNumber + 2
                                                | (position == lastComicNumber - 1 && lastComicNumber == newestComicNumber)
                                                | (position == lastComicNumber && lastComicNumber == newestComicNumber - 1)
                                                | (position == lastComicNumber + 1 && lastComicNumber == newestComicNumber - 2)) {
                                            loadingImages = false;
                                        }

                                    }
                                });
                        comicMap.put(position + 1, comic);
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
                    if (lastComicNumber - 1 + position == 1572) {
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
            if (Arrays.binarySearch(mContext.getResources().getIntArray(R.array.large_comics), lastComicNumber) >= 0) {
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
            case R.id.action_share:
                return shareComic();

            case R.id.action_latest:
                return getLatestComic();

            case R.id.action_random:
                return getRandomComic();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected boolean getLatestComic() {
        if (prefHelper.isOnline(getActivity())) {
            if (newestComicNumber - lastComicNumber > 4)
                ((MainActivity) getActivity()).setProgressDialog(this.getResources().getString(R.string.loading_latest), false);
            return super.getLatestComic();
        }
        return true;
    }

    /*************************
     * Favorite Modification
     ************************/

    protected boolean ModifyFavorites(MenuItem item) {
        if (!prefHelper.isOnline(getActivity())) {
            Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
            return true;
        }
        if (Favorites.checkFavorite(getActivity(), lastComicNumber)) {
            new OnlineDeleteComicImageTask().execute();
            item.setIcon(R.drawable.ic_favorite_outline);
        } else {
            new OnlineSaveComicImageTask().execute();
            item.setIcon(R.drawable.ic_action_favorite);
        }
        return true;
    }

    private class OnlineSaveComicImageTask extends SaveComicImageTask {
        private Bitmap mBitmap;
        private Comic mAddedComic = comicMap.get(lastComicNumber);

        @Override
        protected Void doInBackground(Void... params) {
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
            super.doInBackground(params);
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
            super.onPostExecute(dummy);
        }
    }

    private class OnlineDeleteComicImageTask extends DeleteComicImageTask {
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

            prefHelper.addTitle("", mRemovedNumber);
            prefHelper.addAlt("", mRemovedNumber);

            oc = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new OnlineSaveComicImageTask().execute();
                }
            };
            Favorites.removeFavoriteItem(getActivity(), String.valueOf(mRemovedNumber));
            Snackbar.make(((MainActivity) getActivity()).getFab(), R.string.snackbar_remove, Snackbar.LENGTH_LONG)
                    .setAction(R.string.snackbar_undo, oc)
                    .show();
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            super.onPostExecute(v);
        }
    }

    /*********************************
     * Random Comics
     ***********************************/

    @Override
    public boolean getRandomComic() {
        if (prefHelper.isOnline(getActivity()) && newestComicNumber != 0) {
            ((MainActivity) getActivity()).setProgressDialog(getActivity().getResources().getString(R.string.loading_random), false);
            return super.getRandomComic();
        }
        Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override
    public void getPreviousRandom() {
        if (prefHelper.isOnline(getActivity()) && newestComicNumber != 0)
            super.getRandomComic();
        else
            Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
    }

    /*******************
     * Sharing
     **************************/

    protected boolean shareComic() {
        if (prefHelper.shareImage()) {
            shareComicImage(null);
            return true;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setItems(R.array.share_dialog, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        shareComicImage(null);
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

    @Override
    public void shareComicImage(Uri uri) {
        if (!(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return;
        }
        new ShareImageTask().execute(comicMap.get(lastComicNumber).getComicData()[2]);
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
                String cachePath = Environment.getExternalStorageDirectory() + "/easy xkcd";
                File dir = new File(cachePath);
                dir.mkdirs();
                File file = new File(dir, String.valueOf(lastComicNumber) + ".png");
                FileOutputStream stream = new FileOutputStream(file);
                result.compress(Bitmap.CompressFormat.PNG, 100, stream);
                stream.close();
                Uri uri = Uri.fromFile(file);
                ComicBrowserFragment.super.shareComicImage(uri);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean zoomReset() {
        return loadingImages || super.zoomReset();
    }
}
















































