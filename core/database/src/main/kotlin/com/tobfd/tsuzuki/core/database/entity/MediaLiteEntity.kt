package com.tobfd.tsuzuki.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * The compact media row next to each list entry (docs/ROADMAP.md, M4). Enum values are stored as
 * AniList's names; `core/data` maps them and tolerates values it doesn't know yet.
 */
@Entity(tableName = "media_lite")
data class MediaLiteEntity(
    @PrimaryKey val id: Int,
    val type: String,
    val format: String?,
    val status: String?,
    val episodes: Int?,
    val chapters: Int?,
    val volumes: Int?,
    @ColumnInfo(name = "title_user_preferred") val titleUserPreferred: String,
    @ColumnInfo(name = "title_romaji") val titleRomaji: String?,
    @ColumnInfo(name = "title_english") val titleEnglish: String?,
    @ColumnInfo(name = "title_native") val titleNative: String?,
    @ColumnInfo(name = "cover_url") val coverUrl: String?,
    @ColumnInfo(name = "cover_color") val coverColor: String?,
    val year: Int?,
    @ColumnInfo(name = "average_score") val averageScore: Int?,
    @ColumnInfo(name = "next_airing_episode") val nextAiringEpisode: Int?,
    @ColumnInfo(name = "is_adult") val isAdult: Boolean
)
