package de.tap.easy_xkcd.Activities;

import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.Toolbar;

import com.nbsp.materialfilepicker.ui.FilePickerActivity;
import com.tap.xkcd_reader.R;

import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.utils.ThemePrefs;

public class CustomFilePickerActivity extends FilePickerActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemePrefs themePrefs = new ThemePrefs(this);
        setTheme(themePrefs.getNewTheme());
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            getWindow().setStatusBarColor(themePrefs.getPrimaryDarkColor());
        findViewById(R.id.toolbar).setBackgroundColor(themePrefs.getPrimaryColor(false));
    }

}
