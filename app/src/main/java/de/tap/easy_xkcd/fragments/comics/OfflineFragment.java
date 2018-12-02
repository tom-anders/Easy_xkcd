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
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.database.RealmComic;
import io.realm.Realm;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import timber.log.Timber;

import static de.tap.easy_xkcd.utils.JsonParser.getNewHttpClient;

public class OfflineFragment extends ComicFragment {
    private Boolean randomSelected = false;
    private static final String MISSING_IMAGE = "missing_image";

    private Snackbar missingImageSnackbar;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflateLayout(R.layout.pager_layout, inflater, container, savedInstanceState);

        if (getMainActivity().getProgressDialog() != null)
            getMainActivity().getProgressDialog().dismiss();

        if (prefHelper.fabDisabledComicBrowser()) getMainActivity().getFab().hide(); else getMainActivity().getFab().show();

        updatePager();

        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

            @Override
            public void onPageSelected(int position) {
                pageSelected(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {}
        });

        return v;
    }

    @Override
    protected void pageSelected(int position) {
        super.pageSelected(position);

        View page = pager.findViewWithTag(position);
        if (page != null && page.findViewById(R.id.ivComic).getTag() == MISSING_IMAGE) {
            missingImageSnackbar = Snackbar.make(getMainActivity().getFab(), R.string.offline_image_missing, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.offline_image_missing_redownload, view -> {
                        Timber.e("bitmap for %d is null!", position + 1);
                        RealmComic comic = databaseManager.getRealmComic(position + 1);
                        new redownloadComicImageTask(comic.getUrl(), comic.getComicNumber()).execute();
                    });
            missingImageSnackbar.show();
        } else {
            if (missingImageSnackbar != null) {
                missingImageSnackbar.dismiss();
            }
        }

    }

    private class redownloadComicImageTask extends AsyncTask<Void, Void, Boolean> {
        String url;
        int number;

        public redownloadComicImageTask(String url, int number) {
            this.url = url;
            this.number = number;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                RealmComic.saveOfflineBitmap(getNewHttpClient().newCall(new Request.Builder().url(url).build()).execute(), prefHelper, number, getActivity());
                return true;
            } catch (IOException e) {
                Timber.e(e);
                return false;
            }
        }
        @Override
        protected void onPostExecute(Boolean successful) {
            if (successful) {
                Timber.d("download successful!");
                updatePager();
            } else {
                Toast.makeText(getActivity(), R.string.offline_image_missing_failed, Toast.LENGTH_SHORT).show();
            }
        }

    }

    @Override
    public void updatePager() {
        newestComicNumber = databaseManager.getHighestInDatabase();
        scrollViewPager();
        adapter = new OfflineBrowserPagerAdapter(getActivity(), newestComicNumber);
        pager.setAdapter(adapter);
        if (newComicFound && lastComicNumber != newestComicNumber) {
            View.OnClickListener oc = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getLatestComic();
                }
            };
            FloatingActionButton fab = getActivity().findViewById(R.id.fab);
            //noinspection ResourceType (android studio won't let you set a custom snackbar length)
            Snackbar.make(fab, getActivity().getResources().getString(R.string.new_comic), 4000)
                    .setAction(getActivity().getResources().getString(R.string.new_comic_view), oc)
                    .show();
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_favorite:
                return modifyFavorites(item);

            case R.id.action_share:
                return shareComic();

            case R.id.action_random:
                return getRandomComic();

            case R.id.action_thread:
                return DatabaseManager.showThread(databaseManager.getRealmComic(lastComicNumber).getTitle(), getActivity(), false);
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
        RealmComic getRealmComic(int position) {
            return databaseManager.getRealmComic(position + 1);
        }

        @Override
        void loadComicImage(RealmComic comic, PhotoView pvComic) {
            if (!loadGif(comic.getComicNumber(), pvComic)) {
                Bitmap bitmap = RealmComic.getOfflineBitmap(comic.getComicNumber(), context, prefHelper);
                postImageLoadedSetupPhotoView(pvComic, bitmap, comic);
                if (bitmap != null) {
                    pvComic.setImageBitmap(bitmap);
                } else {
                    pvComic.setTag(MISSING_IMAGE);
                }
                postImageLoaded(comic.getComicNumber());
            }
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

    @Override
    protected boolean modifyFavorites(MenuItem item) {
        if (databaseManager.isFavorite(lastComicNumber)) {
            //new DeleteComicImageTask(lastComicNumber).execute(false);
            removeFavorite(lastComicNumber, false);
            item.setIcon(R.drawable.ic_favorite_outline);
        } else {
            //save image to internal storage
            //new SaveComicImageTask(lastComicNumber).execute(false);
            addFavorite(lastComicNumber, false);
            item.setIcon(R.drawable.ic_action_favorite);
        }
        return true;
    }

    /************************************
     * Sharing
     *********************************/

    protected boolean shareComic() {
        if (prefHelper.shareImage()) {
            shareComicImage(getURI(lastComicNumber), databaseManager.getRealmComic(lastComicNumber));
            return true;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setItems(R.array.share_dialog, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        shareComicImage(getURI(lastComicNumber), databaseManager.getRealmComic(lastComicNumber));
                        break;
                    case 1:
                        shareComicUrl(databaseManager.getRealmComic(lastComicNumber));
                        break;
                }
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
        return true;
    }

}
