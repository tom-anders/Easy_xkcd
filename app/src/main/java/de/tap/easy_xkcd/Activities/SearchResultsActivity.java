/**
 * *******************************************************************************
 * Copyright 2015 Tom Praschan
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
package de.tap.easy_xkcd.Activities;

import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
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
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.kogitune.activity_transition.ActivityTransitionLauncher;
import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.database.RealmComic;
import de.tap.easy_xkcd.fragments.comics.ComicBrowserFragment;
import de.tap.easy_xkcd.fragments.comics.OfflineFragment;
import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmResults;
import jp.wasabeef.recyclerview.animators.adapters.SlideInBottomAnimationAdapter;


public class SearchResultsActivity extends BaseActivity {
    private ArrayList<Integer> resultsTitle = new ArrayList<>();
    private ArrayList<Integer> resultsTranscript = new ArrayList<>();
    @Bind(R.id.rv)
    RecyclerView rv;
    @Bind(R.id.toolbar)
    Toolbar toolbar;
    private searchTask task;
    private ProgressDialog mProgress;
    private String query;
    public static boolean isOpen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_results);
        ButterKnife.bind(this);
        DatabaseManager databaseManager = new DatabaseManager(this);
        setupToolbar(toolbar);

        LinearLayoutManager llm = new LinearLayoutManager(this);
        rv.setLayoutManager(llm);
        rv.setHasFixedSize(true);

        Intent intent = getIntent();
        if (savedInstanceState == null)
            query = intent.getStringExtra(SearchManager.QUERY);
        else
            query = savedInstanceState.getString("query");

        getSupportActionBar().setTitle(getResources().getString(R.string.title_activity_search_results) + " " + query);
        mProgress = ProgressDialog.show(this, "", getResources().getString(R.string.loading_results), true);

        if (savedInstanceState == null)
            databaseManager.new updateComicDatabase(this, null, prefHelper).execute();
        else {
            task = new searchTask();
            task.execute(query);
        }
    }

    /**
     * Called by the databaseManager in order to start the search after the database has been updated
     */
    public void updateDatabasePostExecute() {
        task = new searchTask();
        task.execute(query);
    }

    /**
     * Checks if the query is integer or text and invokes the according search algorithm
     *
     * @param query the query entered by the user
     * @return false if there haven't been any search results
     */
    private boolean performSearch(String query) {
        if (checkInteger(query)) {
            return getComicByNumber(Integer.parseInt(query));
        } else {
            return searchComicTitleOrTranscript(query);
        }
    }

    /**
     * Shows the comic in the Comic Browser
     *
     * @param number the comic to be shown
     * @return always true to let the activity know that it doesn't have to do any more work
     */
    private boolean getComicByNumber(int number) {
        Intent intent = new Intent("de.tap.easy_xkcd.ACTION_COMIC");
        if ((number > prefHelper.getNewest() && number > prefHelper.getHighestOffline()) | number < 1) //check if the number is a valid comic
            intent.putExtra("number", prefHelper.getNewest());
        else
            intent.putExtra("number", number);
        mProgress.dismiss();
        startActivity(intent);
        if (task != null)
            task.cancel(true);
        return true;
    }

    /**
     * Searches the database for the comics containing the query
     * @return false if there haven't been any search results
     */

    private boolean searchComicTitleOrTranscript(String query) {
        Realm realm = Realm.getInstance(SearchResultsActivity.this);
        query = query.trim();
        resultsTitle.clear();
        resultsTranscript.clear();
        RealmResults<RealmComic> title = realm.where(RealmComic.class).contains("title", query, Case.INSENSITIVE).findAll();
        for (RealmComic comic : title)
            resultsTitle.add(comic.getComicNumber());
        RealmResults<RealmComic> trans = realm.where(RealmComic.class).contains("transcript", query, Case.INSENSITIVE).not().contains("title", query, Case.INSENSITIVE).findAll();
        for (RealmComic comic : trans)
            this.resultsTranscript.add(comic.getComicNumber());
        realm.close();
        return (resultsTranscript.size() + resultsTitle.size() == 0);
    }

    private class searchTask extends AsyncTask<String, Void, Void> {
        private Boolean searchSuccessful;

        @Override
        protected Void doInBackground(String... params) {
            query = params[0];
            searchSuccessful = performSearch(params[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            if (!searchSuccessful) {
                RVAdapter adapter = new RVAdapter();
                SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
                slideAdapter.setInterpolator(new DecelerateInterpolator());
                rv.setAdapter(slideAdapter);
                assert getSupportActionBar() != null;
                getSupportActionBar().setTitle(getResources().getString(R.string.title_activity_search_results) + " " + query);
            } else
                Toast.makeText(SearchResultsActivity.this, R.string.search_error, Toast.LENGTH_SHORT).show();
            mProgress.dismiss();
        }
    }

    public class RVAdapter extends RecyclerView.Adapter<RVAdapter.ComicViewHolder> {
        private Realm realm;

        public RVAdapter() {
            realm = Realm.getInstance(SearchResultsActivity.this);
        }

        @Override
        public int getItemCount() {
            return resultsTitle.size() + resultsTranscript.size();
        }

        @Override
        public ComicViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.search_result, viewGroup, false);
            v.setOnClickListener(new CustomOnClickListener());
            return new ComicViewHolder(v);
        }

        @Override
        public void onBindViewHolder(final ComicViewHolder comicViewHolder, int i) {
            RealmComic comic;
            if (i < resultsTitle.size())
                comic = realm.where(RealmComic.class).equalTo("comicNumber", resultsTitle.get(i)).findFirst();
            else
                comic = realm.where(RealmComic.class).equalTo("comicNumber", resultsTranscript.get(i - resultsTitle.size())).findFirst();

            int number = comic.getComicNumber();
            String title = comic.getTitle();
            String preview = getPreview(query, comic.getTranscript());
            String url = comic.getUrl();

            comicViewHolder.comicTitle.setText(title);
            if (i < resultsTitle.size())
                comicViewHolder.comicInfo.setText(String.valueOf(number));
            else
                comicViewHolder.comicInfo.setText(Html.fromHtml(preview));

            //Load the thumbnail
            if (!MainActivity.fullOffline) {
                Glide.with(SearchResultsActivity.this)
                        .load(url)
                        .asBitmap()
                        .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                        .into(comicViewHolder.thumbnail);
            } else {
                try {
                    FileInputStream fis = openFileInput(String.valueOf(number));
                    Bitmap mBitmap = BitmapFactory.decodeStream(fis);
                    fis.close();
                    comicViewHolder.thumbnail.setImageBitmap(mBitmap);
                } catch (Exception e) {
                    Log.e("Error", "loading from internal storage failed");
                    try {
                        File sdCard = prefHelper.getOfflinePath();
                        File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                        File file = new File(dir, String.valueOf(number) + ".png");
                        Glide.with(getApplicationContext())
                                .load(file)
                                .asBitmap()
                                .into(comicViewHolder.thumbnail);
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
                    cv.setCardBackgroundColor(ContextCompat.getColor(SearchResultsActivity.this, R.color.background_material_dark));
                comicTitle = (TextView) itemView.findViewById(R.id.comic_title);
                comicInfo = (TextView) itemView.findViewById(R.id.comic_info);
                thumbnail = (ImageView) itemView.findViewById(R.id.thumbnail);
            }
        }

    }

    /**
     * Creates a preview for the transcript of comics that contain the query
     * @param query the users's query
     * @param transcript the comic's transcript
     * @return a short preview of the transcript with the query highlighted
     */
    private String getPreview(String query, String transcript) {
        String firstWord = query.split(" ")[0].toLowerCase();
        transcript = transcript.replace(".", ". ").replace("?", "? ").replace("]]", " ").replace("[[", " ").replace("{{", " ").replace("}}", " ");
        ArrayList<String> words = new ArrayList<>(Arrays.asList(transcript.toLowerCase().split(" ")));
        int i = 0;
        boolean found = false;
        while (!found && i < words.size()) {
            if (query.length() < 5)
                found = words.get(i).matches(".*\\b" + firstWord + "\\b.*");
            else
                found = words.get(i).contains(firstWord);

            if (!found) i++;
        }
        int start = i - 6;
        int end = i + 6;

        if (i < 6) start = 0;
        if (words.size() - i < 6) end = words.size();

        StringBuilder sb = new StringBuilder();
        for (String s : words.subList(start, end)) {
            sb.append(s);
            sb.append(" ");
        }
        String s = sb.toString();
        return "..." + s.replace(query, "<b>" + query + "</b>") + "...";
    }

    class CustomOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            ImageView imageView = (ImageView) v.findViewById(R.id.thumbnail);
            if (imageView.getDrawable() != null) {
                int pos = rv.getChildAdapterPosition(v);
                Intent intent = new Intent("de.tap.easy_xkcd.ACTION_COMIC");
                if (pos < resultsTitle.size())
                    intent.putExtra("number", resultsTitle.get(pos));
                else
                    intent.putExtra("number", resultsTranscript.get(pos - resultsTitle.size()));
                Bitmap bitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
                if (!prefHelper.fullOfflineEnabled())
                    ComicBrowserFragment.fromSearch = true;
                else
                    OfflineFragment.fromSearch = true;
                ActivityTransitionLauncher.with(SearchResultsActivity.this).from(v.findViewById(R.id.thumbnail)).image(bitmap).launch(intent);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_search_results, menu);

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        final SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setIconifiedByDefault(false);
        MenuItem searchMenuItem = menu.findItem(R.id.action_search);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (task != null) {
                    task.cancel(true);
                }
                task = new searchTask();
                task.execute(newText);
                query = newText;
                return false;
            }
        });

        MenuItemCompat.setOnActionExpandListener(searchMenuItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                View view = getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(view, 0);
                }
                searchView.requestFocus();
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                View view = getCurrentFocus();
                if (view != null) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
                return true;
            }
        });
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mProgress != null)
            mProgress.dismiss();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private boolean checkInteger(String s) {
        boolean isInteger = true;
        try {
            Integer.parseInt(s.trim());
        } catch (Exception e) {
            isInteger = false;
        }
        return isInteger;
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString("query", query);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onBackPressed() {
        MainActivity.fromSearch = true;
        super.onBackPressed();
    }

    @Override
    protected void onStart() {
        super.onStart();
        isOpen = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isOpen = false;
    }

}
