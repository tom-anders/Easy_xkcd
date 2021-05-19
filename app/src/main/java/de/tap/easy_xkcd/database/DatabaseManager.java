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
import androidx.browser.customtabs.CustomTabsIntent;
import android.util.Log;
import android.widget.Toast;

import com.tap.xkcd_reader.R;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Stack;

import de.tap.easy_xkcd.CustomTabHelpers.BrowserFallback;
import de.tap.easy_xkcd.CustomTabHelpers.CustomTabActivityHelper;
import de.tap.easy_xkcd.utils.Article;
import de.tap.easy_xkcd.utils.JsonParser;
import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.utils.ThemePrefs;
import io.reactivex.rxjava3.core.Observable;
import io.realm.DynamicRealm;
import io.realm.FieldAttribute;
import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmMigration;
import io.realm.RealmObjectSchema;
import io.realm.RealmResults;
import io.realm.RealmSchema;
import io.realm.internal.RealmObjectProxy;
import okhttp3.Request;
import timber.log.Timber;

import static de.tap.easy_xkcd.utils.JsonParser.getJSONFromUrl;
import static java.nio.charset.StandardCharsets.UTF_8;

public class DatabaseManager {
    private Context context;
    public Realm realm;
    private static final String REALM_DATABASE_LOADED = "pref_realm_database_loaded";
    private static final String HIGHEST_DATABASE = "highest_database_newversion";
    private static final String COMIC_READ = "comic_read";
    private static final String FAVORITES = "favorites";

    private static RealmConfiguration config;

    private class Migration implements RealmMigration {
        @Override
        public void migrate(DynamicRealm realm, long oldVersion, long newVersion) {
            RealmSchema schema = realm.getSchema();
            RealmObjectSchema objectSchema = schema.get("RealmComic");
            if (!objectSchema.hasField("altText")) { //Add the altText field which wasn't there in the old version!
                objectSchema.addField("altText", String.class);
            }

            if (!schema.contains("Article")) {
                RealmObjectSchema articleSchema = schema.create("Article")
                        .addField("number", int.class, FieldAttribute.PRIMARY_KEY)
                        .addField("title", String.class)
                        .addField("thumbnail", String.class)
                        .addField("favorite", boolean.class)
                        .addField("read", boolean.class)
                        .addField("offline", boolean.class);
            }

        }

