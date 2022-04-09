package de.tap.easy_xkcd.settings

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.tap.xkcd_reader.R
import com.tap.xkcd_reader.databinding.ActivitySettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.Activities.BaseActivity
import de.tap.easy_xkcd.utils.ThemePrefs
import uz.shift.colorpicker.LineColorPicker
import javax.inject.Inject

@AndroidEntryPoint
open class BaseSettingsActivity constructor(
    private val fragment: PreferenceFragmentCompat
) : BaseActivity() {
    private lateinit var binding: ActivitySettingsBinding

    companion object {
        const val RESULT_RESTART_MAIN = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar(binding.toolbar.root)

        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, fragment)
            .commit()
    }
}

@AndroidEntryPoint
abstract class BasePreferenceFragment : PreferenceFragmentCompat() {
    @Inject
    lateinit var themePrefs: ThemePrefs

    protected fun restartParentActivityWithoutAnimation() {
        activity?.setResult(BaseSettingsActivity.RESULT_RESTART_MAIN)

        val intent = activity?.intent
        intent?.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_NO_ANIMATION
        )
        activity?.overridePendingTransition(0, 0)
        activity?.finish()

        activity?.overridePendingTransition(0, 0)
        if (intent != null) {
            startActivity(intent)
        }
    }

    protected fun showColorPickerDialog(
        titleId: Int, colors: IntArray, initialColor: Int, colorSelected: (Int) -> Unit
    ) {
        val dialog = activity?.layoutInflater?.inflate(R.layout.color_chooser, null)?.apply {
            findViewById<TextView>(R.id.title)?.apply {
                text = resources.getString(titleId)
                setBackgroundColor(themePrefs.getPrimaryColor(false))
            }


            findViewById<CardView>(R.id.dialog_card_view)?.apply {
                if (themePrefs.nightThemeEnabled()) {
                    setCardBackgroundColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.background_material_dark
                        )
                    )
                }
            }

        }
        val lineColorPicker = dialog?.findViewById<LineColorPicker>(R.id.picker3)?.apply {
            setColors(colors)
            setSelectedColor(initialColor)
        }

        val alterDialog = AlertDialog.Builder(activity)
            .setView(dialog)
            .show()

        dialog?.findViewById<TextView>(R.id.ok)?.setOnClickListener {
            lineColorPicker?.color?.let(colorSelected)

            alterDialog.dismiss()
            restartParentActivityWithoutAnimation()
        }
    }

}

@AndroidEntryPoint
class SettingsActivity : BaseSettingsActivity(PreferenceFragment()) {
    lateinit var activityResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                when (it.resultCode) {
                    RESULT_RESTART_MAIN -> {
                        setResult(RESULT_RESTART_MAIN)

                        overridePendingTransition(0, 0)
                        finish()
                        overridePendingTransition(0, 0)
                        startActivity(intent)
                    }
                }
            }
    }
}

class PreferenceFragment : PreferenceFragmentCompat(), Preference.OnPreferenceClickListener {
    companion object {
        private const val APPEARANCE = "appearance"
        private const val BEHAVIOR = "behavior"
        private const val OFFLINE_NOTIFICATIONS = "offline_notifications"
        private const val ALT_SHARING = "altSharing"
        private const val ADVANCED = "advanced"
        private const val NIGHT = "night"
        private const val WIDGET = "widget"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        listOf(
            APPEARANCE, BEHAVIOR, OFFLINE_NOTIFICATIONS, ALT_SHARING,
            ADVANCED, NIGHT, WIDGET
        ).map {
            findPreference<Preference>(it)?.onPreferenceClickListener = this
        }
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        when (preference.key) {
            APPEARANCE -> AppearanceActivity::class.java
            BEHAVIOR -> BehaviorActivity::class.java
            OFFLINE_NOTIFICATIONS -> OfflineAndNotificationsActivity::class.java
            NIGHT -> NightActivity::class.java
            WIDGET -> WidgetActivity::class.java
            ALT_SHARING -> AltAndSharingActivity::class.java
            ADVANCED -> AdvancedActivity::class.java
            else -> null
        }?.let {
            (activity as? SettingsActivity)?.activityResultLauncher?.launch(
                Intent(activity, it)
            )
        }
        return false
    }

}

