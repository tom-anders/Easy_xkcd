package de.tap.easy_xkcd.comicBrowsing

import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.tap.xkcd_reader.R
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.GlideRequest
import de.tap.easy_xkcd.database.RealmComic
import timber.log.Timber

@AndroidEntryPoint
class FavoritesFragment : ComicBrowserBaseFragment() {
    override val model: FavoriteComicsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        model.favorites.observe(viewLifecycleOwner) { favorites ->
            favorites?.let {
                pager.adapter = ComicPagerAdapter(favorites)

                //TODO if favorites is empty, show some cute image and text explaining how to add them
            }
        }

        model.scrollToPage.observe(viewLifecycleOwner) {
            it?.let { pager.setCurrentItem(it, false) }
        }

        activity?.findViewById<FloatingActionButton>(R.id.fab)?.apply {
            setOnClickListener { pager.setCurrentItem(model.getRandomFavoriteIndex(), false) }
        }

        return view
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_favorites_fragment, menu)
        menu.findItem(R.id.action_search)?.isVisible = false
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.action_favorite)?.setIcon(R.drawable.ic_favorite_on_24dp)
        menu.findItem(R.id.action_alt)?.isVisible = prefHelper.showAltTip()
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.delete_favorites -> {
                model.removeAllFavorites()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
}