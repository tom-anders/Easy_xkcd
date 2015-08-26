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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceFragment;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(PrefHelper.getTheme());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

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

        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, String key) {
                switch (key) {
                    case "pref_offline":
                        boolean checked = sharedPreferences.getBoolean(key, false);
                        if (checked) {
                            new downloadComicsTask().execute();
                        } else if (!PrefHelper.isFirstInstall()) {
                            android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(SettingsActivity.this, R.style.AlertDialog);
                            mDialog.setMessage(R.string.delete_offline_dialog)
                                    .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener);
                                            PrefHelper.setFullOffline(true);
                                            finish();
                                        }
                                    })
                                    .setPositiveButton(R.string.dialog_yes, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            new deleteComicsTask().execute();
                                        }
                                    })
                                    .setCancelable(false);
                            mDialog.show();
                        }
                        break;
                    case "pref_orientation":
                        switch (Integer.parseInt(PrefHelper.getOrientation())) {
                            case 1: MainActivity.getInstance().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
                                break;
                            case 2: MainActivity.getInstance().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                                break;
                            case 3: MainActivity.getInstance().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                                break;
                        }
                        break;
                    case "pref_theme":
                        finish();
                        MainActivity.getInstance().finish();
                        startActivity(MainActivity.getInstance().getIntent());
                        //startActivity(getIntent());
                }
            }
        };
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(listener);
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
            if (!((SettingsActivity) getActivity()).isOnline() && !PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("pref_offline", false)) {
                findPreference("pref_offline").setEnabled(false);
            }
        }
    }

    public class downloadComicsTask extends AsyncTask<Void, Integer, Void> {
        private ProgressDialog progress;

        //TODO lock orientation

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(SettingsActivity.this);
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
                    Comic comic = new Comic(i, SettingsActivity.this);
                    String url = comic.getComicData()[2];
                    Bitmap mBitmap = Glide.with(SettingsActivity.this)
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
                            FileOutputStream fos = openFileOutput(String.valueOf(i), Context.MODE_PRIVATE);
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
            finish();
            startActivity(MainActivity.getInstance().getIntent());
        }
    }

    public class deleteComicsTask extends AsyncTask<Void, Integer, Void> {
        private ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(SettingsActivity.this);
            progress.setTitle(getResources().getString(R.string.delete_offline));
            progress.setMessage(getResources().getString(R.string.loading_offline_message));
            progress.setIndeterminate(false);
            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            SharedPreferences preferences = getPreferences(Activity.MODE_PRIVATE);
            SharedPreferences.Editor mEditor = preferences.edit();
            //int newest = preferences.getInt("Newest Comic", 0);
            int newest = PrefHelper.getNewest();
            for (int i = 1; i <= newest; i++) {
                if (!Favorites.checkFavorite(MainActivity.getInstance(), i)) {
                    //delete from internal storage
                    deleteFile(String.valueOf(i));
                    //delete from external storage
                    File sdCard = Environment.getExternalStorageDirectory();
                    File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                    File file = new File(dir, String.valueOf(i) + ".png");
                    file.delete();

                    int p = (int) (i / ((float) newest) * 100);
                    publishProgress(p);
                }
            }
            PrefHelper.deleteTitleAndAlt(newest, SettingsActivity.this);

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
            finish();
            startActivity(MainActivity.getInstance().getIntent());
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

}
