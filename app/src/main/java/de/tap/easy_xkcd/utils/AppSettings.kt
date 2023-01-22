package de.tap.easy_xkcd.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.io.File
import kotlin.reflect.KProperty

open class ReadOnlyPref<T>(protected val prefs: SharedPreferences, protected val key: String, private val default: T) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        @Suppress("UNCHECKED_CAST")
        return when (default) {
            is String -> prefs.getString(key, default) ?: default
            is Int -> prefs.getInt(key, default)
            is Boolean -> prefs.getBoolean(key, default)
            else -> throw IllegalArgumentException("Unknown Settings Type!")
        } as? T? ?: default
    }
}

open class Pref<T>(prefs: SharedPreferences, key: String, default: T) : ReadOnlyPref<T>(prefs, key, default) {
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        prefs.edit().apply {
            when (value) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Boolean -> putBoolean(key, value)
                else -> throw IllegalArgumentException("Unknown Settings Type!")
            }
        }.apply()
    }
}

abstract class PrefManagerBase(
    protected val prefs: SharedPreferences,
) {
    open inner class ReadOnlyPref<T>(key: String, default: T)
        : de.tap.easy_xkcd.utils.ReadOnlyPref<T>(prefs, key, default)

    open inner class Pref<T>(key: String, default: T)
        : de.tap.easy_xkcd.utils.Pref<T>(prefs, key, default)
}

class SharedPrefManager(
    context: Context
) : PrefManagerBase(context.getSharedPreferences("MainActivity", Activity.MODE_PRIVATE)) {
    private companion object {
        const val NEWEST_COMIC = "Newest Comic"
        const val LAST_COMIC = "Last Comic"
        const val ALT_TIP = "alt_tip"
        const val WHAT_IF_TIP = "whatif_tip"
        const val HIDE_READ_WHATIF = "hide_read_whatif"
        const val HIDE_READ_OVERVIEW = "hide_read_overview"
        const val SWIPE_ENABLED = "whatif_swipe"
        const val OVERVIEW_FAV = "overview_fav"
        const val OVERVIEW_STYLE = "overview_style"
        const val BOOKMARK = "bookmark"
        const val SHOW_BETA_DIALOG = "show_beta_dialog_8.0beta-1"
        const val RESET_WHATIF_DATABASE = "reset_whatif_database"
    }

    var hasAlreadyResetWhatifDatabase by Pref(RESET_WHATIF_DATABASE, false)
    var showBetaDialog by Pref(SHOW_BETA_DIALOG, true)

    var newestComic by Pref(NEWEST_COMIC, 0)
    var lastComic by Pref(LAST_COMIC, 0)

    var showAltTip by Pref(ALT_TIP, true)
    var showWhatifTip by Pref(WHAT_IF_TIP, true)

    var hideReadWhatif by Pref(HIDE_READ_WHATIF, false)
    var swipeEnabled by Pref(SWIPE_ENABLED, false)

    var hideReadComics by Pref(HIDE_READ_OVERVIEW, false)

    var showOnlyFavsInOverview by Pref(OVERVIEW_FAV, false)

    // TODO Use an enum here
    var overviewStyle by Pref(OVERVIEW_STYLE, 1)

    var bookmark by Pref(BOOKMARK, 0)
}

