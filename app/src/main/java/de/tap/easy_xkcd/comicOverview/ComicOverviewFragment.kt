package de.tap.easy_xkcd.comicOverview

import android.animation.LayoutTransition
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.transition.Transition
import android.transition.TransitionInflater
import android.util.TypedValue
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.tap.xkcd_reader.R
import com.tap.xkcd_reader.databinding.RecyclerLayoutBinding
import dagger.hilt.android.AndroidEntryPoint
import de.tap.easy_xkcd.ComicBaseAdapter
import de.tap.easy_xkcd.ComicViewHolder
import de.tap.easy_xkcd.database.Comic
import de.tap.easy_xkcd.database.toContainer
import de.tap.easy_xkcd.mainActivity.MainActivity
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.ThemePrefs
import de.tap.easy_xkcd.utils.observe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@AndroidEntryPoint
class ComicOverviewFragment : Fragment() {
    val model: ComicOverviewViewModel by viewModels()

    private val mainActivity get() = activity as? MainActivity?

    private var _binding: RecyclerLayoutBinding? = null
    private val binding get() = _binding!!

    private lateinit var themePrefs: ThemePrefs
    private lateinit var prefHelper: PrefHelper

    private lateinit var adapter: OverviewAdapter

    private lateinit var recyclerView: RecyclerView

    private var comicNumberOfSharedElementTransition: Int? = null

    companion object {
        const val SAVED_INSTANCE_ADAPTER_POSITION = "saved_instance_adapter_position"
    }

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

        model.overviewStyle.observe(viewLifecycleOwner) { style ->
            binding.rv.layoutManager =
                when (style) {
                    2 ->
                        StaggeredGridLayoutManager(
                            2,
                            StaggeredGridLayoutManager.VERTICAL
                        ).apply { reverseLayout = true }
                    else -> LinearLayoutManager(activity).apply { reverseLayout = true }
                }

            recyclerView.isVerticalScrollBarEnabled =
                recyclerView.layoutManager !is LinearLayoutManager
            binding.rv.setFastScrollEnabled(!recyclerView.isVerticalScrollBarEnabled)

            adapter = OverviewAdapter(style)
            adapter.comics = model.comics.value.toMutableList()
            recyclerView.adapter = adapter
        }

