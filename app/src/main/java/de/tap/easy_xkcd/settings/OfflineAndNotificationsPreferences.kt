package de.tap.easy_xkcd.settings

import android.Manifest
import android.app.Instrumentation.ActivityResult
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.tap.xkcd_reader.R
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.utils.AppSettings
import de.tap.easy_xkcd.utils.observe
import timber.log.Timber
import javax.inject.Inject

class OfflineAndNotificationsActivity : BaseSettingsActivity(OfflineNotificationsFragment())

@AndroidEntryPoint
class OfflineNotificationsFragment : PreferenceFragmentCompat() {
    private val viewModel: OfflineAndNotificationViewModel by viewModels()

    @Inject
    lateinit var settings: AppSettings

    enum class PendingAction {
        OFFLINE_MODE,
        WHATIF_OFFLINE_MODE,
        NOTIFICATION_INTERVAL
    }
    /// We use this to decide what to do after we have been granted the notification permission
    private lateinit var pendingAction: PendingAction

    private fun requestNotificationPermission(pendingAction: PendingAction) {
        this.pendingAction = pendingAction
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    private fun hasNotificationPermission() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

            override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                isGranted: Boolean ->
            if (isGranted) {
                Timber.i("Permission granted!")
                when (pendingAction) {
                    PendingAction.OFFLINE_MODE -> viewModel.onOfflineModeEnabled()
                    PendingAction.WHATIF_OFFLINE_MODE -> viewModel.onWhatIfOfflineModeEnabled()
                    PendingAction.NOTIFICATION_INTERVAL -> viewModel.onNotificationIntervalChanged(findPreference<ListPreference>("pref_notifications")?.value.toString())
                }
            } else {
                Timber.w("Permission denied!")
            }
        }

        findPreference<SwitchPreference>("pref_offline")?.let { offlinePref ->
            offlinePref.setOnPreferenceChangeListener { _, newValue ->
                if (!hasNotificationPermission()) {
                    requestNotificationPermission(PendingAction.OFFLINE_MODE)
                    false
                } else if (newValue as Boolean) {
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
                            settings.fullOfflineEnabled = false
                            viewModel.onOfflineModeDisabled()
                        }
                        .setCancelable(false)
                        .show()
                    false
                }
            }

            viewModel.disableOfflineModeButton.observe(viewLifecycleOwner) {
                if (settings.fullOfflineEnabled != offlinePref.isChecked) {
                    offlinePref.isChecked = settings.fullOfflineEnabled
                }

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

        findPreference<SwitchPreference>("pref_offline_whatif")?.let { whatIfOfflinePref ->
            whatIfOfflinePref.setOnPreferenceChangeListener { _, newValue ->
                if (!hasNotificationPermission()) {
                    requestNotificationPermission(PendingAction.WHATIF_OFFLINE_MODE)
                    false
                } else if (newValue as Boolean) {
                    viewModel.onWhatIfOfflineModeEnabled()
                    false // Preference will only be set after all images have been downloaded
                } else {
                    AlertDialog.Builder(requireActivity())
                        .setMessage(R.string.delete_offline_whatif_dialog)
                        .setNegativeButton(
                            R.string.dialog_cancel
                        ) { _, _ -> }
                        .setPositiveButton(
                            R.string.dialog_yes
                        ) { _, _ ->
                            settings.fullOfflineWhatIf = false
                            viewModel.onWhatIfOfflineModeDisabled()
                            whatIfOfflinePref.isChecked = false
                        }
                        .setCancelable(false)
                        .show()
                    false
                }
            }

            viewModel.disableWhatifOfflineModeButton.observe(viewLifecycleOwner) {
                if (settings.fullOfflineWhatIf != whatIfOfflinePref.isChecked) {
                    whatIfOfflinePref.isChecked = settings.fullOfflineWhatIf
                }

                whatIfOfflinePref.isEnabled = !it
            }
        }


        findPreference<ListPreference>("pref_notifications")?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    Timber.i("Permission already granted, setting interval ${newValue}")
                    viewModel.onNotificationIntervalChanged(newValue.toString())
                } else {
                    Timber.i("Requesting notification permission...")
                    requestNotificationPermission(PendingAction.NOTIFICATION_INTERVAL)
                }
                true
            }

        }


        return super.onCreateView(inflater, container, savedInstanceState)
    }


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_offline_notifications, rootKey)
    }
}
