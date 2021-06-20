package de.tap.easy_xkcd.comicBrowsing

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.tap.xkcd_reader.R
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.GlideRequest
import de.tap.easy_xkcd.database.RealmComic

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
            }
        }

        activity?.findViewById<FloatingActionButton>(R.id.fab)?.apply {
            setOnClickListener { pager.setCurrentItem(model.getRandomFavoriteIndex(), false) }
        }

        return view
    }
}