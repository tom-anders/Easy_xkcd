package de.tap.easy_xkcd.database;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import com.tap.xkcd_reader.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.utils.Favorites;
import io.realm.Realm;
import io.realm.RealmResults;

public class DatabaseManager {

    private Context context;
    public Realm realm;
    private static final String REALM_DATABASE_LOADED = "pref_realm_database_loaded";
    private static final String COMIC_READ = "comic_read";
    private static final String HIGHEST_DATABASE = "highest_database";


    public DatabaseManager(Context context) {
        this.context = context;
        realm = Realm.getInstance(context);
    }

    private SharedPreferences getSharedPrefs() {
        return context.getSharedPreferences("MainActivity", Activity.MODE_PRIVATE);
    }

    public int getHighestInDatabase() {
        return getSharedPrefs().getInt(HIGHEST_DATABASE, 1);
    }

    public void setHighestDatabase(int i) {
        getSharedPrefs().edit().putInt(HIGHEST_DATABASE, i).apply();
    }

    public boolean databaseLoaded() {
        return getSharedPrefs().getBoolean(REALM_DATABASE_LOADED, false);
    }

    public void setDatabaseLoaded(boolean loaded) {
        getSharedPrefs().edit().putBoolean(REALM_DATABASE_LOADED, loaded).apply();
    }

    public int[] getReadComics() {
        String[] r = getSharedPrefs().getString(COMIC_READ, "").split(",");
        int[] read = new int[r.length];
        for (int i = 0; i < r.length; i++)
            read[i] = Integer.parseInt(r[i]);
        Arrays.sort(read);
        return read;
    }

    public int[] getFavComics() {
        String[] f = Favorites.getFavoriteList(context);
        int[] fav = new int[f.length];
        for (int i = 0; i < f.length; i++)
            fav[i] = Integer.parseInt(f[i]);
        Arrays.sort(fav);
        return fav;
    }

    public void setFavorite(int fav, boolean isFav) {
        realm.beginTransaction();
        RealmComic comic = realm.where(RealmComic.class).equalTo("comicNumber", fav).findFirst();
        comic.setFavorite(isFav);
        realm.copyToRealmOrUpdate(comic);
        realm.commitTransaction();
    }

    public void setRead(int number, boolean isRead) {
        if (number <= getHighestInDatabase()) {
            realm.beginTransaction();
            RealmComic comic = realm.where(RealmComic.class).equalTo("comicNumber", number).findFirst();
            comic.setRead(isRead);
            realm.copyToRealmOrUpdate(comic);
            realm.commitTransaction();
        } else {
            String read = getSharedPrefs().getString(COMIC_READ, "");
            if (!read.equals("")) {
                read = read + "," + String.valueOf(number);
            } else {
                read = String.valueOf(number);
            }
            getSharedPrefs().edit().putString(COMIC_READ, read).apply();
        }
    }

    public int getNextUnread(int number, RealmResults<RealmComic> comics) {
        RealmComic comic;
        try {
            comic = comics.where().greaterThan("comicNumber", number).equalTo("isRead", false).findAll().last();
        } catch (IndexOutOfBoundsException e) {
            Log.e("top of list reached", e.getMessage());
            try {
                comic = comics.where().lessThan("comicNumber", number).equalTo("isRead", false).findAll().first();
            } catch (IndexOutOfBoundsException e2) {
                Log.e("all comics read", e2.getMessage());
                return 1;
            }
        }
        return comic.getComicNumber();
    }

    public String getFile(int rawId) {
        InputStream is = context.getResources().openRawResource(rawId);
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            Log.e("error:", e.getMessage());
        }
        return sb.toString();
    }

}
