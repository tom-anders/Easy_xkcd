package de.tap.easy_xkcd.Activities;

import android.app.ActivityManager;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;

import com.tap.xkcd_reader.R;

import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.utils.ThemePrefs;

public abstract class BaseActivity extends AppCompatActivity {
    protected PrefHelper prefHelper;
    protected ThemePrefs themePrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefHelper = new PrefHelper(this);
        themePrefs = new ThemePrefs(this);

        setTheme(themePrefs.getNewTheme());
        Log.d("info", "amoled enabled " + String.valueOf(themePrefs.amoledThemeEnabled()));
        if (themePrefs.amoledThemeEnabled()) {
            getWindow().getDecorView().setBackgroundColor(Color.BLACK);
        }
        super.onCreate(savedInstanceState);
    }

    /**
     * Sets up the colors of toolbar, status bar and nav drawer
     */
    protected void setupToolbar(Toolbar toolbar) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Bitmap ic = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_easy_xkcd_recents);
            int color = themePrefs.getPrimaryColor(false);
            ActivityManager.TaskDescription description = new ActivityManager.TaskDescription("Easy xkcd", ic, color);
            setTaskDescription(description);

            if (!(this instanceof MainActivity))
                getWindow().setStatusBarColor(themePrefs.getPrimaryDarkColor());
            if (prefHelper.colorNavbar())
                getWindow().setNavigationBarColor(themePrefs.getPrimaryColor(false));
        }
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setBackgroundColor(themePrefs.getPrimaryColor(false));
        if (themePrefs.amoledThemeEnabled()) {
            toolbar.setPopupTheme(R.style.ThemeOverlay_AmoledBackground);
        }
    }

    //Useful for when starting a Async Task that would be leaked by screen rotation
    public void lockRotation() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
    }

    public void unlockRotation() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
    }
}
