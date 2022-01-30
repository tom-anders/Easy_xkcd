package de.tap.easy_xkcd.search

import android.app.SearchManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.SearchView
import androidx.activity.viewModels
import androidx.core.text.HtmlCompat
import androidx.core.view.MenuItemCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tap.xkcd_reader.R
import com.tap.xkcd_reader.databinding.ActivitySearchResultsBinding
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.Activities.BaseActivity
import de.tap.easy_xkcd.Activities.SearchResultsActivity
import de.tap.easy_xkcd.ComicBaseAdapter
import de.tap.easy_xkcd.ComicListViewHolder
import de.tap.easy_xkcd.database.Comic
import de.tap.easy_xkcd.database.ComicContainer
import de.tap.easy_xkcd.utils.collectProgress
import de.tap.easy_xkcd.utils.observe
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchActivity: BaseActivity() {
    private lateinit var binding: ActivitySearchResultsBinding

    private lateinit var adapter: SearchAdapter

    val model: SearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySearchResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar(binding.toolbar.root)

        collectProgress(R.string.update_database, model.progress) {
            if (model.query.value.isEmpty()) {
                model.setQuery(intent.getStringExtra(SearchManager.QUERY).toString())
            }
        }

        model.query.observe(this) {
            supportActionBar?.title = "${resources.getString(R.string.title_activity_search_results)} \"$it\""
        }

        binding.rv.setHasFixedSize(true)
        binding.rv.layoutManager = LinearLayoutManager(this)
        adapter = SearchAdapter().also { binding.rv.adapter = it }

        model.results.observe(this) {
            adapter.comics = it.toMutableList()
            adapter.notifyDataSetChanged()
        }
    }

    inner class SearchAdapter : ComicBaseAdapter<ComicListViewHolder>(
        this, null
    ) {
        override fun getOfflineUri(number: Int) = model.getOfflineUri(number)

        override fun startPostponedTransitions() {}

        override fun onImageLoaded(image: ImageView, bitmap: Bitmap, comic: Comic) {}

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ComicListViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.search_result, parent, false)

            view.setOnClickListener {
                val comic = comics[binding.rv.getChildAdapterPosition(it)]

                val intent = Intent("de.tap.easy_xkcd.ACTION_COMIC").apply {
                    putExtra("number", comic.number)

                    //TODO SharedElementTransition does not work yet
                    putExtra(SearchResultsActivity.FROM_SEARCH, "")
                }

                //TODO The shared element transition doesn't work quite yet, so comment
                // this out for now.
//                val title = view.findViewById<View>(R.id.comic_title)
//                val thumbnail = view.findViewById<View>(R.id.thumbnail)

//                startActivity(intent, ActivityOptionsCompat.makeSceneTransitionAnimation(
//                    this@SearchActivity,
//                    Pair.create(title, title.transitionName),
//                    Pair.create(thumbnail, thumbnail.transitionName)
//                ).toBundle())

                startActivity(intent)
            }

            return ComicListViewHolder(view, themePrefs)
        }

        override fun onDisplayingComic(
            comic: ComicContainer,
            holder: ComicListViewHolder
        ) {
            holder.info?.text = HtmlCompat.fromHtml(comic.searchPreview, HtmlCompat.FROM_HTML_MODE_LEGACY)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_search_results, menu)

        val searchManager = getSystemService(SEARCH_SERVICE) as SearchManager
        val searchView = menu.findItem(R.id.action_search).actionView as SearchView
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        searchView.isIconifiedByDefault = false
        val searchMenuItem = menu.findItem(R.id.action_search)

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                model.setQuery(newText)
                return false
            }
        })

        //TODO Copied over from the old activity, doesnt seem to work anymore
        MenuItemCompat.setOnActionExpandListener(
            searchMenuItem,
            object : MenuItemCompat.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    val view = currentFocus
                    if (view != null) {
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(view, 0)
                    }
                    searchView.requestFocus()
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    val view = currentFocus
                    if (view != null) {
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(view.windowToken, 0)
                    }
                    return true
                }
            })
        return true
    }
}