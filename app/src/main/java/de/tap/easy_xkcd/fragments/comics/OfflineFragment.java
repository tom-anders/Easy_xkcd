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
import android.text.Html;
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
import de.tap.easy_xkcd.database.RealmComic;
import io.realm.Realm;
import io.realm.RealmResults;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import timber.log.Timber;
import uk.co.senab.photoview.PhotoView;

public class OfflineFragment extends ComicFragment {
    private Boolean randomSelected = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflateLayout(R.layout.pager_layout, inflater, container, savedInstanceState);

        if (((MainActivity) getActivity()).getProgressDialog() != null)
            ((MainActivity) getActivity()).getProgressDialog().dismiss();


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
    public void updatePager() {
        newestComicNumber = prefHelper.getHighestOffline();
        scrollViewPager();
        adapter = new OfflineBrowserPagerAdapter(getActivity(), newestComicNumber);
        pager.setAdapter(adapter);
        if (newComicFound && lastComicNumber != newestComicNumber && (prefHelper.getNotificationInterval() == 0)) {
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
        public Object instantiateItem(ViewGroup container, final int position) {
            View itemView = setupPager(container, position);
            PhotoView pvComic = (PhotoView) itemView.findViewById(R.id.ivComic);
            TextView tvAlt = (TextView) itemView.findViewById(R.id.tvAlt);
            TextView tvTitle = (TextView) itemView.findViewById(R.id.tvTitle);

            final int comicNumber = position + 1;

            pvComic.setTransitionName("im" + comicNumber);
            tvTitle.setTransitionName(String.valueOf(comicNumber));
            //comicMap.put(comicNumber, new OfflineComic(comicNumber, getActivity(), ((MainActivity) getActivity()).getPrefHelper()));

            RealmComic realmComic = databaseManager.getRealmComic(comicNumber);
            tvTitle.setText(Html.fromHtml(realmComic.getTitle()));
            tvAlt.setText(realmComic.getAltText());
            if (fromSearch) {
                fromSearch = false;
                transition = ActivityTransition.with(getActivity().getIntent()).duration(300).to(pvComic).start(null);
            }
            if (getGifId(position) != 0)
                Glide.with(getActivity())
                        .load(getGifId(position))
                        .into(new GlideDrawableImageViewTarget(pvComic));
            else {
                Bitmap bitmap = RealmComic.getOfflineBitmap(comicNumber, context, prefHelper);
                if (themePrefs.invertColors(false) && themePrefs.bitmapContainsColor(bitmap, comicNumber))
                    pvComic.clearColorFilter();
                pvComic.setImageBitmap(bitmap);
            }

            if (randomSelected && position == lastComicNumber - 1) {
                Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), android.R.anim.fade_in);
                itemView.setAnimation(animation);
                randomSelected = false;
            }

            if (Arrays.binarySearch(context.getResources().getIntArray(R.array.large_comics), position+1) >= 0)
                pvComic.setMaximumScale(15.0f);

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

    @Override
    protected boolean modifyFavorites(MenuItem item) {
        if (databaseManager.isFavorite(lastComicNumber)) {
            new DeleteComicImageTask(lastComicNumber).execute(false);
            item.setIcon(R.drawable.ic_favorite_outline);
        } else {
            //save image to internal storage
            new SaveComicImageTask(lastComicNumber).execute(false);
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
