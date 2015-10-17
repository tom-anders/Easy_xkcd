/**********************************************************************************
 * Copyright 2015 Tom Praschan
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
 ********************************************************************************/

package de.tap.easy_xkcd.Activities;

import android.content.Intent;
import android.os.Build;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.TypedValue;

import com.tap.xkcd_reader.R;

import de.tap.easy_xkcd.utils.PrefHelper;

public class SettingsActivity extends AppCompatActivity {
    private static SettingsActivity instance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(PrefHelper.getTheme());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        instance = this;

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
            if (!PrefHelper.colorNavbar())
                getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.ColorPrimaryBlack));
        }

        if (savedInstanceState==null)
            getFragmentManager().beginTransaction().replace(R.id.content_frame, new CustomPreferenceFragment(), "preferences").commit();
    }

    public void showPrefFragment(String key) {
        Intent intent = new Intent(SettingsActivity.this, NestedSettingsActivity.class);
        intent.putExtra("key", key);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        // this if statement is necessary to navigate through nested and main fragments
        if (getFragmentManager().getBackStackEntryCount() == 0) {
            super.onBackPressed();
        } else {
            getFragmentManager().popBackStack();
        }
    }

    public static class CustomPreferenceFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener {
        private static final String APPEARANCE = "appearance";
        private static final String BEHAVIOR = "behavior";
        private static final String ALT_SHARING = "altSharing";
        private static final String ADVANCED = "advanced";
        private static final String NIGHT = "night";
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            findPreference(APPEARANCE).setOnPreferenceClickListener(this);
            findPreference(BEHAVIOR).setOnPreferenceClickListener(this);
            findPreference(ALT_SHARING).setOnPreferenceClickListener(this);
            findPreference(ADVANCED).setOnPreferenceClickListener(this);
            findPreference(NIGHT).setOnPreferenceClickListener(this);
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            // here you should use the same keys as you used in the xml-file
            ((SettingsActivity) getActivity()).showPrefFragment(preference.getKey());
            return false;
        }
    }

    public static SettingsActivity getInstance() {
        return instance;
    }

}
