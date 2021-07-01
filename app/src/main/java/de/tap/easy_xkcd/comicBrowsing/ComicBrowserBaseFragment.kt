package de.tap.easy_xkcd.comicBrowsing

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.transition.TransitionInflater
import android.view.*
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.MenuCompat
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.github.chrisbanes.photoview.PhotoView
import com.tap.xkcd_reader.R
import com.tap.xkcd_reader.databinding.PagerLayoutBinding
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.CustomTabHelpers.BrowserFallback
import de.tap.easy_xkcd.CustomTabHelpers.CustomTabActivityHelper
import de.tap.easy_xkcd.GlideApp
import de.tap.easy_xkcd.database.RealmComic
import de.tap.easy_xkcd.mainActivity.ComicDatabaseViewModel
import de.tap.easy_xkcd.mainActivity.MainActivity
import de.tap.easy_xkcd.misc.HackyViewPager
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.ThemePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*

@AndroidEntryPoint
abstract class ComicBrowserBaseFragment : Fragment() {
    private var _binding: PagerLayoutBinding? = null
    protected val binding get() = _binding!!

    protected lateinit var pager: HackyViewPager

    protected lateinit var prefHelper: PrefHelper
    protected lateinit var themePrefs: ThemePrefs

    protected val databaseModel: ComicDatabaseViewModel by activityViewModels()

    protected abstract val model: ComicBrowserBaseViewModel

    private var transitionPending = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = PagerLayoutBinding.inflate(inflater, container, false)

        setHasOptionsMenu(true)

        prefHelper = PrefHelper(activity)
        themePrefs = ThemePrefs(activity)

