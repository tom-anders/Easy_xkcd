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
import androidx.core.app.SharedElementCallback;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import android.transition.TransitionInflater;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.GlideApp;
import de.tap.easy_xkcd.GlideRequest;
import de.tap.easy_xkcd.database.RealmComic;
import timber.log.Timber;

public class OverviewStaggeredGridFragment extends OverviewRecyclerBaseFragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setupVariables();
        View v = inflater.inflate(R.layout.recycler_layout, container, false);
        rv = v.findViewById(R.id.rv);

        rv.setHasFixedSize(true);
        rv.setFastScrollEnabled(false);

        setupAdapter();
        if (savedInstanceState == null) {
            animateToolbar();
            postponeEnterTransition();
        }

        setSharedElementEnterTransition(TransitionInflater.from(getContext()).inflateTransition(R.transition.image_shared_element_transition));

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
            Timber.d("loaded comic %d", number);
            String title = comic.getTitle();

            setupCard(comicViewHolder, comic, title, number);

            loadComicImage(comic, comicViewHolder);
        }
    }

    @Override
    protected void setupAdapter() {
        StaggeredGridLayoutManager manager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        manager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_NONE);
        rv.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        rvAdapter = new GridAdapter();
        rv.setAdapter(rvAdapter);

        super.setupAdapter();
    }

    @Override
    public void updateDatabasePostExecute() {
        setupAdapter();
        animateToolbar();
    }
}
