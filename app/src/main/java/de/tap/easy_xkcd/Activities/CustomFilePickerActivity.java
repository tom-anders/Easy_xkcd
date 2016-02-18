package de.tap.easy_xkcd.Activities;

import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;

import com.nbsp.materialfilepicker.ui.FilePickerActivity;
import com.tap.xkcd_reader.R;

import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.utils.ThemePrefs;

public class CustomFilePickerActivity extends FilePickerActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemePrefs themePrefs = new ThemePrefs(this);
        setTheme(themePrefs.getOldTheme());
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            TypedValue typedValue = new TypedValue();
            getTheme().resolveAttribute(R.attr.colorPrimaryDark, typedValue, true);
            getWindow().setStatusBarColor(typedValue.data);
        }
    }

}
