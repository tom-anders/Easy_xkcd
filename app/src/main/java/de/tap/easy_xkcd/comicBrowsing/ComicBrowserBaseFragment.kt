package de.tap.easy_xkcd.comicBrowsing

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.text.Html
import android.view.*
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.MenuCompat
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.github.chrisbanes.photoview.PhotoView
import com.tap.xkcd_reader.R
import com.tap.xkcd_reader.databinding.PagerLayoutBinding
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.database.RealmComic
import de.tap.easy_xkcd.misc.HackyViewPager
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.ThemePrefs
import java.util.*

@AndroidEntryPoint
abstract class ComicBrowserBaseFragment: Fragment() {
    private var _binding: PagerLayoutBinding? = null
    protected val binding get() = _binding!!

    protected lateinit var pager: HackyViewPager

    protected lateinit var prefHelper: PrefHelper
    protected lateinit var themePrefs: ThemePrefs

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = PagerLayoutBinding.inflate(inflater, container, false)

        pager = binding.pager
        pager.offscreenPageLimit = 2
        pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

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

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        MenuCompat.setGroupDividerEnabled(menu, true)
        super.onCreateOptionsMenu(menu, inflater)
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

        abstract fun loadComicImage(comic: RealmComic, photoView: PhotoView)

        @SuppressLint("SetTextI18n")
        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val view = LayoutInflater.from(activity).inflate(R.layout.pager_item, container, false)
            view.tag = position

            val comic = comics[position]

            val tvAlt: TextView = view.findViewById(R.id.tvAlt)
            val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            val pvComic: PhotoView = view.findViewById(R.id.ivComic)

            tvAlt.text = Html.fromHtml(Html.escapeHtml(comic.altText)) //TODO Get rid of the legacy alt text display
            tvTitle.text = (if (prefHelper.subtitleEnabled()) "" else comic.comicNumber
                .toString() + ": ") + Html.fromHtml(RealmComic.getInteractiveTitle(comic, activity)
            )

            // Transition names used for shared element transitions to the Overview Fragment
            tvTitle.transitionName = comic.comicNumber.toString()
            pvComic.transitionName = "im" + comic.comicNumber

            activity?.let {
                if (Arrays.binarySearch(it.resources.getIntArray(R.array.large_comics), position + 1) >= 0) {
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

            loadComicImage(comic, pvComic)

            container.addView(view)
            return view
        }

        fun makeProgressDrawable() = CircularProgressDrawable(requireActivity()).apply {
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


}