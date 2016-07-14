/**
 * *******************************************************************************
 * Copyright 2016 Tom Praschan
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
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.GlideDrawableImageViewTarget;
import com.kogitune.activity_transition.ActivityTransition;
import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.utils.OfflineComic;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import uk.co.senab.photoview.PhotoView;

public class OfflineFragment extends ComicFragment {
    private Boolean randomSelected = false;
    private static final String OFFLINE_PATH = "/easy xkcd";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflateLayout(R.layout.pager_layout, inflater, container, savedInstanceState);

        if (((MainActivity) getActivity()).getProgressDialog() != null)
            ((MainActivity) getActivity()).getProgressDialog().dismiss();


        if (savedInstanceState == null && prefHelper.isOnline(getActivity()) && (prefHelper.isWifi(getActivity()) | prefHelper.mobileEnabled()) && !fromSearch) {
            new updateImages(true).execute();
        } else {
            newestComicNumber = prefHelper.getHighestOffline();
            scrollViewPager();
            adapter = new OfflineBrowserPagerAdapter(getActivity(), newestComicNumber);
            pager.setAdapter(adapter);
            new updateImages(false).execute();
        }

        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                pageSelected(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        return v;
    }

    @Override
    public void updatePager() {
        new updateImages(false).execute();
    }

    public class updateImages extends AsyncTask<Void, Void, Boolean> {
        private ProgressDialog progress;
        private boolean showProgress;

        public updateImages(boolean showProgress) {
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
            Log.d("info", "updateImages started");
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Override
        protected Boolean doInBackground(Void... pos) {
            boolean showSnackbar = false;
            try {
                newestComicNumber = new Comic(0).getComicNumber();
                if (newestComicNumber > prefHelper.getHighestOffline()) {
                    showSnackbar = prefHelper.getNotificationInterval() == 0 && lastComicNumber != newestComicNumber;
                    OkHttpClient client = new OkHttpClient();
                    File sdCard = prefHelper.getOfflinePath();
                    File dir = new File(sdCard.getAbsolutePath() + OFFLINE_PATH);
                    for (int i = prefHelper.getHighestOffline() + 1; i <= newestComicNumber; i++) {
                        Log.d("comic added", String.valueOf(i));
                        Comic comic = new Comic(i, getActivity());
                        Request request = new Request.Builder()
                                .url(comic.getComicData()[2])
                                .build();
                        Response response = client.newCall(request).execute();
                        try {
                            File file = new File(dir, String.valueOf(i) + ".png");
                            BufferedSink sink = Okio.buffer(Okio.sink(file));
                            sink.writeAll(response.body().source());
                            sink.close();
                        } catch (Exception e) {
                            Log.e("Error at comic" + i, "Saving to external storage failed");
                            try {
                                FileOutputStream fos = getActivity().openFileOutput(String.valueOf(i), Context.MODE_PRIVATE);
                                BufferedSink sink = Okio.buffer(Okio.sink(fos));
                                sink.writeAll(response.body().source());
                                fos.close();
                                sink.close();
                            } catch (Exception e2) {
                                e2.printStackTrace();
                            }
                        }
                        response.body().close();
                        prefHelper.addTitle(comic.getComicData()[0], i);
                        prefHelper.addAlt(comic.getComicData()[1], i);
                        prefHelper.setHighestOffline(newestComicNumber);
                        prefHelper.setNewestComic(i);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (!prefHelper.nomediaCreated()) {
                File sdCard = prefHelper.getOfflinePath();
                File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                File nomedia = new File(dir, ".nomedia");
                try {
                    boolean created = nomedia.createNewFile();
                    Log.d("created", String.valueOf(created));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return showSnackbar;
        }

        @Override
        protected void onPostExecute(Boolean showSnackbar) {
            if (showProgress)
                progress.dismiss();
            if (((MainActivity) getActivity()).getProgressDialog() != null)
                ((MainActivity) getActivity()).getProgressDialog().dismiss();
            scrollViewPager();
            adapter = new OfflineBrowserPagerAdapter(getActivity(), newestComicNumber);
            pager.setAdapter(adapter);
            if (showSnackbar) {
                View.OnClickListener oc = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getLatestComic();
                    }
                };
                FloatingActionButton fab = (FloatingActionButton) getActivity().findViewById(R.id.fab);
                //noinspection ResourceType (android studio won't let you set a custom snackbar length)
                Snackbar.make(fab, getActivity().getResources().getString(R.string.new_comic), 4000)
                        .setAction(getActivity().getResources().getString(R.string.new_comic_view), oc)
                        .show();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_favorite:
                return ModifyFavorites(item);

            case R.id.action_share:
                return shareComic();

            case R.id.action_random:
                return getRandomComic();

            case R.id.action_thread:
                return DatabaseManager.showThread(comicMap.get(lastComicNumber).getComicData()[0], getActivity(), false);
        }
        return super.onOptionsItemSelected(item);
    }

    private class OfflineBrowserPagerAdapter extends ComicAdapter {

        public OfflineBrowserPagerAdapter(Context context, int count) {
            super(context, count);
        }

        @Override
        public int getCount() {
            return count;
        }

        @Override
        public Object instantiateItem(ViewGroup container, final int position) {
            View itemView = setupPager(container, position);
            PhotoView pvComic = (PhotoView) itemView.findViewById(R.id.ivComic);
            TextView tvAlt = (TextView) itemView.findViewById(R.id.tvAlt);
            TextView tvTitle = (TextView) itemView.findViewById(R.id.tvTitle);

            comicMap.put(position + 1, new OfflineComic(position + 1, getActivity(), ((MainActivity) getActivity()).getPrefHelper()));

            tvTitle.setText(comicMap.get(position + 1).getComicData()[0]);
            tvAlt.setText(comicMap.get(position + 1).getComicData()[1]);
            if (fromSearch) {
                fromSearch = false;
                transition = ActivityTransition.with(getActivity().getIntent()).duration(300).to(pvComic).start(null);
            }
            if (getGifId(position) != 0)
                Glide.with(getActivity())
                        .load(getGifId(position))
                        .into(new GlideDrawableImageViewTarget(pvComic));
            else {
                Bitmap bitmap = ((OfflineComic) comicMap.get(position + 1)).getBitmap();
                if (themePrefs.invertColors() && themePrefs.bitmapContainsColor(bitmap))
                    pvComic.clearColorFilter();
                pvComic.setImageBitmap(bitmap);
            }

            if (randomSelected && position == lastComicNumber - 1) {
                Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), android.R.anim.fade_in);
                itemView.setAnimation(animation);
                randomSelected = false;
            }

            if (Arrays.binarySearch(mContext.getResources().getIntArray(R.array.large_comics), position+1) >= 0)
                pvComic.setMaximumScale(13.0f);

            if (position == lastComicNumber - 1)
                animateToolbar();

            container.addView(itemView);
            return itemView;
        }
    }

    /********************
     * Random Comics
     ***************************************/

    @Override
    public boolean getRandomComic() {
        if (newestComicNumber != 0) {
            randomSelected = true;
            return super.getRandomComic();
        }
        return true;
    }

    @Override
    public void getPreviousRandom() {
        if (newestComicNumber != 0) {
            randomSelected = true;
            super.getRandomComic();
        }
    }

    /*************************
     * Favorite Modification
     ************************/

    private boolean ModifyFavorites(MenuItem item) {
        if (databaseManager.checkFavorite(lastComicNumber)) {
            new DeleteComicImageTask().execute(false);
            item.setIcon(R.drawable.ic_favorite_outline);
        } else {
            //save image to internal storage
            new SaveComicImageTask().execute(false);
            item.setIcon(R.drawable.ic_action_favorite);
        }
        return true;
    }

    /************************************
     * Sharing
     *********************************/

    protected boolean shareComic() {
        if (prefHelper.shareImage()) {
            shareComicImage(getURI(lastComicNumber), comicMap.get(lastComicNumber));
            return true;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setItems(R.array.share_dialog, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        shareComicImage(getURI(lastComicNumber), comicMap.get(lastComicNumber));
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

}
