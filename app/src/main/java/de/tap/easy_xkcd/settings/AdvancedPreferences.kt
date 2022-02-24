package de.tap.easy_xkcd.settings

import android.os.Bundle
import com.tap.xkcd_reader.R

class AdvancedActivity: BaseSettingsActivity(WidgetFragment())

class AdvancedFragment: BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_advanced, rootKey)
    }
}
