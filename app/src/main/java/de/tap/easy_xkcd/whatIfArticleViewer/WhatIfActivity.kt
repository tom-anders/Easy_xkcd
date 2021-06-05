package de.tap.easy_xkcd.whatIfArticleViewer

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.FileUriExposedException
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.MenuCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.tap.xkcd_reader.R
import com.tap.xkcd_reader.databinding.ActivityWhatIfBinding
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.Activities.BaseActivity
import de.tap.easy_xkcd.misc.OnSwipeTouchListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@AndroidEntryPoint
class WhatIfActivity : BaseActivity() {
    companion object {
        const val INTENT_NUMBER = "number"
        private const val KEY_SCROLL_POSITION = "key_scroll_position"
    }

    val model: WhatIfArticleViewModel by viewModels()

    private lateinit var binding: ActivityWhatIfBinding

    private lateinit var progress: ProgressDialog

    // Animation to display when an article has been loaded
    var pendingAnimation: Animation? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWhatIfBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar(findViewById(R.id.toolbar))

        model.getTitle().observe(this, { supportActionBar?.subtitle = it })

        progress = ProgressDialog(this)
        progress.setTitle(resources.getString(R.string.loading_article))
        progress.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        progress.setCancelable(false)
        model.progressTextId().observe(this, {
            Timber.d("value: %d", it)
            if (it != null) {
                progress.setTitle(resources.getString(it))
                progress.show()
            } else {
                progress.dismiss()
            }
        })

        model.getArticleHtml().observe(this, {
            binding.web.loadDataWithBaseURL("file:///android_asset/.", it, "text/html", "UTF-8", null)

            pendingAnimation?.let {
                binding.web.startAnimation(pendingAnimation)
                binding.web.visibility = View.VISIBLE
            }
        })

        binding.web.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun performClick(alt: String?) {
                AlertDialog.Builder(this@WhatIfActivity).setMessage(alt).show()
            }
        }, "img")

        binding.web.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun performClick(n: String) {
                AlertDialog.Builder(this@WhatIfActivity)
                        .setMessage(Html.fromHtml(model.getRef(n)))
                        .show()
                        .findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance() //enable hyperlinks
            }
        }, "ref")

        binding.web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try {
                        startActivity(intent)
                    } catch (e: FileUriExposedException) {
                        Timber.e(e)
                    }
                } else {
                    startActivity(intent)
                }
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                savedInstanceState?.getInt(KEY_SCROLL_POSITION, 0)?.let {
                    binding.web.scrollY = it
                }

                if (prefHelper.showWhatIfTip()) {
                    Snackbar.make(findViewById(android.R.id.content), R.string.what_if_tip, BaseTransientBottomBar.LENGTH_LONG)
                            .setAction(R.string.got_it) { prefHelper.setShowWhatIfTip(false) }
                            .show()
                }
            }
        }

        binding.web.settings.builtInZoomControls = true
        binding.web.settings.useWideViewPort = true
        binding.web.settings.javaScriptEnabled = true
        binding.web.settings.displayZoomControls = false
        binding.web.settings.loadWithOverviewMode = true
        binding.web.settings.textZoom = prefHelper.getZoom(binding.web.settings.textZoom)

        binding.web.setOnTouchListener(object : OnSwipeTouchListener(this@WhatIfActivity) {
            override fun onSwipeRight() {
                if (prefHelper.swipeEnabled() && model.hasNextArticle().value == true) {
                    showPreviousArticle()
                }
            }

            override fun onSwipeLeft() {
                if (prefHelper.swipeEnabled() && model.hasPreviousArticle().value == true) {
                    showNextArticle()
                }
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_what_if, menu)
        menu.findItem(R.id.action_night_mode).isChecked = themePrefs.nightThemeEnabled()
        menu.findItem(R.id.action_swipe).isChecked = prefHelper.swipeEnabled()
        MenuCompat.setGroupDividerEnabled(menu, true)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        //TODO Only grey out and disable the button instead of hiding it completely?
        model.hasPreviousArticle().observe(this, {
            menu.findItem(R.id.action_back).isVisible = it
        })
        model.hasNextArticle().observe(this, {
            menu.findItem(R.id.action_next).isVisible = it
        })

        if (menu.findItem(R.id.action_swipe).isChecked) {
            menu.findItem(R.id.action_back).isVisible = false
            menu.findItem(R.id.action_next).isVisible = false
        }

        model.isFavorite().observe(this, {
            if (it)
                menu.findItem(R.id.action_favorite).setIcon(
                    ContextCompat.getDrawable(
                        this, R.drawable.ic_favorite_on_24dp
                    )
                ).setTitle(R.string.action_favorite_remove)
        })

        return super.onPrepareOptionsMenu(menu)
    }

    fun showNextArticle() {
        model.showNextArticle()
        binding.web.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_left))
        binding.web.visibility = View.INVISIBLE
        pendingAnimation = AnimationUtils.loadAnimation(this, R.anim.slide_in_right)
    }

    fun showPreviousArticle() {
        model.showPreviousArticle()
        binding.web.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_right))
        binding.web.visibility = View.INVISIBLE
        pendingAnimation = AnimationUtils.loadAnimation(this, R.anim.slide_in_left)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_next -> {
                showNextArticle()
                return true
            }
            R.id.action_back -> {
                showPreviousArticle()
                return true
            }
            R.id.action_night_mode -> {
                item.isChecked = !item.isChecked
                themePrefs.setNightThemeEnabled(item.isChecked)
                setResult(RESULT_OK)
                finish()
                startActivity(intent)
                return true
            }
            R.id.action_swipe -> {
                item.isChecked = !item.isChecked
                prefHelper.setSwipeEnabled(!prefHelper.swipeEnabled())

                // Next/Prev buttons are only visible if swipe is disabled, so reload the menu
                invalidateOptionsMenu()
                return true
            }
            R.id.action_browser -> {
                startActivity(model.openArticleInBrowser())
                return true
            }

            R.id.action_share -> {
                startActivity(model.shareArticle())
                return true
            }

            R.id.action_random -> {
                model.showRandomArticle()
                return true
            }

            R.id.action_favorite -> {
                model.toggleArticleFavorite()
                return true
            }

            R.id.action_thread -> {
                lifecycleScope.launch {
                    progress.setTitle(resources.getString(R.string.loading_thread))
                    progress.isIndeterminate = true
                    progress.show()

                    val url = model.getRedditThread()

                    withContext(Dispatchers.Main) {
                        progress.dismiss()
                        if (url == "") {
                            Toast.makeText(
                                this@WhatIfActivity,
                                resources.getString(R.string.thread_not_found),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    }
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        savedInstanceState.putInt(KEY_SCROLL_POSITION, binding.web.scrollY)
        super.onSaveInstanceState(savedInstanceState)
    }
}