package de.tap.easy_xkcd.database;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.tap.xkcd_reader.R;

import org.json.JSONException;
import org.json.JSONObject;

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

public class updateComicDatabase extends AsyncTask<Void, Integer, Void> {
    protected ProgressDialog progress;
    protected PrefHelper prefHelper;
    protected DatabaseManager databaseManager;
    protected Context context;
    protected boolean showProgress = true;

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
            }
            if (prefHelper.getLastComic() == 0) { //Should only be true on first startup
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
            final int[] legacyRead = databaseManager.getReadComicsLegacy();
            final int[] legacyFav = databaseManager.getFavComicsLegacy();

            Realm realm = Realm.getDefaultInstance();
            realm.beginTransaction();
            for (Integer num : jsons.keySet()) {
                JSONObject json = jsons.get(num);
                RealmComic realmComic = realm.where(RealmComic.class).equalTo("comicNumber", num).findFirst();
                if (realmComic == null) {
                    realmComic = RealmComic.buildFromJson(realm, num, json, context);
                    Timber.d("created new comic %d", num);
                } else {
                    Timber.d("Comic %d already exists in database", num);
                }

                if (!realmComic.isFavorite() && legacyFav != null) {
                    boolean isFav = databaseManager.checkFavoriteLegacy(num);
                    realmComic.setFavorite(isFav);
                    if (isFav)
                        Timber.d("comic %d was a legacy favorite!", num);
                } else if (realmComic.isFavorite()) {
                    Timber.d("comic %d was a favorite in the old realm database!", num);
                }
                if (!realmComic.isRead() && legacyRead != null) {
                    boolean isRead = databaseManager.checkReadLegacy(num);
                    realmComic.setRead(isRead);
                    if (isRead)
                        Timber.d("comic %d was legacy read!", num);
                }

                realm.copyToRealmOrUpdate(realmComic);

                int p = (int) (((num - highest) / ((float) newest - highest)) * 100);
                publishProgress(p);
            }
            realm.commitTransaction();
            realm.close();

            databaseManager.setHighestInDatabase(newest);
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
