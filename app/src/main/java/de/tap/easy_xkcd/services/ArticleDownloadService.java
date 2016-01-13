package de.tap.easy_xkcd.services;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.tap.xkcd_reader.R;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

import de.tap.easy_xkcd.utils.PrefHelper;

public class ArticleDownloadService extends IntentService {

    private static final String OFFLINE_WHATIF_PATH = "/easy xkcd/what if/";
    private static final String OFFLINE_WHATIF_OVERVIEW_PATH = "/easy xkcd/what if/overview";

    public ArticleDownloadService() {
        super("ArticleDownloadService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(getApplicationContext())
                        .setSmallIcon(R.drawable.ic_notification)
                        .setProgress(100, 0 , false)
                        .setOngoing(true)
                        .setContentTitle(getResources().getString(R.string.loading_offline_whatif))
                        .setAutoCancel(true);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotificationManager.notify(0, mBuilder.build());

        PrefHelper prefHelper = new PrefHelper(getApplicationContext());
        Bitmap mBitmap;
        File sdCard = prefHelper.getOfflinePath();
        File dir;
        Document doc;
        //download overview
        try {
            doc = Jsoup.connect("https://what-if.xkcd.com/archive/")
                    .userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/32.0.1700.19 Safari/537.36")
                    .get();
            StringBuilder sb = new StringBuilder();
            Elements titles = doc.select("h1");
            prefHelper.setNewestWhatif(titles.size());

            sb.append(titles.first().text());
            titles.remove(0);
            for (Element title : titles) {
                sb.append("&&");
                sb.append(title.text());
            }
            prefHelper.setWhatIfTitles(sb.toString());

            Elements img = doc.select("img.archive-image");
            int count = 1;
            for (Element image : img) {
                String url = image.absUrl("src");
                try {
                    mBitmap = Glide.with(this)
                            .load(url)
                            .asBitmap()
                            .into(-1, -1)
                            .get();
                    dir = new File(sdCard.getAbsolutePath() + OFFLINE_WHATIF_OVERVIEW_PATH);
                    dir.mkdirs();
                    File file = new File(dir, String.valueOf(count) + ".png");
                    FileOutputStream fos = new FileOutputStream(file);
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.flush();
                    fos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Log.d("count", String.valueOf(count));
                int p = (int) (count / ((float) img.size()) * 100);
                mBuilder.setProgress(100, p, false);
                mNotificationManager.notify(0, mBuilder.build());
                count++;
            }
            if (prefHelper.getNewestWhatIf() == 0)
                prefHelper.setNewestWhatif(count-1);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //download html
        //for (int i = 1; i <= prefHelper.getNewestWhatIf(); i++) {
        for (int i = 1; i < prefHelper.getNewestWhatIf(); i++) {
            int size = prefHelper.getNewestWhatIf();
            try {
                doc = Jsoup.connect("https://what-if.xkcd.com/" + String.valueOf(i)).get();
                dir = new File(sdCard.getAbsolutePath() + OFFLINE_WHATIF_PATH + String.valueOf(i));
                dir.mkdirs();
                File file = new File(dir, String.valueOf(i) + ".html");
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                writer.write(doc.outerHtml());
                writer.close();
                //download images
                int count = 1;
                for (Element e : doc.select(".illustration")) {
                    try {
                        String url = "http://what-if.xkcd.com" + e.attr("src");
                        mBitmap = Glide.with(getApplicationContext())
                                .load(url)
                                .asBitmap()
                                .into(-1, -1)
                                .get();
                        dir = new File(sdCard.getAbsolutePath() + OFFLINE_WHATIF_PATH + String.valueOf(i));
                        dir.mkdirs();
                        file = new File(dir, String.valueOf(count) + ".png");
                        FileOutputStream fos = new FileOutputStream(file);
                        mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.flush();
                        fos.close();
                        count++;
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                }
                int p = (int) (i / ((float) size) * 100);
                mBuilder.setProgress(100, p, false);
                mBuilder.setContentText(i + "/" + prefHelper.getNewestWhatIf());
                mNotificationManager.notify(0, mBuilder.build());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Intent restart = new Intent("de.tap.easy_xkcd.ACTION_COMIC");
        restart.putExtra("number", prefHelper.getLastComic());
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 1, restart, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(pendingIntent);
        mBuilder.setContentText(getResources().getString(R.string.not_restart));
        mNotificationManager.notify(0, mBuilder.build());
    }

}
