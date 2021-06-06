/**
 * *******************************************************************************
 * Copyright 2016 Tom Praschan
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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import android.widget.Toast;

import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;

import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.fragments.NestedPreferenceFragment;
import de.tap.easy_xkcd.services.ArticleDownloadService;
import timber.log.Timber;

public class NestedSettingsActivity extends BaseActivity {
    private static final String APPEARANCE = "appearance";
    private static final String BEHAVIOR = "behavior";
    private static final String OFFLINE_NOTIFICATIONS = "offline_notifications";
    private static final String ALT_SHARING = "altSharing";
    private static final String ADVANCED = "advanced";
    private static final String NIGHT = "night";
    private static final String WIDGET = "widget";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        //Setup toolbar and status bar color
        androidx.appcompat.widget.Toolbar toolbar = (androidx.appcompat.widget.Toolbar) findViewById(R.id.toolbar);
        setupToolbar(toolbar);

        if (savedInstanceState==null) {
            String key = getIntent().getStringExtra("key");
            Timber.d("key: %s", key);
            getFragmentManager().beginTransaction().replace(R.id.content_frame, NestedPreferenceFragment.newInstance(key), "nested").commit();
            assert getSupportActionBar() != null;
            switch (key) {
                case APPEARANCE:
                    getSupportActionBar().setTitle(getResources().getString(R.string.pref_appearance));
                    break;
                case BEHAVIOR:
                    getSupportActionBar().setTitle(getResources().getString(R.string.pref_behavior));
                    break;
                case OFFLINE_NOTIFICATIONS:
                    getSupportActionBar().setTitle(R.string.pref_offline_notifications);
                    break;
                case ALT_SHARING:
                    getSupportActionBar().setTitle(getResources().getString(R.string.pref_alt_sharing));
                    break;
                case ADVANCED:
                    getSupportActionBar().setTitle(getResources().getString(R.string.pref_advanced));
                    break;
                case NIGHT:
                    getSupportActionBar().setTitle(getResources().getString(R.string.pref_night_options));
                    break;
                case WIDGET:
                    getSupportActionBar().setTitle(getResources().getString(R.string.pref_widget));
                    break;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        NestedPreferenceFragment fragment = (NestedPreferenceFragment) getFragmentManager().findFragmentByTag("nested");
        switch (requestCode) {
            case 1:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.loading_comics), Toast.LENGTH_SHORT).show();
                    new DatabaseManager(NestedSettingsActivity.this).setHighestInDatabase(1);
                    prefHelper.setFullOffline(true);
                    setResult(Activity.RESULT_OK);
                    finish();
                }
                break;
            case 2:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    fragment.new deleteComicsTask().execute();
                else
                    prefHelper.setFullOffline(true);
                break;
            case 3:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, getResources().getString(R.string.loading_articles), Toast.LENGTH_SHORT).show();
                    startService(new Intent(this, ArticleDownloadService.class));
                    prefHelper.setFullOfflineWhatIf(true);
                }
                break;
            case 4:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    fragment.new deleteArticlesTask().execute();
                else
                    prefHelper.setFullOfflineWhatIf(true);
                break;
        }
    }

    public FragmentManager getManger() {
        return getSupportFragmentManager();
    }

}
