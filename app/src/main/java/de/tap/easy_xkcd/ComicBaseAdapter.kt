package de.tap.easy_xkcd

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.text.Html
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.tap.xkcd_reader.R
import de.tap.easy_xkcd.database.comics.Comic
import de.tap.easy_xkcd.database.comics.ComicContainer
import de.tap.easy_xkcd.database.comics.toContainer
import de.tap.easy_xkcd.utils.AppSettings
import de.tap.easy_xkcd.utils.AppTheme
import timber.log.Timber
import java.io.FileNotFoundException

abstract class ComicViewHolder(view: View): RecyclerView.ViewHolder(view) {
    abstract val title: TextView
    abstract val altText: TextView?
    abstract val info: TextView?
    abstract val image: ImageView?
}

class ComicListViewHolder(view: View, appTheme: AppTheme) : ComicViewHolder(view) {
    var cv: CardView = itemView as CardView

    override val title: TextView = cv.findViewById(R.id.comic_title)
    override val info: TextView? = cv.findViewById(R.id.comic_info)
    override val image: ImageView? = cv.findViewById(R.id.thumbnail)
    override val altText: TextView? = null

    init {
        if (appTheme.amoledThemeEnabled()) {
            cv.setCardBackgroundColor(Color.BLACK)
        } else if (appTheme.nightThemeEnabled) {
            cv.setCardBackgroundColor(
                ContextCompat.getColor(
                    view.context,
                    R.color.background_material_dark
                )
            )
        }
    }
}

abstract class ComicBaseAdapter<ViewHolder: ComicViewHolder>(
    private val context: Context,
    private var comicNumberOfSharedElementTransition : Int?
) : RecyclerView.Adapter<ViewHolder>() {
    private val appSettings = AppSettings(context)
    private val appTheme = AppTheme(context)

    var comics = mutableListOf<ComicContainer>()
    override fun getItemCount() = comics.size

    open fun onComicNull(number: Int) {}

    open fun onOfflineImageMissing(number: Int) {}

    open fun onDisplayingComic(comic: ComicContainer, holder: ViewHolder) {}

    abstract fun getOfflineUri(number: Int): Uri?

    fun updateComic(position: Int, comic: Comic) {
        if (position < comics.size) {
            comics[position] = comic.toContainer()
            notifyItemChanged(position)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.image?.setImageBitmap(null)
        holder.title.text = ""
        super.onViewRecycled(holder)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comicContainer = comics[position]
        val comic = comicContainer.comic

        // Transition names used for shared element transitions to the Overview Fragment
        holder.title.transitionName = comic?.number.toString()
        holder.image?.transitionName = "im" + comic?.number


        if (comic == null) {
            onComicNull(comics[position].number)

            holder.image?.setImageDrawable(makeProgressDrawable())
            startPostponedTransitions(comics[position].number)

            return
        }

        val prefix = if (appSettings.subtitleEnabled) "" else "$comic.number: "
        holder.title.text = prefix + Html.fromHtml(Comic.getInteractiveTitle(comic, context))

        if (appTheme.invertColors) {
            holder.image?.colorFilter = appTheme.colorFilter()
        }

        val gifId = when (comic.number) {
            961 -> R.raw.eternal_flame
            1116 -> R.raw.traffic_lights
            1264 -> R.raw.slideshow
            1335 -> R.raw.now
            2293 -> R.raw.rip_john_conway
            else -> null
        }

        holder.image?.let { image ->
            if (gifId != null) {
                GlideApp.with(context)
                    .asGif()
                    .load(gifId)
                    .listener(ComicRequestListener<GifDrawable>(comic, holder))
                    .into(image)
            } else {
                GlideApp.with(context)
                    .asBitmap()
                    .load(if (appSettings.fullOfflineEnabled) getOfflineUri(comic.number) else comic.url)
                    .apply(RequestOptions().placeholder(makeProgressDrawable()))
                    .listener(ComicRequestListener<Bitmap>(comic, holder))
                    .into(image)
            }
        } ?: run {
            // If the holder does not display the image, we can start the postponed transition right away
            startPostponedTransitions(comic.number)
        }

        onDisplayingComic(comicContainer, holder)
    }

    inner class ComicRequestListener<T> constructor(
        private val comic: Comic,
        private val holder: ComicViewHolder,
    ): RequestListener<T> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<T>?,
            isFirstResource: Boolean
        ): Boolean {
            Timber.i("URI ${e?.rootCauses?.any { it is FileNotFoundException }}")
            if (appSettings.fullOfflineEnabled && e?.rootCauses?.any { it is FileNotFoundException } == true) {
                onOfflineImageMissing(comic.number)
            }
            startPostponedTransitions(comic.number)
            Timber.e(e, "At comic $comic")
            return false
        }

        override fun onResourceReady(
            resource: T,
            model: Any?,
            target: Target<T>?,
            dataSource: DataSource?,
            isFirstResource: Boolean
        ): Boolean {
            resource?.let {
                if (resource is Bitmap) {
                    imageLoaded(holder.image, resource, comic)
                }
                startPostponedTransitions(comic.number)
            }
            return false
        }
    }

    fun startPostponedTransitions(comicNumber: Int) {
        if (comicNumber == comicNumberOfSharedElementTransition) {
            startPostponedTransitions()
            comicNumberOfSharedElementTransition = null
        }
    }

    abstract fun startPostponedTransitions()

    override fun getItemId(position: Int): Long {
        return comics[position].number.toLong()
    }

    fun imageLoaded(image: ImageView?, bitmap: Bitmap, comic: Comic) {
        if (appTheme.invertColors && appTheme.bitmapContainsColor(
                bitmap,
                comic.number
            )
        ) image?.clearColorFilter()

        onImageLoaded(image, bitmap, comic)
    }
    abstract fun onImageLoaded(image: ImageView?, bitmap: Bitmap, comic: Comic)

    private fun makeProgressDrawable() = CircularProgressDrawable(context).apply {
        strokeWidth = 5.0f
        centerRadius = 100.0f
        setColorSchemeColors(if (appTheme.nightThemeEnabled) appTheme.accentColorNight else appTheme.accentColor)
        start()
    }
}