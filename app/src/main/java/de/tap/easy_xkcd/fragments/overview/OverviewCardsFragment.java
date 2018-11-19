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

package de.tap.easy_xkcd.fragments.overview;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.transition.TransitionInflater;
import androidx.core.app.SharedElementCallback;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileInputStream;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.GlideApp;
import de.tap.easy_xkcd.database.RealmComic;
import timber.log.Timber;

public class OverviewCardsFragment extends OverviewRecyclerBaseFragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setupVariables();
        View v = inflater.inflate(R.layout.recycler_layout, container, false);
        rv = v.findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(getActivity()));
        rv.setHasFixedSize(true);
        rv.setVerticalScrollBarEnabled(false);

        setupAdapter();
        if (savedInstanceState == null) {
            animateToolbar();

            postponeEnterTransition();
        }

        setSharedElementEnterTransition(TransitionInflater.from(getContext()).inflateTransition(R.transition.image_shared_element_transition));

        return v;
    }

    public class CardsAdapter extends RVAdapter {

        @Override
        public int getItemCount() {
            return comics.size();
        }

        @Override
        public ComicViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.search_result, viewGroup, false);
            v.setOnClickListener(new CustomOnClickListener());
            v.setOnLongClickListener(new CustomOnLongClickListener());
            return new ComicViewHolder(v);
        }

        @Override
        public void onBindViewHolder(final ComicViewHolder comicViewHolder, int i) {
            final RealmComic comic = comics.get(i);
            final int number = comic.getComicNumber();
            String title = comic.getTitle();

            setupCard(comicViewHolder, comic, title, number);

            //TODO we can use glide for all these requests and simplify this call!
            if (!MainActivity.fullOffline) {
                GlideApp.with(OverviewCardsFragment.this)
                        .asBitmap()
                        .load(comic.getUrl())
                        .listener(new RequestListener<Bitmap>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                                if (number == lastComicNumber) {
                                    startPostponedEnterTransition();
                                }
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                                if (themePrefs.invertColors(false) && themePrefs.bitmapContainsColor(resource, comic.getComicNumber()))
                                    comicViewHolder.thumbnail.clearColorFilter();

                                if (number == lastComicNumber) {
                                    startPostponedEnterTransition();
                                }
                                Timber.d("Loaded overview comic %d", number);
                                return false;
                            }
                        })
                        .into(comicViewHolder.thumbnail);
            } else {
                try {
                    File sdCard = prefHelper.getOfflinePath();
                    File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                    File file = new File(dir, String.valueOf(number) + ".png");
                    GlideApp.with(OverviewCardsFragment.this)
                            .asBitmap()
                            .load(file)
                            .listener(new RequestListener<Bitmap>() {
                                @Override
                                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                                    if (number == lastComicNumber) {
                                        startPostponedEnterTransition();
                                    }
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                                    if (themePrefs.invertColors(false) && themePrefs.bitmapContainsColor(resource, comic.getComicNumber()))
                                        comicViewHolder.thumbnail.clearColorFilter();

                                    if (number == lastComicNumber) {
                                        startPostponedEnterTransition();
                                    }
                                    return false;
                                }
                            })
                            .into(comicViewHolder.thumbnail);
                } catch (Exception e) {
                    Log.e("Error", "loading from external storage failed");
                    try {
                        FileInputStream fis = getActivity().openFileInput(String.valueOf(number));
                        Bitmap mBitmap = BitmapFactory.decodeStream(fis);
                        fis.close();
                        comicViewHolder.thumbnail.setImageBitmap(mBitmap);
                        if (themePrefs.invertColors(false) && themePrefs.bitmapContainsColor(mBitmap, comic.getComicNumber()))
                            comicViewHolder.thumbnail.clearColorFilter();
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                }
            }
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_favorite:
                /*if (prefHelper.overviewFav())
                    scroller.setVisibility(View.VISIBLE);
                else
                    scroller.setVisibility(View.INVISIBLE);*/
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void setupAdapter() {
        super.setupAdapter();
        //ComicFragment comicFragment = (ComicFragment) getActivity().getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);

        rvAdapter = new CardsAdapter();
        rv.setAdapter(rvAdapter);

    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(LAST_COMIC, prefHelper.getNewest() - ((LinearLayoutManager) rv.getLayoutManager()).findFirstVisibleItemPosition());
        Timber.d("put last comic %d", outState.getInt(LAST_COMIC));
        super.onSaveInstanceState(outState);
    }

    @Override
    public void updateDatabasePostExecute() {
        setupAdapter();
        super.updateDatabasePostExecute();
    }

}
