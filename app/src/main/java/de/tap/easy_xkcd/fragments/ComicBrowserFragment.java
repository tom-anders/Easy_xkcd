package de.tap.easy_xkcd.fragments;


import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
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
            pager.setAdapter(adapter);
        }

        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
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
                pager.setAdapter(adapter);
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

    private class ComicBrowserPagerAdapter extends ComicAdapter {

        public ComicBrowserPagerAdapter(Context context) {
            super(context);
        }

        @Override
        public int getCount() {
            return newestComicNumber;
        }

        @Override
        public Object instantiateItem(final ViewGroup container, final int position) {
            View itemView = setupPager(container, position);
            final PhotoView pvComic = (PhotoView) itemView.findViewById(R.id.ivComic);
            final TextView tvAlt = (TextView) itemView.findViewById(R.id.tvAlt);
            final TextView tvTitle = (TextView) itemView.findViewById(R.id.tvTitle);

            if (Arrays.binarySearch(mContext.getResources().getIntArray(R.array.large_comics), lastComicNumber) >= 0)
                pvComic.setMaximumScale(7.0f);

            class loadComic extends AsyncTask<Void, Void, Void> {
                private Comic comic;

                @Override
                protected Void doInBackground(Void... dummy) {
                    comic = comicMap.get(position+1);
                    if (comic == null) {
                        try {
                            comic = new Comic(position + 1, getActivity());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        comicMap.put(position + 1, comic);
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void dummy) {
                    if (comic != null && getActivity() != null) {
                        Glide.with(getActivity())
                                .load(comic.getComicData()[2])
                                .asBitmap()
                                .into(pvComic);

                        tvAlt.setText(comic.getComicData()[1]);
                        tvTitle.setText(comic.getComicData()[0]);

                        if (position == lastComicNumber - 1) {
                            if (((MainActivity) getActivity()).getProgressDialog() != null)
                                ((MainActivity) getActivity()).getProgressDialog().dismiss();
                            animateToolbar();
                        }

                        if (position == lastComicNumber + 1
                                || (position == lastComicNumber - 1 && lastComicNumber == newestComicNumber)
                                || (position == lastComicNumber && lastComicNumber == newestComicNumber - 1)) {
                            loadingImages = false;
                        }
                    }
                }
            }
            new loadComic().execute();

            container.addView(itemView);
            return itemView;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((RelativeLayout) object);
            Glide.clear(((RelativeLayout) object).findViewById(R.id.ivComic));
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
            new DeleteComicImageTask().execute(true);
            item.setIcon(R.drawable.ic_favorite_outline);
        } else {
            if (!(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
                return true;
            }
            new SaveComicImageTask().execute(true);
            item.setIcon(R.drawable.ic_action_favorite);
        }
        return true;
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
                        shareComicUrl(comicMap.get(lastComicNumber));
                        break;
                }
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
        return true;
    }

    public void shareComicImage() {
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
                return Glide.with(getActivity())
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
                ComicBrowserFragment.super.shareComicImage(uri, comicMap.get(lastComicNumber));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean zoomReset() {
        return loadingImages || super.zoomReset();
    }
}
















































