package de.tap.easy_xkcd.fragments.overview;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.bumptech.glide.Glide;
import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileInputStream;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.database.RealmComic;
import de.tap.easy_xkcd.fragments.comics.ComicFragment;
import xyz.danoz.recyclerviewfastscroller.vertical.VerticalRecyclerViewFastScroller;

public class OverviewCardsFragment extends OverviewRecyclerBaseFragment {
    private VerticalRecyclerViewFastScroller scroller;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setupVariables();
        View v = inflater.inflate(R.layout.recycler_layout, container, false);
        rv = (RecyclerView) v.findViewById(R.id.rv);
        rv.setLayoutManager(new LinearLayoutManager(getActivity()));
        rv.setHasFixedSize(true);
        rv.setVerticalScrollBarEnabled(false);
        scroller = (VerticalRecyclerViewFastScroller) v.findViewById(R.id.fast_scroller);
        if (!prefHelper.overviewFav())
            scroller.setVisibility(View.VISIBLE);
        scroller.setRecyclerView(rv);
        rv.addOnScrollListener(scroller.getOnScrollListener());

        if (savedInstanceState == null) {
            databaseManager.new updateComicDatabase(null, this, prefHelper).execute();
        } else {
            rvAdapter = new CardsAdapter();
            rv.setAdapter(rvAdapter);
        }

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
            RealmComic comic = comics.get(i);
            int number = comic.getComicNumber();
            String title = comic.getTitle();

            setupCard(comicViewHolder, comic, title, number);

            if (!MainActivity.fullOffline) {
                Glide.with(getActivity())
                        .load(comic.getUrl())
                        .asBitmap()
                        .into(comicViewHolder.thumbnail);
            } else {
                try {
                    File sdCard = prefHelper.getOfflinePath();
                    File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                    File file = new File(dir, String.valueOf(number) + ".png");
                    Glide.with(getActivity())
                            .load(file)
                            .asBitmap()
                            .into(comicViewHolder.thumbnail);
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
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_favorite:
                if (prefHelper.overviewFav())
                    scroller.setVisibility(View.VISIBLE);
                else
                    scroller.setVisibility(View.INVISIBLE);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void setupAdapter() {
        super.setupAdapter();
        ComicFragment comicFragment = (ComicFragment) getActivity().getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);

        rvAdapter = new CardsAdapter();
        rv.setAdapter(rvAdapter);

        if (comicFragment.lastComicNumber <= comics.size())
            rv.scrollToPosition(comics.size() - comicFragment.lastComicNumber);

    }

    @Override
    public void updateDatabasePostExecute() {
        setupAdapter();
        super.updateDatabasePostExecute();
    }

}
