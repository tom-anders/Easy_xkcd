package de.tap.easy_xkcd.comicBrowsing

import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.tap.xkcd_reader.R
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.GlideApp
import de.tap.easy_xkcd.GlideRequest
import de.tap.easy_xkcd.database.RealmComic
import timber.log.Timber

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

        pager.adapter = ComicBrowserAdapter(model.comics)
        model.selectedComic.value?.let {
            pager.currentItem = it.comicNumber - 1
        }

        activity?.findViewById<FloatingActionButton>(R.id.fab)?.apply {
            //TODO add back tooltip about the longpress when the button is pressed the first time
            setOnClickListener { pager.setCurrentItem(model.getNextRandomComic() - 1, false) }

            setOnLongClickListener {
                model.getPreviousRandomComic()?.let {
                    pager.setCurrentItem(it - 1, false)
                }
                true
            }
        }

        model.selectedComic.observe(viewLifecycleOwner) {
            (activity as AppCompatActivity).supportActionBar?.subtitle = it?.comicNumber?.toString()
        }

        return view
    }

    override fun pageSelected(position: Int) {
        model.comicSelected(position + 1)
    }

    override fun getDisplayedComic(): RealmComic? = model.selectedComic.value

    inner class ComicBrowserAdapter(comics: List<RealmComic>) : ComicBaseAdapter(comics) {
        override fun addLoadToRequest(
            request: GlideRequest<Bitmap>,
            comic: RealmComic
        ) = if (comic.isOffline) request.load(
            RealmComic.getOfflineBitmap(
                comic.comicNumber,
                context,
                prefHelper
            )
        ) else request.load(comic.url)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_favorite -> {
                model.toggleFavorite()
                true
            }
            R.id.action_latest -> {
                pager.setCurrentItem(model.comics.size - 1, false)
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
        model.isFavorite.observe(viewLifecycleOwner) {
            menu.findItem(R.id.action_favorite).apply {
                setIcon(if (it) R.drawable.ic_favorite_on_24dp else R.drawable.ic_favorite_off_24dp)
                setTitle(if (it) R.string.action_favorite_remove else R.string.action_favorite)
            }
        }

        menu.findItem(R.id.action_alt).isVisible = prefHelper.showAltTip()
        menu.findItem(R.id.action_browser).isVisible = true

        super.onPrepareOptionsMenu(menu)
    }
}