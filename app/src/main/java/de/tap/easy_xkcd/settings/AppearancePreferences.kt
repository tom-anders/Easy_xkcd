package de.tap.easy_xkcd.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.Preference
import com.tap.xkcd_reader.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AppearanceActivity : BaseSettingsActivity(AppearanceFragment())

@AndroidEntryPoint
class AppearanceFragment: BasePreferenceFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        findPreference<Preference>("pref_color_primary")?.setOnPreferenceClickListener {
            showColorPickerDialog(R.string.theme_primary_color_dialog,
                appTheme.primaryColors.toIntArray(), appTheme.getPrimaryColor(true)) {
                appTheme.setPrimaryColor(it)
            }
            true
        }

        findPreference<Preference>("pref_color_accent")?.setOnPreferenceClickListener {
            showColorPickerDialog(R.string.theme_accent_color_dialog,
                appTheme.accentColors.toIntArray(), appTheme.accentColor) {
                appTheme.theme = it
            }
            true
        }

        findPreference<Preference>("pref_navbar")?.setOnPreferenceClickListener {
            restartParentActivityWithoutAnimation()
            true
        }

        findPreference<Preference>("pref_subtitle")?.setOnPreferenceClickListener {
            activity?.setResult(BaseSettingsActivity.RESULT_RESTART_MAIN)
            true
        }

        findPreference<Preference>("pref_hide_donate")?.setOnPreferenceClickListener {
            activity?.setResult(BaseSettingsActivity.RESULT_RESTART_MAIN)
            true
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_appearance, rootKey)
    }
}
