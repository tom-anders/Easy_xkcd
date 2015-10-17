package de.tap.easy_xkcd.fragments;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.tap.xkcd_reader.R;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;


import butterknife.Bind;
import butterknife.ButterKnife;
import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.Activities.WhatIfActivity;
import jp.wasabeef.recyclerview.animators.adapters.SlideInBottomAnimationAdapter;

public class WhatIfFragment extends android.support.v4.app.Fragment {

    public static ArrayList<String> mTitles = new ArrayList<>();
    private static ArrayList<String> mImgs = new ArrayList<>();
    @Bind(R.id.rv)
    RecyclerView rv;
    private MenuItem searchMenuItem;
    public static RVAdapter adapter;
    private static WhatIfFragment instance;
    public static boolean newIntent;
    private boolean offlineMode;
    private static final String OFFLINE_WHATIF_OVERVIEW_PATH = "/easy xkcd/what if/overview";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.whatif_recycler, container, false);
        ButterKnife.bind(this, v);
        setHasOptionsMenu(true);

        LinearLayoutManager llm = new LinearLayoutManager(getActivity());
        rv.setLayoutManager(llm);
        rv.setHasFixedSize(false);

        offlineMode = PrefHelper.fullOfflineWhatIf();
        instance = this;
        ((MainActivity) getActivity()).getFab().setVisibility(View.GONE);

        if (PrefHelper.isOnline(getActivity()) && (PrefHelper.isWifi(getActivity()) | PrefHelper.mobileEnabled()) && offlineMode) {
            new UpdateArticles().execute();
        } else {
            new DisplayOverview().execute();
        }
        return v;
    }

    private class UpdateArticles extends AsyncTask<Void, Void, Void> {
        private ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(getActivity());
            progress.setMessage(getResources().getString(R.string.loading_articles));
            progress.setIndeterminate(true);
            progress.setCancelable(false);
            progress.show();
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Override
        protected Void doInBackground(Void... dummy) {
            try {
                Document doc = Jsoup.connect("https://what-if.xkcd.com/archive/")
                        .userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/32.0.1700.19 Safari/537.36")
                        .get();
                Elements titles = doc.select("h1");
                Elements img = doc.select("img.archive-image");
                if (titles.size() > PrefHelper.getNewestWhatIf()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(titles.first().text());
                    titles.remove(0);
                    for (Element title : titles) {
                        sb.append("&&");
                        sb.append(title.text());
                    }
                    PrefHelper.setWhatIfTitles(sb.toString());

                    Bitmap mBitmap;
                    for (int i = PrefHelper.getNewestWhatIf(); i < titles.size() + 1; i++) {
                        String url = img.get(i).absUrl("src");
                        try {
                            mBitmap = Glide.with(getActivity())
                                    .load(url)
                                    .asBitmap()
                                    .into(-1, -1)
                                    .get();
                            File sdCard = Environment.getExternalStorageDirectory();
                            File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd/what if/overview");
                            dir.mkdirs();
                            File file = new File(dir, String.valueOf(i + 1) + ".png");
                            FileOutputStream fos = new FileOutputStream(file);
                            mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                            fos.flush();
                            fos.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                }
                PrefHelper.setNewestWhatif(titles.size());
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (!PrefHelper.nomediaCreated()) {
                File sdCard = Environment.getExternalStorageDirectory();
                File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                File nomedia = new File(dir, ".nomedia");
                try {
                    boolean created = nomedia.createNewFile();
                    Log.d("created", String.valueOf(created));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            progress.dismiss();
            new DisplayOverview().execute();
        }

    }

    private class DisplayOverview extends AsyncTask<Void, Void, Void> {
        private ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(getActivity());
            progress.setMessage(getResources().getString(R.string.loading_articles));
            progress.setIndeterminate(true);
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected Void doInBackground(Void... dummy) {
            mTitles.clear();
            mImgs.clear();

            if (offlineMode) {
                mTitles = PrefHelper.getWhatIfTitles();
                Collections.reverse(mTitles);
            } else {
                Document doc = WhatIfOverviewFragment.doc;
                Elements titles = doc.select("h1");
                Elements imagelinks = doc.select("img.archive-image");

                for (Element title : titles)
                    mTitles.add(title.text());

                for (Element image : imagelinks)
                    mImgs.add(image.absUrl("src"));

                Collections.reverse(mTitles);
                Collections.reverse(mImgs);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            PrefHelper.setNewestWhatif(mTitles.size());
            setupAdapter(PrefHelper.hideRead());
            progress.dismiss();
            Toolbar toolbar = ((MainActivity) getActivity()).getToolbar();
            if (toolbar.getAlpha() == 0) {
                toolbar.setTranslationY(-300);
                toolbar.animate().setDuration(300).translationY(0).alpha(1);
                View view;
                for (int i = 0; i < toolbar.getChildCount(); i++) {
                    view = toolbar.getChildAt(i);
                    view.setTranslationY(-300);
                    view.animate().setStartDelay(50 * (i + 1)).setDuration(70 * (i + 1)).translationY(0);
                }
            }
            if (newIntent) {
                Intent intent = new Intent(getActivity(), WhatIfActivity.class);
                startActivity(intent);
                newIntent = false;
            }
        }
    }

    public class RVAdapter extends RecyclerView.Adapter<RVAdapter.ComicViewHolder> {
        public ArrayList<String> titles;
        private ArrayList<String> imgs;

        @Override
        public int getItemCount() {
            return titles.size();
        }

        public RVAdapter(ArrayList<String> t, ArrayList<String> i) {
            this.titles = t;
            this.imgs = i;
        }

        @Override
        public ComicViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.whatif_overview, viewGroup, false);
            v.setOnClickListener(new CustomOnClickListener());
            v.setOnLongClickListener(new CustomOnLongClickListener());
            return new ComicViewHolder(v);
        }

        @Override
        public void onBindViewHolder(final ComicViewHolder comicViewHolder, int i) {
            comicViewHolder.articleTitle.setText(titles.get(i));
            String title = titles.get(i);
            int n = mTitles.size() - mTitles.indexOf(title);

            if (PrefHelper.checkRead(n)) {
                comicViewHolder.articleTitle.setTextColor(ContextCompat.getColor(getActivity(), R.color.Read));
            } else {
                comicViewHolder.articleTitle.setTextColor(ContextCompat.getColor(getActivity(), android.R.color.tertiary_text_light));
            }

            if (titles.get(i).equals("Jupiter Descending")) {
                comicViewHolder.thumbnail.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.mipmap.jupiter_descending));
                return;
            }
            if (titles.get(i).equals("Jupiter Submarine")) {
                comicViewHolder.thumbnail.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.mipmap.jupiter_submarine));
                return;
            }
            if (titles.get(i).equals("New Horizons")) {
                comicViewHolder.thumbnail.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.mipmap.new_horizons));
                return;
            }
            if (titles.get(i).equals("Proton Earth, Electron Moon")) {
                comicViewHolder.thumbnail.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.mipmap.proton_earth));
                return;
            }
            if (offlineMode) {
                File sdCard = Environment.getExternalStorageDirectory();
                File dir = new File(sdCard.getAbsolutePath() + OFFLINE_WHATIF_OVERVIEW_PATH);
                File file = new File(dir, String.valueOf(n) + ".png");
                Glide.with(getActivity())
                        .load(file)
                        .into(comicViewHolder.thumbnail);
            } else {
                Glide.with(getActivity())
                        .load(imgs.get(i))
                        .into(comicViewHolder.thumbnail);
            }
            if (PrefHelper.invertColors()) {
                float[] colorMatrix_Negative = {
                        -1.0f, 0, 0, 0, 255, //red
                        0, -1.0f, 0, 0, 255, //green
                        0, 0, -1.0f, 0, 255, //blue
                        0, 0, 0, 1.0f, 0 //alpha
                };
                ColorFilter cf = new ColorMatrixColorFilter(colorMatrix_Negative);
                comicViewHolder.thumbnail.setColorFilter(cf);
            }
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public class ComicViewHolder extends RecyclerView.ViewHolder {
            CardView cv;
            TextView articleTitle;
            ImageView thumbnail;

            ComicViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.cv);
                if (PrefHelper.nightThemeEnabled())
                    cv.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.background_material_dark));
                articleTitle = (TextView) itemView.findViewById(R.id.article_title);
                thumbnail = (ImageView) itemView.findViewById(R.id.thumbnail);
            }
        }
    }

    class CustomOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (!PrefHelper.isOnline(getActivity()) && !offlineMode) {
                Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
                return;
            }
            int pos = rv.getChildAdapterPosition(v);
            Intent intent = new Intent(getActivity(), WhatIfActivity.class);
            String title = adapter.titles.get(pos);
            int n = mTitles.size() - mTitles.indexOf(title);
            WhatIfActivity.WhatIfIndex = n;
            startActivity(intent);

            PrefHelper.setLastWhatIf(n);
            PrefHelper.setWhatifRead(String.valueOf(n));
            if (searchMenuItem.isActionViewExpanded()) {
                searchMenuItem.collapseActionView();
                rv.scrollToPosition(mTitles.size() - n);
            }
        }
    }

    public void getRandom() {
        if (PrefHelper.isOnline(getActivity()) | offlineMode) {
            Random mRand = new Random();
            int number = mRand.nextInt(adapter.titles.size());
            Intent intent = new Intent(getActivity(), WhatIfActivity.class);
            String title = adapter.titles.get(number);
            int n = mTitles.size() - mTitles.indexOf(title);
            WhatIfActivity.WhatIfIndex = n;
            startActivity(intent);
            PrefHelper.setLastWhatIf(n);
            PrefHelper.setWhatifRead(String.valueOf(n));
        } else {
            Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
        }
    }

    class CustomOnLongClickListener implements View.OnLongClickListener {
        @Override
        public boolean onLongClick(View v) {
            final View view = v;
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            int pos = rv.getChildAdapterPosition(view);
            final String title = adapter.titles.get(pos);
            final int n = mTitles.size() - mTitles.indexOf(title);
            int array;
            if (PrefHelper.checkWhatIfFav(n)) {
                array = R.array.card_long_click_remove;
            } else {
                array = R.array.card_long_click;
            }
            builder.setItems(array, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    int pos;
                    int n;
                    String title;
                    switch (which) {
                        case 0:
                            pos = rv.getChildAdapterPosition(view);
                            title = adapter.titles.get(pos);
                            n = mTitles.size() - mTitles.indexOf(title);
                            Intent share = new Intent(Intent.ACTION_SEND);
                            share.setType("text/plain");
                            share.putExtra(Intent.EXTRA_SUBJECT, "What if: " + title);
                            share.putExtra(Intent.EXTRA_TEXT, "http://what-if.xkcd.com/" + String.valueOf(n));
                            startActivity(share);
                            break;
                        case 1:
                            pos = rv.getChildAdapterPosition(view);
                            title = adapter.titles.get(pos);
                            n = mTitles.size() - mTitles.indexOf(title);
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://what-if.xkcd.com/" + String.valueOf(n)));
                            startActivity(intent);
                            break;
                        case 2:
                            pos = rv.getChildAdapterPosition(view);
                            title = adapter.titles.get(pos);
                            n = mTitles.size() - mTitles.indexOf(title);
                            if (!PrefHelper.checkWhatIfFav(n)) {
                                PrefHelper.setWhatIfFavorite(String.valueOf(n));
                            } else {
                                PrefHelper.removeWhatifFav(n);
                            }
                            WhatIfFavoritesFragment.getInstance().updateFavorites();
                            break;
                    }
                }
            });
            AlertDialog alert = builder.create();
            alert.show();
            return true;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_unread:
                PrefHelper.setAllUnread();
                setupAdapter(PrefHelper.hideRead());
                return true;
            case R.id.action_hide_read:
                item.setChecked(!item.isChecked());
                PrefHelper.setHideRead(item.isChecked());
                setupAdapter(item.isChecked());
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupAdapter(boolean hideRead) {
        if (hideRead) {
            ArrayList<String> titleUnread = new ArrayList<>();
            ArrayList<String> imgUnread = new ArrayList<>();
            for (int i = 0; i < mTitles.size(); i++) {
                if (!PrefHelper.checkRead(mTitles.size() - i)) {
                    titleUnread.add(mTitles.get(i));
                    if (!offlineMode)
                        imgUnread.add(mImgs.get(i));
                }
            }
            adapter = new RVAdapter(titleUnread, imgUnread);
            SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
            slideAdapter.setInterpolator(new DecelerateInterpolator());
            rv.setAdapter(slideAdapter);
            rv.scrollToPosition(titleUnread.size() - PrefHelper.getLastWhatIf());
        } else {
            adapter = new RVAdapter(mTitles, mImgs);
            SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
            slideAdapter.setInterpolator(new DecelerateInterpolator());
            rv.setAdapter(slideAdapter);
            rv.scrollToPosition(mTitles.size() - PrefHelper.getLastWhatIf());
        }
    }

    public void updateRv() {
        setupAdapter(PrefHelper.hideRead());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        final SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setIconifiedByDefault(false);
        searchView.setQueryHint(getResources().getString(R.string.search_hint_whatif));
        searchMenuItem = menu.findItem(R.id.action_search);
        searchMenuItem.setVisible(true);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                ArrayList<String> titleResults = new ArrayList<>();
                ArrayList<String> imgResults = new ArrayList<>();
                for (int i = 0; i < mTitles.size(); i++) {
                    if (mTitles.get(i).toLowerCase().contains(newText.toLowerCase().trim())) {
                        titleResults.add(mTitles.get(i));
                        if (!offlineMode)
                            imgResults.add(mImgs.get(i));
                    }
                }
                adapter = new RVAdapter(titleResults, imgResults);
                SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
                slideAdapter.setInterpolator(new DecelerateInterpolator());
                rv.setAdapter(slideAdapter);
                return false;
            }
        });

        MenuItemCompat.setOnActionExpandListener(searchMenuItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                View view = getActivity().getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(view, 0);
                }
                searchView.requestFocus();
                ((WhatIfOverviewFragment) getParentFragment()).fab.hide();
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                View view = getActivity().getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
                adapter = new RVAdapter(mTitles, mImgs);
                SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
                slideAdapter.setInterpolator(new DecelerateInterpolator());
                rv.setAdapter(slideAdapter);
                searchView.setQuery("", false);
                ((WhatIfOverviewFragment) getParentFragment()).fab.show();
                return true;
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
    }

    public RecyclerView getRv() {
        return rv;
    }

    public static WhatIfFragment getInstance() {
        return instance;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ButterKnife.unbind(this);
    }
}
