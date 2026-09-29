package com.tobfd.tsuzuki.core.database.entity

import androidx.room3.Entity

/** One of the viewer's custom lists, in the order AniList shows them. */
@Entity(tableName = "custom_list", primaryKeys = ["type", "name"])
data class CustomListEntity(
    /** `ANIME` or `MANGA`: custom lists exist per list type. */
    val type: String,
    val name: String,
    val position: Int
)
