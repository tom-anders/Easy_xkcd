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
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.kogitune.activity_transition.ActivityTransitionLauncher;
import com.tap.xkcd_reader.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.fragments.ComicBrowserFragment;
import de.tap.easy_xkcd.fragments.OfflineFragment;
import de.tap.easy_xkcd.utils.PrefHelper;
import jp.wasabeef.recyclerview.animators.adapters.SlideInBottomAnimationAdapter;


public class SearchResultsActivity extends AppCompatActivity {

    private SparseArray<String> resultsTitle = new SparseArray<>();
    private SparseArray<String> resultsTranscript = new SparseArray<>();
    private SparseArray<String> resultsUrls = new SparseArray<>();
    private SparseArray<String> resultsPreview = new SparseArray<>();
    @Bind(R.id.rv)
    RecyclerView rv;
    private searchTask task;
    private ProgressDialog mProgress;
    private String query;
    private static String sComicTitles;
    private static String sComicTrans;
    private static String sComicUrls;
    private PrefHelper prefHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefHelper = new PrefHelper(getApplicationContext());
        setTheme(prefHelper.getTheme());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_results);
        ButterKnife.bind(this);

        //Setup toolbar and status bar color
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        assert getSupportActionBar() != null;
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(R.attr.colorPrimaryDark, typedValue, true);
        TypedValue typedValue2 = new TypedValue();
        getTheme().resolveAttribute(R.attr.colorPrimary, typedValue2, true);
        toolbar.setBackgroundColor(typedValue2.data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(typedValue.data);
            if (!prefHelper.colorNavbar())
                getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.ColorPrimaryBlack));
        }

        LinearLayoutManager llm = new LinearLayoutManager(this);
        rv.setLayoutManager(llm);
        rv.setHasFixedSize(true);

        Intent intent = getIntent();
        if (savedInstanceState == null) {
            query = intent.getStringExtra(SearchManager.QUERY);
        } else {
            query = savedInstanceState.getString("query");
        }
        getSupportActionBar().setTitle(getResources().getString(R.string.title_activity_search_results) + " " + query);
        mProgress = ProgressDialog.show(this, "", getResources().getString(R.string.loading_results), true);

        if (savedInstanceState == null) {
            new updateDatabase().execute();
        } else {
            new searchTask().execute(query);
        }
    }

    private class updateDatabase extends AsyncTask<Void, Integer, Void> {
        private ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(SearchResultsActivity.this);
            progress.setTitle(getResources().getString(R.string.update_database));
            progress.setIndeterminate(false);
            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (!prefHelper.databaseLoaded()) {
                InputStream is = getResources().openRawResource(R.raw.comic_titles);
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                try {
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                } catch (IOException e) {
                    Log.e("error:", e.getMessage());
                }
                prefHelper.setTitles(sb.toString());
                publishProgress(15);
                Log.d("info", "titles loaded");

                is = getResources().openRawResource(R.raw.comic_trans);
                br = new BufferedReader(new InputStreamReader(is));
                sb = new StringBuilder();
                try {
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                } catch (IOException e) {
                    Log.e("error:", e.getMessage());
                }
                prefHelper.setTrans(sb.toString());
                publishProgress(30);
                Log.d("info", "trans loaded");

                is = getResources().openRawResource(R.raw.comic_urls);
                br = new BufferedReader(new InputStreamReader(is));
                sb = new StringBuilder();
                try {
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                } catch (IOException e) {
                    Log.e("error:", e.getMessage());
                }
                prefHelper.setUrls(sb.toString(), 1579);
                Log.d("info", "urls loaded");
                prefHelper.setDatabaseLoaded();
            }
            publishProgress(50);
            if (prefHelper.isOnline(SearchResultsActivity.this)) {
                int newest;
                try {
                    newest = new Comic(0).getComicNumber();
                } catch (IOException e) {
                    newest = prefHelper.getNewest();
                }
                StringBuilder sbTitle = new StringBuilder();
                sbTitle.append(prefHelper.getComicTitles());
                StringBuilder sbTrans = new StringBuilder();
                sbTrans.append(prefHelper.getComicTrans());
                StringBuilder sbUrl = new StringBuilder();
                sbUrl.append(prefHelper.getComicUrls());
                String title;
                String trans;
                String url;
                Comic comic;
                for (int i = prefHelper.getHighestUrls(); i < newest; i++) {
                    try {
                        comic = new Comic(i + 1);
                        title = comic.getComicData()[0];
                        trans = comic.getTranscript();
                        url = comic.getComicData()[2];
                    } catch (IOException e) {
                        title = "";
                        trans = "";
                        url = "";
                    }
                    sbTitle.append("&&");
                    sbTitle.append(title);
                    sbUrl.append("&&");
                    sbUrl.append(url);
                    sbTrans.append("&&");
                    if (!trans.equals("")) {
                        sbTrans.append(trans);
                    } else {
                        sbTrans.append("n.a.");
                    }
                    float x = newest - prefHelper.getHighestUrls();
                    int y = i - prefHelper.getHighestUrls();
                    int p = (int) ((y / x) * 50);
                    publishProgress(p + 50);
                }
                prefHelper.setTitles(sbTitle.toString());
                prefHelper.setTrans(sbTrans.toString());
                prefHelper.setUrls(sbUrl.toString(), newest);
            }
            return null;
        }

        protected void onProgressUpdate(Integer... pro) {
            progress.setProgress(pro[0]);
        }

        @Override
        protected void onPostExecute(Void dummy) {
            sComicTitles = prefHelper.getComicTitles();
            sComicTrans = prefHelper.getComicTrans();
            sComicUrls = prefHelper.getComicUrls();
            if (mProgress != null) {
                progress.dismiss();
            }
            task = new searchTask();
            task.execute(query);
        }
    }

    private boolean performSearch(String query) {
        if (checkInteger(query)) {
            return getComicByNumber(Integer.parseInt(query));
        } else {
            return searchComicTitle(query);
        }
    }

    private boolean getComicByNumber(int number) {
        Intent intent = new Intent("de.tap.easy_xkcd.ACTION_COMIC");
        if ((number > prefHelper.getNewest() && number > prefHelper.getHighestOffline()) | number < 1) {
            intent.putExtra("number", prefHelper.getNewest());
        } else {
            intent.putExtra("number", number);
        }
        mProgress.dismiss();
        startActivity(intent);
        if (task!=null)
            task.cancel(true);
        return true;
    }


    private boolean searchComicTitle(String query) {
        resultsTitle.clear();
        resultsTranscript.clear();
        resultsUrls.clear();
        resultsPreview.clear();
        String[] titles = sComicTitles.split("&&");
        String[] trans = sComicTrans.split("&&");
        String[] urls = sComicUrls.split("&&");
        query = query.trim().toLowerCase();
        for (int i = 0; i < titles.length; i++) {
            String ti = titles[i].toLowerCase();
            Boolean found;
            if (query.length() < 5) {
                found = ti.matches(".*\\b" + query + "\\b.*");
            } else {
                found = ti.contains(query);
            }
            if (found) {
                resultsTitle.put(i + 1, titles[i]);
                resultsUrls.put(i + 1, urls[i]);
            } else {
                String tr = trans[i].toLowerCase();
                if (query.length() < 5) {
                    found = tr.matches(".*\\b" + query + "\\b.*");
                } else {
                    found = tr.contains(query);
                }
                if (found) {
                    resultsTranscript.put(i + 1, titles[i]);
                    resultsUrls.put(i + 1, urls[i]);
                    resultsPreview.put(i + 1, getPreview(query, trans[i]));
                }
            }
        }
        return (resultsTranscript.size() + resultsTitle.size() == 0);
    }

    private class searchTask extends AsyncTask<String, Void, Void> {
        private Boolean done;
        private ProgressBar pb;

        @Override
        protected void onPreExecute() {
            pb = (ProgressBar) findViewById(R.id.pb);
            pb.setVisibility(View.VISIBLE);
        }

        @Override
        protected Void doInBackground(String... params) {
            query = params[0];
            done = performSearch(params[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            pb.setVisibility(View.INVISIBLE);
            if (!done) {
                RVAdapter adapter = new RVAdapter(resultsTitle, resultsTranscript, resultsUrls, resultsPreview);
                SlideInBottomAnimationAdapter slideAdapter = new SlideInBottomAnimationAdapter(adapter);
                slideAdapter.setInterpolator(new DecelerateInterpolator());
                rv.setAdapter(slideAdapter);
                mProgress.dismiss();
                assert getSupportActionBar() != null;
                getSupportActionBar().setTitle(getResources().getString(R.string.title_activity_search_results) + " " + query);
            } else {
                mProgress.dismiss();
                Toast.makeText(getApplicationContext(), R.string.search_error, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public class RVAdapter extends RecyclerView.Adapter<RVAdapter.ComicViewHolder> {

        private SparseArray<String> comicsTitle = new SparseArray<>();
        private SparseArray<String> comicsTrans = new SparseArray<>();
        private SparseArray<String> comicsUrls = new SparseArray<>();
        private SparseArray<String> comicsPreviews = new SparseArray<>();

        RVAdapter(SparseArray<String> comics1, SparseArray<String> comics2, SparseArray<String> comics3, SparseArray<String> comics4) {
            this.comicsTitle = comics1;
            this.comicsTrans = comics2;
            this.comicsUrls = comics3;
            this.comicsPreviews = comics4;
        }

        @Override
        public int getItemCount() {
            return comicsTitle.size() + comicsTrans.size();
        }

        @Override
        public ComicViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.search_result, viewGroup, false);
            v.setOnClickListener(new CustomOnClickListener());
            return new ComicViewHolder(v);
        }

        @Override
        public void onBindViewHolder(final ComicViewHolder comicViewHolder, int i) {
            int number;
            String title;
            String preview;
            if (i < comicsTitle.size()) {
                number = comicsTitle.keyAt(i);
                title = comicsTitle.get(number);
                comicViewHolder.comicInfo.setText(String.valueOf(number));
            } else {
                number = comicsTrans.keyAt(i - comicsTitle.size());
                title = comicsTrans.get(number);
                preview = comicsPreviews.get(number);
                comicViewHolder.comicInfo.setText(Html.fromHtml(preview));
            }

            comicViewHolder.comicTitle.setText(title);

            if (!MainActivity.fullOffline) {
                Glide.with(getApplicationContext())
                        .load(comicsUrls.get(number))
                        .asBitmap()
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
            if (prefHelper.invertColors()) {
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
            TextView comicTitle;
            TextView comicInfo;

            ImageView thumbnail;

            ComicViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.cv);
                if (prefHelper.nightThemeEnabled())
                    cv.setCardBackgroundColor(ContextCompat.getColor(SearchResultsActivity.this, R.color.background_material_dark));
                comicTitle = (TextView) itemView.findViewById(R.id.comic_title);
                comicInfo = (TextView) itemView.findViewById(R.id.comic_info);
                thumbnail = (ImageView) itemView.findViewById(R.id.thumbnail);
            }
        }

    }

    private String getPreview(String query, String transcript) {
        String firstWord = query.split(" ")[0].toLowerCase();
        transcript = transcript.replace(".", ". ").replace("?", "? ").replace("]]", " ").replace("[[", " ").replace("{{", " ").replace("}}", " ");
        ArrayList<String> words = new ArrayList<>(Arrays.asList(transcript.toLowerCase().split(" ")));
        int i = 0;
        boolean found = false;
        while (!found && i < words.size()) {
            if (query.length() < 5) {
                found = words.get(i).matches(".*\\b" + firstWord + "\\b.*");
            } else {
                found = words.get(i).contains(firstWord);
            }
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
                if (pos < resultsTitle.size()) {
                    intent.putExtra("number", resultsTitle.keyAt(pos));
                } else {
                    intent.putExtra("number", resultsTranscript.keyAt(pos - resultsTitle.size()));
                }
                Bitmap bitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
                if (!prefHelper.fullOfflineEnabled()) {
                    ComicBrowserFragment.fromSearch = true;
                } else {
                    OfflineFragment.fromSearch = true;
                }
                ActivityTransitionLauncher.with(SearchResultsActivity.this).from(v.findViewById(R.id.comic_title)).from(v.findViewById(R.id.thumbnail)).image(bitmap).launch(intent);
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
            Integer.parseInt(s);
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


    public static boolean isOpen;

    @Override
    protected void onStart() {
        super.onStart();
        isOpen=true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isOpen=false;
    }

}
