package de.tap.easy_xkcd.comicBrowsing

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Vibrator
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
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.tap.xkcd_reader.R
import com.tap.xkcd_reader.databinding.PagerLayoutBinding
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.ComicBaseAdapter
import de.tap.easy_xkcd.ComicViewHolder
import de.tap.easy_xkcd.CustomTabHelpers.BrowserFallback
import de.tap.easy_xkcd.CustomTabHelpers.CustomTabActivityHelper
import de.tap.easy_xkcd.GlideApp
import de.tap.easy_xkcd.database.Comic
import de.tap.easy_xkcd.database.RealmComic
import de.tap.easy_xkcd.fragments.ImmersiveDialogFragment
import de.tap.easy_xkcd.fragments.comics.FavoritesFragment
import de.tap.easy_xkcd.mainActivity.ComicDatabaseViewModel
import de.tap.easy_xkcd.mainActivity.MainActivity
import de.tap.easy_xkcd.misc.HackyViewPager
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.ThemePrefs
import de.tap.easy_xkcd.utils.observe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*

@AndroidEntryPoint
abstract class ComicBrowserBaseFragment : Fragment() {
    private var _binding: PagerLayoutBinding? = null
    protected val binding get() = _binding!!

    protected lateinit var pager: ViewPager2
    protected lateinit var adapter: ComicBrowserBaseAdapter

    protected lateinit var prefHelper: PrefHelper
    protected lateinit var themePrefs: ThemePrefs

    protected abstract val model: ComicBrowserBaseViewModel

