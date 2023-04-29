package de.tap.easy_xkcd.mainActivity

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tap.xkcd_reader.BuildConfig
import com.tap.xkcd_reader.R
import com.tap.xkcd_reader.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.Activities.BaseActivity
import de.tap.easy_xkcd.search.SearchActivity
import de.tap.easy_xkcd.CustomTabHelpers.CustomTabActivityHelper
import de.tap.easy_xkcd.comicBrowsing.ComicBrowserBaseFragment
import de.tap.easy_xkcd.comicBrowsing.ComicBrowserFragment
import de.tap.easy_xkcd.comicBrowsing.ComicBrowserViewModel
import de.tap.easy_xkcd.comicBrowsing.FavoritesFragment
import de.tap.easy_xkcd.comicOverview.ComicOverviewFragment
import de.tap.easy_xkcd.comicOverview.ComicOverviewViewModel
import de.tap.easy_xkcd.settings.BaseSettingsActivity
import de.tap.easy_xkcd.settings.SettingsActivity
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

    companion object {
        const val ARG_TRANSITION_PENDING = "transition_pending"
        const val ARG_COMIC_OR_ARTICLE_TO_SHOW = "comic_or_article_to_show"
        const val ARG_FROM_FAVORITES = "from_favorites"

        const val COMIC_INTENT = "de.tap.easy_xkcd.ACTION_COMIC"
    }

    val comicBrowserViewModel: ComicBrowserViewModel by viewModels()
    val comicOverviewViewModel: ComicOverviewViewModel by viewModels()

    val viewModel: MainActivityViewModel by viewModels()

    private lateinit var bottomNavigationListener: BottomNavigationListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        toolbar = binding.toolbar.root
        setupToolbar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        bottomNavigationView = binding.bottomNavigationView
        setupBottomAppBar()

        bottomNavigationListener = BottomNavigationListener(savedInstanceState)
        bottomNavigationView.setOnNavigationItemSelectedListener(bottomNavigationListener)

        // Nothing to be done yet in that case
        bottomNavigationView.setOnNavigationItemReselectedListener {}

        if (savedInstanceState == null) {
            if (bottomNavigationListener.comicOrArticleToShow is ComicOrArticleToShow.ShowArticle) {
                bottomNavigationView.selectedItemId = R.id.nav_whatif
            } else {
                bottomNavigationView.selectedItemId =
                    if (settings.launchToOverview) R.id.nav_overview else R.id.nav_browser
            }

            viewModel.onCreateWithNullSavedInstanceState()
        }

        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            when (it.resultCode) {
               BaseSettingsActivity.RESULT_RESTART_MAIN -> {
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
        bottomNavigationListener.comicOrArticleToShow = ComicOrArticleToShow.ShowComic(comicNumber)
        bottomNavigationView.selectedItemId = if (toFavorites) R.id.nav_favorites else R.id.nav_browser
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        bottomNavigationListener.handleNewIntent(intent)
    }

    sealed class ComicOrArticleToShow(val number: Int) {
        data class ShowComic(val n: Int) : ComicOrArticleToShow(n)
        data class ShowArticle(val n: Int) : ComicOrArticleToShow(n)
    }

    inner class BottomNavigationListener(
        val savedInstanceState: Bundle?,
    ) : BottomNavigationView.OnNavigationItemSelectedListener {

        private var transitionPending: Boolean = false
        var comicOrArticleToShow: ComicOrArticleToShow? = null

        private var pendingSharedElements: List<View?> = emptyList()

        fun setTransitionPendingWithSharedElements(sharedElements: List<View?>) {
            pendingSharedElements = sharedElements
            transitionPending = sharedElements.isNotEmpty()
        }

        init {
            if (savedInstanceState == null && intent != null) {
                when (intent.action) {
                    COMIC_INTENT -> {
                        if (intent.hasExtra(SearchActivity.FROM_SEARCH)) {
                            // Needed for the shared element transition, will be resumed once the fragment
                            // has finished loading the comic image
                            postponeEnterTransition()

                            transitionPending = true
                        }
                        comicOrArticleToShow = intent.getIntExtra("number", -1).let {
                            if (it == -1) null else ComicOrArticleToShow.ShowComic(it)
                        }
                    }
                    Intent.ACTION_VIEW -> {
                        intent.dataString?.let { url ->
                            val number = getNumberFromUrl(url)
                            if (!url.contains("what-if")) {
                                comicOrArticleToShow = ComicOrArticleToShow.ShowComic(number ?: sharedPrefs.newestComic)
                            } else if (number != null) {
                                comicOrArticleToShow = ComicOrArticleToShow.ShowArticle(number)
                            }
                        }
                    }
                }
            }
        }

        fun handleNewIntent(intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_VIEW -> {
                    intent.dataString?.let { url ->
                        getNumberFromUrl(url)?.let { number ->
                            if (url.contains("what-if")) {
                                comicOrArticleToShow = ComicOrArticleToShow.ShowArticle(number)
                                if (bottomNavigationView.selectedItemId == R.id.nav_whatif) {
                                    showWhatIfFragment()
                                } else {
                                    bottomNavigationView.selectedItemId = R.id.nav_whatif
                                }
                            } else {
                                comicOrArticleToShow = ComicOrArticleToShow.ShowComic(number)
                                if (bottomNavigationView.selectedItemId == R.id.nav_browser) {
                                    showComicBrowserFragment()
                                } else {
                                    bottomNavigationView.selectedItemId = R.id.nav_browser
                                }
                            }
                        }
                    }
                }
            }
        }

        private fun getNumberFromUrl(uri: String) = Uri.parse(uri).path?.replace("/", "")?.toIntOrNull()

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
                comicOrArticleToShow?.let {
                    putInt(ARG_COMIC_OR_ARTICLE_TO_SHOW, it.number)
                    comicOrArticleToShow = null
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
            comicOrArticleToShow = fragment?.getDisplayedComic()?.number?.let { ComicOrArticleToShow.ShowComic(it) }

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
            appTheme.amoledThemeEnabled() -> {
                bottomAppBar.backgroundTint = ColorStateList.valueOf(Color.BLACK)
            }
            appTheme.nightThemeEnabled -> {
                bottomAppBar.backgroundTint = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.background_material_dark)
                )
                toolbar.popupTheme = R.style.ThemeOverlay_AppCompat
            }
            else -> {
                bottomAppBar.backgroundTint =
                    ColorStateList.valueOf(appTheme.getPrimaryColor(false))
            }
        }

        ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(appTheme.accentColorNight, Color.WHITE)
        ).let {
            bottomNavigationView.itemIconTintList = it
            bottomNavigationView.itemTextColor = it
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        menu.findItem(R.id.action_donate)?.isVisible = !settings.hideDonate
        menu.findItem(R.id.action_night_mode)?.isChecked = appTheme.nightThemeEnabledIgnoreAutoNight()
        menu.findItem(R.id.action_night_mode)?.isVisible = !appTheme.useSystemNightTheme()

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
                override fun onQueryTextSubmit(text: String?): Boolean {
                    searchMenuItem?.collapseActionView()
                    setQuery("", false)

                    hideKeyboard()

                    // When user has entered a number, jump right to the comic
                    text?.toIntOrNull()?.let { number ->
                        if (number > 0 && number <= sharedPrefs.newestComic) {
                            bottomNavigationListener.comicOrArticleToShow =
                                ComicOrArticleToShow.ShowComic(number)
                            bottomNavigationListener.showComicBrowserFragment()
                            return true
                        }
                    }

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
        R.id.action_donate -> {
            when (BuildConfig.FLAVOR) {
                "googleplay" -> startActivity(Intent().setComponent(ComponentName(packageName, "$packageName.Activities.DonateActivity")))
                "fdroid" -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/donate/?hosted_button_id=9P95ACMNTXPM6")))
            }
            true
        }
        R.id.action_feedback -> {
            AlertDialog.Builder(this)
                .setItems(arrayOf("Email", "GitHub")) { _, i ->
                    when (i) {
                        0 -> {
                            startActivity(
                                Intent(
                                    Intent.ACTION_SENDTO,
                                    Uri.fromParts("mailto", "easyxkcd@gmail.com", null)
                                )
                            )
                        }
                        1 -> {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/tom-anders/Easy_xkcd/issues/new")))
                        }
                    }
                }
                .show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun toggleNightMode(item: MenuItem): Boolean {
        item.isChecked = !item.isChecked
        appTheme.nightThemeEnabled = item.isChecked

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
        if (settings.fullscreenModeAllowed) {
            fullscreenEnabled = !fullscreenEnabled

            animateViewForFullscreenToggle(toolbar, true)
            animateViewForFullscreenToggle(bottomAppBar, false)

            if (settings.hideFabInFullscreen) animateViewForFullscreenToggle(binding.fab, false)

            val newMargin = (if (fullscreenEnabled) 0 else bottomAppBar.height)
            (binding.flContent.layoutParams as RelativeLayout.LayoutParams?)?.bottomMargin =
                newMargin
        }
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

    override fun onBackPressed() {
        if (bottomNavigationView.selectedItemId == R.id.nav_favorites ||
            bottomNavigationView.selectedItemId == R.id.nav_browser
        ) {
            bottomNavigationView.selectedItemId = R.id.nav_overview
        } else {
            super.onBackPressed()
        }
    }

}