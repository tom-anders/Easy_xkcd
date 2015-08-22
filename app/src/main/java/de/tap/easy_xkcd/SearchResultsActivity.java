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
package de.tap.easy_xkcd;

import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class SearchResultsActivity extends AppCompatActivity {

    private SparseArray<String> resultsTitle = new SparseArray<>();
    private SparseArray<String> resultsTranscript = new SparseArray<>();
    private List<String> urls = new ArrayList<>();
    private RecyclerView rv;
    private searchTask task;
    private MenuItem searchMenuItem;
    private ProgressDialog mProgress;
    private RVAdapter adapter = null;
    private String queryTrans;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mProgress = ProgressDialog.show(this, "", this.getResources().getString(R.string.loading_results), true);

        setContentView(R.layout.activity_search_results);

        //Setup toolbar and status bar color
        android.support.v7.widget.Toolbar toolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(getResources().getColor(R.color.ColorPrimaryDark));
        }

        rv = (RecyclerView) findViewById(R.id.rv);
        setupRecyclerView(rv);

        findViewById(R.id.pb).setVisibility(View.VISIBLE);

        Intent intent = getIntent();
        String query = intent.getStringExtra(SearchManager.QUERY);
        queryTrans = query;
        getSupportActionBar().setTitle(getResources().getString(R.string.title_activity_search_results)+" "+query);

        task = new searchTask();
        task.execute(query);
    }

    private void setupRecyclerView(RecyclerView recyclerView) {
        LinearLayoutManager llm = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(llm);
        recyclerView.setHasFixedSize(true);
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
        if (number > ComicBrowserFragment.sNewestComicNumber && number > OfflineFragment.sNewestComicNumber) {
            intent.putExtra("number", ComicBrowserFragment.sNewestComicNumber);
        } else {
            intent.putExtra("number", number);
        }
        mProgress.dismiss();
        startActivity(intent);
        task.cancel(true);
        return true;
    }


    private boolean searchComicTitle(String query) {
        resultsTitle.clear();
        resultsTranscript.clear();
        String s = MainActivity.sComicTitles;
        String[] titles = s.split("&&");
        query = query.trim().toLowerCase();
        for (int i = 0; i < titles.length; i++) {
            String l = titles[i].toLowerCase();
            Boolean found;
            if (query.length()<5) {
                found = l.matches(".*\\b" + query + "\\b.*");
            } else {
                found = l.contains(query);
            }
            if (found) {
                resultsTitle.put(i + 1, titles[i]);
            }
        }
        return false;
    }

    private class searchTask extends AsyncTask<String, Void, Void> {
        private Boolean done;
        @Override
        protected Void doInBackground(String... params) {
            done = performSearch(params[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            if (!done) {
                new displayTitleResultsTask().execute();
            } else {
                mProgress.dismiss();
                Toast.makeText(getApplicationContext(), R.string.search_error, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class displayTitleResultsTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            getTitleUrls();
            adapter = new RVAdapter(resultsTitle, resultsTranscript);
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            rv.setAdapter(adapter);
            mProgress.dismiss();
            new displayTransResultsTask().execute();
        }
    }

    private class displayTransResultsTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            searchTranscripts();
            if (!MainActivity.fullOffline) {
                getTransUrls();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            Animation animation = AnimationUtils.loadAnimation(getBaseContext(), android.R.anim.fade_out);
            findViewById(R.id.pb).setAnimation(animation);
            findViewById(R.id.pb).setVisibility(View.INVISIBLE);
            adapter.notifyDataSetChanged();
            if (resultsTitle.size()+resultsTranscript.size()==0) {
                Toast.makeText(getApplicationContext(), R.string.search_error, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void searchTranscripts() {
        String t = MainActivity.sComicTitles;
        String[] titles = t.split("&&");
        String s = MainActivity.sComicTrans;
        String[] trans = s.split("&&");
        queryTrans = queryTrans.trim().toLowerCase();
        for (int i = 0; i < trans.length; i++) {
            String l = trans[i].toLowerCase();
            Boolean found;
            if (queryTrans.length()<5) {
                found = l.matches(".*\\b" + queryTrans + "\\b.*");
            } else {
                found = l.contains(queryTrans);
            }
            if (found && resultsTitle.get(i + 1) == null) {
                resultsTranscript.put(i + 1, titles[i]);
            }
        }
    }

    private void getTitleUrls() {
        for (int i = 0; i < resultsTitle.size(); i++) {
            try {
                urls.add(i, new Comic(resultsTitle.keyAt(i)).getComicData()[2]);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void getTransUrls() {
        for (int i = 0; i <resultsTranscript.size(); i++) {
            try {
                urls.add(i+resultsTitle.size(), new Comic(resultsTranscript.keyAt(i)).getComicData()[2]);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class RVAdapter extends RecyclerView.Adapter<RVAdapter.ComicViewHolder> {

        private SparseArray<String> comicsTitle = new SparseArray<>();
        private SparseArray<String> comicsTrans = new SparseArray<>();
        private int lastPosition = 0;

        RVAdapter(SparseArray<String> comics1, SparseArray<String> comics2) {
            this.comicsTitle = comics1;
            this.comicsTrans = comics2;
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
            try {
                if (i < comicsTitle.size()) {
                    comicViewHolder.comicTitle.setText(resultsTitle.get(resultsTitle.keyAt(i)));
                    comicViewHolder.comicNumber.setText(String.valueOf(resultsTitle.keyAt(i)));
                } else {
                    comicViewHolder.comicTitle.setText(resultsTranscript.get(resultsTranscript.keyAt(i - resultsTitle.size())));
                    comicViewHolder.comicNumber.setText(String.valueOf(resultsTranscript.keyAt(i - resultsTitle.size())));
                }
                if (!MainActivity.fullOffline) {
                    Glide.with(getApplicationContext())
                            .load(urls.get(i))
                            .asBitmap()
                            .into(new SimpleTarget<Bitmap>() {
                                @Override
                                public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                                    comicViewHolder.thumbnail.setImageBitmap(resource);
                                }
                            });
                } else {
                    try {
                        if (i < comicsTitle.size()) {
                            FileInputStream fis = openFileInput(String.valueOf(resultsTitle.keyAt(i)));
                            Bitmap mBitmap = BitmapFactory.decodeStream(fis);
                            fis.close();
                            comicViewHolder.thumbnail.setImageBitmap(mBitmap);
                        } else {
                            FileInputStream fis = openFileInput(String.valueOf(resultsTranscript.keyAt(i - resultsTitle.size())));
                            Bitmap mBitmap = BitmapFactory.decodeStream(fis);
                            fis.close();
                            comicViewHolder.thumbnail.setImageBitmap(mBitmap);
                        }
                    } catch (Exception e) {
                        Log.e("Error", "loading from internal storage failed");
                        try {
                            if (i < comicsTitle.size()) {
                                File sdCard = Environment.getExternalStorageDirectory();
                                File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                                File file = new File(dir, String.valueOf(resultsTitle.keyAt(i)) + ".png");
                                Glide.with(getApplicationContext())
                                        .load(file)
                                        .asBitmap()
                                        .into(new SimpleTarget<Bitmap>() {
                                            @Override
                                            public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                                                comicViewHolder.thumbnail.setImageBitmap(resource);
                                            }
                                        });
                            } else {
                                File sdCard = Environment.getExternalStorageDirectory();
                                File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                                File file = new File(dir, String.valueOf(resultsTranscript.keyAt(i - resultsTitle.size())) + ".png");
                                Glide.with(getApplicationContext())
                                        .load(file)
                                        .asBitmap()
                                        .into(new SimpleTarget<Bitmap>() {
                                            @Override
                                            public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                                                comicViewHolder.thumbnail.setImageBitmap(resource);
                                            }
                                        });
                            }
                        } catch (Exception e2) {
                            e2.printStackTrace();
                        }
                    }
                }
                setAnimation(comicViewHolder.cv, i);
            } catch (IndexOutOfBoundsException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        public class ComicViewHolder extends RecyclerView.ViewHolder {
            CardView cv;
            TextView comicTitle;
            TextView comicNumber;
            ImageView thumbnail;

            ComicViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.cv);
                comicTitle = (TextView) itemView.findViewById(R.id.comic_title);
                comicNumber = (TextView) itemView.findViewById(R.id.comic_number);
                thumbnail = (ImageView) itemView.findViewById(R.id.thumbnail);
            }
        }

        private void setAnimation(View viewToAnimate, int position)
        {
            // If the bound view wasn't previously displayed on screen, it's animated
            if (position > lastPosition)
            {
                Animation animation = AnimationUtils.loadAnimation(getBaseContext(), android.R.anim.slide_in_left);
                viewToAnimate.startAnimation(animation);
                lastPosition = position;
            }
        }

    }

    class CustomOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            //int pos = rv.getChildPosition(v);
            int pos = rv.getChildAdapterPosition(v);
            Intent intent = new Intent("de.tap.easy_xkcd.ACTION_COMIC");
            if (pos < resultsTitle.size()) {
                intent.putExtra("number", resultsTitle.keyAt(pos));
            } else {
                intent.putExtra("number", resultsTranscript.keyAt(pos - resultsTitle.size()));
            }
            startActivity(intent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_search_results, menu);
        // Get the SearchView and set the searchable configuration
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        final SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setIconifiedByDefault(false);
        //searchView.setInputType(InputType.TYPE_CLASS_NUMBER);
        searchMenuItem = menu.findItem(R.id.action_search);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {

                return false;
            }
        });
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        mProgress = ProgressDialog.show(this, "", this.getResources().getString(R.string.loading_results), true);
        resultsTitle.clear();
        resultsTranscript.clear();
        urls.clear();
        adapter = null;
        setIntent(intent);
        String query = intent.getStringExtra(SearchManager.QUERY);
        getSupportActionBar().setTitle(getResources().getString(R.string.title_activity_search_results)+" "+query);
        queryTrans = query;
        findViewById(R.id.pb).setVisibility(View.VISIBLE);

        task = new searchTask();
        task.execute(query);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mProgress!=null) {
            mProgress.dismiss();
        }
    }

    public MenuItem getSearchMenuItem() {
        return searchMenuItem;
    }

    private boolean checkInteger(String s) {
        boolean isInteger = true;
        try {
            Integer.parseInt(s);
        } catch (Exception e) {
            isInteger = false;
        }
        return isInteger;
    }
}
