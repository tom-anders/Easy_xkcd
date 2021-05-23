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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.widget.Toast;

import com.tap.xkcd_reader.R;

import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.utils.PrefHelper;

public class SettingsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        androidx.appcompat.widget.Toolbar toolbar = (androidx.appcompat.widget.Toolbar) findViewById(R.id.toolbar);
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
                case MainActivity.RESULT_SHOW_WHATIF:
                    setResult(resultCode);
                    finish();
                    break;
                case MainActivity.RESULT_UPDATE_ALARM:
                    setResult(MainActivity.RESULT_UPDATE_ALARM);
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
        private static final String OFFLINE_NOTIFICATIONS = "offline_notifications";
        private static final String ALT_SHARING = "altSharing";
        private static final String ADVANCED = "advanced";
        private static final String NIGHT = "night";
        private static final String WIDGET = "widget";
        private static final String REPAIR = "pref_repair";

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            findPreference(APPEARANCE).setOnPreferenceClickListener(this);
            findPreference(OFFLINE_NOTIFICATIONS).setOnPreferenceClickListener(this);
            findPreference(BEHAVIOR).setOnPreferenceClickListener(this);
            findPreference(ALT_SHARING).setOnPreferenceClickListener(this);
            findPreference(ADVANCED).setOnPreferenceClickListener(this);
            findPreference(NIGHT).setOnPreferenceClickListener(this);
            findPreference(WIDGET).setOnPreferenceClickListener(this);

            findPreference(REPAIR).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (new PrefHelper(getActivity()).isOnline(getActivity())) {
                        new DatabaseManager(getActivity()).setHighestInDatabase(1);
                        getActivity().setResult(Activity.RESULT_OK);
                        getActivity().finish();
                    } else {
                        Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
            });
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            // here you should use the same keys as you used in the xml-file
            ((SettingsActivity) getActivity()).showPrefFragment(preference.getKey());
            return false;
        }
    }

}
