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

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;

import androidx.annotation.Nullable;

import com.bumptech.glide.request.RequestOptions;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.viewpager.widget.ViewPager;

import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.GlideApp;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.database.RealmComic;
import io.realm.RealmResults;
import timber.log.Timber;

public class ComicBrowserFragment extends ComicFragment {

    public static boolean newestUpdated = false;

    /*TextView sharedTitle;
    PhotoView sharedPhotoView;*/

    private ComicBrowserPagerAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflateLayout(R.layout.pager_layout, inflater, container, savedInstanceState);

        newestComicNumber = prefHelper.getNewest();
        if (lastComicNumber == 0) {
            lastComicNumber = newestComicNumber;
        }
        prefHelper.setLastComic(lastComicNumber);
        newestComicNumber = prefHelper.getNewest();
        scrollViewPager();
        adapter = new ComicBrowserPagerAdapter(getActivity(), newestComicNumber);
        pager.setAdapter(adapter);

        if (prefHelper.fabDisabledComicBrowser()) ((MainActivity) getActivity()).getFab().hide(); else ((MainActivity) getActivity()).getFab().show();

        if (newComicFound && lastComicNumber != newestComicNumber) {
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

    /*setEnterSharedElementCallback(new SharedElementCallback() {
        @Override
        public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
            sharedElements.put(names.get(0), sharedTitle);
            sharedElements.put(names.get(1), sharedPhotoView);
        }
    });*/

        newComicFound = false;

        return v;
    }

    @Override
    public void updatePager() {
    }

    private class ComicBrowserPagerAdapter extends ComicAdapter {

        private RealmResults<RealmComic> comics;

        public ComicBrowserPagerAdapter(Context context, int count) {
            super(context, count);
            comics = databaseManager.getRealmComics();
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
                GlideApp.with(ComicBrowserFragment.this)
                        .asBitmap()
                        .apply(new RequestOptions().placeholder(getProgressCircle()))
                        .load(comic.getUrl())
                        .listener(new RequestListener<Bitmap>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                                postImageLoaded(comic.getComicNumber());
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                                postImageLoadedSetupPhotoView(pvComic, resource, comic);
                                postImageLoaded(comic.getComicNumber());
                                return false;
                            }
                        })
                        .into(pvComic);
            }
            Timber.d("Loaded comic %d with url %s", comic.getComicNumber(), comic.getUrl());
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((RelativeLayout) object);
            GlideApp.with(container.getContext()).clear((View) ((RelativeLayout) object).findViewById(R.id.ivComic));
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_favorite:
                return modifyFavorites(item);
            case R.id.action_share:
                return shareComic();
            case R.id.action_latest:
                return getLatestComic();
            case R.id.action_random:
                return getRandomComic();
            case R.id.action_thread:
                return DatabaseManager.showThread(databaseManager.getRealmComic(lastComicNumber).getTitle(), getActivity(), false);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected boolean getLatestComic() {
        if (getActivity() != null && prefHelper.isOnline(getActivity())) {
            if (newestComicNumber - lastComicNumber > 4)
                ((MainActivity) getActivity()).setProgressDialog(this.getResources().getString(R.string.loading_latest), false);
            return super.getLatestComic();
        }
        return true;
    }

    /*************************
     * Favorite Modification
     ************************/

    @Override
    protected boolean modifyFavorites(MenuItem item) {
        if (!prefHelper.isOnline(getActivity())) {
            Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
            return true;
        }
        if (databaseManager.isFavorite(lastComicNumber)) {
            //new DeleteComicImageTask(lastComicNumber).execute(true);
            removeFavorite(lastComicNumber, true);
            item.setIcon(R.drawable.ic_favorite_off_24dp);
        } else {
            if (!(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
                return true;
            }
            //new SaveComicImageTask(lastComicNumber).execute(true);
            addFavorite(lastComicNumber, true);
            item.setIcon(R.drawable.ic_favorite_on_24dp);
        }
        return true;
    }

    /*********************************
     * Random Comics
     ***********************************/

    @Override
    public boolean getRandomComic() {
        if (prefHelper.isOnline(getActivity()) && newestComicNumber != 0) {
            //TODO does the progress dialog have to be in MainActivity? Could it be a member of ComicFragment instead?
            return super.getRandomComic(); //TODO we should show a placeholder here... with glide?
        }
        Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override
    public void getPreviousRandom() {
        if (prefHelper.isOnline(getActivity()) && newestComicNumber != 0)
            super.getPreviousRandom();
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
        builder.setItems(R.array.share_dialog, (dialog, which) -> {
            switch (which) {
                case 0:
                    shareComicImage();
                    break;
                case 1:
                    shareComicUrl(databaseManager.getRealmComic(lastComicNumber));
                    break;
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
        GlideApp.with(this)
                .asBitmap()
                .load(databaseManager.getRealmComic(lastComicNumber).getUrl())
                .listener(new RequestListener<Bitmap>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                        Timber.e("sharing failed!");
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                        try {
                            String cachePath = prefHelper.getOfflinePath(getActivity()).getAbsolutePath();
                            File dir = new File(cachePath);
                            dir.mkdirs();
                            File file = new File(dir, lastComicNumber + ".png");
                            FileOutputStream stream = new FileOutputStream(file);
                            resource.compress(Bitmap.CompressFormat.PNG, 100, stream);
                            stream.close();
                            Uri uri = FileProvider.getUriForFile(getActivity(), "de.tap.easy_xkcd.fileProvider", file);
                            ComicBrowserFragment.super.shareComicImage(uri, databaseManager.getRealmComic(lastComicNumber));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return true;
                    }
                }).submit();
    }
}
















































