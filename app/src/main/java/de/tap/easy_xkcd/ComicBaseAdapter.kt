package de.tap.easy_xkcd

import android.content.Context
import android.graphics.Bitmap
import android.text.Html
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.ActivityCompat.startPostponedEnterTransition
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import de.tap.easy_xkcd.database.Comic
import de.tap.easy_xkcd.database.ComicContainer
import de.tap.easy_xkcd.mainActivity.MainActivity
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.ThemePrefs
import timber.log.Timber
import java.util.*

abstract class ComicViewHolder(view: View): RecyclerView.ViewHolder(view) {
    abstract val title: TextView
    abstract val altText: TextView?
    abstract val number: TextView?
    abstract val image: ImageView
}

abstract class ComicBaseAdapter<ViewHolder: ComicViewHolder>(
    private val fragment: Fragment,
    private val activity: MainActivity, //TODO can probably remove at some point?
    private var comicNumberOfSharedElementTransition : Int?
) : RecyclerView.Adapter<ViewHolder>() {
    private val prefHelper = PrefHelper(activity)
    private val themePrefs = ThemePrefs(activity)

    var comics: List<ComicContainer> = emptyList()
    override fun getItemCount() = comics.size

    open fun onComicNull(number: Int) {}

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        Timber.d("qrc bind holder for comic ${position + 1}")
        val comic = comics[position].comic

        if (comic == null) {
            onComicNull(comics[position].number)
            return
        }

        holder.title.text = (if (prefHelper.subtitleEnabled()) "" else "$comic.number: "
            + Html.fromHtml(Comic.getInteractiveTitle(comic, activity)))

        // Transition names used for shared element transitions to the Overview Fragment
        holder.title.transitionName = comic.number.toString()
        holder.image.transitionName = "im" + comic.number

        GlideApp.with(fragment)
            .asBitmap()
            .apply(RequestOptions().placeholder(makeProgressDrawable()))
            .apply {
                //TODO enable offline support again
                /*if (comic.isOffline || comic.isFavorite) load(
                    RealmComic.getOfflineBitmap(
                        comic.comicNumber,
                        context,
                        prefHelper
                    )
                ) else load(comic.url)*/
                load(comic.url)
            }
            .listener(object : RequestListener<Bitmap?> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Bitmap?>,
                    isFirstResource: Boolean
                ): Boolean {
                    startPostponedTransitions(comic.number)
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
                        imageLoaded(holder.image, resource, comic)
                        startPostponedTransitions(comic.number)
                    }
                    return false
                }
            }).into(holder.image)
    }

    fun startPostponedTransitions(comicNumber: Int) {
        if (comicNumber == comicNumberOfSharedElementTransition) {
            startPostponedTransitions()
            comicNumberOfSharedElementTransition = null
        }
    }

    abstract fun startPostponedTransitions()

    fun imageLoaded(image: ImageView, bitmap: Bitmap, comic: Comic) {
        if (themePrefs.invertColors(false) && themePrefs.bitmapContainsColor(
                bitmap,
                comic.number
            )
        ) image.clearColorFilter()

        onImageLoaded(image, bitmap, comic)
    }
    abstract fun onImageLoaded(image: ImageView, bitmap: Bitmap, comic: Comic)

    private fun makeProgressDrawable() = CircularProgressDrawable(activity).apply {
        strokeWidth = 5.0f
        centerRadius = 100.0f
        setColorSchemeColors(if (themePrefs.nightThemeEnabled()) themePrefs.accentColorNight else themePrefs.accentColor)
        start()
    }
}