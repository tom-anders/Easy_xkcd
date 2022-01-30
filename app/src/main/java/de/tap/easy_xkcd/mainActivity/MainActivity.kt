package de.tap.easy_xkcd.mainActivity

import android.app.ProgressDialog
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.RelativeLayout
import android.widget.SearchView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.tap.xkcd_reader.R
import com.tap.xkcd_reader.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.Activities.BaseActivity
import de.tap.easy_xkcd.Activities.NestedSettingsActivity
import de.tap.easy_xkcd.Activities.SearchResultsActivity
import de.tap.easy_xkcd.Activities.SettingsActivity
import de.tap.easy_xkcd.CustomTabHelpers.CustomTabActivityHelper
import de.tap.easy_xkcd.comicBrowsing.ComicBrowserBaseFragment
import de.tap.easy_xkcd.comicBrowsing.ComicBrowserFragment
import de.tap.easy_xkcd.comicBrowsing.ComicBrowserViewModel
import de.tap.easy_xkcd.comicBrowsing.FavoritesFragment
import de.tap.easy_xkcd.comicOverview.ComicOverviewFragment
import de.tap.easy_xkcd.database.ProgressStatus
import de.tap.easy_xkcd.utils.observe
import de.tap.easy_xkcd.whatIfOverview.WhatIfOverviewFragment
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding

    private val FRAGMENT_TAG = "MainActivityFragments"

    private lateinit var bottomAppBar: BottomAppBar
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var toolbar: Toolbar

    private var fullscreenEnabled = false

    private var customTabActivityHelper = CustomTabActivityHelper()
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>

    private val COMIC_NOTIFICATION_INTENT = "de.tap.easy_xkcd.ACTION_COMIC_NOTIFICATION"
    private val COMIC_INTENT = "de.tap.easy_xkcd.ACTION_COMIC"

    companion object {
        const val ARG_TRANSITION_PENDING = "transition_pending"
        const val ARG_COMIC_TO_SHOW = "comic_to_show"
        const val ARG_FROM_FAVORITES = "from_favorites"
    }

    val comicBrowserViewModel: ComicBrowserViewModel by viewModels()

    val dataBaseViewModel: MainActivityViewModel by viewModels()

    private lateinit var bottomNavigationListener: BottomNavigationListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        toolbar = binding.toolbar.root
        setupToolbar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        setupBottomAppBar()

        bottomNavigationView = binding.bottomNavigationView

        bottomNavigationListener = BottomNavigationListener(savedInstanceState)
        bottomNavigationView.setOnNavigationItemSelectedListener(bottomNavigationListener)

        // Nothing to be done yet in that case
        bottomNavigationView.setOnNavigationItemReselectedListener {}

        if (savedInstanceState == null) {
            bottomNavigationView.selectedItemId =
                if (prefHelper.launchToOverview()) R.id.nav_overview else R.id.nav_browser
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

    fun showComicFromOverview(toFavorites: Boolean, sharedElements: List<View?>, comicNumber: Int) {
        bottomNavigationListener.setTransitionPendingWithSharedElements(sharedElements)
        bottomNavigationListener.comicToShow = comicNumber
        bottomNavigationView.selectedItemId = if (toFavorites) R.id.nav_favorites else R.id.nav_browser
    }

    inner class BottomNavigationListener(
        val savedInstanceState: Bundle?,
    ) : BottomNavigationView.OnNavigationItemSelectedListener {

        private var transitionPending: Boolean = false
        var comicToShow: Int? = null

        private var pendingSharedElements: List<View?> = emptyList()

        fun setTransitionPendingWithSharedElements(sharedElements: List<View?>) {
            pendingSharedElements = sharedElements
            transitionPending = sharedElements.isNotEmpty()
        }

        init {
            if (savedInstanceState == null && intent != null) {
                when (intent.action) {
                    COMIC_INTENT -> {
                        if (intent.hasExtra(SearchResultsActivity.FROM_SEARCH)) {
                            // Needed for the shared element transition, will be resumed once the fragment
                            // has finished loading the comic image
                            postponeEnterTransition()

                            transitionPending = true
                        }
                        comicToShow = intent.getIntExtra("number", -1).let {
                            if (it == -1) null else it
                        }
                    }
                }
            }
        }

        override fun onNavigationItemSelected(item: MenuItem): Boolean {
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

        fun makeFragmentTransaction(fragment: Fragment): FragmentTransaction {
            fragment.arguments = Bundle().apply {
                if (transitionPending) {
                    transitionPending = false
                    putBoolean(ARG_TRANSITION_PENDING, true)
                }
                comicToShow?.let {
                    putInt(ARG_COMIC_TO_SHOW, it)
                    comicToShow = null
                }

                putBoolean(ARG_FROM_FAVORITES, supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) is FavoritesFragment)
            }

            return supportFragmentManager.beginTransaction().apply {
                setReorderingAllowed(true)
                pendingSharedElements.filterNotNull().map {
                    addSharedElement(it, it.transitionName)
                }
                pendingSharedElements = emptyList()
            }.replace(R.id.flContent, fragment, FRAGMENT_TAG)
        }

        fun showWhatIfFragment(): Boolean {
            supportActionBar?.title = resources.getString(R.string.nv_whatif)
            makeFragmentTransaction(WhatIfOverviewFragment()).commitAllowingStateLoss()
            return true
        }

        fun showComicOverviewFragment(): Boolean {
            val fragment =
                supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as? ComicBrowserBaseFragment
            comicToShow = fragment?.getDisplayedComic()?.number

            makeFragmentTransaction(
                ComicOverviewFragment()
            ).apply {
                fragment?.getSharedElementsForTransitionToOverview()
                    ?.filterNotNull()
                    ?.map {
                        addSharedElement(it, it.transitionName)
                    }
            }.commitAllowingStateLoss()

            return true
        }

        fun showFavoritesFragment(): Boolean {
            makeFragmentTransaction(FavoritesFragment()).commitAllowingStateLoss()
            return true
        }

        fun showComicBrowserFragment(): Boolean {
            makeFragmentTransaction(ComicBrowserFragment()).commitAllowingStateLoss()
            return true
        }

    }

    override fun onResume() {
        toolbar.title =
            bottomNavigationView.menu.findItem(bottomNavigationView.selectedItemId)?.title
        super.onResume()
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        menu.findItem(R.id.action_donate)?.isVisible = !prefHelper.hideDonate()
        menu.findItem(R.id.action_night_mode)?.isChecked = themePrefs.nightEnabledThemeIgnoreAutoNight()
        menu.findItem(R.id.action_night_mode)?.isVisible = !themePrefs.autoNightEnabled() && !themePrefs.useSystemNightTheme()

        val searchMenuItem = menu.findItem(R.id.action_search)
        val searchView = searchMenuItem?.actionView as SearchView?

        searchMenuItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(menuItem: MenuItem): Boolean {
                //Hide the other menu items to the right
                for (i in 0 until menu.size()) {
                    menu.getItem(i).isVisible = menu.getItem(i) === searchMenuItem
                }

                // Show keyboard
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?)?.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
                searchView?.requestFocus()
                return true
            }

            override fun onMenuItemActionCollapse(menuItem: MenuItem): Boolean {
                invalidateOptionsMenu() // Brings back the hidden menu items in onMenuItemActionExpand()
                hideKeyboard()
                return true
            }
        })

        (searchMenuItem?.actionView as SearchView?)?.apply {
            setSearchableInfo((getSystemService(Context.SEARCH_SERVICE) as SearchManager?)?.getSearchableInfo(componentName))
            isIconifiedByDefault = false
            setOnQueryTextListener(object: SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(p0: String?): Boolean {
                    searchMenuItem?.collapseActionView()
                    setQuery("", false)

                    hideKeyboard()

                    return false
                }

                override fun onQueryTextChange(query: String?) = false

            })
        }

        return true
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?)?.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
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

    fun toggleFullscreen() {
        fullscreenEnabled = !fullscreenEnabled

        animateViewForFullscreenToggle(toolbar, true)
        animateViewForFullscreenToggle(bottomAppBar, false)

        val newMargin = (if (fullscreenEnabled) 0 else bottomAppBar.height)
        (binding.flContent.layoutParams as RelativeLayout.LayoutParams?)?.bottomMargin = newMargin

    }

    private fun animateViewForFullscreenToggle(view: View, up: Boolean) {
        val sign = if (up) -1 else 1
        view.translationY = if (fullscreenEnabled) 0f else (sign * view.height).toFloat()
        view.animate().translationY(if (fullscreenEnabled) sign * view.height.toFloat() else 0f)
        view.visibility = if (fullscreenEnabled) View.GONE else View.VISIBLE
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