        model.comics.observe(viewLifecycleOwner) { newList ->
            // This will be the case either when the overview is intialized the first time,
            // or when "only favorites" or "hide read" is toggled.
            // Otherwise, a comic will have been cached in which case it would hurt performance
            // to calculate the diff of the whole list. We'll notice that via model.comicCached
            // instead and update only the specific item there.
            if (newList.size != adapter.comics.size) {
                val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                    override fun getOldListSize() = adapter.comics.size

                    override fun getNewListSize() = newList.size

                    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                        adapter.comics[oldItemPosition].number == newList[newItemPosition].number

                    override fun areContentsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int
                    ): Boolean {
                        return adapter.comics[oldItemPosition] == newList[newItemPosition]
                    }
                })

                if (adapter.comics.isEmpty()) {
                    val position = comicNumberOfSharedElementTransition?.let {
                        // If we're showing all comics, the position is simply the comic number - 1
                        // Otherwise, we have to search for the number in the list
                        it - 1
                    } ?: run {
                        // This is the case when we have "hide read" enabled, or are showing only favorites,
                        // so the comic we came from might not be in the list here. So scroll to the
                        // nearest comic instead
                        newList.indexOfFirst { comicContainer -> comicContainer.number >= prefHelper.lastComic }
                    }

                    // Calculate offset such that the item will be centered in the middle
                    val offset =
                        (recyclerView.width - (recyclerView.findViewHolderForAdapterPosition(
                            position
                        )?.itemView?.width ?: 0)) / 2

                    (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                        position - 1,
                        offset
                    )
                    (recyclerView.layoutManager as? StaggeredGridLayoutManager)?.scrollToPositionWithOffset(
                        position - 1,
                        offset
                    )
                }

                adapter.comics = newList.toMutableList()

                diffResult.dispatchUpdatesTo(adapter)

                (activity as AppCompatActivity).supportActionBar?.subtitle = ""
            }
        }

        model.comicCached.observe(viewLifecycleOwner) { comic ->
            if (model.onlyFavorites.value || model.hideRead.value) {
                adapter.comics.indexOfFirst { it.number == comic.number }.let {
                    if (it != -1) it else null
                }
            } else {
                comic.number - 1
            }?.let { position ->
                adapter.updateComic(position, comic)
            }
        }

        arguments?.let { args ->
            // If the overview is in "favorites only" mode and we're coming from the normal browser
            // (not from the favorites browser), then we'll probably not have the comic in the list,
            // so don't bother doing the enter transition in that case.
            val onlyShowFavoritesAndNotComingFromFavorites
                    = !args.getBoolean(MainActivity.ARG_FROM_FAVORITES, false)
                        && model.onlyFavorites.value

            if (savedInstanceState == null && !onlyShowFavoritesAndNotComingFromFavorites) {
                args.getInt(MainActivity.ARG_COMIC_TO_SHOW, -1).let { number ->
                    if (number > 0 && number <= prefHelper.newest) {
                        comicNumberOfSharedElementTransition = number
                        postponeEnterTransition(400, TimeUnit.MILLISECONDS)

                        val transition = TransitionInflater.from(context)
                            .inflateTransition(R.transition.image_shared_element_transition)

                        // If we're hiding read comics, we still want the transition, so first show
                        // read comics again, and then when the enter transition is finished,
                        // hide them again
                        if (model.hideRead.value) {
                            model.toggleHideRead()
                            transition.addListener(
                                object: Transition.TransitionListener {
                                    override fun onTransitionEnd(transition: Transition)
                                            = model.toggleHideRead()
                                    override fun onTransitionCancel(transition: Transition)
                                            = model.toggleHideRead()

                                    override fun onTransitionStart(transition: Transition) {}
                                    override fun onTransitionPause(transition: Transition) {}
                                    override fun onTransitionResume(transition: Transition) {}
                                }
                            )
                        }

                        sharedElementEnterTransition = transition
                    }
                }
            }
        }

        activity?.findViewById<FloatingActionButton>(R.id.fab)?.setOnClickListener {
            val randIndex = Random.nextInt(adapter.comics.size)

            mainActivity?.showComicFromOverview(
                prefHelper.overviewFav(), emptyList(), adapter.comics[randIndex].number
            )
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
        R.id.action_boomark -> {
            model.bookmark.value?.let {
                mainActivity?.showComicFromOverview(false, emptyList(), it)
            }
            true
        }
        R.id.action_earliest_unread -> {
            lifecycleScope.launch {
                model.getOldestUnread()?.let {
                    mainActivity?.showComicFromOverview(false, emptyList(), it.number)
                }
            }
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    inner class OverviewAdapter(
        private val style: Int
    ) : ComicBaseAdapter<OverviewViewHolder>(
        this,
        requireActivity(),
        comicNumberOfSharedElementTransition,
    ) {
        override fun getOfflineUri(number: Int) = model.getOfflineUri(number)

        override fun onComicNull(number: Int) {
            //TODO When we're scrolling to fast through the list,
            // we're updating the list too often, hurting performance
            // Need to find a way to bunch the cache request together here
            model.cacheComic(number)
        }

        override fun startPostponedTransitions() {
            startPostponedEnterTransition()
            comicNumberOfSharedElementTransition = null
        }

        override fun onImageLoaded(image: ImageView, bitmap: Bitmap, comic: Comic) {}

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OverviewViewHolder {
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
                    ), comics[recyclerView.getChildAdapterPosition(it)].number
                )
            }

            view.setOnLongClickListener { view
                comics[recyclerView.getChildAdapterPosition(view)].comic?.let { comic ->
                    AlertDialog.Builder(requireActivity()).setItems(
                        if (comic.read) R.array.card_long_click_remove else R.array.card_long_click) { dialog, i ->
                        when (i) {
                            0 -> {
                                model.setBookmark(comic.number)
                            }
                            1 -> {
                                model.setRead(comic.number, !comic.read)
                            }
                        }
                    }.create().show()
                }

                true
            }

            return OverviewViewHolder(view)
        }

        override fun onBindViewHolder(holder: OverviewViewHolder, position: Int) {
            holder.number?.text = comics[position].number.toString()
            super.onBindViewHolder(holder, position)
        }

        override fun onDisplayingComic(comic: Comic, holder: OverviewViewHolder) {
            holder.title.apply {
                val markAsRead = (comic.read && !prefHelper.overviewFav())
                setTextColor(
                    when {
                        comic.number == model.bookmark.value -> themePrefs.accentColor
                        markAsRead xor themePrefs.nightThemeEnabled() -> ContextCompat.getColor(
                            context,
                            R.color.Read
                        )
                        else -> ContextCompat.getColor(context, android.R.color.tertiary_text_light)
                    }
                )
            }
        }
    }

    /*inner class OverviewAdapter constructor(
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
                            postImageLoaded(comic.comicNumber)
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

        fun postImageLoaded(comicNumber: Int) {
            comicNumberOfSharedElementTransition?.let {
                if (comicNumber == it) {
                    startPostponedEnterTransition()
                    comicNumberOfSharedElementTransition = null
                }
            }
        }

    }*/

    inner class OverviewViewHolder constructor(view: View) : ComicViewHolder(view) {
        var cv: CardView = itemView as CardView

        override val title: TextView = cv.findViewById(R.id.comic_title)
        override val number: TextView? = cv.findViewById(R.id.comic_info)
        override val image: ImageView = cv.findViewById(R.id.thumbnail)
        override val altText: TextView? = null


        init {
            if (themePrefs.amoledThemeEnabled()) {
                cv.setCardBackgroundColor(Color.BLACK)
            } else if (themePrefs.nightThemeEnabled()) {
                cv.setCardBackgroundColor(
                    ContextCompat.getColor(
                        view.context,
                        R.color.background_material_dark
                    )
                )
            }
        }
    }
}