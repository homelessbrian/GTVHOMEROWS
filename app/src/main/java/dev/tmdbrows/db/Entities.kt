package dev.tmdbrows.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One configured row on the home screen. Source is a TMDB list, a Discover query, or a preset. */
@Entity(tableName = "list_config")
data class ListConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "LIST", "DISCOVER" or "PRESET" */
    val kind: String = "LIST",
    /** Set when kind == LIST. */
    val tmdbListId: String = "",
    /** JSON-encoded DiscoverSpec; set when kind == DISCOVER. */
    val discoverJson: String = "",
    /** Preset id and media kind; set when kind == PRESET. */
    val presetId: String = "",
    val presetMediaKind: String = "movie",
    val presetMaxItems: Int = 40,
    val displayName: String,
    /** Package name of the app to open items in; empty = use the global default. */
    val targetPackage: String = "",
    val sortOrder: Int = 0,
    /** ID assigned by the TV provider once the channel is created; null until then. */
    val channelId: Long? = null
)

/** Cached item from a list so the click handler can build a deep link instantly. */
@Entity(tableName = "cached_item", primaryKeys = ["configId", "tmdbId", "mediaType"])
data class CachedItem(
    val configId: Long,
    val tmdbId: Long,
    /** "movie" or "series" */
    val mediaType: String,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String?,
    val rating: Double?,
    val imdbId: String?,
    /** Row id of the PreviewProgram published for this item, if any. */
    val programId: Long? = null
)
