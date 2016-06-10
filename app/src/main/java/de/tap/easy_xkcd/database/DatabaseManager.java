package de.tap.easy_xkcd.database;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.customtabs.CustomTabsIntent;
import android.util.Log;
import android.widget.Toast;

import com.tap.xkcd_reader.R;

import org.jsoup.Jsoup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

import de.tap.easy_xkcd.Activities.SearchResultsActivity;
import de.tap.easy_xkcd.CustomTabHelpers.BrowserFallback;
import de.tap.easy_xkcd.CustomTabHelpers.CustomTabActivityHelper;
import de.tap.easy_xkcd.fragments.overview.OverviewBaseFragment;
import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.utils.ThemePrefs;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.exceptions.RealmPrimaryKeyConstraintException;

public class DatabaseManager {
    private Context context;
    public Realm realm;
    private static final String REALM_DATABASE_LOADED = "pref_realm_database_loaded";
    private static final String HIGHEST_DATABASE = "highest_database";
    private static final String COMIC_READ = "comic_read";
    private static final String FAVORITES_MOVED = "fav_moved";
    private static final String FAVORITES = "favorites";

    public DatabaseManager(Context context) {
        this.context = context;
        realm = Realm.getInstance(context);
    }

    private SharedPreferences getSharedPrefs() {
        return context.getSharedPreferences("MainActivity", Activity.MODE_PRIVATE);
    }

    /////////////////////// FAVORITES /////////////////////////////////////////

    public boolean noFavorites() {
        return getSharedPrefs().getString(FAVORITES, null) == null;
    }

    /**
     * Gets an array of all favorite comics
     * @return an int array containing the numbers of all favorite comics, null if there are no favorites
     */
    public int[] getFavComics() {
        String fs = getSharedPrefs().getString(FAVORITES, null);
        if (fs == null)
            return null;
        HashSet<String> fSet = new HashSet<>(Arrays.asList(fs.split(","))); // HashSet automatically removes duplicate Items.
        String[] f = new String[fSet.size()];                               // (There was a bug before where favorites would be added twice,
        fSet.toArray(f);                                                    // so this fixes it while not risking to lose any data
        int[] fav = new int[f.length];
        for (int i = 0; i < f.length; i++)
            fav[i] = Integer.parseInt(f[i]);
        Arrays.sort(fav);
        return fav;
    }

    public boolean checkFavorite(int fav) {
        return getFavComics() != null && Arrays.binarySearch(getFavComics(), fav) >= 0;
    }

    /**
     * Adds or removes a favorite to realm and sharedPreferences
     * @param fav the comic number of the fac to be modified
     * @param isFav if the comic has been added or removed from favorites
     */
    public void setFavorite(int fav, boolean isFav) {
        //Save to realm
        if (fav <= getHighestInDatabase()) {
            Realm realm = Realm.getInstance(context);
            realm.beginTransaction();
            RealmComic comic = realm.where(RealmComic.class).equalTo("comicNumber", fav).findFirst();
            comic.setFavorite(isFav);
            realm.copyToRealmOrUpdate(comic);
            realm.commitTransaction();
            realm.close();
        }
        //Save to sharedPrefs
        if (isFav)
            addFavorite(fav);
        else
            removeFavorite(fav);
    }

    private void addFavorite(int fav) {
        String favorites = getSharedPrefs().getString(FAVORITES, null);
        if (favorites == null)
            favorites = String.valueOf(fav);
        else
            favorites += "," + String.valueOf(fav);
        getSharedPrefs().edit().putString(FAVORITES, favorites).apply();
    }

    private void removeFavorite(int favToRemove) {
        int[] old = getFavComics();
        int a = Arrays.binarySearch(old, favToRemove);
        int[] out = new int[old.length - 1];
        if (out.length != 0 && a >= 0) {
            System.arraycopy(old, 0, out, 0, a);
            System.arraycopy(old, a + 1, out, a, out.length - a);
            StringBuilder sb = new StringBuilder();
            sb.append(out[0]);
            for (int i = 1; i < out.length; i++) {
                sb.append(",");
                sb.append(out[i]);
            }
            getSharedPrefs().edit().putString(FAVORITES, sb.toString()).apply();
        } else {
            getSharedPrefs().edit().putString(FAVORITES, null).apply();
        }
    }

