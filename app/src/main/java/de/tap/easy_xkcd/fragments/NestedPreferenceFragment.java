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

package de.tap.easy_xkcd.fragments;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;

import android.preference.SwitchPreference;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.tap.xkcd_reader.BuildConfig;
import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.Activities.NestedSettingsActivity;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.services.ArticleDownloadService;
import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.utils.ThemePrefs;
import timber.log.Timber;
import uz.shift.colorpicker.LineColorPicker;

public class NestedPreferenceFragment extends PreferenceFragment {
    private static final String APPEARANCE = "appearance";
    private static final String OFFLINE_NOTIFICATIONS = "offline_notifications";
    private static final String BEHAVIOR = "behavior";
    private static final String ALT_SHARING = "altSharing";
    private static final String ADVANCED = "advanced";
    private static final String NIGHT = "night";
    private static final String WIDGET = "widget";
    private static final String TAG_KEY = "NESTED_KEY";
    private static final String SUBTITLE_ENABLED = "pref_subtitle";

    private static final String COLOR_PRIMARY = "pref_color_primary";
    private static final String COLOR_ACCENT = "pref_color_accent";
    private static final String NOTIFICATIONS_INTERVAL = "pref_notifications";
    private static final String FULL_OFFLINE = "pref_offline";
    private static final String OFFLINE_INTERNAL_EXTERNAL = "pref_offline_internal_external";
    private static final String WHATIF_OFFLINE = "pref_offline_whatif";
    private static final String NIGHT_THEME = "pref_night";
    private static final String AMOLED_NIGHT = "pref_amoled";
    private static final String AUTO_NIGHT = "pref_auto_night";
    private static final String NIGHT_SYSTEM = "pref_night_system";
    private static final String AUTO_NIGHT_START = "pref_auto_night_start";
    private static final String AUTO_NIGHT_END = "pref_auto_night_end";
    private static final String INVERT_COLORS = "pref_invert";
    private static final String DETECT_COLOR = "pref_detect_color";
    private static final String MOBILE_ENABLED = "pref_update_mobile";
    private static final String FAB_OPTIONS = "pref_random";
    private static final String FAB_LEFT = "pref_fab_left";
    private static final String DONATE = "pref_hide_donate";
    private static final String WIDGET_ALT = "widget_alt";
    private static final String WIDGET_COMIC_NUMBER = "widget_comicNumber";
    private static final String NAV_DRAWER_SWIPE = "pref_nav_swipe";

    private static final String OFFLINE_WHATIF_PATH = "/what if/";

    private PrefHelper prefHelper;
    private ThemePrefs themePrefs;

    public static NestedPreferenceFragment newInstance(String key) {
        NestedPreferenceFragment fragment = new NestedPreferenceFragment();
        // supply arguments to bundle.
        Bundle args = new Bundle();
        args.putString(TAG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefHelper = new PrefHelper(getActivity());
        themePrefs = new ThemePrefs(getActivity());
        checkPreferenceResource();
    }

    private void checkPreferenceResource() {
        String key = getArguments().getString(TAG_KEY);
        Timber.d("key: %s", key);
        assert key != null;
        // Load the preferences from an XML resource
        switch (key) {
            case APPEARANCE:
                addPreferencesFromResource(R.xml.pref_appearance);

                findPreference(SUBTITLE_ENABLED).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        getActivity().setResult(Activity.RESULT_OK);
                        return true;
                    }
                });

                findPreference(COLOR_ACCENT).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        final View dialog = getActivity().getLayoutInflater().inflate(R.layout.color_chooser, null);
                        final TextView title = (TextView) dialog.findViewById(R.id.title);
                        title.setText(getResources().getString(R.string.theme_accent_color_dialog));
                        title.setBackgroundColor(themePrefs.getPrimaryColor(false));
                        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                        final LineColorPicker lineColorPicker = (LineColorPicker) dialog.findViewById(R.id.picker3);
                        lineColorPicker.setColors(themePrefs.getAccentColors());
                        lineColorPicker.setSelectedColor(themePrefs.getAccentColor());
                        if (themePrefs.nightThemeEnabled())
                            ((CardView) dialog.findViewById(R.id.dialog_card_view)).setCardBackgroundColor(ContextCompat.getColor(getActivity(), R.color.background_material_dark));

