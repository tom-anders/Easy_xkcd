package de.tap.easy_xkcd.fragments.whatIf;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import android.os.Handler;
import android.os.Looper;
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

import com.bumptech.glide.request.RequestOptions;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;
import com.tap.xkcd_reader.R;

import org.jetbrains.annotations.NotNull;
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
import java.util.Objects;
import java.util.Random;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.Activities.WhatIfActivity;
import de.tap.easy_xkcd.GlideApp;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.database.RealmComic;
import de.tap.easy_xkcd.utils.Article;
import de.tap.easy_xkcd.utils.JsonParser;
import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.utils.ThemePrefs;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;
import jp.wasabeef.recyclerview.adapters.SlideInBottomAnimationAdapter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import timber.log.Timber;

public class WhatIfFragment extends Fragment {

    @Bind(R.id.rv)
    FastScrollRecyclerView rv;
    private MenuItem searchMenuItem;
    private MenuItem favoritesItem;
    public static RVAdapter adapter;
    private static WhatIfFragment instance;

    private boolean offlineMode;
    private static final String OFFLINE_WHATIF_OVERVIEW_PATH = "/easy xkcd/what if/overview";
    private static final String OFFLINE_WHATIF_PATH = "/easy xkcd/what if/";
    private static final String WHATIF_INTENT = "de.tap.easy_xkcd.ACTION_WHAT_IF";
    private PrefHelper prefHelper;
    private DatabaseManager databaseManager;
    private ThemePrefs themePrefs;

    // Used for starting the WhatIfActivity. When the activity finishes,
    // we use this to update our recycler view accordingly
    private static final int WHATIF_REQUEST_CODE = 100;

