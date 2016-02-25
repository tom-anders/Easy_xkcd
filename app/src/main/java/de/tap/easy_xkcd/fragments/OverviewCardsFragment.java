package de.tap.easy_xkcd.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileInputStream;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.database.RealmComic;
import xyz.danoz.recyclerviewfastscroller.vertical.VerticalRecyclerViewFastScroller;

public class OverviewCardsFragment extends OverviewBaseFragment {
    private RVAdapter rvAdapter;
    private RecyclerView rv;
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

            databaseManager.new updateDatabase(null, this, prefHelper).execute();
        } else {
            rvAdapter = new RVAdapter();
            rv.setAdapter(rvAdapter);
        }

        return v;
    }

    @Override
    protected void updateBookmark(int i) {
        if (bookmark == 0)
            Toast.makeText(getActivity(), R.string.bookmark_toast_2, Toast.LENGTH_LONG).show();
        super.updateBookmark(i);
        rvAdapter.notifyDataSetChanged();
    }

    @Override
    public void notifyAdapter(int number) {
        if (prefHelper == null)
            return;
        rvAdapter.notifyDataSetChanged();
        if (!prefHelper.hideRead())
            rv.scrollToPosition(comics.size() - number);
        else
            rv.scrollToPosition(comics.size() - databaseManager.getNextUnread(number, comics));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_favorite:
                if (prefHelper.overviewFav()) {
                    item.setIcon(R.drawable.ic_favorite_outline);
                    item.setTitle(R.string.nv_favorites);
                } else {
                    item.setIcon(R.drawable.ic_action_favorite);
                    item.setTitle(R.string.action_overview);
                }
                prefHelper.setOverviewFav(!prefHelper.overviewFav());
                getActivity().invalidateOptionsMenu();
                if (prefHelper.overviewFav())
                    scroller.setVisibility(View.INVISIBLE);
                else
                    scroller.setVisibility(View.VISIBLE);
                setupAdapter();
                break;
            case R.id.action_boomark:
                super.showComic(bookmark - 1);
                break;
            case R.id.action_unread:
                prefHelper.setComicsUnread();
                setupAdapter();
                break;
            case R.id.action_hide_read:
                item.setChecked(!item.isChecked());
                prefHelper.setHideRead(item.isChecked());
                setupAdapter();
                break;

        }
        return super.onOptionsItemSelected(item);
    }

    protected void setupAdapter() {
        super.setupAdapter();
        ComicFragment comicFragment = (ComicFragment) getActivity().getSupportFragmentManager().findFragmentByTag(BROWSER_TAG);
        rvAdapter = new RVAdapter();
        rv.setAdapter(rvAdapter);
        if (comicFragment.lastComicNumber <= comics.size())
            rv.scrollToPosition(comics.size() - comicFragment.lastComicNumber);
    }

    public class RVAdapter extends RecyclerView.Adapter<RVAdapter.ComicViewHolder> {

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

            if (title.equals("Toasts//TODO fix transcripts"))
                title = "Toasts";
            if (comic.isRead() && !prefHelper.overviewFav()) {
                if (themePrefs.nightThemeEnabled())
                    comicViewHolder.comicTitle.setTextColor(ContextCompat.getColor(getActivity(), android.R.color.tertiary_text_light));
                else
                    comicViewHolder.comicTitle.setTextColor(ContextCompat.getColor(getActivity(), R.color.Read));
            } else {
                if (themePrefs.nightThemeEnabled())
                    comicViewHolder.comicTitle.setTextColor(ContextCompat.getColor(getActivity(), R.color.Read));
                else
                    comicViewHolder.comicTitle.setTextColor(ContextCompat.getColor(getActivity(), android.R.color.tertiary_text_light));
            }
            if (number == bookmark) {
                TypedValue typedValue = new TypedValue();
                getActivity().getTheme().resolveAttribute(R.attr.colorAccent, typedValue, true);
                comicViewHolder.comicTitle.setTextColor(typedValue.data);
            }

            comicViewHolder.comicInfo.setText(String.valueOf(number));
            comicViewHolder.comicTitle.setText(title);

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
            if (themePrefs.invertColors())
                comicViewHolder.thumbnail.setColorFilter(themePrefs.getNegativeColorFilter());

        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public class ComicViewHolder extends RecyclerView.ViewHolder {
            CardView cv;
            TextView comicTitle;
            TextView comicInfo;

            ImageView thumbnail;

            ComicViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.cv);
                if (themePrefs.nightThemeEnabled())
                    cv.setCardBackgroundColor(ContextCompat.getColor(getActivity(), R.color.background_material_dark));
                comicTitle = (TextView) itemView.findViewById(R.id.comic_title);
                comicInfo = (TextView) itemView.findViewById(R.id.comic_info);
                thumbnail = (ImageView) itemView.findViewById(R.id.thumbnail);
            }
        }
    }

    class CustomOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            showComic(rv.getChildAdapterPosition(v));
        }
    }

    class CustomOnLongClickListener implements View.OnLongClickListener {
        @Override
        public boolean onLongClick(View v) {
            updateBookmark(rv.getChildAdapterPosition(v));
            return true;
        }
    }

    @Override
    public void updateDatabasePostExecute() {
        setupAdapter();
        super.updateDatabasePostExecute();
    }

}
