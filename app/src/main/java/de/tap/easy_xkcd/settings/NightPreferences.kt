package de.tap.easy_xkcd.settings

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.tap.xkcd_reader.R
import dagger.hilt.android.AndroidEntryPoint

class NightActivity: BaseSettingsActivity(NightFragment())

@AndroidEntryPoint
class NightFragment: BasePreferenceFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val nightSystemPreference = findPreference<SwitchPreference>("pref_night_system")
        val nightPreference = findPreference<SwitchPreference>("pref_night")
        if (Build.VERSION.SDK_INT < 29) {
            nightSystemPreference?.isVisible = false
        } else {
            nightPreference?.isEnabled = false == nightSystemPreference?.isChecked
        }

        nightPreference?.setOnPreferenceClickListener {
            restartParentActivityWithoutAnimation()
            true
        }

        nightSystemPreference?.setOnPreferenceClickListener { pref ->
            restartParentActivityWithoutAnimation()

            if (pref is SwitchPreference) {
                nightPreference?.isEnabled = !(pref.isChecked)
                if (pref.isChecked) {
                    nightPreference?.isChecked = appTheme.nightThemeEnabled
                }
            }
            true
        }

        findPreference<Preference>("pref_invert_color")?.setOnPreferenceClickListener {
            activity?.setResult(BaseSettingsActivity.RESULT_RESTART_MAIN)
            true
        }

        findPreference<Preference>("pref_amoled")?.setOnPreferenceClickListener {
            restartParentActivityWithoutAnimation()
            true
        }

        findPreference<Preference>("pref_color_accent")?.setOnPreferenceClickListener {
            showColorPickerDialog(R.string.theme_accent_color_dialog,
                appTheme.accentColors.toIntArray(), appTheme.accentColorNight) {
                appTheme.accentColorNight = it
            }
            true
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_night, rootKey)
    }
}
