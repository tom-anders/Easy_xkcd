package de.tap.easy_xkcd.fragments;


import android.Manifest;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.TimePicker;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.tap.xkcd_reader.BuildConfig;
import com.tap.xkcd_reader.R;
import com.turhanoz.android.reactivedirectorychooser.ui.DirectoryChooserFragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.Activities.NestedSettingsActivity;
import de.tap.easy_xkcd.Activities.SettingsActivity;
import de.tap.easy_xkcd.notifications.ComicListener;
import de.tap.easy_xkcd.services.ArticleDownloadService;
import de.tap.easy_xkcd.services.ComicDownloadService;
import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.utils.Favorites;
import de.tap.easy_xkcd.utils.PrefHelper;

public class NestedPreferenceFragment extends PreferenceFragment {
    private static final String APPEARANCE = "appearance";
    private static final String BEHAVIOR = "behavior";
    private static final String ALT_SHARING = "altSharing";
    private static final String ADVANCED = "advanced";
    private static final String NIGHT = "night";
    private static final String TAG_KEY = "NESTED_KEY";

    private static final String COLORED_NAVBAR = "pref_navbar";
    private static final String THEME = "pref_theme";
    private static final String NOTIFICATIONS_INTERVAL = "pref_notifications";
    private static final String ORIENTATION = "pref_orientation";
    private static final String FULL_OFFLINE = "pref_offline";
    private static final String WHATIF_OFFLINE = "pref_offline_whatif";
    private static final String NIGHT_THEME = "pref_night";
    private static final String AUTO_NIGHT = "pref_auto_night";
    private static final String AUTO_NIGHT_START = "pref_auto_night_start";
    private static final String AUTO_NIGHT_END = "pref_auto_night_end";
    private static final String REPAIR = "pref_repair";
    private static final String MOBILE_ENABLED = "pref_update_mobile";
    private static final String FAB_OPTIONS = "pref_random";
    private static final String OFFLINE_PATH_PREF = "pref_offline_path";

    private static final String OFFLINE_PATH = "/easy xkcd";
    private static final String OFFLINE_WHATIF_PATH = "/easy xkcd/what if/";
    private static final String OFFLINE_WHATIF_OVERVIEW_PATH = "/easy xkcd/what if/overview";


    public static boolean themeSettingChanged;
    private PrefHelper prefHelper;

    public static NestedPreferenceFragment newInstance(String key) {
        NestedPreferenceFragment fragment = new NestedPreferenceFragment();
        // supply arguments to bundle.
        Bundle args = new Bundle();
        args.putString(TAG_KEY, key);
        fragment.setArguments(args);
        themeSettingChanged = false;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefHelper = new PrefHelper(getActivity().getApplicationContext());
        checkPreferenceResource();
    }

