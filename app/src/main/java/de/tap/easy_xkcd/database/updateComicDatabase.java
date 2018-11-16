package de.tap.easy_xkcd.database;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import com.tap.xkcd_reader.BuildConfig;
import com.tap.xkcd_reader.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import de.tap.easy_xkcd.fragments.comics.ComicFragment;
import de.tap.easy_xkcd.utils.PrefHelper;
import io.realm.Realm;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import timber.log.Timber;

import static de.tap.easy_xkcd.utils.JsonParser.getNewHttpClient;

public class updateComicDatabase extends AsyncTask<Void, Integer, Void> {
    protected ProgressDialog progress;
    protected PrefHelper prefHelper;
    protected DatabaseManager databaseManager;
    protected Context context;
    protected boolean showProgress = true;
    protected boolean newComicFound = false;

    public updateComicDatabase(PrefHelper prefHelper, DatabaseManager databaseManager, Context context) {
        this.prefHelper = prefHelper;
        this.databaseManager = databaseManager;
        this.context = context;
    }

    @Override
    protected void onPreExecute() {
        if (showProgress) {
            progress = new ProgressDialog(context);
            progress.setTitle(context.getResources().getString(R.string.update_database));
            progress.setMessage(context.getResources().getString(R.string.update_database_message));
            progress.setIndeterminate(false);
            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progress.setCancelable(false);
            progress.show();
        }
    }

    private int findNewest() {
        try {
            return RealmComic.findNewestComicNumber();
        } catch (IOException e) {
            return prefHelper.getNewest();
        } catch (JSONException e) {
            Timber.wtf("Latest JSON at https://xkcd.com/info.0.json doesnt have a number?!");
            return prefHelper.getNewest();
        }
    }

