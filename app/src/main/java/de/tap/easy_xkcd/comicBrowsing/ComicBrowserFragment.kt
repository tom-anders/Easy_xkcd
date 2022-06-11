package de.tap.easy_xkcd.comicBrowsing

import android.os.Bundle
import android.view.*
import androidx.core.view.MenuCompat
import androidx.fragment.app.activityViewModels
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.tap.xkcd_reader.R
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.mainActivity.MainActivity
import de.tap.easy_xkcd.utils.observe

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

            override fun onOfflineImageMissing(number: Int) {
                model.downloadMissingOfflineBitmap(number)
            }
        }.also { pager.adapter = it }

        model.comics.observe(viewLifecycleOwner) { newList ->
            // For the comic browser, this should only happen once on start up when the initial list
            // is received and then again if at least one new comic was found
            // If the size stays the same, it means a comic has been cached in
            // the database, which we'll notice via model.comicCached.
            // This way we don't have to replace the whole list and save performance
            if (newList.size != adapter.comics.size) {
                adapter.comics = newList.toMutableList()
                adapter.notifyDataSetChanged()

                // Restores position after rotation
                model.selectedComicNumber.value?.let { selectedNumber ->
                    if (pager.currentItem != selectedNumber - 1) {
                        pager.setCurrentItem(selectedNumber - 1, false)
                    }
                }
            }
        }

        model.comicCached.observe(viewLifecycleOwner) { comic ->
            pager.post {
                // When migrating the old Realm database, this would lead to crashes when onComicNull()
                // would be called in onBindViewHolder, but when repository.cacheComic() would be called,
                // the comic was already in the database, so comicCached would be emitted while
                // the recyclerview is still computing its layout, leading to IllegalStateException.
                // So use post() to make sure the RV is ready
                adapter.updateComic(comic.number - 1, comic)
            }
        }

        model.foundNewComic.observe(this) {
            (activity as? MainActivity?)?.let {
                Snackbar.make(
                    it.findViewById(R.id.fab),
                    resources.getString(R.string.new_comic),
                    4000
                )
                    .setAction(resources.getString(R.string.new_comic_view)) {
                        pager.setCurrentItem(sharedPrefs.newestComic - 1, false)
                    }.show()
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
                pager.setCurrentItem(sharedPrefs.newestComic - 1, false)
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
            menu.findItem(R.id.action_favorite)?.apply {
                setIcon(if (it) R.drawable.ic_favorite_on_24dp else R.drawable.ic_favorite_off_24dp)
                setTitle(if (it) R.string.action_favorite_remove else R.string.action_favorite)
            }
        }

//        menu.findItem(R.id.action_alt)?.isVisible = prefHelper.showAltTip()

        super.onPrepareOptionsMenu(menu)
    }
}