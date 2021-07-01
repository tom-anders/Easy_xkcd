package de.tap.easy_xkcd.comicOverview

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import com.tap.xkcd_reader.R
import com.tap.xkcd_reader.databinding.RecyclerLayoutBinding
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.GlideApp
import de.tap.easy_xkcd.database.RealmComic
import de.tap.easy_xkcd.mainActivity.MainActivity
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.ThemePrefs

@AndroidEntryPoint
class ComicOverviewFragment : Fragment() {
    val model: ComicOverviewViewModel by activityViewModels()

    private var _binding: RecyclerLayoutBinding? = null
    private val binding get() = _binding!!

    private lateinit var themePrefs: ThemePrefs
    private lateinit var prefHelper: PrefHelper

    private lateinit var adapter: OverviewAdapter

    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = RecyclerLayoutBinding.inflate(inflater, container, false)

        setHasOptionsMenu(true)

        themePrefs = ThemePrefs(activity)
        prefHelper = PrefHelper(activity)

        recyclerView = binding.rv
        recyclerView.setHasFixedSize(true)

        model.overviewStyle.observe(viewLifecycleOwner) {
            it?.let {
                binding.rv.layoutManager =
                    when (it) {
                        2 ->
                            StaggeredGridLayoutManager(
                                2,
                                StaggeredGridLayoutManager.VERTICAL
                            )
                        else -> LinearLayoutManager(activity)
                    }

                recyclerView.isVerticalScrollBarEnabled =
                    recyclerView.layoutManager !is LinearLayoutManager
                binding.rv.setFastScrollEnabled(!recyclerView.isVerticalScrollBarEnabled)

                adapter = OverviewAdapter(model.comics.value ?: emptyList(), it)
                recyclerView.adapter = adapter
            }
        }

        model.comics.observe(viewLifecycleOwner) {
            it?.let {
                adapter.setComics(it)
            }
        }

        return binding.root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_overview_fragment, menu)

        model.bookmark.observe(viewLifecycleOwner) { bookmark ->
            menu.findItem(R.id.action_boomark)?.apply {
                isVisible = (bookmark != 0)
                setTitle(R.string.open_bookmark)
            }
            // For updating which entry to highlight with the accent color
            adapter.notifyDataSetChanged()
        }

        model.onlyFavorites.observe(viewLifecycleOwner) {
            menu.findItem(R.id.action_favorite)
                .setIcon(if (it) R.drawable.ic_favorite_on_24dp else R.drawable.ic_favorite_off_24dp)
        }

        model.hideRead.observe(viewLifecycleOwner) {
            menu.findItem(R.id.action_hide_read).isChecked = it
        }

        menu.findItem(R.id.action_hide_read)?.apply { isChecked = prefHelper.hideRead() }
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_overview_style -> {
            AlertDialog.Builder(requireContext()).setTitle(R.string.overview_style_title)
                .setSingleChoiceItems(
                    R.array.overview_style, prefHelper.overviewStyle
                ) { dialogInterface, i ->
                    model.overviewStyleSelected(i)
                    dialogInterface.dismiss()
                }.show()
            true
        }
        R.id.action_favorite -> {
            model.toggleOnlyFavorites()
            true
        }
        R.id.action_hide_read -> {
            model.toggleHideRead()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    inner class OverviewAdapter constructor(
        private var comics: List<RealmComic>,
        private val style: Int
    ) : RecyclerView.Adapter<OverviewAdapter.ComicViewHolder>(),
        FastScrollRecyclerView.SectionedAdapter {

        fun setComics(comics: List<RealmComic>) {
            this.comics = comics
            notifyDataSetChanged()
        }

        override fun getSectionName(pos: Int) = ""

        override fun getItemCount() = comics.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComicViewHolder {
            val view = when (style) {
                2 -> LayoutInflater.from(parent.context)
                    .inflate(R.layout.grid_item, parent, false)

                else -> LayoutInflater.from(parent.context)
                    .inflate(R.layout.search_result, parent, false)
            }

            view.setOnClickListener {
                (activity as MainActivity?)?.showComicFromOverview(
                    prefHelper.overviewFav(), listOf(
                        view.findViewById(R.id.comic_title),
                        view.findViewById(R.id.thumbnail)
                    ), comics[recyclerView.getChildAdapterPosition(it)].comicNumber)
            }

            return ComicViewHolder(view)
        }

        override fun onBindViewHolder(holder: ComicViewHolder, position: Int) {
            val comic = comics[position]

            holder.comicTitle?.apply {
                text = comic.title
                transitionName = comic.comicNumber.toString()

                val markAsRead = (comic.isRead && !prefHelper.overviewFav())
                setTextColor(ContextCompat.getColor(context,
                    when {
                        comic.comicNumber == model.bookmark.value -> {
                            val typedValue = TypedValue()
                            activity?.theme?.resolveAttribute(R.attr.colorAccent, typedValue, true)
                            typedValue.data
                        }
                        markAsRead xor themePrefs.nightThemeEnabled() -> {
                            R.color.Read
                        }
                        else -> {
                            android.R.color.tertiary_text_light
                        }
                    }
                ))
            }

            holder.comicInfo?.text = comic.comicNumber.toString()

            holder.thumbnail?.let { thumbnail ->
                thumbnail.transitionName = "im" + comic.comicNumber.toString()

                if (themePrefs.invertColors(false))
                    thumbnail.colorFilter = themePrefs.negativeColorFilter

                // TODO add listener to handle postponed enter transition
                GlideApp.with(this@ComicOverviewFragment)
                    .asBitmap()
                    .apply(RequestOptions().placeholder(CircularProgressDrawable(requireContext()).apply {
                        centerRadius = 60.0f
                        strokeWidth = 5.0f
                        setColorSchemeColors(if (themePrefs.nightThemeEnabled()) themePrefs.accentColorNight else themePrefs.accentColor)
                        start()
                    }))
                    .apply {
                        if (comic.isOffline || comic.isFavorite) load(
                            RealmComic.getOfflineBitmap(
                                comic.comicNumber,
                                context,
                                prefHelper
                            )
                        ) else load(comic.url)
                    }
                    .listener(object : RequestListener<Bitmap?> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Bitmap?>?,
                            isFirstResource: Boolean
                        ): Boolean {
                            //TODO start postponed
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
                                if (themePrefs.invertColors(false)
                                    && themePrefs.bitmapContainsColor(resource, comic.comicNumber)
                                ) {
                                    thumbnail.clearColorFilter()
                                }
                            }
                            return false
                        }
                    }).into(thumbnail)
            }
        }

        inner class ComicViewHolder constructor(view: View) : RecyclerView.ViewHolder(view) {
            var cv: CardView? = null
            var comicTitle: TextView? = null
            var comicInfo: TextView? = null

            var thumbnail: ImageView? = null

            init {
                cv = itemView as CardView
                if (themePrefs.amoledThemeEnabled()) {
                    cv?.setCardBackgroundColor(Color.BLACK)
                } else if (themePrefs.nightThemeEnabled()) {
                    cv?.setCardBackgroundColor(
                        ContextCompat.getColor(
                            view.context,
                            R.color.background_material_dark
                        )
                    )
                }
                comicTitle = itemView.findViewById(R.id.comic_title)
                comicInfo = itemView.findViewById(R.id.comic_info)
                thumbnail = itemView.findViewById(R.id.thumbnail)
            }
        }
    }
}