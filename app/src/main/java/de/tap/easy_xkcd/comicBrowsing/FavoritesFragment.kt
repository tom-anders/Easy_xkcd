package de.tap.easy_xkcd.comicBrowsing

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.tap.xkcd_reader.R
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.Activities.CustomFilePickerActivity
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

        model.favorites.observe(viewLifecycleOwner) { favorites ->
            favorites?.let {
                pager.adapter = ComicPagerAdapter(favorites)

                //TODO if favorites is empty, show some cute image and text explaining how to add them
            }
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