    // Static because we want to save this across instances of the fragment
    // This is used so that we only ever reload the WhatIf overview once at startup
    private static boolean overviewUpdated = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.recycler_layout, container, false);
        ButterKnife.bind(this, v);
        setHasOptionsMenu(true);
        prefHelper = ((MainActivity) getActivity()).getPrefHelper();
        themePrefs = ((MainActivity) getActivity()).getThemePrefs();
        databaseManager = ((MainActivity) getActivity()).getDatabaseManager();

        LinearLayoutManager llm = new LinearLayoutManager(getActivity());
        rv.setLayoutManager(llm);
        rv.setHasFixedSize(false);

        rv.setVerticalScrollBarEnabled(false);

        offlineMode = prefHelper.fullOfflineWhatIf();
        instance = this;
        ((MainActivity) getActivity()).getFab().setVisibility(View.GONE);

        if (!overviewUpdated && prefHelper.isOnline(getActivity())) {
            if (!prefHelper.fullOfflineWhatIf() || prefHelper.mayDownloadDataForOfflineMode(getActivity())) {
                ProgressDialog progress = new ProgressDialog(getActivity());
                progress.setMessage(getResources().getString(R.string.loading_articles));
                progress.setIndeterminate(true);
                progress.setCancelable(false);
                progress.show();
                databaseManager.updateWhatifDatabase(prefHelper)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnError(Timber::e)
                        .doFinally(() -> {
                            progress.dismiss();
                            displayOverview();
                        })
                        .subscribe();
            }
        } else {
            displayOverview();
        }
        return v;
    }


    void displayOverview() {
        setupAdapter();

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

        Intent mainIntent = getActivity().getIntent();
        if (mainIntent != null) {
            if (Objects.equals(mainIntent.getAction(), Intent.ACTION_VIEW)) {
                displayWhatIf(MainActivity.getNumberFromUrl(mainIntent.getDataString(), 1));
            } else if (Objects.equals(mainIntent.getAction(), WHATIF_INTENT)) {
                displayWhatIf(mainIntent.getIntExtra("number", 1));
            }
        }
    }

    private class RVAdapter extends RecyclerView.Adapter<RVAdapter.ComicViewHolder>
            implements FastScrollRecyclerView.SectionedAdapter, View.OnClickListener, View.OnLongClickListener { //TODO color of fast scroller in night mode?
        private RealmResults<Article> articles;

        final private DatabaseManager databaseManager;

        @NonNull
        @Override
        public String getSectionName(int position) {
            return "B";
        }

        public RVAdapter(RealmResults<Article> articles, MainActivity activity) {
            this.articles = articles;

            databaseManager = activity.getDatabaseManager();
        }

        public void setArticles(RealmResults<Article> articles) {
            this.articles = articles;
            notifyDataSetChanged();
        }

        private CircularProgressDrawable getCircularProgress() {
            CircularProgressDrawable circularProgressDrawable = new CircularProgressDrawable(getActivity());
            circularProgressDrawable.setCenterRadius(60.0f);
            circularProgressDrawable.setStrokeWidth(5.0f);
            circularProgressDrawable.setColorSchemeColors(themePrefs.getAccentColor());
            circularProgressDrawable.start();
            return circularProgressDrawable;
        }

        @Override
        public ComicViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.whatif_overview, viewGroup, false);
            v.setOnClickListener(this);
            v.setOnLongClickListener(this);
            return new ComicViewHolder(v);
        }

        @Override
        public void onClick(View view) {
            if (!prefHelper.isOnline(getActivity()) && !offlineMode) {
                Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
                return;
            }
            displayWhatIf(articles.get(rv.getChildAdapterPosition(view)).getNumber());
        }

        @Override
        public boolean onLongClick(View view) {
            Article article = articles.get(rv.getChildAdapterPosition(view));

            int array = article.isFavorite() ? R.array.whatif_card_long_click_remove : R.array.whatif_card_long_click;

            new AlertDialog.Builder(getActivity()).setItems(array, (dialog, which) -> {
                switch (which) {
                    case 0:
                        Intent share = new Intent(Intent.ACTION_SEND);
                        share.setType("text/plain");
                        share.putExtra(Intent.EXTRA_SUBJECT, "What if: " + article.getTitle());
                        share.putExtra(Intent.EXTRA_TEXT, "https://what-if.xkcd.com/" + article.getNumber());
                        startActivity(share);
                        break;
                    case 1:
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://what-if.xkcd.com/" + article.getNumber())));
                        break;
                    case 2:
                        Realm realm = Realm.getDefaultInstance();
                        realm.beginTransaction();
                        article.setFavorite(!article.isFavorite());
                        realm.copyToRealmOrUpdate(article);
                        realm.commitTransaction();
                        realm.close();

                        setupAdapter();
                        break;
                }
            }).create().show();
            return true;
        }

        @Override
        public void onBindViewHolder(final RVAdapter.ComicViewHolder comicViewHolder, int i) {
            Article article = articles.get(i);
            comicViewHolder.articleTitle.setText(article.getTitle());
            comicViewHolder.articleNumber.setText(String.valueOf(article.getNumber()));

            int id = databaseManager.getWhatIfMissingThumbnailId(article.getTitle());
            if (id != 0) {
                comicViewHolder.thumbnail.setImageDrawable(ContextCompat.getDrawable(getActivity(), id));
            } else if (prefHelper.fullOfflineWhatIf()) {
                File offlinePath = prefHelper.getOfflinePath();
                File dir = new File(offlinePath.getAbsolutePath() + OFFLINE_WHATIF_OVERVIEW_PATH);
                File file = new File(dir, article.getNumber() + ".png");

                GlideApp.with(getActivity())
                        .load(file)
                        .apply(new RequestOptions().placeholder(getCircularProgress()))
                        .into(comicViewHolder.thumbnail);
            } else {
                GlideApp.with(getActivity())
                        .load(article.getThumbnail())
                        .apply(new RequestOptions().placeholder(getCircularProgress()))
                        .into(comicViewHolder.thumbnail);
            }

            if (article.isRead()) {
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
        }

        @Override
        public int getItemCount() {
            return articles.size();
        }

        public class ComicViewHolder extends RecyclerView.ViewHolder {
            CardView cv;
            TextView articleTitle;
            TextView articleNumber;
            ImageView thumbnail;

            ComicViewHolder(View itemView) {
                super(itemView);
                cv = itemView.findViewById(R.id.cv);
                if (themePrefs.amoledThemeEnabled()) {
                    cv.setCardBackgroundColor(Color.BLACK);
                } else if (themePrefs.nightThemeEnabled()) {
                    cv.setCardBackgroundColor(ContextCompat.getColor(getActivity(), R.color.background_material_dark));
                }
                articleTitle = itemView.findViewById(R.id.article_title);
                articleNumber = itemView.findViewById(R.id.article_info);
                thumbnail = itemView.findViewById(R.id.thumbnail);
                if (themePrefs.invertColors(false) || themePrefs.amoledThemeEnabled())
                    thumbnail.setColorFilter(themePrefs.getNegativeColorFilter());
            }
        }
    }

