package de.tap.easy_xkcd.settings

import android.os.Bundle
import com.tap.xkcd_reader.R

class AltAndSharingActivity: BaseSettingsActivity(WidgetFragment())

class AltAndSharingFragment: BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_alt_sharing, rootKey)
    }
}