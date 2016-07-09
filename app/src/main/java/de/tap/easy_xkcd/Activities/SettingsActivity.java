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
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import com.tap.xkcd_reader.R;

public class SettingsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        android.support.v7.widget.Toolbar toolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.toolbar);
        setupToolbar(toolbar);

        if (savedInstanceState==null)
            getFragmentManager().beginTransaction().replace(R.id.content_frame, new CustomPreferenceFragment(), "preferences").commit();
    }

    public void showPrefFragment(String key) {
        Intent intent = new Intent(SettingsActivity.this, NestedSettingsActivity.class);
        intent.putExtra("key", key);
        startActivityForResult(intent, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1)
            switch (resultCode) {
                case RESULT_OK:
                    setResult(RESULT_OK);
                    finish();
                    break;
                case MainActivity.UPDATE_ALARM:
                    setResult(MainActivity.UPDATE_ALARM);
                    break;
            }
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
        private static final String WIDGET = "widget";

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            findPreference(APPEARANCE).setOnPreferenceClickListener(this);
            findPreference(BEHAVIOR).setOnPreferenceClickListener(this);
            findPreference(ALT_SHARING).setOnPreferenceClickListener(this);
            findPreference(ADVANCED).setOnPreferenceClickListener(this);
            findPreference(NIGHT).setOnPreferenceClickListener(this);
            findPreference(WIDGET).setOnPreferenceClickListener(this);
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            // here you should use the same keys as you used in the xml-file
            ((SettingsActivity) getActivity()).showPrefFragment(preference.getKey());
            return false;
        }
    }

}