    protected var comicNumberOfSharedElementTransition : Int? = null

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
        pager.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                model.comicSelected(position)
            }
        })

        model.selectedComicNumber.observe(viewLifecycleOwner) {
            (activity as AppCompatActivity).supportActionBar?.subtitle = it?.toString()
        }

        arguments?.let { args ->
            if (args.containsKey(MainActivity.ARG_COMIC_TO_SHOW)) {
                model.jumpToComic(args.getInt(MainActivity.ARG_COMIC_TO_SHOW))
            }

            // Prepare for shared element transition
            if (savedInstanceState == null && args.getBoolean(
                    MainActivity.ARG_TRANSITION_PENDING,
                    false
                )
            ) {
                comicNumberOfSharedElementTransition = model.selectedComicNumber.value
                postponeEnterTransition()
                sharedElementEnterTransition = TransitionInflater.from(context)
                    .inflateTransition(R.transition.image_shared_element_transition)
            }

        }

        return binding.root
    }

    // Used by the MainActivity for passing the view to the OverviewFragment
    fun getSharedElementsForTransitionToOverview() : List<View?> {
        val underlyingRecyclerView = pager.getChildAt(0) as? RecyclerView?
        val holder = underlyingRecyclerView?.findViewHolderForAdapterPosition(pager.currentItem) as? ComicViewHolder?
        return listOf(
            holder?.title,
            holder?.image
        )
    }

    open inner class ComicBrowserBaseAdapter : ComicBaseAdapter<ComicBrowserBaseAdapter.ComicBrowserViewHolder>(
        this,
        requireActivity(),
        comicNumberOfSharedElementTransition
    ) {
        override fun startPostponedTransitions() {
            startPostponedEnterTransition()
            activity?.startPostponedEnterTransition()
        }

        override fun onImageLoaded(image: ImageView, bitmap: Bitmap, comic: Comic) {
            if (comicNumberOfSharedElementTransition == null) {
                image.alpha = 0f
                image.animate().alpha(1f).duration = 300
            }
        }

        inner class LongAndDoubleTapListener(
            private val image: PhotoView,
            private val comic: Comic
        ) : GestureDetector.OnDoubleTapListener, View.OnLongClickListener {
            private var fingerLifted = true

            override fun onDoubleTapEvent(e: MotionEvent?): Boolean {
                if (e?.action == MotionEvent.ACTION_UP) fingerLifted = true
                if (e?.action == MotionEvent.ACTION_DOWN) fingerLifted = false
                return false
            }

            override fun onDoubleTap(e: MotionEvent?): Boolean {
                if (prefHelper.doubleTapToFavorite()) {
                    toggleFavorite()
                    (activity?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator?)?.vibrate(100)
                } else {
                    when {
                        image.scale < 0.7f * image.maximumScale -> {
                            image.setScale(0.7f * image.maximumScale, true)
                        }
                        else -> {
                            image.setScale(1.0f, true)
                        }
                    }
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                if (RealmComic.isInteractiveComic(comic.number, activity)) {
                    openInBrowser(comic)
                } else if (prefHelper.fullscreenModeEnabled()) {
                    (activity as? MainActivity?)?.toggleFullscreen()
                }
                return false
            }

            override fun onLongClick(p0: View?): Boolean {
                if (fingerLifted) {
                    if (prefHelper.altVibration()) {
                        (activity?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator?)?.vibrate(10)
                    }

                    showAltText()
                }
                return true
            }
        }

        override fun onBindViewHolder(holder: ComicBrowserViewHolder, position: Int) {
            if (Arrays.binarySearch(
                    requireActivity().resources.getIntArray(R.array.large_comics),
                    position + 1
                ) >= 0
            ) {
                holder.image.maximumScale = 15.0f
            }

            if (prefHelper.scrollDisabledWhileZoom() && prefHelper.defaultZoom()) {
                holder.image.setOnMatrixChangeListener {
                    pager.isUserInputEnabled = holder.image.scale <= 1.4
                }
            }

            comics[position].comic?.let { comic ->
                LongAndDoubleTapListener(holder.image, comic).let {
                    holder.image.setOnDoubleTapListener(it)
                    holder.image.setOnLongClickListener(it)
                }
            }

            super.onBindViewHolder(holder, position)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ComicBrowserViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.pager_item, parent, false)
        )

        inner class ComicBrowserViewHolder(view: View) : ComicViewHolder(view) {
            override val title: TextView = itemView.findViewById(R.id.tvTitle)
            override val altText: TextView = itemView.findViewById(R.id.tvAlt)
            override val number: TextView? = null
            override val image: PhotoView = itemView.findViewById(R.id.ivComic)

            init {
                if (themePrefs.nightThemeEnabled()) {
                    title.setTextColor(Color.WHITE)
                }

                if (!prefHelper.defaultZoom()) {
                    image.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    image.maximumScale = 10f
                }
            }
        }
    }

    /*inner class ComicPagerAdapter constructor(
        private val comics: List<Comic>,
    ) : PagerAdapter() {

        override fun getCount(): Int = comics.size
        override fun isViewFromObject(view: View, obj: Any): Boolean = view == obj

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            container.removeView(obj as RelativeLayout)
        }

        inner class LongAndDoubleTapListener(
            private val pvComic: PhotoView,
            private val comic: Comic
        ) : GestureDetector.OnDoubleTapListener, View.OnLongClickListener {
            private var fingerLifted = true

            override fun onDoubleTapEvent(e: MotionEvent?): Boolean {
                if (e?.action == MotionEvent.ACTION_UP) fingerLifted = true
                if (e?.action == MotionEvent.ACTION_DOWN) fingerLifted = false
                return false
            }

            override fun onDoubleTap(e: MotionEvent?): Boolean {
                if (prefHelper.doubleTapToFavorite()) {
                    toggleFavorite()
                    (activity?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator?)?.vibrate(100)
                } else {
                    when {
                        pvComic.scale < 0.5f * pvComic.maximumScale -> {
                            pvComic.setScale(0.5f * pvComic.maximumScale, true)
                        }
                        pvComic.scale < pvComic.maximumScale -> {
                            pvComic.setScale(pvComic.maximumScale, true)
                        }
                        else -> {
                            pvComic.setScale(1.0f, true)
                        }
                    }
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                if (RealmComic.isInteractiveComic(comic.comicNumber, activity)) {
                    openInBrowser(comic)
                } else if (prefHelper.fullscreenModeEnabled()) {
                    (activity as? MainActivity?)?.toggleFullscreen()
                }
                return false
            }

            override fun onLongClick(p0: View?): Boolean {
                if (fingerLifted) {
                    if (prefHelper.altVibration()) {
                        (activity?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator?)?.vibrate(10)
                    }

                    showAltText()
                }
                return true
            }
        }

        @SuppressLint("SetTextI18n")
        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val view = LayoutInflater.from(activity).inflate(R.layout.pager_item, container, false)
            view.tag = position

            val comic = comics[position]

            val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            val pvComic: PhotoView = view.findViewById(R.id.ivComic)

            tvTitle.text = (if (prefHelper.subtitleEnabled()) "" else comic.comicNumber
                .toString() + ": ") + Html.fromHtml(
                RealmComic.getInteractiveTitle(comic, activity)
            )

            // Transition names used for shared element transitions to the Overview Fragment
            tvTitle.transitionName = comic.number.toString()
            pvComic.transitionName = "im" + comic.number

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
                tvTitle.setTextColor(Color.WHITE)
            }

            if (!prefHelper.defaultZoom()) {
                pvComic.scaleType = ImageView.ScaleType.CENTER_INSIDE
                pvComic.maximumScale = 10f
            }

            if (themePrefs.invertColors(false)) {
                pvComic.colorFilter = themePrefs.negativeColorFilter
            }

            if (prefHelper.scrollDisabledWhileZoom() && prefHelper.defaultZoom()) {
                pvComic.setOnMatrixChangeListener {
                    pager.setLocked(pvComic.scale > 1.4)
                }
            }

            LongAndDoubleTapListener(pvComic, comic).let {
                pvComic.setOnDoubleTapListener(it)
                pvComic.setOnLongClickListener(it)
            }

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
            if (transitionPending && comicNumber == model.selectedComic.value?.number) {
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
    }*/

    fun getDisplayedComic(): Comic? = model.selectedComic.value

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        MenuCompat.setGroupDividerEnabled(menu, true)
        super.onCreateOptionsMenu(menu, inflater)
    }

    fun toggleFavorite() {
        if (prefHelper.isOnline(activity) || prefHelper.fullOfflineEnabled()) {
            model.toggleFavorite()
        } else {
            Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAltText(fromMenu: Boolean = false): Boolean {
        //Show alt text
        getDisplayedComic()?.let { comic ->
            ImmersiveDialogFragment.getInstance(Html.fromHtml(Html.escapeHtml(comic.altText)).toString()).apply {
                if (fromMenu) {
                    dismissListener = ImmersiveDialogFragment.DismissListener {
                        //If the user selected the menu item for the first time, show the toast
                        if (prefHelper.showAltTip()) {
                            Snackbar.make(
                                requireActivity().findViewById(android.R.id.content),
                                R.string.action_alt_tip,
                                Snackbar.LENGTH_LONG
                            ).setAction(R.string.got_it) { prefHelper.setShowAltTip(false) }
                                .show()
                        }
                    }
                }
            }.showImmersive(requireActivity() as AppCompatActivity)
        }
        return true
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
            R.id.action_alt -> {
                showAltText(fromMenu = true)
                true
            }
            R.id.action_favorite -> {
                toggleFavorite()
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
                openRedditThread()
                true
            }
            R.id.action_boomark -> {
                lifecycleScope.launch {
                    model.setBookmark()

                    Toast.makeText(
                        activity,
                        if (prefHelper.bookmark == 0) R.string.bookmark_toast else R.string.bookmark_toast_2,
                        Toast.LENGTH_LONG
                    ).show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun openRedditThread() {
        lifecycleScope.launch {
            //TODO model should show the progress here

            model.getRedditThread()?.let { url ->
                withContext(Dispatchers.Main) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            } ?: run {
                Toast.makeText(
                    activity,
                    resources.getString(R.string.thread_not_found),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun openInBrowser(comic: Comic) {
        // We open the mobile site (m.xkcd.com) by default
        // For interactive comics we use the desktop since it has better support for some interactive comics

        activity?.let { activity ->
            val intent = Intent(
                Intent.ACTION_VIEW, Uri.parse(
                    "https://"
                            + (if (RealmComic.isInteractiveComic(
                            comic.number,
                            activity
                        )
                    ) "" else "m.")
                            + "xkcd.com/" + comic.number
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

    private fun explainComic(comic: Comic) {
        val url = "https://explainxkcd.com/${comic.number}"
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

    private fun shareComicImage(comic: Comic) {
        lifecycleScope.launch {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, model.getUriForSharing(comic.number))
                putExtra(Intent.EXTRA_SUBJECT, comic.title)
            }

            var extraText = comic.title
            if (prefHelper.shareAlt()) {
                extraText += "\n" + comic.altText
            }
            if (prefHelper.includeLink()) {
                extraText += "https://${if (prefHelper.shareMobile()) "m." else ""}xkcd.com/${comic.number}/"
            }
            share.putExtra(Intent.EXTRA_TEXT, extraText)

            startActivity(Intent.createChooser(share, resources.getString(R.string.share_image)))
        }
    }

    private fun shareComicUrl(comic: Comic) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, comic.title)
            putExtra(
                Intent.EXTRA_TEXT,
                " https://" + (if (prefHelper.shareMobile()) "m." else "") + "xkcd.com/" + comic.number + "/"
            )
        }, resources.getString(R.string.share_url)))
    }

    protected fun showTranscript(comic: Comic) {
        activity?.let {
            androidx.appcompat.app.AlertDialog.Builder(it)
                .setMessage(if (comic.number >= 1675) resources.getString(R.string.no_transcript) else comic.transcript)
                .show()
        }
    }
}