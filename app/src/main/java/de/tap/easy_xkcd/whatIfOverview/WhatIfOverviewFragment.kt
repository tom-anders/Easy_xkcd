package de.tap.easy_xkcd.whatIfOverview

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.tap.xkcd_reader.R
import com.tap.xkcd_reader.databinding.RecyclerLayoutBinding
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.database.whatif.Article
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.ThemePrefs
import de.tap.easy_xkcd.utils.observe
import de.tap.easy_xkcd.whatIfArticleViewer.WhatIfActivity
import io.realm.Case
import io.realm.Realm
import io.realm.Sort
import jp.wasabeef.recyclerview.adapters.SlideInBottomAnimationAdapter
import kotlinx.coroutines.processNextEventInCurrentThread
import timber.log.Timber
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class WhatIfOverviewFragment : Fragment() {
    private var _binding: RecyclerLayoutBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: OverviewAdapter

    @Inject
    lateinit var prefHelper: PrefHelper

    @Inject
    lateinit var themePrefs: ThemePrefs

    private lateinit var searchMenuItem: MenuItem

    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>

    val model: WhatIfOverviewViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = RecyclerLayoutBinding.inflate(inflater, container, false)

        setHasOptionsMenu(true)

        adapter = OverviewAdapter(
            themePrefs, prefHelper, requireActivity(),
            emptyList(), this::onArticleClicked, this::onArticleLongClicked
        )
        binding.rv.adapter =
            SlideInBottomAnimationAdapter(adapter).apply { setInterpolator(DecelerateInterpolator()) }

        binding.rv.layoutManager = LinearLayoutManager(activity)
        binding.rv.setHasFixedSize(false)
        binding.rv.isVerticalScrollBarEnabled = false

        model.articles.observe(viewLifecycleOwner) { newList ->
            val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = adapter.articles.size

                override fun getNewListSize() = newList.size

                override fun areItemsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ) = adapter.articles[oldItemPosition].number == newList[newItemPosition].number

                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ) = adapter.articles[oldItemPosition] == newList[newItemPosition]
            })

            adapter.articles = newList
            diffResult.dispatchUpdatesTo(adapter)
        }

        activity?.findViewById<FloatingActionButton>(R.id.fab)?.setOnClickListener {
            displayWhatIf(Random().nextInt(adapter.itemCount))
        }

       activityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                searchMenuItem.collapseActionView()
            }

        return binding.root
    }

    private fun onArticleClicked(article: Article) {
        if (!prefHelper.isOnline(activity) && !prefHelper.fullOfflineWhatIf()) {
            Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show()
            return
        }
        displayWhatIf(article.number)
    }

    private fun displayWhatIf(number: Int) {
        activityResultLauncher.launch(Intent(activity, WhatIfActivity::class.java).apply {
            putExtra(WhatIfActivity.INTENT_NUMBER, number)
        })
    }

    private fun onArticleLongClicked(article: Article): Boolean {

        val array =
            if (article.favorite) R.array.whatif_card_long_click_remove else R.array.whatif_card_long_click

        AlertDialog.Builder(activity).setItems(array) { _, which ->
            when (which) {
                0 -> {
                    startActivity(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "What if: " + article.title)
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "https://what-if.xkcd.com/" + article.number
                        )
                    })
                }
                1 -> startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://what-if.xkcd.com/" + article.number)
                    )
                )
                2 -> model.toggleArticleFavorite(article)
            }
        }.create().show()

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_favorite -> model.toggleOnlyFavorites()
            R.id.action_hide_read -> model.toggleHideRead()
            R.id.action_all_read -> model.setAllArticlesRead()
            R.id.action_unread -> model.setAllArticlesUnread()
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_what_if_fragment, menu)
        MenuCompat.setGroupDividerEnabled(menu, true)

        model.hideRead.observe(viewLifecycleOwner) {
            menu.findItem(R.id.action_hide_read).isChecked = it
        }

        searchMenuItem = menu.findItem(R.id.action_search)
        searchMenuItem.apply {
            isVisible = true
            (actionView as SearchView).apply {
                setIconifiedByDefault(false)
                queryHint = resources.getString(R.string.search_hint_whatif)

                setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String): Boolean {
                        return true
                    }

                    override fun onQueryTextChange(newText: String): Boolean {
                        //TODO set query to model
                        return false
                    }
                })
            }
        }

        model.onlyFavorites.observe(viewLifecycleOwner) {
            menu.findItem(R.id.action_favorite)
                .setIcon(if (it) R.drawable.ic_favorite_on_24dp else R.drawable.ic_favorite_off_24dp)
        }

        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}