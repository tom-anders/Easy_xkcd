package de.tap.easy_xkcd.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.tap.xkcd_reader.R
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.Activities.BaseActivity

@AndroidEntryPoint
class BehaviorActivity : BaseSettingsActivity(BehaviorFragment())

class BehaviorFragment: PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_behavior, rootKey)
    }
}
