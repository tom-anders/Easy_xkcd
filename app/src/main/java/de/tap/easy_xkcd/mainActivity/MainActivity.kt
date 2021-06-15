package de.tap.easy_xkcd.mainActivity

import android.app.ProgressDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tap.xkcd_reader.R
import com.tap.xkcd_reader.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.Activities.BaseActivity
import de.tap.easy_xkcd.Activities.NestedSettingsActivity
import de.tap.easy_xkcd.Activities.SettingsActivity
import de.tap.easy_xkcd.CustomTabHelpers.CustomTabActivityHelper
import de.tap.easy_xkcd.comicBrowsing.ComicBrowserFragment
import de.tap.easy_xkcd.whatIfOverview.WhatIfOverviewFragment

@AndroidEntryPoint
class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding

    private val FRAGMENT_TAG = "MainActivityFragments"

    private lateinit var bottomAppBar: BottomAppBar
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var toolbar: Toolbar

    private lateinit var progress: ProgressDialog

    private var customTabActivityHelper = CustomTabActivityHelper()
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>

    val model: ComicDatabaseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        toolbar = binding.toolbar.root
        setupToolbar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        setupBottomAppBar()

        bottomNavigationView = binding.bottomNavigationView
        bottomNavigationView.setOnNavigationItemSelectedListener {
            showFragmentForSelectedNavigationItem(it)
        }
        // Nothing to be done yet in that case
        bottomNavigationView.setOnNavigationItemReselectedListener {}

        progress = ProgressDialog(this)
        progress.setTitle(resources?.getString(R.string.update_database))
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progress.isIndeterminate = false
        model.progress.observe(this) {
            if (it != null) {
                progress.progress = it
                progress.max = model.progressMax
                progress.show()
            } else {
                progress.dismiss()
            }
        }

        model.foundNewComic.observe(this) {
            //TODO show snackbar here or maybe observe this in the fragments instead
        }

        model.databaseLoaded.observe(this) { databaseLoaded ->
            if (databaseLoaded) {
                if (savedInstanceState == null) {
                    bottomNavigationView.selectedItemId =
                        if (prefHelper.launchToOverview()) R.id.nav_overview else R.id.nav_browser
                }
            }
        }

        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            when (it.resultCode) {
               NestedSettingsActivity.RESULT_RESTART_MAIN -> {
                   overridePendingTransition(0, 0)
                   finish()
                   overridePendingTransition(0, 0)
                   startActivity(intent)
               }
            }
        }
    }

    override fun onResume() {
        toolbar.title = bottomNavigationView.menu.findItem(bottomNavigationView.selectedItemId)?.title
        super.onResume()
    }

    fun showFragmentForSelectedNavigationItem(item: MenuItem): Boolean {
        toolbar.title = item.title
        return when (item.itemId) {
            R.id.nav_whatif -> {
                showWhatIfFragment()
            }
            R.id.nav_browser -> {
                showComicBrowserFragment()
            }
            R.id.nav_favorites -> {
                showFavoritesFragment()
            }
            R.id.nav_overview -> {
                showComicOverviewFragment()
            }
            else -> false
        }
    }

    fun setupBottomAppBar() {
        bottomAppBar = binding.bottomAppBar
        when {
            themePrefs.amoledThemeEnabled() -> {
                bottomAppBar.backgroundTint = ColorStateList.valueOf(Color.BLACK)
            }
            themePrefs.nightThemeEnabled() -> {
                bottomAppBar.backgroundTint = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.background_material_dark)
                )
                toolbar.popupTheme = R.style.ThemeOverlay_AppCompat
            }
            else -> {
                bottomAppBar.backgroundTint =
                    ColorStateList.valueOf(themePrefs.getPrimaryColor(false))
            }
        }

    }

    fun makeFragmentTransaction(fragment: Fragment): FragmentTransaction =
        supportFragmentManager.beginTransaction()
            .replace(R.id.flContent, fragment, FRAGMENT_TAG)

    fun showWhatIfFragment(): Boolean {
        supportActionBar?.title = resources.getString(R.string.nv_whatif)
        makeFragmentTransaction(WhatIfOverviewFragment()).commitAllowingStateLoss()
        return true
    }

    fun showComicOverviewFragment(): Boolean {
//        makeFragmentTransaction(
//            OverviewBaseFragment.getOverviewFragment(
//                prefHelper,
//                prefHelper.lastComic
//            )
//        ).commitAllowingStateLoss()
        return true
    }

    fun showFavoritesFragment(): Boolean {
//        makeFragmentTransaction(FavoritesFragment()).commitAllowingStateLoss()
        return true
    }

    fun showComicBrowserFragment(): Boolean {
        makeFragmentTransaction(ComicBrowserFragment())
            .commitAllowingStateLoss()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        menu?.findItem(R.id.action_donate)?.isVisible = !prefHelper.hideDonate()
        menu?.findItem(R.id.action_night_mode)?.isChecked = themePrefs.nightEnabledThemeIgnoreAutoNight()
        menu?.findItem(R.id.action_night_mode)?.isVisible = !themePrefs.autoNightEnabled() && !themePrefs.useSystemNightTheme()

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_settings -> {
            activityResultLauncher.launch(Intent(this, SettingsActivity::class.java))
            true
        }
        R.id.action_night_mode -> {
            toggleNightMode(item)
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun toggleNightMode(item: MenuItem): Boolean {
        item.isChecked = !item.isChecked
        themePrefs.setNightThemeEnabled(item.isChecked)

        val intent = intent
        intent.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_NO_ANIMATION
        )

        overridePendingTransition(0, 0)
        finish()
        overridePendingTransition(0, 0)
        startActivity(intent)

        return true
    }

    override fun onStart() {
        super.onStart()
        customTabActivityHelper.bindCustomTabsService(this)
    }

    override fun onStop() {
        super.onStop()
        customTabActivityHelper.unbindCustomTabsService(this)
    }

}