        @Override
        public int hashCode() {
            return 37;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof Migration);
        }
    }

    public DatabaseManager(Context context) {
        if (config == null) {
            config = new RealmConfiguration.Builder(context)
                    .schemaVersion(3) // Must be bumped when the schema changes
                    .migration(new Migration()) // Migration to run
                    .build();
            Realm.setDefaultConfiguration(config);
        }

        this.context = context;
        realm = Realm.getDefaultInstance();
    }

    private SharedPreferences getSharedPrefs() {
        return context.getSharedPreferences("MainActivity", Activity.MODE_PRIVATE);
    }

    /////////////////////// FAVORITES /////////////////////////////////////////

    public boolean noFavorites() {
        return realm.where(RealmComic.class).equalTo("isFavorite", true).count() == 0;
    }

    /**
     * Gets an array of all favorite comics
     * @return an int array containing the numbers of all favorite comics, null if there are no favorites
     */
    public int[] getFavComicsLegacy() {
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

    public RealmResults<RealmComic> getFavComics() {
        return realm.where(RealmComic.class).equalTo("isFavorite", true).findAll();
    }

    public boolean checkFavoriteLegacy(int fav) {
        return getFavComicsLegacy() != null && Arrays.binarySearch(getFavComicsLegacy(), fav) >= 0;
    }

    public boolean checkReadLegacy(int read) {
        return getReadComicsLegacy() != null && Arrays.binarySearch(getReadComicsLegacy(), read) >= 0;
    }

    public boolean isFavorite(int fav) {
        try {
            return realm.where(RealmComic.class).equalTo("comicNumber", fav).findFirst().isFavorite();
        } catch (NullPointerException e) {
            Timber.e("favorite comic not in database!");
            return false;
        }
    }

    /**
     * Adds or removes a favorite to realm and sharedPreferences
     * @param fav the comic number of the fac to be modified
     * @param isFav if the comic has been added or removed from favorites
     */
    public void setFavorite(int fav, boolean isFav) {
        //Save to realm
        realm.beginTransaction();
        RealmComic comic = realm.where(RealmComic.class).equalTo("comicNumber", fav).findFirst();
        if (comic != null) {
            comic.setFavorite(isFav);
            realm.copyToRealmOrUpdate(comic);
        } else {
            Timber.wtf("Favorited comic %d is not in Realm database!", fav);
        }
        realm.commitTransaction();
    }

    public void removeAllFavorites() {
        RealmResults<RealmComic> favorites = getFavComics();
        realm.beginTransaction();
        Timber.d("size : %d", favorites.size());
        while (favorites.size() != 0) {
            favorites.first().setFavorite(false);
            Timber.d("size : %d", favorites.size());
        }
        realm.commitTransaction();
    }


    ///////////////// READ COMICS //////////////////////////////////////////

    /**
     * Sets a comic read or unread
     * @param number the comic number
     * @param isRead if the comic is read or unread
     */
    public void setRead(int number, boolean isRead) {
            realm.beginTransaction();
            RealmComic comic = realm.where(RealmComic.class).equalTo("comicNumber", number).findFirst();
            if (comic != null) {
                comic.setRead(isRead);
                realm.copyToRealmOrUpdate(comic);
            } else {
                Timber.wtf("Read Comic %d is not in Realm database!", number);
            }
            realm.commitTransaction();
    }

    /**
     * Gets an array of read comics
     * @return an int array containing the numbers of all read comics
     */
    public int[] getReadComicsLegacy() {
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

    public void setComicsRead(boolean isRead) {
        getSharedPrefs().edit().putString(COMIC_READ, "").apply();
        realm.beginTransaction();
        RealmResults<RealmComic> comics = realm.where(RealmComic.class).findAll();
        for (int i = 0; i < comics.size(); i++) {
            RealmComic comic = comics.get(i);
            comic.setRead(isRead);
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

    public RealmResults<RealmComic> getRealmComics() {
        return realm.where(RealmComic.class).findAll();
    }

    public RealmComic getRealmComic(int number) {
        return realm.where(RealmComic.class).equalTo("comicNumber", number).findFirst();
    }

    public int getRandomUnread() {
        RealmResults<RealmComic> unread = realm.where(RealmComic.class).equalTo("isRead", false).findAll();
        int n = new Random().nextInt(unread.size());
        return unread.get(n).getComicNumber();
    }

    public int getHighestInDatabase() {
        return getSharedPrefs().getInt(HIGHEST_DATABASE, 0);
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

    ////////////////// WHAT IF DATABASE /////////////////////////

    /**
     * The latest what-if doesn't have an official thumbnail yet.
     * @param title the title of the what-if
     * @return the resource id of the custom thumbnail or 0 if the article already has a thumbnail
     */
    public int getWhatIfMissingThumbnailId(String title) {
        switch (title) {
            case "Earth-Moon Fire Pole":
                return R.mipmap.slide;
            case "New Horizons":
                return R.mipmap.new_horizons;
            default:
                return 0;
        }
    }

    public void setAllArticlesReadStatus(boolean read) {
        realm.beginTransaction();
        RealmResults<Article> articles = realm.where(Article.class).findAll();
        for (int i = 0; i < articles.size(); i++)
            articles.get(i).setRead(read);
        realm.copyToRealmOrUpdate(articles);
        realm.commitTransaction();
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
                return "https://www.reddit.com" + getJSONFromUrl("https://www.reddit.com/r/xkcd/search.json?q=" + title[0] + "&restrict_sr=on")
                        .getJSONObject("data")
                        .getJSONArray("children").getJSONObject(0).getJSONObject("data").getString("permalink");
            } catch (Exception e) {
                Log.d("reddit link", "timeout, trying again...");
                try {
                    return "https://www.reddit.com" + getJSONFromUrl("https://www.reddit.com/r/xkcd/search.json?q=" + title[0] + "&restrict_sr=on")
                            .getJSONObject("data")
                            .getJSONArray("children").getJSONObject(0).getJSONObject("data").getString("permalink");
                } catch (Exception e2) {
                    Log.e("error at " + title[0], e2.getMessage());
                }
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

    public Observable<Integer> updateWhatifDatabase(PrefHelper prefHelper) {
        return Observable.create(subscriber -> {
            try {
                Document document =
                        Jsoup.parse(JsonParser.getNewHttpClient().newCall(new Request.Builder()
                                .url("https://what-if.xkcd.com/archive/")
                                .build()).execute().body().string());

                Realm realm = Realm.getDefaultInstance();
                realm.beginTransaction();

                Elements titles = document.select("h1");
                Elements thumbnails = document.select("img.archive-image");

                for (int number = 1; number <= titles.size(); number++) {
                    Article article = realm.where(Article.class).equalTo("number", number).findFirst();
                    if (article == null) {
                        article = new Article();
                        article.setNumber(number);
                        article.setTitle(titles.get(number - 1).text());
                        article.setThumbnail("https://what-if.xkcd.com/" + thumbnails.get(number - 1).attr("src")); // -1 cause articles a 1-based indexed

                        article.setOffline(false);

                        // Import from the legacy database
                        article.setRead(prefHelper.checkRead(number));
                        article.setFavorite(prefHelper.checkWhatIfFav(number));

                        realm.copyToRealm(article);

                        Timber.d("Stored new article: %d %s %s", article.getNumber(), article.getTitle(), article.getThumbnail());
                    }
                    subscriber.onNext(number);
                }

                if (prefHelper.fullOfflineWhatIf()) {
                    RealmResults<Article> articlesToDownload = realm.where(Article.class).equalTo("offline", false).findAll();
                    Article.downloadThumbnails(articlesToDownload, prefHelper);
                    for (int i = 0; i < articlesToDownload.size(); i++) {
                        boolean success = Article.downloadArticle(articlesToDownload.get(i).getNumber(), prefHelper);
                        articlesToDownload.get(i).setOffline(success);
                        subscriber.onNext(i + 1);
                    }
                    realm.copyToRealmOrUpdate(articlesToDownload);
                }

                realm.commitTransaction();
                realm.close();
            } catch (IOException e) {
                Timber.e(e);
            }

            subscriber.onComplete();
        });
    }

    // Implement some fixes for comic data that may have already been cached
    public void fixCache() {
        int[] comicsToFix = {76, 80, 104, 1037, 1054, 1137, 1193, 1608, 1663, 1350, 2175, 2185, 2202}; //When adding new comic fixes, don't forget to add the number here!
        ArrayList<RealmComic> comicFixes = new ArrayList<>();
        for (int i : comicsToFix) {
            comicFixes.add(getRealmComic(i));
        }

        realm.beginTransaction();

        for (RealmComic comic : comicFixes) {
            if (comic == null) { continue; }
            switch (comic.getComicNumber()) {
                case 76: comic.setUrl("https://i.imgur.com/h3fi2RV.jpg");
                    break;
                case 80: comic.setUrl("https://i.imgur.com/lWmI1lB.jpg");
                    break;
                case 104: comic.setUrl("https://i.imgur.com/dnCNfPo.jpg");
                    break;
                case 1037: comic.setUrl("https://www.explainxkcd.com/wiki/images/f/ff/umwelt_the_void.jpg");
                    break;
                case 1054: comic.setTitle("The Bacon");
                    break;
                case 1137: comic.setTitle("RTL");
                    break;
                case 1193: comic.setUrl("https://www.explainxkcd.com/wiki/images/0/0b/externalities.png");
                    break;
                case 1350: comic.setUrl("https://www.explainxkcd.com/wiki/images/3/3d/lorenz.png");
                    break;
                case 1608: comic.setUrl("https://www.explainxkcd.com/wiki/images/4/41/hoverboard.png");
                    break;
                case 1663: comic.setUrl("https://explainxkcd.com/wiki/images/c/ce/garden.png");
                    break;
                case 2175: comic.setAltText(new String("When Salvador Dalí died, it took months to get all the flagpoles sufficiently melted.".getBytes(UTF_8)));
                    break;
                case 2185:
                    comic.setTitle("Cumulonimbus");
                    comic.setAltText("The rarest of all clouds is the altocumulenticulostratonimbulocirruslenticulomammanoctilucent cloud, caused by an interaction between warm moist air, cool dry air, cold slippery air, cursed air, and a cloud of nanobots.");
                    comic.setUrl("https://imgs.xkcd.com/comics/cumulonimbus_2x.png");
                    break;
                case 2202: comic.setUrl("https://imgs.xkcd.com/comics/earth_like_exoplanet.png");
                    break;
            }
        }

        realm.copyToRealmOrUpdate(comicFixes);

        RealmResults<RealmComic> httpComics = realm.where(RealmComic.class).contains("url", "http://").findAll();
        for (int i = 0; i < httpComics.size(); i++) {
            httpComics.get(i).setUrl(httpComics.get(i).getUrl().replace("http", "https"));
        }

        realm.copyToRealmOrUpdate(httpComics);

        realm.commitTransaction();
    }

    public void fixTranscripts() {
        realm.beginTransaction();
        for (int number = 1609; number < 1664; number++) {
            RealmComic comic = getRealmComic(number);
            comic.setTranscript(getRealmComic(number + 2).getTranscript());
            realm.copyToRealmOrUpdate(comic);
        }
        for (int number = 1664; number < 1674; number++) {
            RealmComic comic = getRealmComic(number);
            comic.setTranscript(getRealmComic(number + 3).getTranscript());
            realm.copyToRealmOrUpdate(comic);
        }
        realm.commitTransaction();
    }

    public RealmComic findNewestComic(Context context) throws IOException, JSONException {
        JSONObject json = getJSONFromUrl(RealmComic.getJsonUrl(0));
        return RealmComic.buildFromJson(realm, json.getInt("num"), json, context);
    }

}



