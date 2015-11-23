package de.tap.easy_xkcd.Activities;

import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;

import com.tap.xkcd_reader.R;
import com.turhanoz.android.reactivedirectorychooser.event.OnDirectoryCancelEvent;
import com.turhanoz.android.reactivedirectorychooser.event.OnDirectoryChosenEvent;
import com.turhanoz.android.reactivedirectorychooser.ui.OnDirectoryChooserFragmentInteraction;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.tap.easy_xkcd.fragments.NestedPreferenceFragment;
import de.tap.easy_xkcd.utils.PrefHelper;

public class NestedSettingsActivity extends AppCompatActivity implements OnDirectoryChooserFragmentInteraction  {
    private static final String APPEARANCE = "appearance";
    private static final String BEHAVIOR = "behavior";
    private static final String ALT_SHARING = "altSharing";
    private static final String ADVANCED = "advanced";
    private static final String NIGHT = "night";
    private PrefHelper prefHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefHelper = new PrefHelper(getApplicationContext());
        setTheme(prefHelper.getTheme());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        //Setup toolbar and status bar color
        android.support.v7.widget.Toolbar toolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        assert getSupportActionBar() != null;
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);


        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(R.attr.colorPrimaryDark, typedValue, true);
        TypedValue typedValue2 = new TypedValue();
        getTheme().resolveAttribute(R.attr.colorPrimary, typedValue2, true);
        toolbar.setBackgroundColor(typedValue2.data);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(typedValue.data);
        }
        if (!prefHelper.colorNavbar() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.ColorPrimaryBlack));
        }

        if (savedInstanceState==null) {
            String key = getIntent().getStringExtra("key");
            getFragmentManager().beginTransaction().replace(R.id.content_frame, NestedPreferenceFragment.newInstance(key), "nested").commit();
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
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        NestedPreferenceFragment fragment = (NestedPreferenceFragment) getFragmentManager().findFragmentByTag("nested");
        switch (requestCode) {
            case 1:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    fragment.new downloadComicsTask().execute();
                    prefHelper.setFullOffline(true);
                }
                break;
            case 2:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    fragment.new deleteComicsTask().execute();
                } else {
                    prefHelper.setFullOffline(true);
                }
                break;
            case 3:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    fragment.new downloadArticlesTask().execute();
                    prefHelper.setFullOfflineWhatIf(true);
                }
                break;
            case 4:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    fragment.new deleteArticlesTask().execute();
                } else {
                    prefHelper.setFullOfflineWhatIf(true);
                } break;
            case 5:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    fragment.new repairComicsTask().execute();
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
        new moveData().execute(new String[]{oldPath.getAbsolutePath(), path.getAbsolutePath()});
    }

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
            MainActivity.getInstance().finish();
            SettingsActivity.getInstance().finish();
            NestedSettingsActivity.this.finish();
            startActivity(MainActivity.getInstance().getIntent());
        }
    }

    @Override
    public void onEvent(OnDirectoryCancelEvent event) {
    }

    @Override
    public void onPause() {
        if (NestedPreferenceFragment.themeSettingChanged) {
            MainActivity.getInstance().finish();
            try {
                SettingsActivity.getInstance().finish();
            } catch (NullPointerException e) {
                //only happens when entered via the snackbar
            }
            startActivity(MainActivity.getInstance().getIntent());
        }
        super.onPause();
    }
}
