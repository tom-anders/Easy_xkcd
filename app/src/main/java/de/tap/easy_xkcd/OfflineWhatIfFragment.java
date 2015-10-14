package de.tap.easy_xkcd;


import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
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
import jp.wasabeef.recyclerview.animators.adapters.SlideInBottomAnimationAdapter;

public class OfflineWhatIfFragment extends android.support.v4.app.Fragment {
    public static ArrayList<String> mTitles = new ArrayList<>();
    //public static RecyclerView rv;
    @Bind(R.id.rv) RecyclerView rv;
    private MenuItem searchMenuItem;
    public static RVAdapter adapter;
    private static OfflineWhatIfFragment instance;
    public static boolean newIntent;
    //private FloatingActionButton fab;
    //@Bind(R.id.fab) FloatingActionButton fab;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.whatif_recycler, container, false);
        ButterKnife.bind(this, v);
        setHasOptionsMenu(true);



        //rv = (RecyclerView) v.findViewById(R.id.rv);
        LinearLayoutManager llm = new LinearLayoutManager(getActivity());
        rv.setLayoutManager(llm);
        rv.setHasFixedSize(false);
        //rv.addOnScrollListener(new CustomOnScrollListener());

        instance = this;

        ((MainActivity) getActivity()).mFab.setVisibility(View.GONE);

        if (isOnline() && (isWifi()|PrefHelper.mobileEnabled())) {
            new UpdateArticles().execute();
        } else {
            new DisplayOverview().execute();
        }

        return v;
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

            mTitles = PrefHelper.getWhatIfTitles();
            Collections.reverse(mTitles);
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            if (PrefHelper.hideRead()) {
                ArrayList<String> titleUnread = new ArrayList<>();
                for (int i = 0; i < mTitles.size(); i++) {
                    if (!PrefHelper.checkRead(mTitles.size() - i)) {
                        titleUnread.add(mTitles.get(i));
                    }
                }
                adapter = new RVAdapter(titleUnread);
                SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
                slideAdapter.setInterpolator(new DecelerateInterpolator());
                rv.setAdapter(slideAdapter);
                rv.scrollToPosition(titleUnread.size() - PrefHelper.getLastWhatIf());
            } else {
                adapter = new RVAdapter(mTitles);
                SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
                slideAdapter.setInterpolator(new DecelerateInterpolator());
                rv.setAdapter(slideAdapter);
                rv.scrollToPosition(mTitles.size() - PrefHelper.getLastWhatIf());
            }
            progress.dismiss();
            Toolbar toolbar = ((MainActivity) getActivity()).toolbar;
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

    public class RVAdapter extends RecyclerView.Adapter<RVAdapter.ComicViewHolder> {
        private int lastPosition = 0;
        public ArrayList<String> titles;

        @Override
        public int getItemCount() {
            return titles.size();
        }