class AppSettings(
    context: Context,
) : PrefManagerBase(PreferenceManager.getDefaultSharedPreferences(context)) {

    private companion object {
        const val FULL_OFFLINE = "pref_offline"
        const val WHATIF_OFFLINE = "pref_offline_whatif"

        const val CUSTOM_TABS = "pref_custom_tabs"
        const val ALT_VIBRATION = "pref_alt"
        const val ALT_BACK = "pref_alt_back"
        const val ALT_ALWAYS_SHOW = "pref_show_alt"
        const val SHARE_IMAGE = "pref_share"
        const val SHARE_MOBILE = "pref_mobile"
        const val NOTIFICATIONS_INTERVAL = "pref_notifications"
        const val SHARE_ALT = "pref_share_alt"
        const val PREF_ZOOM = "pref_zoom"
        const val PREF_DONATE = "pref_hide_donate"

        const val ALT_OPTIONS = "pref_alt_options"
        const val ALT_ACTIVATION = "pref_alt_activation"
        const val COLORED_NAVBAR = "pref_navbar"
        const val OFFLINE_INTERNAL_EXTERNAL = "pref_offline_internal_external"
        const val DOUBLE_TAP_FAV = "pref_doubletap"
        const val ZOOM_SCROLL = "pref_zoom_scroll"
        const val DEFAULT_ZOOM = "pref_default_zoom"
        const val LAUNCH_TO_OVERVIEW = "pref_overview_default"
        const val INCLUDE_LINK = "pref_include_link"
        const val WIDGET_ALT = "widget_alt"
        const val WIDGET_COMIC_NUMBER = "widget_comicNumber"
        const val FULLSCREEN_ALLOWED = "pref_fullscreen_enabled"
        const val FULLSCREEN_HIDE_FAB = "pref_fullscreen_hide_fab"
        const val SUBTITLE_ENABLED = "pref_subtitle"
    }

    var fullOfflineEnabled by Pref(FULL_OFFLINE, false)
    var fullOfflineWhatIf by Pref(WHATIF_OFFLINE, false)

    var doubleTapToFavorite by Pref(DOUBLE_TAP_FAV, false)
    var useCustomTabs by Pref(CUSTOM_TABS, true)

    val altLongTap get() = prefs.getString(ALT_ACTIVATION, "1")?.toInt() == 1

    private fun hasAltOption(option: String) =
        prefs.getStringSet(ALT_OPTIONS, emptySet()).orEmpty().contains(option)

    val altVibration get() = hasAltOption(ALT_VIBRATION)
    val altBackButton get() = hasAltOption(ALT_BACK)
    val alwaysShowAltText get() = hasAltOption(ALT_ALWAYS_SHOW)

    var shareImage by Pref(SHARE_IMAGE, false)
    var shareMobile by Pref(SHARE_MOBILE, false)
    val shareAlt get() = hasAltOption(SHARE_ALT)

    val notificationIntervalHours
        get() = (prefs.getString(NOTIFICATIONS_INTERVAL, "0") ?: "0").toInt()

    fun getZoom(webDefault: Int) =
        prefs.getString(PREF_ZOOM, webDefault.toString())?.toIntOrNull() ?: webDefault

    var hideDonate by Pref(PREF_DONATE, false)

    val colorNavbar by ReadOnlyPref(COLORED_NAVBAR, true)
    val mobileEnabled by ReadOnlyPref(COLORED_NAVBAR, true)
    val subtitleEnabled by ReadOnlyPref(SUBTITLE_ENABLED, true)

    fun getOfflinePathForValue(context: Context, settingsValue: String?): File {
        val baseDir =
            if (settingsValue == "external") context.getExternalFilesDir(null) else context.filesDir
        val offlineDir = File(baseDir, "offlineData")
        if (!offlineDir.exists()) {
            offlineDir.mkdir()
        }
        return offlineDir
    }

    fun getOfflinePath(context: Context): File {
        return getOfflinePathForValue(
            context,
            prefs.getString(OFFLINE_INTERNAL_EXTERNAL, "external")
        )
    }

    val scrollDisabledWhileZoom by ReadOnlyPref(ZOOM_SCROLL, true)
    val defaultZoom by ReadOnlyPref(DEFAULT_ZOOM, true)

    val launchToOverview by ReadOnlyPref(LAUNCH_TO_OVERVIEW, false)

    val includeLinkWhenSharing by ReadOnlyPref(INCLUDE_LINK, false)

    val widgetShowAlt by ReadOnlyPref(WIDGET_ALT, false)
    val widgetShowComicNumber by ReadOnlyPref(WIDGET_COMIC_NUMBER, true)

    val fullscreenModeAllowed by ReadOnlyPref(FULLSCREEN_ALLOWED, true)
    val hideFabInFullscreen by ReadOnlyPref(FULLSCREEN_HIDE_FAB, false)
}