                        builder.setView(dialog);
                        final AlertDialog alertDialog = builder.show();
                        dialog.findViewById(R.id.ok).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                themePrefs.setNewTheme(lineColorPicker.getColor());
                                getActivity().setResult(Activity.RESULT_OK);
                                new Handler().post(new Runnable() {
                                    @Override
                                    public void run() {
                                        alertDialog.dismiss();
                                        Intent intent = getActivity().getIntent();
                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                                                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                                        getActivity().overridePendingTransition(0, 0);
                                        getActivity().finish();

                                        getActivity().overridePendingTransition(0, 0);
                                        startActivity(intent);
                                    }
                                });
                            }
                        });
                        return true;
                    }
                });
                findPreference(COLOR_PRIMARY).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        final LayoutInflater inflater = getActivity().getLayoutInflater();
                        final View dialog = inflater.inflate(R.layout.color_chooser, null);
                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                        final TextView title = (TextView) dialog.findViewById(R.id.title);
                        title.setText(getResources().getString(R.string.theme_primary_color_dialog));
                        title.setBackgroundColor(themePrefs.getPrimaryColor(false));
                        final LineColorPicker lineColorPicker = (LineColorPicker) dialog.findViewById(R.id.picker3);
                        lineColorPicker.setColors(themePrefs.getPrimaryColors());
                        lineColorPicker.setSelectedColor(themePrefs.getPrimaryColor(true));
                        if (themePrefs.nightThemeEnabled())
                            ((CardView) dialog.findViewById(R.id.dialog_card_view)).setCardBackgroundColor(ContextCompat.getColor(getActivity(), R.color.background_material_dark));


                        builder.setView(dialog);
                        final AlertDialog alertDialog = builder.show();
                        dialog.findViewById(R.id.ok).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                themePrefs.setPrimaryColor(lineColorPicker.getColor());
                                getActivity().setResult(Activity.RESULT_OK);
                                new Handler().post(new Runnable() {
                                    @Override
                                    public void run() {
                                        alertDialog.dismiss();
                                        Intent intent = getActivity().getIntent();
                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                                                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                                        getActivity().overridePendingTransition(0, 0);
                                        getActivity().finish();

                                        getActivity().overridePendingTransition(0, 0);
                                        startActivity(intent);
                                    }
                                });

                            }
                        });
                        return true;
                    }
                });

                findPreference(DONATE).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        getActivity().setResult(Activity.RESULT_OK);
                        return true;
                    }
                });

                findPreference(FAB_OPTIONS).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object o) {
                        getActivity().setResult(Activity.RESULT_OK);
                        return true;
                    }
                });
                findPreference(FAB_LEFT).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object o) {
                        getActivity().setResult(Activity.RESULT_OK);
                        return true;
                    }
                });
                break;

            case OFFLINE_NOTIFICATIONS:
                addPreferencesFromResource(R.xml.pref_offline_notifications);
                findPreference(NOTIFICATIONS_INTERVAL).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(final Preference preference, Object o) {
                        getActivity().setResult(MainActivity.UPDATE_ALARM);
                        return true;
                    }
                });
                findPreference(FULL_OFFLINE).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        boolean checked = Boolean.valueOf(newValue.toString());
                        if (checked) {
                            if (prefHelper.isOnline(getActivity())) {
                                if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                                    Toast.makeText(getActivity(), getResources().getString(R.string.loading_comics), Toast.LENGTH_SHORT).show();
                                    new DatabaseManager(getActivity()).setHighestInDatabase(1);
                                    prefHelper.setFullOffline(true);
                                    getActivity().setResult(Activity.RESULT_OK);
                                    getActivity().finish();
                                } else {
                                    ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                                }
                            } else {
                                Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
                            }
                            return false;
                        } else {
                            androidx.appcompat.app.AlertDialog.Builder mDialog = new androidx.appcompat.app.AlertDialog.Builder(getActivity());
                            mDialog.setMessage(R.string.delete_offline_dialog)
                                    .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            getActivity().finish();
                                            prefHelper.setFullOffline(true);
                                        }
                                    })
                                    .setPositiveButton(R.string.dialog_yes, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                                                new deleteComicsTask().execute();
                                            } else {
                                                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
                                            }
                                        }
                                    })
                                    .setCancelable(false);
                            mDialog.show();
                            return true;
                        }
                    }
                });
                findPreference(WHATIF_OFFLINE).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        boolean checked = Boolean.valueOf(newValue.toString());
                        if (checked) {
                            if (prefHelper.isOnline(getActivity())) {
                                if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                                    //new downloadArticlesTask().execute();
                                    Toast.makeText(getActivity(), getResources().getString(R.string.loading_articles), Toast.LENGTH_SHORT).show();
                                    getActivity().startService(new Intent(getActivity(), ArticleDownloadService.class));
                                    return true;
                                } else {
                                    ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 3);
                                    return false;
                                }
                            } else {
                                Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
                                return false;
                            }

                        } else {
                            androidx.appcompat.app.AlertDialog.Builder mDialog = new androidx.appcompat.app.AlertDialog.Builder(getActivity());
                            mDialog.setMessage(R.string.delete_offline_whatif_dialog)
                                    .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            getActivity().finish();
                                            prefHelper.setFullOfflineWhatIf(true);
                                        }
                                    })
                                    .setPositiveButton(R.string.dialog_yes, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                                                new deleteArticlesTask().execute();
                                            } else {
                                                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
                                            }
                                        }
                                    })
                                    .setCancelable(false);
                            mDialog.show();
                            return true;
                        }
                    }
                });
                findPreference(MOBILE_ENABLED).setEnabled(MainActivity.fullOffline | MainActivity.fullOfflineWhatIf);

                findPreference(OFFLINE_INTERNAL_EXTERNAL).setOnPreferenceChangeListener((preference, o) -> {
                    File oldPath = prefHelper.getOfflinePath(getActivity());
                    File newPath = prefHelper.getOfflinePathForValue(getActivity(), o.toString());
                    if (newPath != oldPath) {
                        Timber.d("Moving from %s to %s", oldPath.getAbsolutePath(), newPath.getAbsolutePath());
                        new moveData().execute(new String[]{oldPath.getAbsolutePath(), newPath.getAbsolutePath()});
                    }
                    return true;
                });

                break;


            case BEHAVIOR:
                addPreferencesFromResource(R.xml.pref_behavior);
                findPreference(NAV_DRAWER_SWIPE).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        prefHelper.setNavDrawerSwipe(Boolean.parseBoolean(newValue.toString()));
                        getActivity().setResult(Activity.RESULT_OK);
                        return true;
                    }
                });

                break;

            case NIGHT:
                addPreferencesFromResource(R.xml.pref_night);
                final Preference start = findPreference(AUTO_NIGHT_START);
                final Preference end = findPreference(AUTO_NIGHT_END);
                final int[] startTime = themePrefs.getAutoNightStart();
                final int[] endTime = themePrefs.getAutoNightEnd();
                start.setSummary(themePrefs.getStartSummary());
                end.setSummary(themePrefs.getEndSummary());

                if (Build.VERSION.SDK_INT < 29) {
                    getPreferenceScreen().removePreference(findPreference(NIGHT_SYSTEM));
                } else {
                    findPreference(NIGHT_THEME).setEnabled(!((SwitchPreference) findPreference(NIGHT_SYSTEM)).isChecked());
                    findPreference(AUTO_NIGHT).setEnabled(!((SwitchPreference) findPreference(NIGHT_SYSTEM)).isChecked());

                    if (((SwitchPreference) findPreference(NIGHT_SYSTEM)).isChecked()) {
                        ((SwitchPreference) findPreference(NIGHT_THEME)).setEnabled(false);
                    }
                
                    findPreference(NIGHT_SYSTEM).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            getActivity().setResult(Activity.RESULT_OK);
                            Intent intent = getActivity().getIntent();
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                            getActivity().overridePendingTransition(0, 0);
                            getActivity().finish();

                            getActivity().overridePendingTransition(0, 0);
                            startActivity(intent);
                            return true;
                        }
                    });
                }

                findPreference(NIGHT_THEME).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        getActivity().setResult(Activity.RESULT_OK);
                        Intent intent = getActivity().getIntent();
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        getActivity().overridePendingTransition(0, 0);
                        getActivity().finish();

                        getActivity().overridePendingTransition(0, 0);
                        startActivity(intent);
                        return true;
                    }
                });
                findPreference(AUTO_NIGHT).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        getActivity().setResult(Activity.RESULT_OK);
                        return true;
                    }
                });
                findPreference(DETECT_COLOR).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        getActivity().setResult(Activity.RESULT_OK);
                        return true;
                    }
                });
                findPreference(AMOLED_NIGHT).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        getActivity().setResult(Activity.RESULT_OK);
                        Intent intent = getActivity().getIntent();
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        getActivity().overridePendingTransition(0, 0);
                        getActivity().finish();

                        getActivity().overridePendingTransition(0, 0);
                        startActivity(intent);
                        return true;
                    }
                });
                findPreference(INVERT_COLORS).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        getActivity().setResult(Activity.RESULT_OK);
                        return true;
                    }
                });
                start.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        TimePickerDialog tpd = new TimePickerDialog(getActivity(), new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker timePicker, int hourOfDay, int minute) {
                                themePrefs.setAutoNightStart(new int[]{hourOfDay, minute});
                                getActivity().setResult(Activity.RESULT_OK);
                                start.setSummary(themePrefs.getStartSummary());
                            }
                        }, startTime[0], startTime[1], android.text.format.DateFormat.is24HourFormat(getActivity()));
                        tpd.show();
                        return true;
                    }
                });
                end.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        TimePickerDialog tpd = new TimePickerDialog(getActivity(), new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker timePicker, int hourOfDay, int minute) {
                                themePrefs.setAutoNightEnd(new int[]{hourOfDay, minute});
                                getActivity().setResult(Activity.RESULT_OK);
                                end.setSummary(themePrefs.getEndSummary());
                            }
                        }, endTime[0], endTime[1], android.text.format.DateFormat.is24HourFormat(getActivity()));
                        tpd.show();
                        return true;
                    }
                });
                findPreference(COLOR_ACCENT).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        final View dialog = getActivity().getLayoutInflater().inflate(R.layout.color_chooser, null);
                        final TextView title = (TextView) dialog.findViewById(R.id.title);
                        title.setText(getResources().getString(R.string.theme_accent_color_dialog));
                        title.setBackgroundColor(themePrefs.getPrimaryColor(false));
                        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                        final LineColorPicker lineColorPicker = (LineColorPicker) dialog.findViewById(R.id.picker3);
                        lineColorPicker.setColors(themePrefs.getAccentColors());
                        lineColorPicker.setSelectedColor(themePrefs.getAccentColor());
                        if (themePrefs.nightThemeEnabled())
                            ((CardView) dialog.findViewById(R.id.dialog_card_view)).setCardBackgroundColor(ContextCompat.getColor(getActivity(), R.color.background_material_dark));

                        builder.setView(dialog);
                        final AlertDialog alertDialog = builder.show();
                        dialog.findViewById(R.id.ok).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                themePrefs.setAccentColorNight(lineColorPicker.getColor());
                                getActivity().setResult(Activity.RESULT_OK);
                                new Handler().post(new Runnable() {
                                    @Override
                                    public void run() {
                                        alertDialog.dismiss();
                                        Intent intent = getActivity().getIntent();
                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                                                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                                        getActivity().overridePendingTransition(0, 0);
                                        getActivity().finish();

                                        getActivity().overridePendingTransition(0, 0);
                                        startActivity(intent);
                                    }
                                });
                            }
                        });
                        return true;
                    }
                });
                break;

            case ALT_SHARING:
                addPreferencesFromResource(R.xml.pref_alt_sharing);
                break;

            case WIDGET:
                addPreferencesFromResource(R.xml.pref_widgets);
                break;

            case ADVANCED:
                addPreferencesFromResource(R.xml.pref_advanced);
                break;
        }
    }

    public class deleteComicsTask extends AsyncTask<Void, Integer, Void> {
        private ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(getActivity());
            progress.setTitle(getResources().getString(R.string.delete_offline));
            progress.setMessage(getResources().getString(R.string.loading_offline_message));
            progress.setIndeterminate(false);
            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            int newest = prefHelper.getNewest();
            if (!BuildConfig.DEBUG) {
                DatabaseManager databaseManager = new DatabaseManager(getActivity());
                for (int i = 1; i <= newest; i++) {
                    if (!databaseManager.isFavorite(i)) {
                        //delete from internal storage
                        getActivity().deleteFile(String.valueOf(i));
                        //delete from external storage
                        File file = new File(prefHelper.getOfflinePath(getActivity()), String.valueOf(i) + ".png");
                        file.delete();

                        int p = (int) (i / ((float) newest) * 100);
                        publishProgress(p);
                    }
                }
            }
            prefHelper.setFullOffline(false);
            return null;
        }

        protected void onProgressUpdate(Integer... pro) {
            progress.setProgress(pro[0]);
        }

        @Override
        protected void onPostExecute(Void dummy) {
            progress.dismiss();
            getActivity().setResult(Activity.RESULT_OK);
            getActivity().finish();
        }
    }

    public class deleteArticlesTask extends AsyncTask<Void, Void, Void> {
        private ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(getActivity());
            progress.setTitle(getResources().getString(R.string.delete_offline_articles));
            progress.setMessage(getResources().getString(R.string.loading_offline_message));
            progress.setIndeterminate(true);
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (!BuildConfig.DEBUG) {
                File sdCard = prefHelper.getOfflinePath(getActivity());
                File dir = new File(sdCard.getAbsolutePath() + OFFLINE_WHATIF_PATH);
                deleteFolder(dir);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            progress.dismiss();
            prefHelper.setFullOfflineWhatIf(false);
            getActivity().setResult(Activity.RESULT_OK);
            getActivity().finish();
        }
    }

    private void deleteFolder(File file) {
        if (file.isDirectory())
            for (File child : file.listFiles())
                deleteFolder(child);
        file.delete();
    }

    /**
     * moves the folder for offline data to a new directory
     */
    public class moveData extends AsyncTask<String[], Void, Void> {
        private ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(getActivity());
            progress.setTitle(getResources().getString(R.string.copy_folder));
            progress.setMessage(getResources().getString(R.string.loading_offline_message));
            progress.setIndeterminate(true);
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected Void doInBackground(String[]... params) {
            File oldPath = new File(params[0][0]);
            File newPath = new File(params[0][1]);

            try {
                copyDirectory(oldPath, newPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            deleteFolder(oldPath);
            return null;
        }

        private void copyDirectory(File sourceLocation , File targetLocation)
                throws IOException {

            if (sourceLocation.isDirectory()) {
                if (!targetLocation.exists() && !targetLocation.mkdirs()) {
                    throw new IOException("Cannot create dir " + targetLocation.getAbsolutePath());
                }

                String[] children = sourceLocation.list();
                for (int i=0; i<children.length; i++) {
                    copyDirectory(new File(sourceLocation, children[i]),
                            new File(targetLocation, children[i]));
                }
            } else {

                // make sure the directory we plan to store the recording in exists
                File directory = targetLocation.getParentFile();
                if (directory != null && !directory.exists() && !directory.mkdirs()) {
                    throw new IOException("Cannot create dir " + directory.getAbsolutePath());
                }

                InputStream in = new FileInputStream(sourceLocation);
                OutputStream out = new FileOutputStream(targetLocation);

                // Copy the bits from instream to outstream
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                out.close();
            }
        }

        private void deleteFolder(File file) {
            if (file.isDirectory())
                for (File child : file.listFiles())
                    deleteFolder(child);
            file.delete();
        }

        @Override
        protected void onPostExecute(Void dummy) {
            progress.dismiss();
            getActivity().setResult(Activity.RESULT_OK);
        }
    }

}