        public RVAdapter(ArrayList<String> t) {
            this.titles = t;
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
                comicViewHolder.articleTitle.setTextColor(getResources().getColor(R.color.Read));
            } else {
                comicViewHolder.articleTitle.setTextColor(getResources().getColor(android.R.color.tertiary_text_light));
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

            File sdCard = Environment.getExternalStorageDirectory();
            File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd/what if/overview");
            //File file = new File(dir, String.valueOf(titles.size()-i) + ".png");
            File file = new File(dir, String.valueOf(n) + ".png");

            Glide.with(getActivity())
                    .load(file)
                    .into(comicViewHolder.thumbnail);

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
                    cv.setBackgroundColor(getResources().getColor(R.color.background_material_dark));
                articleTitle = (TextView) itemView.findViewById(R.id.article_title);
                thumbnail = (ImageView) itemView.findViewById(R.id.thumbnail);
            }
        }
    }

    class CustomOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                int pos = rv.getChildAdapterPosition(v);
                Intent intent = new Intent(getActivity(), WhatIfActivity.class);
                String title = adapter.titles.get(pos);
                int n = mTitles.size() - mTitles.indexOf(title);
                WhatIfActivity.WhatIfIndex = n;
                startActivity(intent);
                Log.d("index", String.valueOf(n));

                PrefHelper.setLastWhatIf(n);
                PrefHelper.setWhatifRead(String.valueOf(n));
                if (searchMenuItem.isActionViewExpanded()) {
                    searchMenuItem.collapseActionView();
                    rv.scrollToPosition(mTitles.size() - n);
                }
            } else {
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7);
            }
        }
    }

    public void getRandom() {
        Random mRand = new Random();
        int number = mRand.nextInt(adapter.titles.size());
        Intent intent = new Intent(getActivity(), WhatIfActivity.class);
        String title = adapter.titles.get(number);
        int n = mTitles.size() - mTitles.indexOf(title);
        WhatIfActivity.WhatIfIndex = n;
        startActivity(intent);
        PrefHelper.setLastWhatIf(n);
        PrefHelper.setWhatifRead(String.valueOf(n));
    }

    /*private void openRandomWhatIf() {

        Random mRand = new Random();
        int number = mRand.nextInt(adapter.titles.size());
        Intent intent = new Intent(getActivity(), WhatIfActivity.class);
        String title = adapter.titles.get(number);
        int n = mTitles.size() - mTitles.indexOf(title);
        WhatIfActivity.WhatIfIndex = n;
        startActivity(intent);
        PrefHelper.setLastWhatIf(n);
        PrefHelper.setWhatifRead(String.valueOf(n));

    }*/

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

   /* class CustomOnScrollListener extends RecyclerView.OnScrollListener {
        int scrollDist = 0;
        boolean isVisible = true;
        static final float MINIMUM = 25;

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            if (isVisible && scrollDist > MINIMUM) {
                Resources r = getActivity().getResources();
                float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, r.getDisplayMetrics());
                fab.animate().translationY(fab.getHeight() + px).setInterpolator(new AccelerateInterpolator(2)).start();
                scrollDist = 0;
                isVisible = false;
            } else if (!isVisible && scrollDist < -MINIMUM) {
                fab.animate().translationY(0).setInterpolator(new DecelerateInterpolator(2)).start();
                scrollDist = 0;
                isVisible = true;
            }
            if ((isVisible && dy > 0) || (!isVisible && dy < 0)) {
                scrollDist += dy;
            }
        }
    }*/

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_unread:
                PrefHelper.setAllUnread();
                adapter.notifyDataSetChanged();
                return true;
            case R.id.action_hide_read:
                item.setChecked(!item.isChecked());
                PrefHelper.setHideRead(item.isChecked());
                if (item.isChecked()) {
                    ArrayList<String> titleUnread = new ArrayList<>();
                    for (int i = 0; i < mTitles.size(); i++) {
                        if (!PrefHelper.checkRead(mTitles.size() - i)) {
                            titleUnread.add(mTitles.get(i));
                        }
                    }
                    adapter = new RVAdapter(titleUnread);
                    SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
                    slideAdapter.setInterpolator(new DecelerateInterpolator());
                    rv.setAdapter(slideAdapter);
                    rv.scrollToPosition(titleUnread.size() - PrefHelper.getLastWhatIf());
                } else {
                    adapter = new RVAdapter(mTitles);
                    SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
                    slideAdapter.setInterpolator(new DecelerateInterpolator());
                    rv.setAdapter(slideAdapter);
                    rv.scrollToPosition(mTitles.size() - PrefHelper.getLastWhatIf());
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void updateRv() {
        if (PrefHelper.hideRead()) {
            ArrayList<String> titleUnread = new ArrayList<>();
            for (int i = 0; i < mTitles.size(); i++) {
                if (!PrefHelper.checkRead(mTitles.size() - i)) {
                    titleUnread.add(mTitles.get(i));
                }
            }
            adapter = new RVAdapter(titleUnread);
            SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
            slideAdapter.setInterpolator(new DecelerateInterpolator());
            rv.setAdapter(slideAdapter);
            rv.scrollToPosition(titleUnread.size() - PrefHelper.getLastWhatIf());
        } else {
            adapter = new RVAdapter(mTitles);
            SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
            slideAdapter.setInterpolator(new DecelerateInterpolator());
            rv.setAdapter(slideAdapter);
            rv.scrollToPosition(mTitles.size() - PrefHelper.getLastWhatIf());
        }
    }

    public static OfflineWhatIfFragment getInstance() {
        return instance;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

        // Get the SearchView and set the searchable configuration
        SearchManager searchManager = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
        final SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getActivity().getComponentName()));
        searchView.setIconifiedByDefault(false);
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
                for (int i = 0; i < mTitles.size(); i++) {
                    if (mTitles.get(i).toLowerCase().contains(newText.toLowerCase().trim())) {
                        titleResults.add(mTitles.get(i));
                    }
                }
                adapter = new RVAdapter(titleResults);
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


                //fab.setVisibility(View.INVISIBLE);
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
                adapter = new RVAdapter(mTitles);
                SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
                slideAdapter.setInterpolator(new DecelerateInterpolator());
                rv.setAdapter(slideAdapter);
                searchView.setQuery("", false);

                /*fab.setVisibility(View.VISIBLE);
                fab.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.grow));*/
                ((WhatIfOverviewFragment) getParentFragment()).fab.show();
                return true;
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
    }

    private boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    private boolean isWifi() {
        ConnectivityManager cm = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return (info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        ButterKnife.unbind(this);
    }
}
