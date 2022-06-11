package de.tap.easy_xkcd.Activities

import android.app.ActivityManager.TaskDescription
import android.app.ProgressDialog
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.tap.xkcd_reader.R
import de.tap.easy_xkcd.database.ProgressStatus
import de.tap.easy_xkcd.utils.*
import kotlinx.coroutines.flow.Flow

abstract class BaseActivity : AppCompatActivity() {
    // TODO Inject these
    protected lateinit var sharedPrefs: SharedPrefManager
    protected lateinit var settings: AppSettings
    protected lateinit var themePrefs: ThemePrefs
    @JvmField @Deprecated("Remove when MainActivity.java is fully removed")
    var defaultVisibility = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPrefs = SharedPrefManager(this)
        themePrefs = ThemePrefs(this)
        settings = AppSettings(this)
        defaultVisibility = window.decorView.systemUiVisibility
        setTheme(themePrefs.newTheme)
        if (themePrefs.amoledThemeEnabled()) {
            window.decorView.setBackgroundColor(Color.BLACK)
        } else if (themePrefs.nightThemeEnabled()) {
            window.decorView.setBackgroundColor(
                ContextCompat.getColor(
                    this,
                    R.color.background_material_dark
                )
            )
        }
        super.onCreate(savedInstanceState)
    }

    /**
     * Sets up the colors of toolbar, status bar and nav drawer
     */
    protected fun setupToolbar(toolbar: Toolbar) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val ic = BitmapFactory.decodeResource(resources, R.mipmap.ic_easy_xkcd_recents)
            val color = themePrefs.getPrimaryColor(false)
            val description = TaskDescription("Easy xkcd", ic, color)
            setTaskDescription(description)
            if (settings.colorNavbar) window.navigationBarColor =
                themePrefs.primaryDarkColor
        }
        window.statusBarColor = themePrefs.primaryDarkColor
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setBackgroundColor(themePrefs.getPrimaryColor(false))
        if (themePrefs.amoledThemeEnabled()) {
            toolbar.popupTheme = R.style.ThemeOverlay_AmoledBackground
        }
    }

    //Useful for when starting a Async Task that would be leaked by screen rotation
    fun lockRotation() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
    }

    fun unlockRotation() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    }

    @Deprecated("Use collectAsProgressDialog extension function")
    protected inline fun collectProgress(progressId: Int, progressFlow: Flow<ProgressStatus>,
                                         crossinline actionWhenFinished: suspend () -> Unit) {
        val progress = ProgressDialog(this)
        progress.setTitle(resources?.getString(progressId))
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progress.isIndeterminate = false
        progress.setCancelable(false)

        progressFlow.observe(this) {
            when (it) {
                is ProgressStatus.Finished -> {
                    progress.dismiss()

                    actionWhenFinished()
                }
                is ProgressStatus.SetProgress -> {
                    progress.max = it.max
                    progress.progress = it.value
                    progress.show()
                }
                is ProgressStatus.ResetProgress -> {
                    progress.progress = 0
                }
            }
        }
    }
}