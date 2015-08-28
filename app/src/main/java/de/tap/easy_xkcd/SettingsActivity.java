/**********************************************************************************
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
 ********************************************************************************/

package de.tap.easy_xkcd;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.tap.xkcd_reader.R;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.SimpleTimeZone;

public class SettingsActivity extends AppCompatActivity {
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    private static SettingsActivity instance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(PrefHelper.getTheme());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        instance = this;

        //Setup toolbar and status bar color
        android.support.v7.widget.Toolbar toolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);


        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(R.attr.colorPrimaryDark, typedValue, true);
        TypedValue typedValue2 = new TypedValue();
        getTheme().resolveAttribute(R.attr.colorPrimary, typedValue2, true);
        toolbar.setBackgroundColor(typedValue2.data);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(typedValue.data);
        }

        getFragmentManager().beginTransaction().replace(R.id.content_frame, new CustomPreferenceFragment()).commit();
    }

    public static SettingsActivity getInstance() {
        return instance;
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    public static class CustomPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            findPreference("pref_theme").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    getActivity().finish();
                    MainActivity.getInstance().finish();
                    startActivity(MainActivity.getInstance().getIntent());
                    return true;
                }
            });

            findPreference("pref_orientation").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    switch (Integer.parseInt(PrefHelper.getOrientation())) {
                        case 1:
                            MainActivity.getInstance().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
                            break;
                        case 2:
                            MainActivity.getInstance().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                            break;
                        case 3:
                            MainActivity.getInstance().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                            break;
                    }
                    return true;
                }
            });

            findPreference("pref_offline").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean checked = Boolean.valueOf(newValue.toString());
                    if (checked) {
                        if (((SettingsActivity) getActivity()).isOnline()) {
                            new downloadComicsTask().execute();
                            return true;
                        } else {
                            Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
                            return false;
                        }

                    } else {
                        android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity(), PrefHelper.getDialogTheme());
                        mDialog.setMessage(R.string.delete_offline_dialog)
                                .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        SettingsActivity.getInstance().finish();
                                        PrefHelper.setFullOffline(true);
                                    }
                                })
                                .setPositiveButton(R.string.dialog_yes, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        new deleteComicsTask().execute();
                                    }
                                })
                                .setCancelable(false);
                        mDialog.show();
                        return true;
                    }
                }
            });

            findPreference("pref_offline_whatif").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean checked = Boolean.valueOf(newValue.toString());
                    if (checked) {
                        if (((SettingsActivity) getActivity()).isOnline()) {
                            new downloadArticlesTask().execute();
                            return true;
                        } else {
                            Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
                            return false;
                        }

                    } else {
                        android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity(), PrefHelper.getDialogTheme());
                        mDialog.setMessage(R.string.delete_offline_whatif_dialog)
                                .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        SettingsActivity.getInstance().finish();
                                        PrefHelper.setFullOfflineWhatIf(true);
                                    }
                                })
                                .setPositiveButton(R.string.dialog_yes, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        new deleteArticlesTask().execute();
                                    }
                                })
                                .setCancelable(false);
                        mDialog.show();
                        return true;
                    }
                }
            });

        }

        public class downloadComicsTask extends AsyncTask<Void, Integer, Void> {
            private ProgressDialog progress;

            @Override
            protected void onPreExecute() {
                progress = new ProgressDialog(getActivity());
                progress.setTitle(getResources().getString(R.string.loading_offline));
                progress.setMessage(getResources().getString(R.string.loading_offline_message));
                progress.setIndeterminate(false);
                progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progress.setCancelable(false);
                progress.show();
            }

            @Override
            protected Void doInBackground(Void... params) {
                for (int i = 1; i <= ComicBrowserFragment.sNewestComicNumber; i++) {
                    Log.d("i", String.valueOf(i));
                    try {
                        Comic comic = new Comic(i, getActivity());
                        String url = comic.getComicData()[2];
                        Bitmap mBitmap = Glide.with(getActivity())
                                .load(url)
                                .asBitmap()
                                .into(-1, -1)
                                .get();
                        try {
                            File sdCard = Environment.getExternalStorageDirectory();
                            File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                            dir.mkdirs();
                            File file = new File(dir, String.valueOf(i) + ".png");
                            FileOutputStream fos = new FileOutputStream(file);
                            mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                            fos.flush();
                            fos.close();
                        } catch (Exception e) {
                            Log.e("Error", "Saving to external storage failed");
                            try {
                                FileOutputStream fos = getActivity().openFileOutput(String.valueOf(i), Context.MODE_PRIVATE);
                                mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                                fos.close();
                            } catch (Exception e2) {
                                e2.printStackTrace();
                            }
                        }

                        PrefHelper.addTitle(comic.getComicData()[0], i);
                        PrefHelper.addAlt(comic.getComicData()[1], i);
                        int p = (int) (i / ((float) ComicBrowserFragment.sNewestComicNumber) * 100);
                        publishProgress(p);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                PrefHelper.setHighestOffline(ComicBrowserFragment.sNewestComicNumber);
                return null;
            }

            protected void onProgressUpdate(Integer... pro) {
                progress.setProgress(pro[0]);
                switch (pro[0]) {
                    case 2:
                        progress.setMessage(getResources().getString(R.string.loading_offline_2));
                        break;
                    case 20:
                        progress.setMessage(getResources().getString(R.string.loading_offline_20));
                        break;
                    case 50:
                        progress.setMessage(getResources().getString(R.string.loading_offline_50));
                        break;
                    case 80:
                        progress.setMessage(getResources().getString(R.string.loading_offline_80));
                        break;
                    case 95:
                        progress.setMessage(getResources().getString(R.string.loading_offline_95));
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                progress.setMessage(getResources().getString(R.string.loading_offline_96));
                            }
                        }, 1000);
                        break;
                    case 97:
                        progress.setMessage(getResources().getString(R.string.loading_offline_97));
                        break;
                }
            }

            @Override
            protected void onPostExecute(Void dummy) {
                progress.dismiss();
                MainActivity.getInstance().finish();
                getActivity().finish();
                startActivity(MainActivity.getInstance().getIntent());
            }
        }

        public class deleteComicsTask extends AsyncTask<Void, Integer, Void> {
            private ProgressDialog progress;

            @Override
            protected void onPreExecute() {
                progress = new ProgressDialog(getActivity());
                progress.setTitle(getResources().getString(R.string.delete_offline));
                progress.setMessage(getResources().getString(R.string.loading_offline_message));
                progress.setIndeterminate(false);
                progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progress.setCancelable(false);
                progress.show();
            }

            @Override
            protected Void doInBackground(Void... params) {
                int newest = PrefHelper.getNewest();
                for (int i = 1; i <= newest; i++) {
                    if (!Favorites.checkFavorite(MainActivity.getInstance(), i)) {
                        //delete from internal storage
                        getActivity().deleteFile(String.valueOf(i));
                        //delete from external storage
                        File sdCard = Environment.getExternalStorageDirectory();
                        File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                        File file = new File(dir, String.valueOf(i) + ".png");
                        file.delete();

                        int p = (int) (i / ((float) newest) * 100);
                        publishProgress(p);
                    }
                }
                PrefHelper.deleteTitleAndAlt(newest, getActivity());

                PrefHelper.setHighestOffline(0);

                return null;
            }

            protected void onProgressUpdate(Integer... pro) {
                progress.setProgress(pro[0]);
            }

            @Override
            protected void onPostExecute(Void dummy) {
                progress.dismiss();
                MainActivity.getInstance().finish();
                getActivity().finish();
                startActivity(MainActivity.getInstance().getIntent());
            }
        }

        public class downloadArticlesTask extends AsyncTask<Void, Integer, Void> {
            private ProgressDialog progress;
            private Document doc;

            @Override
            protected void onPreExecute() {
                progress = new ProgressDialog(getActivity());
                progress.setTitle(getResources().getString(R.string.loading_articles));
                progress.setMessage(getResources().getString(R.string.loading_offline_message));
                progress.setIndeterminate(false);
                progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progress.setCancelable(false);
                progress.show();
            }

            @Override
            protected Void doInBackground(Void... params) {
                Bitmap mBitmap;
                File sdCard = Environment.getExternalStorageDirectory();
                File dir;
                //download overview
                try {
                    doc = Jsoup.connect("https://what-if.xkcd.com/archive/")
                            .userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/32.0.1700.19 Safari/537.36")
                            .get();
                    StringBuilder sb = new StringBuilder();
                    Elements titles = doc.select("h1");
                    PrefHelper.setNewestWhatif(titles.size());

                    sb.append(titles.first().text());
                    titles.remove(0);
                    for (Element title : titles) {
                        sb.append("&&");
                        sb.append(title.text());
                    }
                    PrefHelper.setWhatIfTitles(sb.toString());

                    Elements img = doc.select("img.archive-image");
                    int count = 1;
                    for (Element image : img) {
                        String url = image.absUrl("src");
                        try {
                            mBitmap = Glide.with(getActivity())
                                    .load(url)
                                    .asBitmap()
                                    .into(-1, -1)
                                    .get();
                            dir = new File(sdCard.getAbsolutePath() + "/easy xkcd/what if/overview");
                            dir.mkdirs();
                            File file = new File(dir, String.valueOf(count) + ".png");
                            FileOutputStream fos = new FileOutputStream(file);
                            mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                            fos.flush();
                            fos.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Log.d("count", String.valueOf(count));
                        int p = (int) (count / ((float) img.size()) * 100);
                        publishProgress(p);
                        count++;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //download html
                for (int i = 1; i <= PrefHelper.getNewestWhatIf(); i++) {
                    int size = PrefHelper.getNewestWhatIf();
                    try {
                        doc = Jsoup.connect("https://what-if.xkcd.com/" + String.valueOf(i)).get();
                        dir = new File(sdCard.getAbsolutePath() + "/easy xkcd/what if/" + String.valueOf(i));
                        dir.mkdirs();
                        File file = new File(dir, String.valueOf(i) + ".html");
                        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                        writer.write(doc.outerHtml());
                        writer.close();
                        //download images
                        int count = 1;
                        for (Element e : doc.select(".illustration")) {
                            try {
                                String url = "http://what-if.xkcd.com" + e.attr("src");
                                mBitmap = Glide.with(getActivity())
                                        .load(url)
                                        .asBitmap()
                                        .into(-1, -1)
                                        .get();
                                dir = new File(sdCard.getAbsolutePath() + "/easy xkcd/what if/" + String.valueOf(i));
                                dir.mkdirs();
                                file = new File(dir, String.valueOf(count) + ".png");
                                FileOutputStream fos = new FileOutputStream(file);
                                mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                                fos.flush();
                                fos.close();
                                count++;
                            } catch (Exception e2) {
                                e2.printStackTrace();
                            }
                        }
                        int p = (int) (i / ((float) size) * 100);
                        publishProgress(p);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                return null;
            }

            protected void onProgressUpdate(Integer... pro) {
                progress.setProgress(pro[0]);
            }

            @Override
            protected void onPostExecute(Void dummy) {
                progress.dismiss();
                MainActivity.getInstance().finish();
                getActivity().finish();
                startActivity(MainActivity.getInstance().getIntent());
            }
        }

        public class deleteArticlesTask extends AsyncTask<Void, Void, Void> {
            private ProgressDialog progress;

            @Override
            protected void onPreExecute() {
                progress = new ProgressDialog(getActivity());
                progress.setTitle(getResources().getString(R.string.delete_offline_articles));
                progress.setMessage(getResources().getString(R.string.loading_offline_message));
                progress.setIndeterminate(true);
                progress.setCancelable(false);
                progress.show();
            }

            @Override
            protected Void doInBackground(Void... params) {
                File sdCard = Environment.getExternalStorageDirectory();
                File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd/what if/");
                deleteFolder(dir);
                return null;
            }

            @Override
            protected void onPostExecute(Void dummy) {
                progress.dismiss();
                MainActivity.getInstance().finish();
                getActivity().finish();
                startActivity(MainActivity.getInstance().getIntent());
            }
        }

        private void deleteFolder(File file) {
            if (file.isDirectory())
                for (File child : file.listFiles())
                    deleteFolder(child);
            file.delete();
        }

    }



    private boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

}
