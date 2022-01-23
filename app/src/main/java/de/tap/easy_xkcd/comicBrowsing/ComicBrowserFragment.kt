package de.tap.easy_xkcd.comicBrowsing

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.MenuCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.tap.xkcd_reader.R
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.ComicBaseAdapter
import de.tap.easy_xkcd.ComicViewHolder
import de.tap.easy_xkcd.GlideApp
import de.tap.easy_xkcd.database.Comic
import de.tap.easy_xkcd.mainActivity.MainActivity
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*

@AndroidEntryPoint
class ComicBrowserFragment : ComicBrowserBaseFragment() {
    // Scoping this to the activity makes sure that the current comic is saved when the app is killed
    override val model: ComicBrowserViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        adapter = object : ComicBrowserBaseAdapter() {
            override fun onComicNull(number: Int) {
                model.cacheComic(number)
            }
        }.also { pager.adapter = it }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.comics.collect { newList ->
                    val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                        override fun getOldListSize() = adapter.comics.size

                        override fun getNewListSize() = newList.size

                        // We're only caching comics that were null previously, so the positions
                        // should never change...
                        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int)
                                = oldItemPosition == newItemPosition

                        // ... and we can take the shortcut of just checking whether an item changed
                        // from null to not-null, instead of comparing the contents
                        override fun areContentsTheSame(
                            oldItemPosition: Int,
                            newItemPosition: Int
                        ): Boolean {
                            return adapter.comics[oldItemPosition].hasComic() == newList[newItemPosition].hasComic()
                        }
                    })

                    adapter.comics = newList

                    diffResult.dispatchUpdatesTo(adapter)

                    // Restores position after rotation
                    model.selectedComicNumber.value?.let { selectedNumber ->
                        if (pager.currentItem != selectedNumber - 1) {
                            pager.setCurrentItem(selectedNumber - 1, false)
                        }
                    }
                }
            }
        }

        activity?.findViewById<FloatingActionButton>(R.id.fab)?.apply {
            //TODO add back tooltip about the longpress when the button is pressed the first time
            setOnClickListener {
                model.getNextRandomComic()?.let {
                    pager.setCurrentItem(it - 1, false)
                }
            }

            setOnLongClickListener {
                model.getPreviousRandomComic()?.let {
                    pager.setCurrentItem(it - 1, false)
                }
                true
            }
        }

        return view
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_latest -> {
                pager.setCurrentItem(prefHelper.newest - 1, false)
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_comic_fragment, menu)
        MenuCompat.setGroupDividerEnabled(menu, true)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.isFavorite.collect {
                    menu.findItem(R.id.action_favorite)?.apply {
                        setIcon(if (it) R.drawable.ic_favorite_on_24dp else R.drawable.ic_favorite_off_24dp)
                        setTitle(if (it) R.string.action_favorite_remove else R.string.action_favorite)
                    }
                }
            }
        }

//        menu.findItem(R.id.action_alt)?.isVisible = prefHelper.showAltTip()

        super.onPrepareOptionsMenu(menu)
    }
}