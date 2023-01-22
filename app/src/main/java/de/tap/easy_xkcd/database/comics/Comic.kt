package de.tap.easy_xkcd.database.comics

import android.content.Context
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.nio.charset.StandardCharsets

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

    companion object {
        val largeComics = mapOf<Int, String>(
            256 to "https://imgs.xkcd.com/comics/online_communities.png",
            482 to "https://imgs.xkcd.com/comics/height.png",
            657 to "https://imgs.xkcd.com/comics/movie_narrative_charts_large.png",
            681 to "https://imgs.xkcd.com/comics/gravity_wells_large.png",
            802 to "https://imgs.xkcd.com/comics/online_communities_2_large.png",
            832 to "https://i.imgur.com/EObZHw1.jpg",
            930 to "https://imgs.xkcd.com/comics/days_of_the_week_large.png",
            1000 to "https://imgs.xkcd.com/comics/1000_comics_large.png",
            1040 to "https://imgs.xkcd.com/comics/lakes_and_oceans_large.png",
            1071 to "https://imgs.xkcd.com/comics/exoplanets_large.png",
            1079 to "https://imgs.xkcd.com/comics/united_shapes_large.png",
            1080 to "https://imgs.xkcd.com/comics/visual_field_large.png",
            1196 to "https://imgs.xkcd.com/comics/subways_large.png",
            1256 to "https://imgs.xkcd.com/comics/questions_large.png",
            1298 to "https://imgs.xkcd.com/comics/exoplanet_neighborhood_large.png",
            1389 to "https://imgs.xkcd.com/comics/surface_area_large.png",
            1392 to "https://imgs.xkcd.com/comics/dominant_players_large.png",
            1461 to "https://imgs.xkcd.com/comics/payloads_large.png",
            1491 to "https://imgs.xkcd.com/comics/stories_of_the_past_and_future_large.png",
            1509 to "https://imgs.xkcd.com/comics/scenery_cheat_sheet_large.png",
            1688 to "https://imgs.xkcd.com/comics/map_age_guide_large.png",
            1732 to "https://imgs.xkcd.com/comics/earth_temperature_timeline.png",
        )

        fun isLargeComic(number: Int): Boolean {
            return largeComics.containsKey(number)
        }

        fun isInteractiveComic(number: Int): Boolean {
            return setOf(
                826,
                1037,
                1110,
                1190,
                1193,
                1264,
                1331,
                1350,
                1416,
                1506,
                1525,
                1572,
                1608,
                1663,
                1975,
                2067,
                2198,
                2445,
            ).contains(number)
        }

        //Thanks to /u/doncajon https://www.reddit.com/r/xkcd/comments/667yaf/xkcd_1826_birdwatching/
        fun getDoubleResolutionUrl(url: String, number: Int): String {
            val no2xVersion = setOf(
                1097,
                1103,
                1127,
                1151,
                1182,
                1193,
                1229,
                1253,
                1335,
                1349,
                1350,
                1446,
                1452,
                1506,
                1551,
                1608,
                1663,
                1667,
                1735,
                1739,
                1744,
                1778,
                2202,
                2281,
                2293
            )
            return if (number >= 1084 && !no2xVersion.contains(number) && !url.contains("_2x.png") && url.lastIndexOf(
                    '.'
                ) != -1
            ) url.substring(0, url.lastIndexOf('.')) + "_2x.png" else url
        }

        fun getInteractiveTitle(comic: Comic, context: Context): String {
            //In older versions of the database getTitle() may return a string that already contains
            // the (interactive) string, so we need to check for this here
            return if (Comic.isInteractiveComic(
                    comic.number,
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

    constructor(xkcdApiComic: XkcdApiComic) : this(xkcdApiComic.num) {
        //TODO Check if we still need to convert to UTF-8 here?! Check old github issues for examples of weird char titles
        title = xkcdApiComic.title
        url = xkcdApiComic.url
        if (!isLargeComic(
                number,
            ) && !isInteractiveComic(number)
        ) {
            url = getDoubleResolutionUrl(url, number)
        }

        largeComics[number]?.let {
            url = it
        }

        altText = xkcdApiComic.alt

        if (number <= 1608) {
            transcript = xkcdApiComic.transcript
        }

        year = xkcdApiComic.year
        month = xkcdApiComic.month
        day = xkcdApiComic.day

        when (number) {
            76 -> url = "https://explainxkcd.com/wiki/images/1/16/familiar.jpg"
            80 -> url = "https://explainxkcd.com/wiki/images/2/20/other_car.jpg"
            104 -> url = "https://explainxkcd.com/wiki/images/a/a6/find_you.jpg"
            1037 -> url = "https://www.explainxkcd.com/wiki/images/f/ff/umwelt_the_void.jpg"
            1054 -> title = "The Bacon"
            1137 -> title = "RTL"
            1190 -> {
                //TODO Pressing this comic should link to http://geekwagon.net/projects/xkcd1190/
                url = "https://upload.wikimedia.org/wikipedia/en/0/07/Xkcd_time_frame_0001.png"
            }
            1193 -> url = "https://www.explainxkcd.com/wiki/images/0/0b/externalities.png"
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