    private void checkPreferenceResource() {
        String key = getArguments().getString(TAG_KEY);
        assert key != null;
        // Load the preferences from an XML resource
        switch (key) {
            case APPEARANCE:
                addPreferencesFromResource(R.xml.pref_appearance);
                findPreference(COLORED_NAVBAR).setEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
                findPreference(COLORED_NAVBAR).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        themeSettingChanged = true;
                        return true;
                    }
                });
                findPreference(THEME).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        themeSettingChanged = true;
                        return true;
                    }
                });
                findPreference(FAB_OPTIONS).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object o) {
                        themeSettingChanged = true;
                        return true;
                    }
                });
                break;

            case BEHAVIOR:
                addPreferencesFromResource(R.xml.pref_behavior);
                findPreference(NOTIFICATIONS_INTERVAL).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(final Preference preference, Object o) {
                        final int newValue = Integer.parseInt(o.toString());
                        //Wait a few seconds for the new value to be written to memory
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (newValue != 0) {
                                    WakefulIntentService.scheduleAlarms(new ComicListener(), MainActivity.getInstance(), true);
                                } else {
                                    WakefulIntentService.cancelAlarms(MainActivity.getInstance());
                                }
                            }
                        }, 5000);
                        return true;
                    }
                });
                /*findPreference(ORIENTATION).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        switch (Integer.parseInt(prefHelper.getOrientation())) {
                            case 1:
                                MainActivity.getInstance().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
                                break;
                            case 2:
                                MainActivity.getInstance().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                                break;
                            case 3:
                                MainActivity.getInstance().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                                break;
                        }
                        return true;
                    }
                });*/
                findPreference(FULL_OFFLINE).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        boolean checked = Boolean.valueOf(newValue.toString());
                        if (checked) {
                            if (prefHelper.isOnline(getActivity())) {
                                if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                                    Toast.makeText(getActivity(), getResources().getString(R.string.loading_comics), Toast.LENGTH_SHORT).show();
                                    getActivity().startService(new Intent(getActivity(), ComicDownloadService.class));
                                } else {
                                    ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                                }
                            } else {
                                Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
                            }
                            return false;
                        } else {
                            android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity(), prefHelper.getDialogTheme());
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
                            android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity(), prefHelper.getDialogTheme());
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
                break;

            case NIGHT:
                addPreferencesFromResource(R.xml.pref_night);
                final Preference start = findPreference(AUTO_NIGHT_START);
                final Preference end = findPreference(AUTO_NIGHT_END);
                final int[] startTime = prefHelper.getAutoNightStart();
                final int[] endTime = prefHelper.getAutoNightEnd();
                start.setSummary(prefHelper.getStartSummary());
                end.setSummary(prefHelper.getEndSummary());


                findPreference(NIGHT_THEME).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        prefHelper.setNightMode(Boolean.valueOf(newValue.toString()));
                        themeSettingChanged = true;
                        return true;
                    }
                });
                findPreference(AUTO_NIGHT).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        themeSettingChanged = true;
                        return true;
                    }
                });
                start.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        TimePickerDialog tpd = new TimePickerDialog(getActivity(), new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker timePicker, int hourOfDay, int minute) {
                                prefHelper.setAutoNightStart(new int[]{hourOfDay, minute});
                                themeSettingChanged = true;
                                start.setSummary(prefHelper.getStartSummary());
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
                                prefHelper.setAutoNightEnd(new int[]{hourOfDay, minute});
                                themeSettingChanged = true;
                                end.setSummary(prefHelper.getEndSummary());
                            }
                        }, endTime[0], endTime[1], android.text.format.DateFormat.is24HourFormat(getActivity()));
                        tpd.show();
                        return true;
                    }
                });
                break;

            case ALT_SHARING:
                addPreferencesFromResource(R.xml.pref_alt_sharing);
                break;

            case ADVANCED:
                addPreferencesFromResource(R.xml.pref_advanced);
                findPreference(REPAIR).setEnabled(MainActivity.fullOffline);
                findPreference(MOBILE_ENABLED).setEnabled(MainActivity.fullOffline | MainActivity.fullOfflineWhatIf);

                findPreference(REPAIR).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (prefHelper.isOnline(getActivity())) {
                            if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                                new repairComicsTask().execute();
                            } else {
                                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 5);
                            }
                        } else {
                            Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    }
                });
                findPreference(OFFLINE_PATH_PREF).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                            DialogFragment directoryChooserFragment = DirectoryChooserFragment.newInstance(Environment.getExternalStorageDirectory());

                            FragmentTransaction transaction = ((NestedSettingsActivity) getActivity()).getManger().beginTransaction();
                            directoryChooserFragment.show(transaction, "RDC");
                        } else {
                            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 12);
                        }
                        return true;
                    }
                });
                break;
        }
    }

    public class repairComicsTask extends AsyncTask<Void, Integer, Void> {
        private ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(getActivity());
            progress.setTitle(getResources().getString(R.string.loading_offline));
            progress.setMessage(getResources().getString(R.string.loading_offline_message));
            progress.setIndeterminate(false);
            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            int newest;
            try {
                newest = new Comic(0).getComicNumber();
                prefHelper.setNewestComic(newest);
                prefHelper.setHighestOffline(newest);
            } catch (Exception e) {
                newest = prefHelper.getNewest();
            }
            Bitmap mBitmap = null;
            for (int i = 1; i <= newest; i++) {
                Log.d("i", String.valueOf(i));
                try {
                    FileInputStream fis = getActivity().openFileInput(String.valueOf(i));
                    //mBitmap = BitmapFactory.decodeStream(fis);
                    fis.close();
                } catch (Exception e) {
                    Log.e("error", "not found in internal");
                    try {
                        File sdCard = prefHelper.getOfflinePath();
                        File dir = new File(sdCard.getAbsolutePath() + OFFLINE_PATH);
                        File file = new File(dir, String.valueOf(i) + ".png");
                        FileInputStream fis = new FileInputStream(file);
                        mBitmap = BitmapFactory.decodeStream(fis);
                        fis.close();
                    } catch (Exception e2) {
                        Log.e("error", "not found in external");
                        redownloadComic(i);
                    }
                }
                int p = (int) (i / ((float) newest) * 100);
                publishProgress(p);
            }

            return null;
        }

        private void redownloadComic(int i) {
            try {
                Comic comic = new Comic(i, getActivity());
                String url = comic.getComicData()[2];
                Bitmap mBitmap = Glide.with(getActivity())
                        .load(url)
                        .asBitmap()
                        .into(-1, -1)
                        .get();
                try {
                    File sdCard = prefHelper.getOfflinePath();
                    File dir = new File(sdCard.getAbsolutePath() + OFFLINE_PATH);
                    dir.mkdirs();
                    File file = new File(dir, String.valueOf(i) + ".png");
                    FileOutputStream fos = new FileOutputStream(file);
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.flush();
                    fos.close();
                } catch (Exception e) {
                    Log.e("Error", "Saving to external storage failed");
                    try {
                        FileOutputStream fos = getActivity().openFileOutput(String.valueOf(i), Context.MODE_PRIVATE);
                        mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.close();
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                }
                prefHelper.addTitle(comic.getComicData()[0], i);
                prefHelper.addAlt(comic.getComicData()[1], i);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        protected void onProgressUpdate(Integer... pro) {
            progress.setProgress(pro[0]);
        }

        @Override
        protected void onPostExecute(Void dummy) {
            progress.dismiss();
            MainActivity.getInstance().finish();
            SettingsActivity.getInstance().finish();
            getActivity().finish();
            startActivity(MainActivity.getInstance().getIntent());
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
                for (int i = 1; i <= newest; i++) {
                    if (!Favorites.checkFavorite(MainActivity.getInstance(), i)) {
                        //delete from internal storage
                        getActivity().deleteFile(String.valueOf(i));
                        //delete from external storage
                        File sdCard = prefHelper.getOfflinePath();
                        File dir = new File(sdCard.getAbsolutePath() + OFFLINE_PATH);
                        File file = new File(dir, String.valueOf(i) + ".png");
                        file.delete();

                        int p = (int) (i / ((float) newest) * 100);
                        publishProgress(p);
                    }
                }
                prefHelper.deleteTitleAndAlt(newest, getActivity());
            }
            prefHelper.setHighestOffline(0);
            prefHelper.setFullOffline(false);
            return null;
        }

        protected void onProgressUpdate(Integer... pro) {
            progress.setProgress(pro[0]);
        }

        @Override
        protected void onPostExecute(Void dummy) {
            progress.dismiss();
            MainActivity.getInstance().finish();
            SettingsActivity.getInstance().finish();
            getActivity().finish();
            startActivity(MainActivity.getInstance().getIntent());
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
                File sdCard = prefHelper.getOfflinePath();
                File dir = new File(sdCard.getAbsolutePath() + OFFLINE_WHATIF_PATH);
                deleteFolder(dir);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            progress.dismiss();
            prefHelper.setFullOfflineWhatIf(false);
            MainActivity.getInstance().finish();
            SettingsActivity.getInstance().finish();
            getActivity().finish();
            startActivity(MainActivity.getInstance().getIntent());
        }
    }

    private void deleteFolder(File file) {
        if (file.isDirectory())
            for (File child : file.listFiles())
                deleteFolder(child);
        file.delete();
    }

}