    public void removeAllFavorites() {
        getSharedPrefs().edit().putString(FAVORITES, null).apply();
    }


    /**
     * In previous versions the favorite list would be only accessible from MainActivity,
     * so this moves the favorites list to regular sharedPreferences
     *
     * @param activity should be MainActivity
     */
    public void moveFavorites(Activity activity) {

        if (!getSharedPrefs().getBoolean(FAVORITES_MOVED, false)) {
            String fav = activity.getPreferences(Activity.MODE_PRIVATE).getString("favorites", null);
            getSharedPrefs().edit().putString(FAVORITES, fav).putBoolean(FAVORITES_MOVED, true).apply();
            Log.d("prefHelper", "moved favorites");
        }
    }

    ///////////////// READ COMICS //////////////////////////////////////////

    /**
     * Sets a comic read or unread
     * @param number the comic number
     * @param isRead if the comic is read or unread
     */
    public void setRead(int number, boolean isRead) {
        if (number <= getHighestInDatabase()) { //Save to realm
            realm.beginTransaction();
            RealmComic comic = realm.where(RealmComic.class).equalTo("comicNumber", number).findFirst();
            if (comic != null) {
                comic.setRead(isRead);
                realm.copyToRealmOrUpdate(comic);
            }
            realm.commitTransaction();
        } else { //Save to sharedPrefs
            String read = getSharedPrefs().getString(COMIC_READ, "");
            if (!read.equals("")) {
                read = read + "," + String.valueOf(number);
            } else {
                read = String.valueOf(number);
            }
            getSharedPrefs().edit().putString(COMIC_READ, read).apply();
        }
    }

    /**
     * Gets an array of read comics
     * @return an int array containing the numbers of all read comics
     */
    public int[] getReadComics() {
        String r = getSharedPrefs().getString(COMIC_READ, "");
        if (r.equals(""))
            return new int[0];
        String[] re = r.split(",");
        int[] read = new int[re.length];
        for (int i = 0; i < re.length; i++)
            read[i] = Integer.parseInt(re[i]);
        Arrays.sort(read);
        return read;
    }

    public void setComicsUnread() {
        getSharedPrefs().edit().putString(COMIC_READ, "").apply();
        realm.beginTransaction();
        RealmResults<RealmComic> comics = realm.where(RealmComic.class).findAll();
        for (int i = 0; i < comics.size(); i++) {
            RealmComic comic = comics.get(i);
            comic.setRead(false);
            realm.copyToRealmOrUpdate(comic);
        }
        realm.commitTransaction();
    }

    /**
     * Interpolates the number of the next unread comic when "hide read" is checked in overview mode
     * @param number the selected comic
     * @param comics all comics in the realm database
     * @return the comic number that the list should scroll to
     */
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

    //////////////// COMIC DATABASE //////////////////////////////////////////

    public class updateComicDatabase extends AsyncTask<Void, Integer, Void> {
        private ProgressDialog progress;
        private SearchResultsActivity activity;
        private OverviewBaseFragment fragment;
        private PrefHelper prefHelper;