    @Override
    protected Void doInBackground(Void... params) {
        if (prefHelper.isOnline(context)) {
            final int newest = findNewest();
            if (newest > prefHelper.getNewest()) {
                ComicFragment.newComicFound = prefHelper.getNewest() != 0.0; //TODO test if this still works
                prefHelper.setNewestComic(newest);
                newComicFound = true;
            }
            if (prefHelper.getLastComic() == 0) { //Should only be true on first startup
                Timber.d("prefHelper.getLastComic() was 0, either this is the first launch or we're coming from a notification");
                prefHelper.setLastComic(newest);
            }
            final int highest = databaseManager.getHighestInDatabase(); //TODO make a new key in sharedPreferences for that, such old users update their database as well!
            if (highest == newest) {
                publishProgress(100);
                Timber.d("No new comic found!");
                return null;  //Database already up to date
            }
            OkHttpClient client = getNewHttpClient();
            final CountDownLatch latch = new CountDownLatch(newest - highest);

            final ConcurrentHashMap<Integer, JSONObject> jsons = new ConcurrentHashMap<>(newest - highest - 1);

            for (int i = highest + 1; i <= newest; i++) {
                final int num = i;
                client.newCall(new Request.Builder().url(RealmComic.getJsonUrl(i)).build()).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        e.printStackTrace();
                        Log.e("error " + num, e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        JSONObject json = new JSONObject();
                        try {
                            json = new JSONObject(response.body().string());
                        } catch (JSONException e) {
                            if (num == 404) {
                                Timber.i("Json not found, but that's expected for comic 404");
                            } else {
                                Timber.e("json exception at %d: %s", num, e.getMessage());
                            }
                        }
                        jsons.put(num, json);
                        response.body().close();

                        int p = (int) (((newest - highest - latch.getCount()) / ((float) newest - highest)) * 100);
                        publishProgress(p);
                        latch.countDown();
                    }
                });
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            final Realm realm = Realm.getDefaultInstance();
            realm.beginTransaction();
            boolean fullOffline = prefHelper.fullOfflineEnabled();
            final CountDownLatch latch2 = new CountDownLatch(newest - highest);
            if (fullOffline) {
                progress.setMessage(context.getResources().getString(R.string.update_database_message_offline));
            }
            for (Integer num : jsons.keySet()) {
                JSONObject json = jsons.get(num);
                RealmComic oldRealmComic = realm.where(RealmComic.class).equalTo("comicNumber", num).findFirst();
                final RealmComic newRealmComic = RealmComic.buildFromJson(realm, num, json, context);

                //Import read and favorite comics from the old database
                if (oldRealmComic != null && oldRealmComic.isFavorite()) {
                    Timber.d("comic %d was a favorite in the old realm database!", num);
                    newRealmComic.setFavorite(true);
                } else if (databaseManager.checkFavoriteLegacy(num)) {
                    Timber.d("comic %d was a legacy favorite!", num);
                    newRealmComic.setFavorite(true);
                }
                if (oldRealmComic != null && oldRealmComic.isRead()) {
                    Timber.d("comic %d was read in the old realm database!", num);
                    newRealmComic.setRead(true);
                } else if (databaseManager.checkReadLegacy(num)) {
                    Timber.d("comic %d was legacy read!", num);
                    newRealmComic.setRead(true);
                }

                realm.copyToRealmOrUpdate(newRealmComic);

                if (fullOffline) {
                    Request request = new Request.Builder()
                            .url(newRealmComic.getUrl())
                            .build();
                    client.newCall(request).enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            Timber.e("call to comic %d failed", newRealmComic.getComicNumber());
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            try {
                                File sdCard = prefHelper.getOfflinePath();
                                File dir = new File(sdCard.getAbsolutePath() + RealmComic.OFFLINE_PATH);
                                File file = new File(dir, newRealmComic.getComicNumber() + ".png");
                                BufferedSink sink = Okio.buffer(Okio.sink(file));
                                sink.writeAll(response.body().source());
                                sink.close();
                            } catch (Exception e) {
                                Timber.e("Error at comic %d: Saving to external storage failed!", newRealmComic.getComicNumber());
                                Timber.e(e);
                                try {
                                    FileOutputStream fos = context.openFileOutput(String.valueOf(newRealmComic.getComicNumber()), Context.MODE_PRIVATE);
                                    BufferedSink sink = Okio.buffer(Okio.sink(fos));
                                    sink.writeAll(response.body().source());
                                    fos.close();
                                    sink.close();
                                } catch (Exception e2) {
                                    Timber.e("Error at comic %d: Saving to internal storage failed!", newRealmComic.getComicNumber());
                                }
                            }
                            response.body().close();
                            int prog = (int) (((newest - highest - latch2.getCount()) / ((float) newest - highest)) * 100);
                            publishProgress(prog);
                            latch2.countDown();
                            Timber.d("Saved offline comic %s", newRealmComic.getComicNumber());
                        }
                    });
                } else {
                    int prog = (int) (((num - highest) / ((float) newest - highest)) * 100);
                    publishProgress(prog);
                    latch2.countDown();
                }

            }
            try {
                latch2.await();
            } catch (InterruptedException e) {
                Timber.e(e);
            }
            prefHelper.setHighestOffline(newest);

            realm.commitTransaction();
            realm.close();

            databaseManager.setHighestInDatabase(newest);

            Timber.d("Highest Offline: %d, highest databse: %d", prefHelper.getHighestOffline(), databaseManager.getHighestInDatabase()); //We dont actually need highestOffline now!
        }

        if (!prefHelper.nomediaCreated()) {
            File sdCard = prefHelper.getOfflinePath();
            File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
            File nomedia = new File(dir, ".nomedia");
            try {
                boolean created = nomedia.createNewFile();
                Timber.d("created nomedia in external storage: %s", created);
            } catch (IOException e) {
                Timber.e(e);
            }
        }

        return null;
    }

    protected void onProgressUpdate(Integer... pro) {
        if (showProgress)
            progress.setProgress(pro[0]);
    }

    @Override
    protected void onPostExecute(Void dummy) {
        if (showProgress)
            progress.dismiss();
    }
}
