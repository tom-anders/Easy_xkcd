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

package de.tap.easy_xkcd.database;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.tap.xkcd_reader.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import de.tap.easy_xkcd.utils.PrefHelper;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import okhttp3.Response;
import timber.log.Timber;

import static de.tap.easy_xkcd.utils.JsonParser.getJSONFromUrl;
import static java.nio.charset.StandardCharsets.UTF_8;

public class RealmComic extends RealmObject {

    @PrimaryKey
    private int comicNumber;

    private boolean isRead;
    private boolean isFavorite;

    private String title;
    private String transcript;
    private String url;
    private String altText;
    private String preview;

    public int getComicNumber() {
        return comicNumber;
    }

    public void setComicNumber(int comicNumber) {
        this.comicNumber = comicNumber;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public String getTitle() {
        return title;
    }

    public static String getInteractiveTitle(RealmComic comic, Context context) {
        //In older versions of the database getTitle() may return a string that already contains
        // the (interactive) string, so we need to check for this here
        if (isInteractiveComic(comic.getComicNumber(), context) && !comic.getTitle().contains("(interactive)")) {
            return comic.getTitle() + " (interactive)";
        }
        return comic.getTitle();
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTranscript() {
        return transcript;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    public String getPreview() {
        return preview;
    }

    public void setPreview(String preview) {
        this.preview = preview;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    public static boolean isInteractiveComic(int number, Context context) {
        return Arrays.binarySearch(context.getResources().getIntArray(R.array.interactive_comics), number) >= 0;
    }

    public static boolean isLargeComic(int number, Context context) {
        return Arrays.binarySearch(context.getResources().getIntArray(R.array.large_comics), number) >= 0;
    }

    //Thanks to /u/doncajon https://www.reddit.com/r/xkcd/comments/667yaf/xkcd_1826_birdwatching/
    public static String getDoubleResolutionUrl(String url, int number) {
        int no2xVersion[] = {1097, 1103, 1127, 1151, 1182, 1193, 1229, 1253, 1335, 1349, 1350, 1446, 1452, 1506, 1608, 1663, 1667, 1735, 1739, 1744, 1778, 2202, 2281};

        if(number >= 1084 && Arrays.binarySearch(no2xVersion, number) < 0 &&  !url.contains("_2x.png"))
            return url.substring(0, url.lastIndexOf('.')) + "_2x.png";

        return url;
    }

    public static String getJsonUrl(int number) {
        if (number != 0) {
            return "https://xkcd.com/" + number + "/info.0.json";
        } else {
            return "https://xkcd.com/info.0.json";
        }
    }

    public static int findNewestComicNumber() throws IOException, JSONException {
        return getJSONFromUrl(getJsonUrl(0)).getInt("num");
    }

    public static RealmComic buildFromJson(Realm realm, int comicNumber, JSONObject json, Context context) {
        RealmComic realmComic = new RealmComic();

        String title = "", altText = "", url = "", transcript = "";
        if (comicNumber == 404) {
            title = "404";
            altText = "404";
            url = "https://i.imgur.com/p0eKxKs.png";
        } else if (json.length() != 0) {
            try {
                title = new String(json.getString("title").getBytes(UTF_8));

                url = json.getString("img");
                if (!isLargeComic(comicNumber, context) && !isInteractiveComic(comicNumber, context)) {
                    url = getDoubleResolutionUrl(url, comicNumber);
                }
                if (isLargeComic(comicNumber, context)) {
                    url = context.getResources().
                            getStringArray(R.array.large_comics_urls)[Arrays.binarySearch(context.getResources().getIntArray(R.array.large_comics), comicNumber)];
                }

                altText = new String(json.getString("alt").getBytes(UTF_8));
                transcript = json.getString("transcript");

                // some image and title fixes
                switch (comicNumber) {
                }
            } catch (JSONException e) {
                Timber.wtf(e);
            }
        } else {
            Timber.wtf("json is empty but comic number is not 404!");
        }

        realmComic.setComicNumber(comicNumber);
        realmComic.setTitle(title);
        realmComic.setAltText(altText);
        realmComic.setUrl(url);
        realmComic.setTranscript(transcript);

        return realmComic;
    }

    public static void saveOfflineBitmap(Bitmap bitmap, PrefHelper prefHelper, int number, Context context) {
        try {
            File file = new File(prefHelper.getOfflinePath(context), String.valueOf(number) + ".png");
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            Timber.e("Error at comic %d: Saving to external storage failed: %s", number, e.getMessage());
            try {
                FileOutputStream fos = context.openFileOutput(String.valueOf(number), Context.MODE_PRIVATE);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();
            } catch (Exception e2) {
                Timber.e("Error at comic %d: Saving to external storage failed: %s", number, e2.getMessage());
            }
        }
    }

    public static void saveOfflineBitmap(Response response, PrefHelper prefHelper, int comicNumber, Context context) {
        String comicFileName = comicNumber + ".png"; // TODO: Some early comics are .jpg
        try {
            try (FileOutputStream fos = new FileOutputStream(prefHelper.getOfflinePath(context).getAbsolutePath() + "/" + comicFileName)) {
                fos.write(response.body().bytes());
            }
        } catch (Exception e) {
            Timber.e("Error at comic %d: Saving to external storage failed: %s", comicNumber, e.getMessage());
            try (FileOutputStream fos = context.openFileOutput(String.valueOf(comicNumber), Context.MODE_PRIVATE)) {
                fos.write(response.body().bytes());
            } catch (Exception e2) {
                Timber.e("Error at comic %d: Saving to internal storage failed: %s", comicNumber, e2.getMessage());
            }
        } finally {
            response.body().close();
        }
    }

    public static Bitmap getOfflineBitmap(int comicNumber, Context context, PrefHelper prefHelper) {
        //Fix for offline users who downloaded the HUGE version of #1826
        if (comicNumber == 1826) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            return BitmapFactory.decodeResource(context.getResources(), R.mipmap.birdwatching, options);
        } else if (comicNumber == 2185) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            return BitmapFactory.decodeResource(context.getResources(), R.mipmap.cumulonimbus_2x, options);
        }

        Bitmap mBitmap = null;
        String comicFileName = comicNumber + ".png";
        try {
            File file = new File(prefHelper.getOfflinePath(context), comicFileName);
            FileInputStream fis = new FileInputStream(file);
            mBitmap = BitmapFactory.decodeStream(fis);
            fis.close();
        } catch (IOException e) {
            Timber.e( "Image not found, looking in internal storage");
            try {
                FileInputStream fis = context.openFileInput(String.valueOf(comicNumber));
                mBitmap = BitmapFactory.decodeStream(fis);
                fis.close();
            } catch (Exception e2) {
                Timber.e(e2.getMessage());
            }
        }
        return mBitmap;
    }
}
