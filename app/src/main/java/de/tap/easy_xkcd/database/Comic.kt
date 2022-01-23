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


        fun buildFromJson(json: JSONObject, context: Context): Comic {
            val comicNumber = json.getInt("num")
            val comic = Comic(comicNumber)
            if (comicNumber == 404) {
                comic.title = "404"
                comic.altText = "404"
                comic.url = "https://i.imgur.com/p0eKxKs.png"

                comic.year = "2008"
                comic.month = "3"
                comic.day = "31"

            } else if (json.length() != 0) {
                try {
                    comic.title =
                        String(json.getString("title").toByteArray(StandardCharsets.UTF_8))
                    comic.url = json.getString("img")
                    if (!isLargeComic(
                            comicNumber,
                            context
                        ) && !RealmComic.isInteractiveComic(comicNumber, context)
                    ) {
                        comic.url = RealmComic.getDoubleResolutionUrl(comic.url, comicNumber)
                    }
                    if (RealmComic.isLargeComic(comicNumber, context)) {
                        comic.url =
                            context.resources.getStringArray(R.array.large_comics_urls)[Arrays.binarySearch(
                                context.resources.getIntArray(R.array.large_comics),
                                comicNumber
                            )]
                    }
                    comic.altText = String(json.getString("alt").toByteArray(StandardCharsets.UTF_8))
                    comic.transcript = json.getString("transcript")

                    comic.year = json.getString("year")
                    comic.month = json.getString("month")
                    comic.day = json.getString("day")

                    when (comicNumber) {
                        76 -> comic.url = "https://i.imgur.com/h3fi2RV.jpg"
                        80 -> comic.url = "https://i.imgur.com/lWmI1lB.jpg"
                        104 -> comic.url = "https://i.imgur.com/dnCNfPo.jpg"
                        1037 -> comic.url =
                            "https://www.explainxkcd.com/wiki/images/f/ff/umwelt_the_void.jpg"
                        1054 -> comic.title = "The Bacon"
                        1137 -> comic.title = "RTL"
                        1190 -> {
                        }
                        1193 -> comic.url =
                            "https://www.explainxkcd.com/wiki/images/0/0b/externalities.png"
                        1335 -> {
                        }
                        1350 -> comic.url = "https://www.explainxkcd.com/wiki/images/3/3d/lorenz.png"
                        1608 -> comic.url = "https://www.explainxkcd.com/wiki/images/4/41/hoverboard.png"
                        1663 -> comic.url = "https://explainxkcd.com/wiki/images/c/ce/garden.png"
                        2175 -> comic.altText = String(
                            "When Salvador Dalí died, it took months to get all the flagpoles sufficiently melted.".toByteArray(
                                StandardCharsets.UTF_8
                            )
                        )
                        2185 -> {
                            comic.title = "Cumulonimbus"
                            comic.altText =
                                "The rarest of all clouds is the altocumulenticulostratonimbulocirruslenticulomammanoctilucent cloud, caused by an interaction between warm moist air, cool dry air, cold slippery air, cursed air, and a cloud of nanobots."
                            comic.url = "https://imgs.xkcd.com/comics/cumulonimbus_2x.png"
                        }
                        2202 -> comic.url = "https://imgs.xkcd.com/comics/earth_like_exoplanet.png"
                    }
                } catch (e: JSONException) {
                    Timber.wtf(e)
                }
            } else {
                Timber.wtf("json is empty but comic number is not 404!")
            }
            // The API sometimes gives http URLs, but these lead to an exception on Android
            comic.url = comic.url.replace("http://", "https://")
            return comic
        }
    }
}
