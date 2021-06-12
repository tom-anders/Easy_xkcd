package de.tap.easy_xkcd.comicBrowsing

import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import androidx.core.view.MenuCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.github.chrisbanes.photoview.PhotoView
import com.tap.xkcd_reader.R
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.GlideApp
import de.tap.easy_xkcd.database.RealmComic
import timber.log.Timber

@AndroidEntryPoint
class ComicBrowserFragment : ComicBrowserBaseFragment() {
    // Scoping this to the activity makes sure that the current comic is saved when the app is killed
    private val model: ComicBrowserViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        pager.adapter = ComicBrowserAdapter(model.comics)
        pager.currentItem = model.selectedComic - 1

        return view
    }

    override fun pageSelected(position: Int) {
        model.comicSelected(position + 1)
    }

    inner class ComicBrowserAdapter(comics: List<RealmComic>) : ComicBaseAdapter(comics) {
        override fun loadComicImage(comic: RealmComic, photoView: PhotoView) {
            GlideApp.with(this@ComicBrowserFragment)
                .asBitmap()
                .apply(RequestOptions().placeholder(makeProgressDrawable()))
                .load(comic.url)
                .listener(object : RequestListener<Bitmap?> {
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
                            setupPhotoViewWhenImageLoaded(photoView, resource, comic)
                            postImageLoaded(comic.comicNumber)
                        }
                        return false
                    }
                })
                .into(photoView)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_favorite -> {
                model.toggleFavorite()
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