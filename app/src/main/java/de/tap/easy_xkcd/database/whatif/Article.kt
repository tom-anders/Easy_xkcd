package de.tap.easy_xkcd.database.whatif

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Article(
    @PrimaryKey var number: Int,
    var title: String,
    var thumbnail: String,
    var favorite: Boolean = false,
    var read: Boolean = false,
)