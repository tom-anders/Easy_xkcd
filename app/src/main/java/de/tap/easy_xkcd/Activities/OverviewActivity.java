package de.tap.easy_xkcd.Activities;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.tap.xkcd_reader.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.tap.easy_xkcd.fragments.ComicBrowserFragment;
import de.tap.easy_xkcd.fragments.FavoritesFragment;
import de.tap.easy_xkcd.fragments.OfflineFragment;
import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.utils.Favorites;
import de.tap.easy_xkcd.utils.PrefHelper;

public class OverviewActivity extends AppCompatActivity {

    private static String[] titles;
    private ListAdapter adapter;
    @Bind(R.id.list)
    ListView list;
    private static final String BROWSER_TAG = "browser";
    private static final String FAV_TAG = "favorites";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overview);
        ButterKnife.bind(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        assert getSupportActionBar() != null;
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        list.setFastScrollEnabled(true);

        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(R.attr.colorPrimaryDark, typedValue, true);
        TypedValue typedValue2 = new TypedValue();
        getTheme().resolveAttribute(R.attr.colorPrimary, typedValue2, true);
        toolbar.setBackgroundColor(typedValue2.data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(typedValue.data);
            if (!PrefHelper.colorNavbar())
                getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.ColorPrimaryBlack));
        }

        if (savedInstanceState==null) {
            new updateDatabase().execute();
        } else {
            adapter = new ListAdapter();
            list.setAdapter(adapter);
            list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    Intent intent = new Intent("de.tap.easy_xkcd.ACTION_COMIC");
                    intent.putExtra("number", i+1);
                    startActivity(intent);
                }
            });
        }
    }

    private class ListAdapter extends BaseAdapter {
        private LayoutInflater inflater;

        public ListAdapter() {
            inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return PrefHelper.getNewest();
        }

        @Override
        public String getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            ViewHolder holder;
            if (view == null) {
                holder = new ViewHolder();
                view = inflater.inflate(R.layout.overview_item, parent, false);
                holder.textView = (TextView) view.findViewById(R.id.tv);
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
            }
            String label = String.valueOf(position+1) +" " + titles[position];
            holder.textView.setText(label);
            return view;
        }
    }

    public static class ViewHolder {
        public TextView textView;
    }


    private class updateDatabase extends AsyncTask<Void, Integer, Void> {
        private ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(OverviewActivity.this);
            progress.setTitle(getResources().getString(R.string.update_database));
            progress.setIndeterminate(false);
            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (!PrefHelper.databaseLoaded()) {
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
                PrefHelper.setTitles(sb.toString());
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
                PrefHelper.setTrans(sb.toString());
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
                PrefHelper.setUrls(sb.toString(), 1579);
                Log.d("info", "urls loaded");
                PrefHelper.setDatabaseLoaded();
            }
            publishProgress(50);
            if (PrefHelper.isOnline(OverviewActivity.this)) {
                int newest;
                try {
                    newest = new Comic(0).getComicNumber();
                } catch (IOException e) {
                    newest = PrefHelper.getNewest();
                }
                StringBuilder sbTitle = new StringBuilder();
                sbTitle.append(PrefHelper.getComicTitles());
                StringBuilder sbTrans = new StringBuilder();
                sbTrans.append(PrefHelper.getComicTrans());
                StringBuilder sbUrl = new StringBuilder();
                sbUrl.append(PrefHelper.getComicUrls());
                String title;
                String trans;
                String url;
                Comic comic;
                for (int i = PrefHelper.getHighestUrls(); i < newest; i++) {
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
                    float x = newest - PrefHelper.getHighestUrls();
                    int y = i - PrefHelper.getHighestUrls();
                    int p = (int) ((y / x) * 50);
                    publishProgress(p + 50);
                }
                PrefHelper.setTitles(sbTitle.toString());
                PrefHelper.setTrans(sbTrans.toString());
                PrefHelper.setUrls(sbUrl.toString(), newest);
            }
            return null;
        }

        protected void onProgressUpdate(Integer... pro) {
            progress.setProgress(pro[0]);
        }

        @Override
        protected void onPostExecute(Void dummy) {
            titles = PrefHelper.getComicTitles().split("&&");
            progress.dismiss();
            adapter = new ListAdapter();
            list.setAdapter(adapter);
            if (PrefHelper.fullOfflineEnabled()) {
                list.setSelection(OfflineFragment.sLastComicNumber);
            } else {
                list.setSelection(ComicBrowserFragment.sLastComicNumber-1);
            }
            list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    finish();
                    MainActivity.getInstance().scrollBrowser(i);
                }
            });
        }
    }


}
