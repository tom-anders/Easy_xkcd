package de.tap.easy_xkcd.database

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tap.xkcd_reader.R
import de.tap.easy_xkcd.utils.PrefHelper
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*

@Entity
data class Comic(
    @PrimaryKey val number: Int,
) {
    var read: Boolean = false
    var favorite: Boolean = false

    var title: String = ""
    var transcript: String = ""
    var url: String = ""
    var altText: String = ""

    var year: String = ""
    var month: String = ""
    var day: String = ""

    // TODO should just keep the array here...
    companion object {
        fun isLargeComic(number: Int, context: Context): Boolean {
            return Arrays.binarySearch(
                context.resources.getIntArray(R.array.large_comics),
                number
            ) >= 0
        }

        fun getOfflineBitmap(comicNumber: Int, context: Context, prefHelper: PrefHelper): Bitmap? {
            //Fix for offline users who downloaded the HUGE version of #1826 or #2185
            if (comicNumber == 1826) {
                return BitmapFactory.decodeResource(
                    context.resources,
                    R.mipmap.birdwatching,
                    BitmapFactory.Options().apply { inScaled = false }
                )
            } else if (comicNumber == 2185) {
                return BitmapFactory.decodeResource(
                    context.resources,
                    R.mipmap.cumulonimbus_2x,
                    BitmapFactory.Options().apply { inScaled = false }
                )
            }
            val comicFileName = "$comicNumber.png"
            return try {
                val file = File(prefHelper.getOfflinePath(context), "${comicNumber}.png")
                val fis = FileInputStream(file)
                val bitmap = BitmapFactory.decodeStream(fis)
                fis.close()
                bitmap
            } catch (e: IOException) {
                Timber.e(e, "While loading offline comic #$comicNumber")
                null
            }
        }

        fun getInteractiveTitle(comic: Comic, context: Context): String {
            //In older versions of the database getTitle() may return a string that already contains
            // the (interactive) string, so we need to check for this here
            return if (RealmComic.isInteractiveComic(
                    comic.number,
                    context
                ) && !comic.title.contains("(interactive)")
            ) {
                comic.title + " (interactive)"
            } else comic.title
        }

        fun makeComic404() = Comic(404).apply {
            title = "404"
            altText = "404"
            url = "https://i.imgur.com/p0eKxKs.png"

            year = "2008"
            month = "3"
            day = "31"
        }
    }

    constructor(xkcdApiComic: XkcdApiComic, context: Context) : this(xkcdApiComic.num) {
        //TODO Check if we still need to convert to UTF-8 here?! Check old github issues for examples of weird char titles
        title = xkcdApiComic.title
        url = xkcdApiComic.url
        if (!isLargeComic(
                number,
                context
            ) && !RealmComic.isInteractiveComic(number, context)
        ) {
            url = RealmComic.getDoubleResolutionUrl(url, number)
        }
        if (RealmComic.isLargeComic(number, context)) {
            url =
                context.resources.getStringArray(R.array.large_comics_urls)[Arrays.binarySearch(
                    context.resources.getIntArray(R.array.large_comics),
                    number
                )]
        }
        altText = xkcdApiComic.alt
        transcript = xkcdApiComic.transcript

        year = xkcdApiComic.year
        month = xkcdApiComic.month
        day = xkcdApiComic.day

        when (number) {
            76 -> url = "https://i.imgur.com/h3fi2RV.jpg"
            80 -> url = "https://i.imgur.com/lWmI1lB.jpg"
            104 -> url = "https://i.imgur.com/dnCNfPo.jpg"
            1037 -> url =
                "https://www.explainxkcd.com/wiki/images/f/ff/umwelt_the_void.jpg"
            1054 -> title = "The Bacon"
            1137 -> title = "RTL"
            1190 -> {
            }
            1193 -> url =
                "https://www.explainxkcd.com/wiki/images/0/0b/externalities.png"
            1335 -> {
            }
            1350 -> url = "https://www.explainxkcd.com/wiki/images/3/3d/lorenz.png"
            1608 -> url = "https://www.explainxkcd.com/wiki/images/4/41/hoverboard.png"
            1663 -> url = "https://explainxkcd.com/wiki/images/c/ce/garden.png"
            2175 -> altText = String(
                "When Salvador Dalí died, it took months to get all the flagpoles sufficiently melted.".toByteArray(
                    StandardCharsets.UTF_8
                )
            )
            2185 -> {
                title = "Cumulonimbus"
                altText =
                    "The rarest of all clouds is the altocumulenticulostratonimbulocirruslenticulomammanoctilucent cloud, caused by an interaction between warm moist air, cool dry air, cold slippery air, cursed air, and a cloud of nanobots."
                url = "https://imgs.xkcd.com/comics/cumulonimbus_2x.png"
            }
            2202 -> url = "https://imgs.xkcd.com/comics/earth_like_exoplanet.png"
        }
        // The API sometimes gives http URLs, but these lead to an exception on Android
        url = url.replace("http://", "https://")
    }
}