//    public class WhatIfRVAdapter extends WhatIfOverviewFragment.RVAdapter {
//
//
//        @Override
//        public void onBindViewHolder(final ComicViewHolder comicViewHolder, int i) {
//        }
//    }

    private void displayWhatIf(int number) {
        Intent intent = new Intent(getActivity(), WhatIfActivity.class);
        intent.putExtra(WhatIfActivity.INTENT_NUMBER, number);
        startActivityForResult(intent, WHATIF_REQUEST_CODE);
    }

    public void getRandom() {
        if (prefHelper.isOnline(getActivity()) | offlineMode) {
            displayWhatIf(new Random().nextInt(adapter.getItemCount()));
        } else {
            Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_favorite:
                item.setChecked(!item.isChecked());
                item.setIcon(item.isChecked() ? R.drawable.ic_favorite_on_24dp : R.drawable.ic_favorite_off_24dp);
                setupAdapter();
                return true;
            case R.id.action_unread:
                databaseManager.setAllArticlesReadStatus(false);
                setupAdapter();
                return true;
            case R.id.action_all_read:
                databaseManager.setAllArticlesReadStatus(true);
                setupAdapter();
                return true;
            case R.id.action_hide_read:
                item.setChecked(!item.isChecked());
                prefHelper.setHideReadWhatIf(item.isChecked());
                setupAdapter();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupAdapter() {
        Realm realm = Realm.getDefaultInstance();
        RealmResults<Article> articles;
        if (favoritesItem != null && favoritesItem.isChecked()) {
            articles = realm.where(Article.class).equalTo("favorite", true).findAll();
        } else if (prefHelper.hideReadWhatIf()) {
            articles = realm.where(Article.class).equalTo("read", false).findAll();
        } else {
            articles = realm.where(Article.class).findAll();
        }
        articles.sort("number", Sort.DESCENDING);
        adapter = new RVAdapter(articles, (MainActivity) getActivity());

        SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
        slideAdapter.setInterpolator(new DecelerateInterpolator());
        rv.setAdapter(slideAdapter);

        if (!prefHelper.hideRead()) {
            rv.getLayoutManager().scrollToPosition(articles.size() - prefHelper.getLastWhatIf());
        }

//        if (hideRead) {
//            ArrayList<String> titleUnread = new ArrayList<>();
//            ArrayList<String> imgUnread = new ArrayList<>();
//            for (int i = 0; i < mTitles.size(); i++) {
//                if (!prefHelper.checkRead(mTitles.size() - i)) {
//                    titleUnread.add(mTitles.get(i));
//                    if (!offlineMode)
//                        imgUnread.add(mImgs.get(i));
//                }
//            }
//            adapter = new WhatIfRVAdapter(titleUnread, imgUnread, (MainActivity) getActivity());
//
//        } else {
//            adapter = new WhatIfRVAdapter(mTitles, mImgs, (MainActivity) getActivity());
//            SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
//            slideAdapter.setInterpolator(new DecelerateInterpolator());
//            rv.setAdapter(slideAdapter);
//            rv.scrollToPosition(mTitles.size() - prefHelper.getLastWhatIf());
//        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_what_if_fragment, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);

        menu.findItem(R.id.action_hide_read).setChecked(prefHelper.hideReadWhatIf());

        final SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setIconifiedByDefault(false);
        searchView.setQueryHint(getResources().getString(R.string.search_hint_whatif));
        searchMenuItem = menu.findItem(R.id.action_search);
        searchMenuItem.setVisible(true);

        favoritesItem = menu.findItem(R.id.action_favorite);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                //TODO refactor to use realm. Probably setupAdapter should take a parameter RealmResult<Article>?

                Realm realm = Realm.getDefaultInstance();
                RealmResults<Article> articles = realm.where(Article.class).contains("title", newText, Case.INSENSITIVE).findAll();
                articles.sort("number", Sort.DESCENDING);
                adapter.setArticles(articles);
                realm.close();

//                ArrayList<String> titleResults = new ArrayList<>();
//                ArrayList<String> imgResults = new ArrayList<>();
//                for (int i = 0; i < mTitles.size(); i++) {
//                    if (mTitles.get(i).toLowerCase().contains(newText.toLowerCase().trim())) {
//                        titleResults.add(mTitles.get(i));
//                        if (!offlineMode)
//                            imgResults.add(mImgs.get(i));
//                    }
//                }
//                adapter = new WhatIfRVAdapter(titleResults, imgResults, (MainActivity) getActivity());
//                SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
//                slideAdapter.setInterpolator(new DecelerateInterpolator());
//                rv.setAdapter(slideAdapter);
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

                //TODO Hack this back in?!
//                ((WhatIfOverviewFragment) getParentFragment()).fab.hide();
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                View view = getActivity().getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
                searchView.setQuery("", false);

//                adapter = new WhatIfRVAdapter(mTitles, mImgs, (MainActivity) getActivity());
//                SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
//                slideAdapter.setInterpolator(new DecelerateInterpolator());
//                rv.setAdapter(slideAdapter);
//                searchView.setQuery("", false);
//                ((WhatIfOverviewFragment) getParentFragment()).fab.show();
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
        Timber.d("destroyed");
        ButterKnife.unbind(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable @org.jetbrains.annotations.Nullable Intent data) {
        if (requestCode == WHATIF_REQUEST_CODE) {
            if (searchMenuItem.isActionViewExpanded()) {
                searchMenuItem.collapseActionView();
            }

            setupAdapter();
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
}
