package de.tap.easy_xkcd.comicBrowsing

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.text.Html
import android.view.*
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.MenuCompat
import androidx.core.view.size
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
import de.tap.easy_xkcd.GlideApp
import de.tap.easy_xkcd.GlideRequest
import de.tap.easy_xkcd.database.RealmComic
import de.tap.easy_xkcd.mainActivity.ComicDatabaseViewModel
import de.tap.easy_xkcd.misc.HackyViewPager
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.ThemePrefs
import kotlinx.coroutines.launch
import java.util.*

@AndroidEntryPoint
abstract class ComicBrowserBaseFragment : Fragment() {
    private var _binding: PagerLayoutBinding? = null
    protected val binding get() = _binding!!

    protected lateinit var pager: HackyViewPager

    protected lateinit var prefHelper: PrefHelper
    protected lateinit var themePrefs: ThemePrefs

    protected val databaseModel: ComicDatabaseViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = PagerLayoutBinding.inflate(inflater, container, false)

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
                pageSelected(position)
            }

            override fun onPageScrollStateChanged(state: Int) {}
        })

        setHasOptionsMenu(true)

        prefHelper = PrefHelper(activity)
        themePrefs = ThemePrefs(activity)

        return binding.root
    }

    abstract fun pageSelected(position: Int)

    abstract inner class ComicBaseAdapter constructor(
        private val comics: List<RealmComic>,
    ) : PagerAdapter() {

        override fun getCount(): Int = comics.size
        override fun isViewFromObject(view: View, obj: Any): Boolean = view == obj

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            container.removeView(obj as RelativeLayout)
        }

        abstract fun addLoadToRequest(
            request: GlideRequest<Bitmap>,
            comic: RealmComic
        ): GlideRequest<Bitmap>

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

            addLoadToRequest(
                GlideApp.with(this@ComicBrowserBaseFragment)
                    .asBitmap()
                    .apply(RequestOptions().placeholder(makeProgressDrawable())),
                comic
            ).listener(object : RequestListener<Bitmap?> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any,
                    target: Target<Bitmap?>,
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
            //TODO
        }

        fun setupPhotoViewWhenImageLoaded(photoView: PhotoView, bitmap: Bitmap, comic: RealmComic) {
            if (themePrefs.invertColors(false) && themePrefs.bitmapContainsColor(
                    bitmap,
                    comic.comicNumber
                )
            ) photoView.clearColorFilter()

//            if (!transitionPending) {
            photoView.setAlpha(0f)
            photoView.animate()
                .alpha(1f)
                .setDuration(300)
//            }
        }
    }

    abstract fun getDisplayedComic(): RealmComic?

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
            else -> super.onOptionsItemSelected(item)
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

}