        public updateComicDatabase(SearchResultsActivity activity, OverviewBaseFragment fragment, PrefHelper prefHelper) {
            this.activity = activity;
            this.fragment = fragment;
            this.prefHelper = prefHelper;
        }

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(context);
            progress.setTitle(context.getResources().getString(R.string.update_database));
            progress.setIndeterminate(false);
            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            Realm realm = Realm.getInstance(context);
            int[] read = getReadComics();
            int[] fav = getFavComics();
            if (!databaseLoaded()) { //Save preloaded comic data to realm
                String[] titles = getFile(R.raw.comic_titles).split("&&");
                String[] trans = getFile(R.raw.comic_trans).split("&&");
                String[] urls = getFile(R.raw.comic_urls).split("&&");
                realm.beginTransaction();
                for (int i = 0; i < 1645; i++) {
                    try {
                        RealmComic comic = realm.createObject(RealmComic.class);
                        comic.setComicNumber(i + 1);
                        comic.setTitle(titles[i]);
                        comic.setTranscript(trans[i]);
                        comic.setUrl(urls[i]);
                        comic.setRead(read != null && Arrays.binarySearch(read, i + 1) >= 0);
                        comic.setFavorite(fav != null && Arrays.binarySearch(fav, i + 1) >= 0);
                    } catch (RealmPrimaryKeyConstraintException e) {
                        Log.d("error at " + i, e.getMessage());
                    }
                }
                realm.commitTransaction();
                setHighestInDatabase(1645);
            }
            if (prefHelper.isOnline(context)) {
                int newest;
                try {
                    Comic comic = new Comic(0);
                    newest = comic.getComicNumber();
                } catch (IOException e) {
                    newest = prefHelper.getNewest();
                }
                realm.beginTransaction();
                int highest = getHighestInDatabase() + 1;
                for (int i = highest; i <= newest; i++) {
                    try {
                        Comic comic = new Comic(i);
                        RealmComic realmComic = realm.createObject(RealmComic.class);
                        realmComic.setComicNumber(i);
                        realmComic.setTitle(comic.getComicData()[0]);
                        realmComic.setTranscript(comic.getTranscript());
                        realmComic.setUrl(comic.getComicData()[2]);
                        realmComic.setRead(read != null && Arrays.binarySearch(read, i) >= 0);
                        realmComic.setFavorite(fav != null && Arrays.binarySearch(fav, i) >= 0);
                        float x = newest - highest;
                        int y = i - highest;
                        int p = (int) ((y / x) * 100);
                        publishProgress(p);
                    } catch (IOException | RealmPrimaryKeyConstraintException e) {
                        Log.d("error at " + i, e.getMessage());
                    }
                }
                realm.commitTransaction();
                setHighestInDatabase(newest);
            }
            setDatabaseLoaded(true);
            realm.close();
            return null;
        }

        private String getFile(int rawId) {
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

        protected void onProgressUpdate(Integer... pro) {
            progress.setProgress(pro[0]);
        }

        @Override
        protected void onPostExecute(Void dummy) {
            progress.dismiss();
            if (activity != null)
                activity.updateDatabasePostExecute();
            else
                fragment.updateDatabasePostExecute();
        }
    }

    public int getRandomUnread() {
        RealmResults<RealmComic> unread = realm.where(RealmComic.class).equalTo("isRead", false).findAll();
        int n = new Random().nextInt(unread.size());
        return unread.get(n).getComicNumber();
    }

    public int getHighestInDatabase() {
        return getSharedPrefs().getInt(HIGHEST_DATABASE, 1);
    }

    public void setHighestInDatabase(int i) {
        getSharedPrefs().edit().putInt(HIGHEST_DATABASE, i).apply();
    }

    public boolean databaseLoaded() {
        return getSharedPrefs().getBoolean(REALM_DATABASE_LOADED, false);
    }

    public void setDatabaseLoaded(boolean loaded) {
        getSharedPrefs().edit().putBoolean(REALM_DATABASE_LOADED, loaded).apply();
    }

    /**
     * Gets the transcript of a comic
     * @param number the comic number
     * @return the comics transcript or " " if the comic does not exist in the database
     */
    public static String getTranscript(int number, Context context) {
        RealmComic comic = Realm.getInstance(context).where(RealmComic.class).equalTo("comicNumber", number).findFirst();
        if (comic != null)
            return comic.getTranscript();
        return " ";
    }

    ////////////////// WHAT IF DATABASE /////////////////////////

