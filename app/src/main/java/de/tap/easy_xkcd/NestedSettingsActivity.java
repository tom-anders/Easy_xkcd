package de.tap.easy_xkcd;


import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.TypedValue;

import com.tap.xkcd_reader.R;

import de.tap.easy_xkcd.fragments.NestedPreferenceFragment;
import de.tap.easy_xkcd.utils.PrefHelper;

public class NestedSettingsActivity extends AppCompatActivity {
    private static final String APPEARANCE = "appearance";
    private static final String BEHAVIOR = "behavior";
    private static final String ALT_SHARING = "altSharing";
    private static final String ADVANCED = "advanced";
    private static final String NIGHT = "night";
    private String mKey;

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
        if (!PrefHelper.colorNavbar() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(getResources().getColor(R.color.ColorPrimaryBlack));
        }

        if (savedInstanceState==null) {
            String key = getIntent().getStringExtra("key");
            mKey = key;
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
                    PrefHelper.setFullOffline(true);
                }
                break;
            case 2:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    fragment.new deleteComicsTask().execute();
                } else {
                    PrefHelper.setFullOffline(true);
                }
                break;
            case 3:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    fragment.new downloadArticlesTask().execute();
                    PrefHelper.setFullOfflineWhatIf(true);
                }
                break;
            case 4:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    fragment.new deleteArticlesTask().execute();
                } else {
                    PrefHelper.setFullOfflineWhatIf(true);
                } break;
            case 5:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    fragment.new repairComicsTask().execute();
                }
        }
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

    public boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }
}
