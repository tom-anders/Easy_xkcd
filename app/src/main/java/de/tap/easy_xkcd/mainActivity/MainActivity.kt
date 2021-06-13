package de.tap.easy_xkcd.mainActivity

import android.app.ProgressDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tap.xkcd_reader.R
import com.tap.xkcd_reader.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.Activities.BaseActivity
import de.tap.easy_xkcd.Activities.SettingsActivity
import de.tap.easy_xkcd.comicBrowsing.ComicBrowserFragment
import de.tap.easy_xkcd.whatIfOverview.WhatIfOverviewFragment
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding

    private val FRAGMENT_TAG = "MainActivityFragments"

    private lateinit var bottomAppBar: BottomAppBar
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var toolbar: Toolbar

    private lateinit var progress: ProgressDialog

    val model: ComicDatabaseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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


        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        toolbar = binding.toolbar.root
        setupToolbar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        setupBottomAppBar()

        bottomNavigationView = binding.bottomNavigationView
        bottomNavigationView.setOnNavigationItemSelectedListener {
            showFragmentForSelectedNavigationItem(it.itemId)
        }
        // Nothing to be done yet in that case
        bottomNavigationView.setOnNavigationItemReselectedListener {}

        model.databaseLoaded.observe(this) { databaseLoaded ->
            if (databaseLoaded) {
                if (savedInstanceState == null) {
                    bottomNavigationView.selectedItemId =
                        if (prefHelper.launchToOverview()) R.id.nav_overview else R.id.nav_browser
                } else {
                    showFragmentForSelectedNavigationItem(bottomNavigationView.selectedItemId)
                }
            }
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

    fun showFragmentForSelectedNavigationItem(itemId: Int): Boolean =
        when (itemId) {
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

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_settings -> {
            //TODO should be startActivityForResult
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}