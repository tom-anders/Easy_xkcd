package de.tap.easy_xkcd.database;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;

import com.tap.xkcd_reader.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
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
import timber.log.Timber;

import static de.tap.easy_xkcd.utils.JsonParser.getNewHttpClient;

public class updateComicDatabase extends AsyncTask<Void, String, Void> {
    protected ProgressDialog progress;
    protected PrefHelper prefHelper;
    protected DatabaseManager databaseManager;
    protected Context context;
    private OkHttpClient client;
    private int newest;
    private int highest;
    protected boolean showProgress = true;
    protected boolean newComicFound = false;

    public updateComicDatabase(PrefHelper prefHelper, Context context) {
        this.prefHelper = prefHelper;
        this.context = context;

        Timber.d("offline mode enabled: %s", String.valueOf(prefHelper.fullOfflineEnabled()));
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

    private ConcurrentHashMap<Integer, JSONObject> downloadComicJsons() {
        final CountDownLatch latch = new CountDownLatch(newest - highest);
        final ConcurrentHashMap<Integer, JSONObject> jsons = new ConcurrentHashMap<>(newest - highest - 1);

        for (int i = highest + 1; i <= newest; i++) {
            final int num = i;
            client.newCall(new Request.Builder().url(RealmComic.getJsonUrl(i)).build()).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Timber.e("request for %d failed: %s", num, e.getMessage());
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
                    String[] prog = {String.valueOf(p), ""};
                    publishProgress(prog);
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return jsons;
    }

    void saveComicInDatabase(JSONObject json, Realm realm, int num, final CountDownLatch latch) {
        RealmComic oldRealmComic = realm.where(RealmComic.class).equalTo("comicNumber", num).findFirst();

        final RealmComic comic = RealmComic.buildFromJson(realm, num, json, context);

        //Import read and favorite comics from the old database
        if (oldRealmComic != null && oldRealmComic.isFavorite()) {
            Timber.d("comic %d was a favorite in the old database!", num);
            comic.setFavorite(true);
        } else if (databaseManager.checkFavoriteLegacy(num)) {
            Timber.d("comic %d was a legacy favorite!", num);
            comic.setFavorite(true);
        }
        if (oldRealmComic != null && oldRealmComic.isRead()) {
            Timber.d("comic %d was read in the old database!", num);
            comic.setRead(true);
        } else if (databaseManager.checkReadLegacy(num)) {
            Timber.d("comic %d was legacy read!", num);
            comic.setRead(true);
        }
        realm.copyToRealmOrUpdate(comic);
        if (prefHelper.fullOfflineEnabled()) {
            saveOfflineImage(latch, comic);
        } else {
            int p = (int) (((num - highest) / ((float) newest - highest)) * 100);
            String[] prog = {String.valueOf(p), ""};
            publishProgress(prog);
            latch.countDown();
        }

    }

    void saveOfflineImage(final CountDownLatch latch, final RealmComic comic) {
        Request request = new Request.Builder()
                .url(comic.getUrl())
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Timber.e("call to comic %d failed: %s", comic.getComicNumber(), e.getMessage());
                latch.countDown();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                RealmComic.saveOfflineBitmap(response, prefHelper, comic.getComicNumber(), context);
                int p = (int) (((newest - highest - latch.getCount()) / ((float) newest - highest)) * 100);
                String[] prog = {String.valueOf(p), ""};
                publishProgress(prog);
                latch.countDown();
                Timber.d("Saved offline comic %s", comic.getComicNumber());
            }
        });
    }

    @Override
    protected Void doInBackground(Void... params) {
        if (prefHelper.isOnline(context)) {
            databaseManager = new DatabaseManager(context);

            newest = findNewest();
            if (newest > prefHelper.getNewest()) {
                ComicFragment.newComicFound = prefHelper.getNewest() != 0.0; //TODO test if this still works
                prefHelper.setNewestComic(newest);
                newComicFound = true;
            }
            if (prefHelper.getLastComic() == 0) { //Should only be true on first startup
                Timber.d("prefHelper.getLastComic() was 0, either this is the first launch or we're coming from a notification");
                prefHelper.setLastComic(newest);
            }
            highest = databaseManager.getHighestInDatabase();
            if (highest == newest) {
                String[] prog = {"100", ""};
                publishProgress(prog);
                Timber.d("No new comic found!");
                return null;  //Database already up to date
            }

            client = getNewHttpClient();
            ConcurrentHashMap<Integer, JSONObject> jsons = downloadComicJsons();

            final Realm realm = Realm.getDefaultInstance();
            realm.beginTransaction();

            if (prefHelper.fullOfflineEnabled()) {
                String[] prog = new String[2];
                prog[0] = "0";
                prog[1] = context.getResources().getString(R.string.update_database_message_offline);
                publishProgress(prog);
            }

            final CountDownLatch latch = new CountDownLatch(newest - highest);
            for (Integer num : jsons.keySet()) {
                JSONObject json = jsons.get(num);

                saveComicInDatabase(json, realm, num, latch);

            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                Timber.e(e);
            }
            realm.commitTransaction();
            realm.close();

            databaseManager.setHighestInDatabase(newest);


            if (!prefHelper.transcriptsFixed()) {
                databaseManager.fixTranscripts();
                prefHelper.setTranscriptsFixed();
                Timber.d("Transcripts fixed!");
            }

            Timber.d("highest database: %d", databaseManager.getHighestInDatabase()); //We dont actually need highestOffline now!
        }

        return null;
    }

    protected void onProgressUpdate(String... pro) {
        if (showProgress && progress != null) {
            progress.setProgress(Integer.parseInt(pro[0]));

            if (!pro[1].equals("")) {
                Timber.d("prog[1]: %s", pro[1]);
                progress.setMessage(pro[1]);
            }
        }
    }

    @Override
    protected void onPostExecute(Void dummy) {
        if (!prefHelper.cacheFixed()) {
            new DatabaseManager(context).fixCache();
            prefHelper.setCacheFixed();
            Timber.d("Fixed cache!");
        }


        if (showProgress) {
            try {
                progress.dismiss();
            } catch (Exception e) {
                Timber.e(e);
            }
        }
    }
}
