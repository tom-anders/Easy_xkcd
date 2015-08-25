/**********************************************************************************
 * Copyright 2015 Tom Praschan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ********************************************************************************/

package de.tap.easy_xkcd;

import android.os.Build;
import android.preference.PreferenceFragment;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.TypedValue;

import com.tap.xkcd_reader.R;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(PrefHelper.getTheme());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        //Setup toolbar and status bar color
        android.support.v7.widget.Toolbar toolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState==null) {
            TypedValue typedValue = new TypedValue();
            getTheme().resolveAttribute(R.attr.colorPrimaryDark, typedValue, true);
            TypedValue typedValue2 = new TypedValue();
            getTheme().resolveAttribute(R.attr.colorPrimary, typedValue2, true);
            toolbar.setBackgroundColor(typedValue2.data);
            if (Build.VERSION.SDK_INT >= 21) {
                getWindow().setStatusBarColor(typedValue.data);
            }
        }

        //load the settings fragment
        getFragmentManager().beginTransaction().replace(R.id.content_frame, new CustomPreferenceFragment()).commit();
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    public static class CustomPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState){
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
        }

    }

}
