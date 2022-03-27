package de.tap.easy_xkcd.comicBrowsing

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.tap.xkcd_reader.R
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.Activities.CustomFilePickerActivity
import de.tap.easy_xkcd.utils.observe
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.net.URI

@AndroidEntryPoint
class FavoritesFragment : ComicBrowserBaseFragment() {
    override val model: FavoriteComicsViewModel by viewModels()

    private lateinit var exportFavoritesResult: ActivityResultLauncher<Intent>
    private lateinit var importFavoritesResult: ActivityResultLauncher<Intent>

    private var progress: ProgressDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        adapter = ComicBrowserBaseAdapter().also { pager.adapter = it }

        model.favorites.observe(viewLifecycleOwner) { newList ->
            // TODO if new list is empty, show some cute image and text explaining how to add them

            val diffResult = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
                override fun getOldListSize() = adapter.comics.size

                override fun getNewListSize() = newList.size

                // In the normal comic browser we could assume that the position of items in the list
                // didn't change, but here when a favorite is removed, they actually will.
                // So we need to compare the comic number here instead of the position
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int)
                        = adapter.comics[oldItemPosition]?.number == newList[newItemPosition].number

                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ): Boolean {
                    return adapter.comics[oldItemPosition] == newList[newItemPosition]
                }
            })

            adapter.comics = newList.toMutableList()
            diffResult.dispatchUpdatesTo(adapter)

            comicNumberOfSharedElementTransition?.let { model.selectComic(it) }
        }

        model.scrollToPage.observe(viewLifecycleOwner) {
            it?.let { pager.setCurrentItem(it, false) }
        }

        model.importingFavorites.observe(viewLifecycleOwner) {
            if (it == true) {
                progress = ProgressDialog(activity).apply {
                    isIndeterminate = true
                    setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
                    setTitle(R.string.pref_import_progress)
                    show()
                }
            } else {
                progress?.dismiss()
            }
        }

        activity?.findViewById<FloatingActionButton>(R.id.fab)?.apply {
            setOnClickListener { pager.setCurrentItem(model.getRandomFavoriteIndex(), false) }
        }

        exportFavoritesResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let {
                    if (model.exportFavorites(it)) {
                        Toast.makeText(activity, R.string.pref_export_success, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Timber.e("Choosing export file failed with result code ${result.resultCode}")
            }
        }

        importFavoritesResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let {
                    model.importFavorites(it)
                }
            } else {
                Timber.e("Choosing import file failed with result code ${result.resultCode}")
            }
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
            R.id.export_import_favorites -> {
                AlertDialog.Builder(activity).setItems(
                    R.array.export_import_dialog
                ) { _, which ->
                    when (which) {
                        0 -> {
                            exportFavoritesResult.launch(
                                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TITLE, "favorites.txt")
                                })
                        }
                        1 -> {
                            importFavoritesResult.launch(
                                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "text/plain"
                                })
                        }
                    }
                }.create().show()

                true
            }
            else -> super.onOptionsItemSelected(item)
        }
}