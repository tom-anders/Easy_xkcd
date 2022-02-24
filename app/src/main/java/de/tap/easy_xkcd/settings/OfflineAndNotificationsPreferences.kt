package de.tap.easy_xkcd.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.tap.xkcd_reader.R
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.fragments.NestedPreferenceFragment.deleteComicsTask
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.observe
import javax.inject.Inject

class OfflineAndNotificationsActivity : BaseSettingsActivity(OfflineNotificationsFragment())

@AndroidEntryPoint
class OfflineNotificationsFragment : PreferenceFragmentCompat() {
    private val viewModel: OfflineAndNotificationViewModel by viewModels()

    @Inject
    lateinit var prefHelper: PrefHelper

    private fun syncOfflinePref(offlinePref: SwitchPreference) {
        if (prefHelper.fullOfflineEnabled() != offlinePref.isChecked) {
            offlinePref.isChecked = prefHelper.fullOfflineEnabled()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        findPreference<SwitchPreference>("pref_offline")?.let { offlinePref ->
            offlinePref.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    viewModel.onOfflineModeEnabled()
                    false // Preference will only be set after all images have been downloaded
                } else {
                    AlertDialog.Builder(requireActivity())
                        .setMessage(R.string.delete_offline_dialog)
                        .setNegativeButton(
                            R.string.dialog_cancel
                        ) { _, _ -> }
                        .setPositiveButton(
                            R.string.dialog_yes
                        ) { _, _ ->
                            prefHelper.setFullOffline(false)
                            viewModel.onOfflineModeDisabled()
                        }
                        .setCancelable(false)
                        .show()
                    false
                }
            }

            viewModel.disableOfflineModeButton.observe(viewLifecycleOwner) {
                syncOfflinePref(offlinePref)

                offlinePref.isEnabled = !it
            }
        }

        findPreference<ListPreference>("pref_offline_internal_external")?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.onOfflinePathSelected(newValue.toString())
                true
            }

            viewModel.moveOfflineDataInProgress.observe(viewLifecycleOwner) {
                isEnabled = !it
            }
        }

        //TODO Migrate whatif offline mode to workmanager
        // (and first migrate whatif offline to room)

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_offline_notifications, rootKey)
    }
}