        pager = binding.pager
        pager.offscreenPageLimit = 2
        pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
            }

            override fun onPageSelected(position: Int) {
                model.comicSelected(position)
            }

            override fun onPageScrollStateChanged(state: Int) {}
        })

        model.selectedComic.observe(viewLifecycleOwner) {
            (activity as AppCompatActivity).supportActionBar?.subtitle = it?.comicNumber?.toString()
        }

        arguments?.let { args ->
            // Prepare for shared element transition
            if (savedInstanceState == null && args.getBoolean(
                    MainActivity.ARG_TRANSITION_PENDING,
                    false
                )
            ) {
                transitionPending = true
                postponeEnterTransition()
                sharedElementEnterTransition = TransitionInflater.from(context)
                    .inflateTransition(R.transition.image_shared_element_transition)
            }

            if (args.containsKey(MainActivity.ARG_COMIC_TO_SHOW)) {
                model.jumpToComic(args.getInt(MainActivity.ARG_COMIC_TO_SHOW, prefHelper.lastComic))
            }
        }

        return binding.root
    }

    fun getSharedElementsForTransitionToOverview() : List<View?> {
        val view: View? = pager.findViewWithTag(pager.currentItem)
        return listOf(
            view?.findViewById(R.id.tvTitle),
            view?.findViewById(R.id.ivComic)
        )
    }

    inner class ComicPagerAdapter constructor(
        private val comics: List<RealmComic>,
    ) : PagerAdapter() {

        override fun getCount(): Int = comics.size
        override fun isViewFromObject(view: View, obj: Any): Boolean = view == obj

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            container.removeView(obj as RelativeLayout)
        }

        @SuppressLint("SetTextI18n")
        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val view = LayoutInflater.from(activity).inflate(R.layout.pager_item, container, false)
            view.tag = position

            val comic = comics[position]

            val tvAlt: TextView = view.findViewById(R.id.tvAlt)
            val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            val pvComic: PhotoView = view.findViewById(R.id.ivComic)

            tvAlt.text =
                Html.fromHtml(Html.escapeHtml(comic.altText)) //TODO Get rid of the legacy alt text display
            tvTitle.text = (if (prefHelper.subtitleEnabled()) "" else comic.comicNumber
                .toString() + ": ") + Html.fromHtml(
                RealmComic.getInteractiveTitle(comic, activity)
            )

            // Transition names used for shared element transitions to the Overview Fragment
            tvTitle.transitionName = comic.comicNumber.toString()
            pvComic.transitionName = "im" + comic.comicNumber

            activity?.let {
                if (Arrays.binarySearch(
                        it.resources.getIntArray(R.array.large_comics),
                        position + 1
                    ) >= 0
                ) {
                    pvComic.maximumScale = 15.0f
                }
            }

            if (themePrefs.nightThemeEnabled()) {
                tvAlt.setTextColor(Color.WHITE)
                tvTitle.setTextColor(Color.WHITE)
            }

            if (!prefHelper.defaultZoom()) {
                pvComic.scaleType = ImageView.ScaleType.CENTER_INSIDE
                pvComic.maximumScale = 10f
            }

            //TODO setup tap/long tap/double tap listeners
            GlideApp.with(this@ComicBrowserBaseFragment)
                .asBitmap()
                .apply(RequestOptions().placeholder(makeProgressDrawable()))
                .apply {
                    if (comic.isOffline || comic.isFavorite) load(
                        RealmComic.getOfflineBitmap(
                            comic.comicNumber,
                            context,
                            prefHelper
                        )
                    ) else load(comic.url)
                }
                .listener(object : RequestListener<Bitmap?> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Bitmap?>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        postImageLoaded(comic.comicNumber)
                        return false
                    }

                    override fun onResourceReady(
                        resource: Bitmap?,
                        model: Any,
                        target: Target<Bitmap?>,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        resource?.let {
                            setupPhotoViewWhenImageLoaded(pvComic, resource, comic)
                            postImageLoaded(comic.comicNumber)
                        }
                        return false
                    }
                }).into(pvComic)

            container.addView(view)
            return view
        }

        private fun makeProgressDrawable() = CircularProgressDrawable(requireActivity()).apply {
            strokeWidth = 5.0f
            centerRadius = 100.0f
            setColorSchemeColors(if (themePrefs.nightThemeEnabled()) themePrefs.accentColorNight else themePrefs.accentColor)
            start()
        }

        fun postImageLoaded(comicNumber: Int) {
            if (transitionPending && comicNumber == model.selectedComic.value?.comicNumber) {
                startPostponedEnterTransition()
                activity?.startPostponedEnterTransition()
                transitionPending = false
            }
        }

        fun setupPhotoViewWhenImageLoaded(photoView: PhotoView, bitmap: Bitmap, comic: RealmComic) {
            if (themePrefs.invertColors(false) && themePrefs.bitmapContainsColor(
                    bitmap,
                    comic.comicNumber
                )
            ) photoView.clearColorFilter()

            if (!transitionPending) {
                photoView.alpha = 0f
                photoView.animate().alpha(1f).duration = 300
            }
        }
    }

    fun getDisplayedComic(): RealmComic? = model.selectedComic.value

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        MenuCompat.setGroupDividerEnabled(menu, true)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.action_share -> {
                AlertDialog.Builder(activity).setItems(R.array.share_dialog) { _, which ->
                    getDisplayedComic()?.let {
                        when (which) {
                            0 -> lifecycleScope.launch { shareComicImage(it) }
                            1 -> shareComicUrl(it)
                        }
                    }
                }.create().show()
                true
            }
            R.id.action_favorite -> {
                if (prefHelper.isOnline(activity) || prefHelper.fullOfflineEnabled()) {
                    model.toggleFavorite()
                } else {
                    Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.action_trans -> {
                getDisplayedComic()?.let { showTranscript(it) }
                true
            }
            R.id.action_explain -> {
                getDisplayedComic()?.let { explainComic(it) }
                true
            }
            R.id.action_browser -> {
                getDisplayedComic()?.let { openInBrowser(it) }
                true
            }
            R.id.action_thread -> {
                getDisplayedComic()?.let { openRedditThread(it) }
                true
            }
            R.id.action_boomark -> {
                Toast.makeText(
                    activity,
                    if (prefHelper.bookmark == 0) R.string.bookmark_toast else R.string.bookmark_toast_2,
                    Toast.LENGTH_LONG
                ).show()
                model.setBookmark()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun openRedditThread(comic: RealmComic) {
        lifecycleScope.launch {
            //TODO model should show the progress here
            val url = databaseModel.getRedditThread(comic)

            withContext(Dispatchers.Main) {
                if (url == "" && activity != null) {
                    Toast.makeText(
                        activity,
                        resources.getString(R.string.thread_not_found),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
        }
    }

    private fun openInBrowser(comic: RealmComic) {
        // We open the mobile site (m.xkcd.com) by default
        // For interactive comics we use the desktop since it has better support for some interactive comics

        activity?.let { activity ->
            val intent = Intent(
                Intent.ACTION_VIEW, Uri.parse(
                    "https://"
                            + (if (RealmComic.isInteractiveComic(
                            comic.comicNumber,
                            activity
                        )
                    ) "" else "m.")
                            + "xkcd.com/" + comic.comicNumber
                )
            )

            // Since the app also handles xkcd intents, we need to exxlude it from the intent chooser
            // Code adapted from https://codedogg.wordpress.com/2018/11/09/how-to-exclude-your-own-activity-from-activity-startactivityintent-chooser/
            val packageManager = activity.packageManager
            val possibleIntents: MutableList<Intent> = ArrayList()

            val possiblePackageNames: MutableSet<String> = HashSet()
            for (resolveInfo in packageManager.queryIntentActivities(intent, 0)) {
                val packageName = resolveInfo.activityInfo.packageName
                if (packageName != activity.packageName) {
                    val possibleIntent = Intent(intent)
                    possibleIntent.setPackage(resolveInfo.activityInfo.packageName)
                    possiblePackageNames.add(resolveInfo.activityInfo.packageName)
                    possibleIntents.add(possibleIntent)
                }
            }

            val defaultResolveInfo = packageManager.resolveActivity(intent, 0)

            if (defaultResolveInfo == null || possiblePackageNames.isEmpty()) {
                Timber.e("No browser found!")
                return
            }

            // If there is a default app to handle the intent (which is not this app), use it.
            if (possiblePackageNames.contains(defaultResolveInfo.activityInfo.packageName)) {
                activity.startActivity(intent)
            } else { // Otherwise, let the user choose.
                val intentChooser = Intent.createChooser(
                    possibleIntents.removeAt(0),
                    activity.resources.getString(R.string.chooser_title)
                )
                intentChooser.putExtra(
                    Intent.EXTRA_INITIAL_INTENTS,
                    possibleIntents.toTypedArray()
                )
                activity.startActivity(intentChooser)
            }
        }
    }

    private fun explainComic(comic: RealmComic) {
        val url = "https://explainxkcd.com/${comic.comicNumber}"
        if (prefHelper.useCustomTabs()) {
            CustomTabActivityHelper.openCustomTab(
                activity,
                CustomTabsIntent.Builder()
                    .setToolbarColor(themePrefs.getPrimaryColor(false))
                    .build(),
                Uri.parse(url),
                BrowserFallback()
            )
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun shareComicImage(comic: RealmComic) {
        lifecycleScope.launch {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, databaseModel.getUriForSharing(comic))
                putExtra(Intent.EXTRA_SUBJECT, comic.title)
            }

            var extraText = comic.title
            if (prefHelper.shareAlt()) {
                extraText += "\n" + comic.altText
            }
            if (prefHelper.includeLink()) {
                extraText += "https://${if (prefHelper.shareMobile()) "m." else ""}xkcd.com/${comic.comicNumber}/"
            }
            share.putExtra(Intent.EXTRA_TEXT, extraText)

            startActivity(Intent.createChooser(share, resources.getString(R.string.share_image)))
        }
    }

    private fun shareComicUrl(comic: RealmComic) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, comic.title)
            putExtra(
                Intent.EXTRA_TEXT,
                " https://" + (if (prefHelper.shareMobile()) "m." else "") + "xkcd.com/" + comic.comicNumber + "/"
            )
        }, resources.getString(R.string.share_url)))
    }

    protected fun showTranscript(comic: RealmComic) {
        activity?.let {
            androidx.appcompat.app.AlertDialog.Builder(it)
                .setMessage(if (comic.comicNumber >= 1675) resources.getString(R.string.no_transcript) else comic.transcript)
                .show()
        }
    }
}