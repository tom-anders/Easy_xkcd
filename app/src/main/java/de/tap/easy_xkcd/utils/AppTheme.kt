package de.tap.easy_xkcd.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import com.tap.xkcd_reader.R

class AppTheme(
    private val context: Context
) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val sharedPrefs = context.getSharedPreferences("MainActivity", Activity.MODE_PRIVATE)

    companion object {
        private const val THEME = "pref_theme"
        private const val COLOR_PRIMARY = "pref_color_primary"
        private const val COLOR_PRIMARY_DARK = "pref_color_primary_dark"
        private const val COLOR_ACCENT = "pref_color_accent"
        private const val COLOR_ACCENT_NIGHT = "pref_color_accent_night"
        private const val NIGHT_THEME = "pref_night"
        private const val NIGHT_SYSTEM = "pref_night_system"
        private const val AMOLED_NIGHT = "pref_amoled"
        private const val DETECT_COLOR = "pref_detect_color"

        private const val AUTO_NIGHT_START_MIN = "pref_auto_night_start_min"
        private const val AUTO_NIGHT_START_HOUR = "pref_auto_night_start_hour"
        private const val AUTO_NIGHT_END_MIN = "pref_auto_night_end_min"
        private const val AUTO_NIGHT_END_HOUR = "pref_auto_night_end_hour"
        private const val INVERT_COLORS = "pref_invert"

        val negativeColorFilter = ColorMatrixColorFilter(floatArrayOf(
            -1.0f, 0f, 0f, 0f, 255f, //red
            0f, -1.0f, 0f, 0f, 255f, //green
            0f, 0f, -1.0f, 0f, 255f, //blue
            0f, 0f, 0f, 1.0f, 0f //alpha
        ))
    }

    private val detectColors by ReadOnlyPref(prefs, DETECT_COLOR, true)
    fun bitmapContainsColor(bitmap: Bitmap, comicNumber: Int): Boolean {
        //TODO Add more special cases here
        if (comicNumber == 1913) //https://github.com/tom-anders/Easy_xkcd/issues/116
            return true
        if (comicNumber == 1551) return true
        if (comicNumber == 2018) //This one doesn't work w/o the yellow color
            return true
        return if (detectColors) try {
            Palette.from(bitmap).generate().vibrantSwatch != null
        } catch (e: Exception) {
            false
        } else false
    }

    fun useSystemNightTheme() = Build.VERSION.SDK_INT >= 29 && prefs.getBoolean(NIGHT_SYSTEM, true)

    fun amoledThemeEnabled() = nightThemeEnabled && prefs.getBoolean(AMOLED_NIGHT, false)

    private var nightThemePref by Pref(prefs, NIGHT_THEME, false)

    var nightThemeEnabled: Boolean
        get() = if (useSystemNightTheme()) {
            // Use system setting in Android 10 and above
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        } else {
            nightThemePref
        }
        set(value) {
            nightThemePref = value
        }

    fun nightThemeEnabledIgnoreAutoNight() = nightThemePref

    @ColorInt
    private fun getColor(@ColorRes color: Int): Int {
        return ContextCompat.getColor(context, color)
    }

    var accentColor by Pref(sharedPrefs, COLOR_ACCENT, R.color.AccentLime)
    var accentColorNight by Pref(sharedPrefs, COLOR_ACCENT_NIGHT, accentColor)

    private var _primaryColorDark by Pref(sharedPrefs, COLOR_PRIMARY_DARK, R.color.PrimaryDarkBlueGrey)
    val primaryColorDark: Int
        get() = if (nightThemeEnabled) Color.BLACK else _primaryColorDark

    private var _primaryColor by Pref(sharedPrefs, COLOR_PRIMARY, getColor(R.color.PrimaryBlueGrey))

    fun getPrimaryColor(ignoreNightTheme: Boolean): Int {
        return if (ignoreNightTheme || !nightThemeEnabled) _primaryColor else Color.BLACK
    }

    private val primaryColorToDarkColor by lazy {
        mapOf(
            getColor(R.color.PrimaryPurple) to getColor(R.color.PrimaryDarkPurple),
            getColor(R.color.PrimaryDeepPurple) to getColor(R.color.PrimaryDarkDeepPurple),
            getColor(R.color.PrimaryIndigo) to getColor(R.color.PrimaryDarkIndigo),
            getColor(R.color.PrimaryBlue) to getColor(R.color.PrimaryDarkBlue),
            getColor(R.color.PrimaryLightBlue) to getColor(R.color.PrimaryDarkLightBlue),
            getColor(R.color.PrimaryCyan) to getColor(R.color.PrimaryDarkCyan),
            getColor(R.color.PrimaryTeal) to getColor(R.color.PrimaryDarkTeal),
            getColor(R.color.PrimaryGreen) to getColor(R.color.PrimaryDarkGreen),
            getColor(R.color.PrimaryLightGreen) to getColor(R.color.PrimaryDarkLightGreen),
            getColor(R.color.PrimaryLime) to getColor(R.color.PrimaryDarkLime),
            getColor(R.color.PrimaryYellow) to getColor(R.color.PrimaryDarkYellow),
            getColor(R.color.PrimaryAmber) to getColor(R.color.PrimaryDarkAmber),
            getColor(R.color.PrimaryOrange) to getColor(R.color.PrimaryDarkOrange),
            getColor(R.color.PrimaryDeepOrange) to getColor(R.color.PrimaryDarkDeepOrange),
            getColor(R.color.PrimaryRed) to getColor(R.color.PrimaryDarkRed),
            getColor(R.color.PrimaryBrown) to getColor(R.color.PrimaryDarkBrown),
            getColor(R.color.PrimaryGrey) to getColor(R.color.PrimaryDarkGrey),
            getColor(R.color.PrimaryBlueGrey) to getColor(R.color.PrimaryDarkBlueGrey),
            getColor(R.color.PrimaryBlack) to getColor(R.color.PrimaryDarkBlack),
        )
    }
    val primaryColors by lazy { primaryColorToDarkColor.keys }

    val accentColors by lazy {
        listOf(
            getColor(R.color.AccentPurple),
            getColor(R.color.AccentIndigo),
            getColor(R.color.AccentBlue),
            getColor(R.color.AccentLightBlue),
            getColor(R.color.AccentCyan),
            getColor(R.color.AccentTeal),
            getColor(R.color.AccentGreen),
            //getColor(R.color.AccentLightGreen), this color didn't work for some reason
            getColor(R.color.AccentLime),
            getColor(R.color.AccentYellow),
            getColor(R.color.AccentAmber),
            getColor(R.color.AccentOrange),
            getColor(R.color.AccentDeepOrange),
            getColor(R.color.AccentRed),
        )
    }

    fun setPrimaryColor(color: Int) {
        _primaryColor = color
        _primaryColorDark = primaryColorToDarkColor.getOrDefault(color, R.color.PrimaryDarkBlueGrey)
    }

    var theme: Int
        set(value) {
            accentColor = value
        }
        get() {
            val chooseTheme =
                { nightTheme: Int, regularTheme: Int -> if (nightThemeEnabled) nightTheme else regularTheme }
            return when (if (nightThemeEnabled) accentColorNight else accentColor) {
                // @formatter:off
                getColor(R.color.AccentPurple)     -> chooseTheme(R.style.PurpleNightTheme,     R.style.PurpleTheme)
                getColor(R.color.AccentIndigo)     -> chooseTheme(R.style.IndigoNightTheme,     R.style.IndigoTheme)
                getColor(R.color.AccentBlue)       -> chooseTheme(R.style.BlueNightTheme,       R.style.BlueTheme)
                getColor(R.color.AccentLightBlue)  -> chooseTheme(R.style.LightBlueNightTheme,  R.style.LightBlueTheme)
                getColor(R.color.AccentCyan)       -> chooseTheme(R.style.CyanNightTheme,       R.style.CyanTheme)
                getColor(R.color.AccentTeal)       -> chooseTheme(R.style.TealNightTheme,       R.style.TealTheme)
                getColor(R.color.AccentGreen)      -> chooseTheme(R.style.GreenNightTheme,      R.style.GreenTheme)
                getColor(R.color.AccentLightGreen) -> chooseTheme(R.style.LightBlueNightTheme,  R.style.LightBlueTheme)
                getColor(R.color.AccentLime)       -> chooseTheme(R.style.LimeNightTheme,       R.style.LimeTheme)
                getColor(R.color.AccentYellow)     -> chooseTheme(R.style.YellowNightTheme,     R.style.YellowTheme)
                getColor(R.color.AccentAmber)      -> chooseTheme(R.style.AmberNightTheme,      R.style.AmberTheme)
                getColor(R.color.AccentOrange)     -> chooseTheme(R.style.OrangeNightTheme,     R.style.OrangeTheme)
                getColor(R.color.AccentDeepOrange) -> chooseTheme(R.style.DeepOrangeNightTheme, R.style.DeepOrangeTheme)
                getColor(R.color.AccentRed)        -> chooseTheme(R.style.RedNightTheme,        R.style.RedTheme)
                else                               -> chooseTheme(R.style.LimeNightTheme,       R.style.LimeTheme)
                // @formatter:on
            }
        }

    val invertColors by Pref(prefs, INVERT_COLORS, true)
}