    /**
     * Randall doesn't seem to update the thumbnails for what-if articles at http://what-if.xkcd.com/archive/ anymore.
     * @param title the title of the what-if
     * @return the resource id of the custom thumbnail or 0 if the article already has a thumbnail
     */
    public int getWhatIfMissingThumbnailId(String title) {
        switch (title) {
            case "Jupiter Descending":
                return R.mipmap.jupiter_descending;
            case "Jupiter Submarine":
                return R.mipmap.jupiter_submarine;
            case "New Horizons":
                return R.mipmap.new_horizons;
            case "Proton Earth, Electron Moon":
                return R.mipmap.proton_earth;
            case "Sunbeam":
                return R.mipmap.sun_beam;
            case "Space Jetta":
                return R.mipmap.jetta;
            case "Europa Water Siphon":
                return R.mipmap.straw;
            case "Saliva Pool":
                return R.mipmap.question;
            case "Fire From Moonlight":
                return R.mipmap.rabbit;
            case "Stop Jupiter":
                return R.mipmap.burlap;
            case "Niagara Straw":
                return R.mipmap.barrel;
            case "Eat the Sun":
                return R.mipmap.snakemeat;
            case "Pizza Bird":
                return R.mipmap.pickup;
            case "Tatooine Rainbow":
                return R.mipmap.trig;
            default:
                return 0;
        }
    }

    /**
     * Shows the reddit or forum thread for comics or WhatIf
     * @param title of the comic or WhatIf
     */
    public static boolean showThread(final String title, final Context context, final boolean isWhatIf) {
        new AlertDialog.Builder(context)
                .setItems(R.array.forum_thread, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                new GetRedditLinkTask(context).execute(title);
                                break;
                            case 1:
                                new GetForumLinkTask(context, isWhatIf).execute(title);
                                break;
                        }
                    }
                }).create().show();
        return true;
    }

    private static class GetRedditLinkTask extends AsyncTask<String, Void, String> {
        private ProgressDialog progress;
        private Context context;

        public GetRedditLinkTask(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(context);
            progress.setIndeterminate(true);
            progress.setMessage(context.getResources().getString(R.string.loading_thread));
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected String doInBackground(String... title) {
            try {
                return Jsoup.connect("https://www.reddit.com/r/xkcd/search?q=title%3A\"" + title[0] + "\"&restrict_sr=on&sort=relevance&t=all").get()
                        .select(".search-result-meta").first()
                        .select("a[href]").first().absUrl("href");
            } catch (Exception e) {
                Log.e("error at " + title[0], e.getMessage());
                return "";
            }
        }

        @Override
        protected void onPostExecute(String url) {
            progress.dismiss();
            if (url.equals(""))
                Toast.makeText(context, context.getString(R.string.thread_not_found), Toast.LENGTH_SHORT).show();
            else {
                url = url.replace("www", "m");
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        }
    }

    protected static class GetForumLinkTask extends AsyncTask<String, Void, String> {
        private ProgressDialog progress;
        private Context context;
        private boolean isWhatIf;

        public GetForumLinkTask(Context context, boolean isWhatIf) {
            this.context = context;
            this.isWhatIf = isWhatIf;
        }

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(context);
            progress.setIndeterminate(true);
            progress.setMessage(context.getResources().getString(R.string.loading_thread));
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected String doInBackground(String... title) {
            String end;
            if (isWhatIf)
                end = "&terms=all&author=&fid%5B%5D=60&sc=1&sf=titleonly&sr=posts&sk=t&sd=d&st=0&ch=300&t=0&submit=Search"; //Comic forum
            else
                end = "&terms=all&author=&fid%5B%5D=7&sc=1&sf=titleonly&sr=posts&sk=t&sd=d&st=0&ch=300&t=0&submit=Search"; //What-if forum
            try {
                return Jsoup.connect("http://forums.xkcd.com/search.php?keywords=" + title[0] + end).get()
                        .select("div#wrap").select("div#page-body")
                        .first().select(".postbody").first().select("a[href]").first().absUrl("href");
            } catch (Exception e) {
                Log.e("error at " + title[0], e.getMessage());
                return "";
            }
        }

        @Override
        protected void onPostExecute(String url) {
            progress.dismiss();
            if (url.equals(""))
                Toast.makeText(context, context.getResources().getString(R.string.thread_not_found), Toast.LENGTH_SHORT).show();
            else {
                CustomTabsIntent.Builder intentBuilder = new CustomTabsIntent.Builder();
                intentBuilder.setToolbarColor(new ThemePrefs(context).getPrimaryColor(false));
                CustomTabActivityHelper.openCustomTab(((Activity) context), intentBuilder.build(), Uri.parse(url), new BrowserFallback());
            }

        }
    }

}
