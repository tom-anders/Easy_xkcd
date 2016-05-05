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
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.target.Target;
import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileInputStream;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.database.RealmComic;

public class OverviewStaggeredGridFragment extends OverviewRecyclerBaseFragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setupVariables();
        View v = inflater.inflate(R.layout.recycler_layout, container, false);
        rv = (RecyclerView) v.findViewById(R.id.rv);
        StaggeredGridLayoutManager manager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        manager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_NONE);
        rv.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        rv.setHasFixedSize(true);

        if (savedInstanceState == null) {
            databaseManager.new updateComicDatabase(null, this, prefHelper).execute();
        } else {
            super.setupAdapter();
            rvAdapter = new GridAdapter();
            rv.setAdapter(rvAdapter);
        }

        return v;
    }

    public class GridAdapter extends RVAdapter {
        @Override
        public ComicViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.grid_item, viewGroup, false);
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

            if (!MainActivity.fullOffline) {
                comicViewHolder.thumbnail.layout(0, 0, 0, 0);
                Glide.with(getActivity())
                        .load(comic.getUrl())
                        .dontAnimate()
                        .override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                        .into(comicViewHolder.thumbnail);
            } else {
                try {
                    File sdCard = prefHelper.getOfflinePath();
                    File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                    File file = new File(dir, String.valueOf(number) + ".png");
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(file.getAbsolutePath(), options);

                    comicViewHolder.thumbnail.setImageBitmap(Bitmap.createBitmap(options.outWidth, options.outHeight, Bitmap.Config.ALPHA_8));

                    Glide.with(getActivity())
                            .load(file)
                            .asBitmap()
                            .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                            .into(new SimpleTarget<Bitmap>() {
                                @Override
                                public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                                    comicViewHolder.thumbnail.setImageBitmap(resource);

                                }
                            });
                } catch (Exception e) {
                    Log.e("Error", "loading from external storage failed");
                    try {
                        FileInputStream fis = getActivity().openFileInput(String.valueOf(number));
                        Bitmap mBitmap = BitmapFactory.decodeStream(fis);
                        fis.close();
                        comicViewHolder.thumbnail.setImageBitmap(mBitmap);
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    protected void setupAdapter() {
        super.setupAdapter();
        StaggeredGridLayoutManager manager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        manager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_NONE);
        rv.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        rvAdapter = new GridAdapter();
        rv.setAdapter(rvAdapter);
    }

    @Override
    public void updateDatabasePostExecute() {
        setupAdapter();
        animateToolbar();
    }
}
