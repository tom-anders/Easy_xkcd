package de.tap.easy_xkcd;

import android.app.Application;

import com.tap.xkcd_reader.BuildConfig;

import timber.log.Timber;

public class EasyXkcdApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        } else {
            Timber.plant(new NoLogTree());
        }
    }

    private class NoLogTree extends Timber.Tree {
        @Override
        protected void log(final int priority, final String tag, final String message, final Throwable throwable) {
        }
    }
}

