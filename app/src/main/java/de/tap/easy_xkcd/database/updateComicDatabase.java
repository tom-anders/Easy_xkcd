package de.tap.easy_xkcd.database;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.tap.xkcd_reader.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import de.tap.easy_xkcd.fragments.comics.ComicFragment;
import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.utils.PrefHelper;
import io.realm.Realm;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

import static de.tap.easy_xkcd.utils.JsonParser.getNewHttpClient;

public class updateComicDatabase extends AsyncTask<Void, Integer, Boolean> {
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

    int findNewest() {
        try {
            return new Comic(0).getComicNumber();
        } catch (IOException e) {
            return prefHelper.getNewest();
        }
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        if (prefHelper.isOnline(context)) {
            final int newest = findNewest();
            if (newest > prefHelper.getNewest()) {
                ComicFragment.newComicFound = prefHelper.getNewest() != 0.0;
                prefHelper.setNewestComic(newest);
            }
            if (prefHelper.getLastComic() == 0) { //Should only be true on first startup
                prefHelper.setLastComic(newest);
            }
            final int highest = databaseManager.getHighestInDatabase(); //TODO make a new key in sharedPreferences for that, such old users update their database as well!
            if (highest == newest) {
                publishProgress(100);
                Timber.d("No new comic found!");
                return false;  //Database already up to date
            }
            OkHttpClient client = getNewHttpClient();
            final CountDownLatch latch = new CountDownLatch(newest - highest);

            final ConcurrentHashMap<Integer, Comic> comics = new ConcurrentHashMap<>(newest - highest - 1);

            for (int i = highest + 1; i <= newest; i++) {
                final int num = i;
                client.newCall(new Request.Builder().url(Comic.getJsonUrl(i)).build()).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        e.printStackTrace();
                        Log.e("error " + num, e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        JSONObject json = null;
                        try {
                            json = new JSONObject(response.body().string());
                        } catch (JSONException e) {
                            Log.e("error", "json exception at " + num + ": " + e.getMessage());
                        }
                        try {
                            comics.put(num,  new Comic(num, json));
                        } catch (IOException e) {
                            Log.e("error", "ioexception at " + num + ": " + e.getMessage());
                        } finally {
                            response.body().close();
                        }
                        int p = (int) (((num - highest) / ((float) newest - highest)) * 100);
                        publishProgress(p);
                        latch.countDown();
                        //Log.d("test", latch.getCount() + "/" + comics.size() + "/" + num);
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
            for (Integer num : comics.keySet()) {
                Comic comic = comics.get(num);
                RealmComic realmComic = realm.where(RealmComic.class).equalTo("comicNumber", num).findFirst();
                if (realmComic == null) {
                    realmComic = realm.createObject(RealmComic.class);
                    Timber.d("created new comic %d", num);
                } else {
                    Timber.d("Comic %d already exists in database", num);
                }
                realmComic.setComicNumber(num);
                realmComic.setTitle(comic.getComicData()[0]);
                realmComic.setTranscript(comic.getTranscript());
                realmComic.setUrl(comic.getComicData()[2]);
                realmComic.setRead(legacyRead != null && Arrays.binarySearch(legacyRead, num) >= 0);
                realmComic.setFavorite(legacyFav != null && Arrays.binarySearch(legacyFav, num) >= 0);
                realmComic.setAltText(comic.getComicData()[1]);
                realm.copyToRealmOrUpdate(realmComic);

                int p = (int) (((num - highest) / ((float) newest - highest)) * 100);
                publishProgress(p);
            }
            realm.commitTransaction();
            realm.close();

            databaseManager.setHighestInDatabase(newest);
        }

        return true;
    }

    protected void onProgressUpdate(Integer... pro) {
        if (showProgress)
            progress.setProgress(pro[0]);
    }

    @Override
    protected void onPostExecute(Boolean dummy) {
        if (showProgress)
            progress.dismiss();
    }
}
