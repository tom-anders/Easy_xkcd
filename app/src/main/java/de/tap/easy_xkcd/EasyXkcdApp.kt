package de.tap.easy_xkcd

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import com.tap.xkcd_reader.BuildConfig
import org.acra.annotation.ReportsCrashes
import org.acra.ReportingInteractionMode
import de.tap.easy_xkcd.acra.CrashReportActivity
import de.tap.easy_xkcd.acra.CrashReportSenderFactory
import org.acra.ReportField
import dagger.hilt.android.HiltAndroidApp
import org.acra.ACRA
import timber.log.Timber
import timber.log.Timber.DebugTree
import javax.inject.Inject

@ReportsCrashes(
    mailTo = "easyxkcd@gmail.com",
    mode = ReportingInteractionMode.DIALOG,
    reportDialogClass = CrashReportActivity::class,
    reportSenderFactoryClasses = [CrashReportSenderFactory::class],
    customReportContent = [ReportField.USER_COMMENT, ReportField.PACKAGE_NAME, ReportField.APP_VERSION_NAME, ReportField.ANDROID_VERSION, ReportField.BRAND, ReportField.PHONE_MODEL, ReportField.STACK_TRACE, ReportField.SHARED_PREFERENCES, ReportField.LOGCAT]
)
@HiltAndroidApp
class EasyXkcdApp : Application(), Configuration.Provider {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        if (!BuildConfig.DEBUG) {
            ACRA.init(this)
        }
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    override fun getWorkManagerConfiguration() =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        Timber.plant(if (BuildConfig.DEBUG) {
            DebugTree()
        } else {
            NoDebugTree()
        })
    }

    private inner class NoDebugTree : DebugTree() {
        override fun log(priority: Int, tag: String?, message: String, throwable: Throwable?) {
            //No Debug messages in the final app
            if (priority != Log.DEBUG) {
                super.log(priority, tag, message, throwable)
            }
        }
    }
}