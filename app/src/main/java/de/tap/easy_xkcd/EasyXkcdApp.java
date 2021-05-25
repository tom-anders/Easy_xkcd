package de.tap.easy_xkcd;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.tap.xkcd_reader.BuildConfig;

import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

import dagger.hilt.android.HiltAndroidApp;
import timber.log.Timber;

@ReportsCrashes(mailTo = "easyxkcd@gmail.com",
        mode = ReportingInteractionMode.DIALOG,
        reportDialogClass = de.tap.easy_xkcd.acra.CrashReportActivity.class,
        reportSenderFactoryClasses = de.tap.easy_xkcd.acra.CrashReportSenderFactory.class,
        customReportContent = {
                ReportField.USER_COMMENT,
                ReportField.PACKAGE_NAME,
                ReportField.APP_VERSION_NAME,
                ReportField.ANDROID_VERSION,
                ReportField.BRAND,
                ReportField.PHONE_MODEL,
                ReportField.STACK_TRACE,
                ReportField.SHARED_PREFERENCES,
                ReportField.LOGCAT
        }
)

@HiltAndroidApp
public class EasyXkcdApp extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        ACRA.init(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        } else {
            Timber.plant(new NoDebugTree());
        }

    }

    private class NoDebugTree extends Timber.DebugTree {
        @Override
        protected void log(final int priority, final String tag, final String message, final Throwable throwable) {
            //No Debug messages in the final app
            if (priority != Log.DEBUG) {
                super.log(priority, tag, message, throwable);
            }
        }
    }
}

