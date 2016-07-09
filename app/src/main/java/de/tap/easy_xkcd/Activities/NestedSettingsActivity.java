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
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.widget.Toast;

import com.tap.xkcd_reader.R;
import com.turhanoz.android.reactivedirectorychooser.event.OnDirectoryCancelEvent;
import com.turhanoz.android.reactivedirectorychooser.event.OnDirectoryChosenEvent;
import com.turhanoz.android.reactivedirectorychooser.ui.DirectoryChooserFragment;
import com.turhanoz.android.reactivedirectorychooser.ui.OnDirectoryChooserFragmentInteraction;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;

import de.tap.easy_xkcd.fragments.NestedPreferenceFragment;
import de.tap.easy_xkcd.services.ArticleDownloadService;
import de.tap.easy_xkcd.services.ComicDownloadService;

public class NestedSettingsActivity extends BaseActivity implements OnDirectoryChooserFragmentInteraction  {
    private static final String APPEARANCE = "appearance";
    private static final String BEHAVIOR = "behavior";
    private static final String ALT_SHARING = "altSharing";
    private static final String ADVANCED = "advanced";
    private static final String NIGHT = "night";
    private static final String WIDGET = "widget";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        //Setup toolbar and status bar color
        android.support.v7.widget.Toolbar toolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.toolbar);
        setupToolbar(toolbar);

        if (savedInstanceState==null) {
            String key = getIntent().getStringExtra("key");
            getFragmentManager().beginTransaction().replace(R.id.content_frame, NestedPreferenceFragment.newInstance(key), "nested").commit();
            assert getSupportActionBar() != null;
            switch (key) {
                case APPEARANCE:
                    getSupportActionBar().setTitle(getResources().getString(R.string.pref_appearance));
                    break;
                case BEHAVIOR:
                    getSupportActionBar().setTitle(getResources().getString(R.string.pref_behavior));
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
                    startService(new Intent(this, ComicDownloadService.class));
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
            case 5:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    fragment.new repairComicsTask().execute();
            case 12:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            DialogFragment directoryChooserFragment = DirectoryChooserFragment.newInstance(new File("/"));
                            FragmentTransaction transaction = getManger().beginTransaction();
                            directoryChooserFragment.show(transaction, "RDC");
                        }
                    }, 100);
                }
        }
    }

    public android.support.v4.app.FragmentManager getManger() {
        return getSupportFragmentManager();
    }

    @Override
    public void onEvent(OnDirectoryChosenEvent event) {
        File path = event.getFile();
        File oldPath = prefHelper.getOfflinePath();
        prefHelper.setOfflinePath(path.getAbsolutePath());
        //if (MainActivity.fullOffline | MainActivity.fullOfflineWhatIf)
        if (oldPath.exists())
            new moveData().execute(new String[]{oldPath.getAbsolutePath(), path.getAbsolutePath()});
    }

    /**
     * moves the folder for offline data to a new directory
     */
    public class moveData extends AsyncTask<String[], Void, Void> {
        private ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(NestedSettingsActivity.this);
            progress.setTitle(getResources().getString(R.string.copy_folder));
            progress.setMessage(getResources().getString(R.string.loading_offline_message));
            progress.setIndeterminate(true);
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected Void doInBackground(String[]... params) {
            File oldPath = new File(params[0][0] + "/easy xkcd");
            File newPath = new File(params[0][1] + "/easy xkcd");

            try {
                copyDirectory(oldPath, newPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            deleteFolder(oldPath);
            return null;
        }

        private void copyDirectory(File sourceLocation , File targetLocation)
                throws IOException {

            if (sourceLocation.isDirectory()) {
                if (!targetLocation.exists() && !targetLocation.mkdirs()) {
                    throw new IOException("Cannot create dir " + targetLocation.getAbsolutePath());
                }

                String[] children = sourceLocation.list();
                for (int i=0; i<children.length; i++) {
                    copyDirectory(new File(sourceLocation, children[i]),
                            new File(targetLocation, children[i]));
                }
            } else {

                // make sure the directory we plan to store the recording in exists
                File directory = targetLocation.getParentFile();
                if (directory != null && !directory.exists() && !directory.mkdirs()) {
                    throw new IOException("Cannot create dir " + directory.getAbsolutePath());
                }

                InputStream in = new FileInputStream(sourceLocation);
                OutputStream out = new FileOutputStream(targetLocation);

                // Copy the bits from instream to outstream
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                out.close();
            }
        }

        private void deleteFolder(File file) {
            if (file.isDirectory())
                for (File child : file.listFiles())
                    deleteFolder(child);
            file.delete();
        }

        @Override
        protected void onPostExecute(Void dummy) {
            progress.dismiss();
            setResult(Activity.RESULT_OK);
            finish();
        }
    }

    @Override
    public void onEvent(OnDirectoryCancelEvent event) {
    }

}
