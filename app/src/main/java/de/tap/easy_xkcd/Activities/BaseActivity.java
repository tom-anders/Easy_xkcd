package de.tap.easy_xkcd.Activities;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;

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
        //setTheme(themePrefs.getOldTheme());
        setTheme(themePrefs.getNewTheme());
        super.onCreate(savedInstanceState);
    }

    protected void setupToolbar(Toolbar toolbar) {
        //On Lollipop, change the app's icon in the recents app screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Bitmap ic = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_easy_xkcd_recents);
            int color = themePrefs.getPrimaryColor();
            ActivityManager.TaskDescription description = new ActivityManager.TaskDescription("Easy xkcd", ic, color);
            setTaskDescription(description);
            getWindow().setStatusBarColor(themePrefs.getPrimaryDarkColor());
            if (prefHelper.colorNavbar())
                getWindow().setNavigationBarColor(themePrefs.getPrimaryColor()); //TODO text color
        }
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setBackgroundColor(themePrefs.getPrimaryColor());
    }

}
