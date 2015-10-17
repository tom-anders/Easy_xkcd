package de.tap.easy_xkcd.CustomTabHelpers;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

/**
 * Adapted from https://medium.com/ribot-labs/exploring-chrome-customs-tabs-on-android-ef427effe2f4
 */

public class BrowserFallback implements CustomTabActivityHelper.CustomTabFallback {
    @Override
    public void openUri(Activity activity, Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(uri);
        activity.startActivity(intent);
    }
}
