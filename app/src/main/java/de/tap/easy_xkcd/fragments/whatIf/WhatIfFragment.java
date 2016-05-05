package de.tap.easy_xkcd.fragments.whatIf;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
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
import android.widget.SearchView;
import android.widget.Toast;

import com.tap.xkcd_reader.R;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.Activities.WhatIfActivity;
import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.utils.ThemePrefs;
import jp.wasabeef.recyclerview.animators.adapters.SlideInBottomAnimationAdapter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

public class WhatIfFragment extends android.support.v4.app.Fragment {

    public static ArrayList<String> mTitles = new ArrayList<>();
    private static ArrayList<String> mImgs = new ArrayList<>();
    @Bind(R.id.rv)
    RecyclerView rv;
    private MenuItem searchMenuItem;
    public static WhatIfRVAdapter adapter;
    private static WhatIfFragment instance;
    public static boolean newIntent;
    private boolean offlineMode;
    private static final String OFFLINE_WHATIF_OVERVIEW_PATH = "/easy xkcd/what if/overview";
    private static final String OFFLINE_WHATIF_PATH = "/easy xkcd/what if/";
    private PrefHelper prefHelper;
    private ThemePrefs themePrefs;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.recycler_layout, container, false);
        ButterKnife.bind(this, v);
        setHasOptionsMenu(true);
        prefHelper = ((MainActivity) getActivity()).getPrefHelper();
        themePrefs = ((MainActivity) getActivity()).getThemePrefs();

        LinearLayoutManager llm = new LinearLayoutManager(getActivity());
        rv.setLayoutManager(llm);
        rv.setHasFixedSize(false);

        offlineMode = prefHelper.fullOfflineWhatIf();
        instance = this;
        ((MainActivity) getActivity()).getFab().setVisibility(View.GONE);

        if (prefHelper.isOnline(getActivity()) && (prefHelper.isWifi(getActivity()) | prefHelper.mobileEnabled()) && offlineMode) {
            new UpdateArticles().execute();
            Log.d("info", "update started");
        } else {
            new DisplayOverview().execute();
        }
        return v;
    }

    private class UpdateArticles extends AsyncTask<Void, Void, Void> {
        private ProgressDialog progress;
        private boolean showProgress;
        private OkHttpClient client;

        @Override
        protected void onPreExecute() {
            showProgress = ((MainActivity) getActivity()).getCurrentFragment() == R.id.nav_whatif;
            if (showProgress) {
                progress = new ProgressDialog(getActivity());
                progress.setMessage(getResources().getString(R.string.loading_articles));
                progress.setIndeterminate(true);
                progress.setCancelable(false);
                progress.show();
            }
            client = new OkHttpClient();
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Override
        protected Void doInBackground(Void... dummy) {
            int highestOffline = prefHelper.getNewestWhatIf();
            try {
                Document doc = Jsoup.connect("https://what-if.xkcd.com/archive/")
                        .userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/32.0.1700.19 Safari/537.36")
                        .get();
                Elements titles = doc.select("h1");
                Elements img = doc.select("img.archive-image");
                if (titles.size() > prefHelper.getNewestWhatIf()) {
                    Log.d("what if", "updating overview");
                    prefHelper.setNewestWhatif(titles.size());

                    StringBuilder sb = new StringBuilder();
                    sb.append(titles.first().text());
                    titles.remove(0);
                    for (Element title : titles) {
                        sb.append("&&");
                        sb.append(title.text());
                    }
                    prefHelper.setWhatIfTitles(sb.toString());

                    File sdCard = prefHelper.getOfflinePath();
                    File dir = new File(sdCard.getAbsolutePath() + OFFLINE_WHATIF_OVERVIEW_PATH);
                    if (!dir.exists()) dir.mkdirs();
                    for (int i = prefHelper.getNewestWhatIf(); i < titles.size() + 1; i++) {
                        String url = img.get(i).absUrl("src");
                        try {
                            File file = new File(dir, String.valueOf(i + 1) + ".png");
                            Request request = new Request.Builder()
                                    .url(url)
                                    .build();
                            Response response = client.newCall(request).execute();
                            BufferedSink sink = Okio.buffer(Okio.sink(file));
                            sink.writeAll(response.body().source());
                            sink.close();
                            response.body().close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            for (int i = highestOffline + 1; i <= prefHelper.getNewestWhatIf(); i++) {
                downloadArticle(i);
            }

            //this What If failed downloading when it first came out
            if (!prefHelper.sunBeamDownloaded())
                downloadArticle(141);

            if (!prefHelper.nomediaCreated()) {
                File sdCard = prefHelper.getOfflinePath();
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

        private void downloadArticle(int i) {
            Log.d("what if", "downloading " + i);
            if (i == 141) prefHelper.setSunbeamLoaded();
            Document doc;
            File sdCard = prefHelper.getOfflinePath();
            File dir;
            try {
                doc = Jsoup.connect("https://what-if.xkcd.com/" + String.valueOf(i)).get();
                dir = new File(sdCard.getAbsolutePath() + OFFLINE_WHATIF_PATH + String.valueOf(i));
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, String.valueOf(i) + ".html");
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                writer.write(doc.outerHtml());
                writer.close();
                //download images
                int count = 1;
                for (Element e : doc.select(".illustration")) {
                    try {
                        String url = "http://what-if.xkcd.com" + e.attr("src");
                        Request request = new Request.Builder()
                                .url(url)
                                .build();
                        Response response = client.newCall(request).execute();
                        file = new File(dir, String.valueOf(count) + ".png");
                        BufferedSink sink = Okio.buffer(Okio.sink(file));
                        sink.writeAll(response.body().source());
                        sink.close();
                        response.body().close();
                        count++;
                    } catch (Exception e2) {
                        Log.e("article" + i, e2.getMessage());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        protected void onPostExecute(Void dummy) {
            if (showProgress)
                progress.dismiss();
            new DisplayOverview().execute();
        }

    }

    private class DisplayOverview extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... dummy) {
            mTitles.clear();
            mImgs.clear();

            if (offlineMode) {
                mTitles = prefHelper.getWhatIfTitles();
                Collections.reverse(mTitles);
            } else {
                Document doc = WhatIfOverviewFragment.doc;
                Elements titles = doc.select("h1");
                Elements imagelinks = doc.select("img.archive-image");

                boolean bowlingFixed = false; //This title appears twice, so add " " to one of the titles to make everything work later
                    for (Element title : titles) {
                        if (!bowlingFixed && title.text().equals("Bowling Ball")) {
                            mTitles.add(title.text() + " ");
                            bowlingFixed = true;
                        } else
                            mTitles.add(title.text());
                    }

                for (Element image : imagelinks)
                    mImgs.add(image.absUrl("src"));

                Collections.reverse(mTitles);
                Collections.reverse(mImgs);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            prefHelper.setNewestWhatif(mTitles.size());
            setupAdapter(prefHelper.hideReadWhatIf());

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

    public class WhatIfRVAdapter extends WhatIfOverviewFragment.RVAdapter {
        public WhatIfRVAdapter(ArrayList<String> t, ArrayList<String> i, MainActivity activity) {
            super(t,i, activity);
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
            if (prefHelper.checkRead(titles.size() - titles.indexOf(titles.get(i)))) {
                if (themePrefs.nightThemeEnabled())
                    comicViewHolder.articleTitle.setTextColor(ContextCompat.getColor(getActivity(), android.R.color.tertiary_text_light));
                else
                    comicViewHolder.articleTitle.setTextColor(ContextCompat.getColor(getActivity(), R.color.Read));
            } else {
                if (themePrefs.nightThemeEnabled())
                    comicViewHolder.articleTitle.setTextColor(ContextCompat.getColor(getActivity(), R.color.Read));
                else
                    comicViewHolder.articleTitle.setTextColor(ContextCompat.getColor(getActivity(), android.R.color.tertiary_text_light));
            }
            super.onBindViewHolder(comicViewHolder, i);
        }
    }

    private class CustomOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            if (!prefHelper.isOnline(getActivity()) && !offlineMode) {
                Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
                return;
            }
            int pos = rv.getChildAdapterPosition(v);
            Intent intent = new Intent(getActivity(), WhatIfActivity.class);
            String title = adapter.titles.get(pos);
            int n = mTitles.size() - mTitles.indexOf(title);
            WhatIfActivity.WhatIfIndex = n;
            startActivity(intent);

            prefHelper.setLastWhatIf(n);
            prefHelper.setWhatifRead(String.valueOf(n));
            if (searchMenuItem.isActionViewExpanded()) {
                searchMenuItem.collapseActionView();
                rv.scrollToPosition(mTitles.size() - n);
            }
        }
    }

    public void getRandom() {
        if (prefHelper.isOnline(getActivity()) | offlineMode) {
            Random mRand = new Random();
            int number = mRand.nextInt(adapter.titles.size());
            Intent intent = new Intent(getActivity(), WhatIfActivity.class);
            String title = adapter.titles.get(number);
            int n = mTitles.size() - mTitles.indexOf(title);
            WhatIfActivity.WhatIfIndex = n;
            startActivity(intent);
            prefHelper.setLastWhatIf(n);
            prefHelper.setWhatifRead(String.valueOf(n));
        } else {
            Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
        }
    }

    class CustomOnLongClickListener implements View.OnLongClickListener {
        @Override
        public boolean onLongClick(final View v) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            int pos = rv.getChildAdapterPosition(v);
            final String title = adapter.titles.get(pos);
            final int n = mTitles.size() - mTitles.indexOf(title);
            int array = prefHelper.checkWhatIfFav(n) ? R.array.whatif_card_long_click_remove : R.array.whatif_card_long_click;
            builder.setItems(array, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    int pos;
                    int n;
                    String title;
                    switch (which) {
                        case 0:
                            pos = rv.getChildAdapterPosition(v);
                            title = adapter.titles.get(pos);
                            n = mTitles.size() - mTitles.indexOf(title);
                            Intent share = new Intent(Intent.ACTION_SEND);
                            share.setType("text/plain");
                            share.putExtra(Intent.EXTRA_SUBJECT, "What if: " + title);
                            share.putExtra(Intent.EXTRA_TEXT, "http://what-if.xkcd.com/" + String.valueOf(n));
                            startActivity(share);
                            break;
                        case 1:
                            pos = rv.getChildAdapterPosition(v);
                            title = adapter.titles.get(pos);
                            n = mTitles.size() - mTitles.indexOf(title);
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://what-if.xkcd.com/" + String.valueOf(n)));
                            startActivity(intent);
                            break;
                        case 2:
                            pos = rv.getChildAdapterPosition(v);
                            title = adapter.titles.get(pos);
                            n = mTitles.size() - mTitles.indexOf(title);
                            if (!prefHelper.checkWhatIfFav(n)) {
                                prefHelper.setWhatIfFavorite(String.valueOf(n));
                            } else {
                                prefHelper.removeWhatifFav(n);
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
                prefHelper.setAllUnread();
                setupAdapter(prefHelper.hideReadWhatIf());
                return true;
            case R.id.action_all_read:
                prefHelper.setAllWhatIfRead();
                setupAdapter(prefHelper.hideReadWhatIf());
            case R.id.action_hide_read:
                item.setChecked(!item.isChecked());
                prefHelper.setHideReadWhatIf(item.isChecked());
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
                if (!prefHelper.checkRead(mTitles.size() - i)) {
                    titleUnread.add(mTitles.get(i));
                    if (!offlineMode)
                        imgUnread.add(mImgs.get(i));
                }
            }
            adapter = new WhatIfRVAdapter(titleUnread, imgUnread, (MainActivity) getActivity());
            SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
            slideAdapter.setInterpolator(new DecelerateInterpolator());
            rv.setAdapter(slideAdapter);
            rv.scrollToPosition(titleUnread.size() - prefHelper.getLastWhatIf());
        } else {
            adapter = new WhatIfRVAdapter(mTitles, mImgs, (MainActivity) getActivity());
            SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
            slideAdapter.setInterpolator(new DecelerateInterpolator());
            rv.setAdapter(slideAdapter);
            rv.scrollToPosition(mTitles.size() - prefHelper.getLastWhatIf());
        }
    }

    public void updateRv() {
        setupAdapter(prefHelper.hideReadWhatIf());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.findItem(R.id.action_hide_read).setChecked(prefHelper.hideReadWhatIf());
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
                adapter = new WhatIfRVAdapter(titleResults, imgResults, (MainActivity) getActivity());
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
                adapter = new WhatIfRVAdapter(mTitles, mImgs, (MainActivity) getActivity());
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
