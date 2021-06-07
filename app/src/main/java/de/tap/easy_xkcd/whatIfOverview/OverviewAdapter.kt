package de.tap.easy_xkcd.whatIfOverview

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.bumptech.glide.request.RequestOptions
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import com.tap.xkcd_reader.R
import de.tap.easy_xkcd.GlideApp
import de.tap.easy_xkcd.GlideRequest
import de.tap.easy_xkcd.fragments.whatIf.WhatIfFragment
import de.tap.easy_xkcd.utils.Article
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.ThemePrefs
import java.io.File

class OverviewAdapter constructor(
    private val themePrefs: ThemePrefs,
    private val prefHelper: PrefHelper,
    private val context: Context,
    private var articles: List<Article>,
    private var itemClickedCallback: (article: Article) -> Unit,
    private var itemLongClickedCallback: (article: Article) -> Boolean,
) : RecyclerView.Adapter<OverviewAdapter.ViewHolder>(),
    FastScrollRecyclerView.SectionedAdapter {

    private val OFFLINE_WHATIF_OVERVIEW_PATH = "/what if/overview"

    override fun getSectionName(pos: Int) = ""

    override fun getItemCount() = articles.size

    fun setArticles(articles: List<Article>) {
        this.articles = articles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        viewGroup: ViewGroup,
        i: Int
    ) = ViewHolder(
        LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.whatif_overview, viewGroup, false)
    )

    /** Some articles do not have a thumbnail in the overview, so use our own replacement */
    fun getWhatIfMissingThumbnailId(number: Int) = when (number) {
        157 -> R.mipmap.slide
        137 -> R.mipmap.new_horizons
        else -> null
    }

    override fun onBindViewHolder(holder: OverviewAdapter.ViewHolder, position: Int) {
        val article = articles[position]
        holder.articleTitle.text = article.title
        holder.articleNumber.text = article.number.toString()

        val circularProgress = CircularProgressDrawable(context).apply {
            centerRadius = 60.0f
            strokeWidth = 5.0f
            setColorSchemeColors(themePrefs.accentColor)
            start()
        }

        getWhatIfMissingThumbnailId(article.number)?.let {
            holder.thumbnail.setImageDrawable(ContextCompat.getDrawable(context, it))
        } ?: run {
            if (prefHelper.fullOfflineWhatIf()) {
                val offlinePath: File = prefHelper.getOfflinePath(context)
                val dir =
                    File(offlinePath.absolutePath + OFFLINE_WHATIF_OVERVIEW_PATH)
                val file = File(dir, article.number.toString() + ".png")

                GlideApp.with(context).load(file)
            } else {
                GlideApp.with(context).load(article.thumbnail)
            }
                .apply(RequestOptions().placeholder(circularProgress))
                .into(holder.thumbnail)
        }

        if (article.isRead xor (themePrefs.nightThemeEnabled()))
            holder.articleTitle.setTextColor(ContextCompat.getColor(context, R.color.Read))
        else
            holder.articleTitle.setTextColor(
                ContextCompat.getColor(context, android.R.color.tertiary_text_light)
            )

    }

    inner class ViewHolder internal constructor(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        var cv: CardView = itemView.findViewById(R.id.cv)
        var articleTitle: TextView = itemView.findViewById(R.id.article_title)
        var articleNumber: TextView = itemView.findViewById(R.id.article_info)
        var thumbnail: ImageView = itemView.findViewById(R.id.thumbnail)

        init {
            if (themePrefs.amoledThemeEnabled()) {
                cv.setCardBackgroundColor(Color.BLACK)
            } else if (themePrefs.nightThemeEnabled()) {
                cv.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.background_material_dark)
                )
            }
            if (themePrefs.invertColors(false) || themePrefs.amoledThemeEnabled())
                thumbnail.colorFilter = themePrefs.negativeColorFilter

            itemView.setOnClickListener { itemClickedCallback(articles[adapterPosition]) }
            itemView.setOnLongClickListener { itemLongClickedCallback(articles[adapterPosition]) }
        }
    }
}
