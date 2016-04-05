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

import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.Toolbar;

import com.nbsp.materialfilepicker.ui.FilePickerActivity;
import com.tap.xkcd_reader.R;

import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.utils.ThemePrefs;

public class CustomFilePickerActivity extends FilePickerActivity {

    /**
     * Setup toolbar and status bar color to match the app's theme
     */
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
