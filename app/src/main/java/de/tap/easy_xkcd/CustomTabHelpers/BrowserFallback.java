package de.tap.easy_xkcd.CustomTabHelpers;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

/**
 * Created by Tom on 14.09.2015.
 */
public class BrowserFallback implements CustomTabActivityHelper.CustomTabFallback {
    @Override
    public void openUri(Activity activity, Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(uri);
        activity.startActivity(intent);
    }
}
