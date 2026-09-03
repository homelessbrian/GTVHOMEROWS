package dev.tmdbrows.tmdb

import android.net.Uri
import dev.tmdbrows.db.CachedItem

/** Tile shape for a row. */
enum class ArtStyle(val id: String, val label: String) {
    POSTER("poster", "Posters (tall)"),
    LANDSCAPE("landscape", "Landscape (wide)");

    companion object {
        fun from(s: String?) = entries.firstOrNull { it.id == s } ?: POSTER
    }
}

/**
 * Resolves the image URL for a tile. A custom pattern (btttr.cc, RPDB, or anything else
 * keyed by id) takes priority; TMDB is the fallback whenever the pattern can't be filled.
 */
object Artwork {

    /** Placeholders accepted in a custom artwork pattern. */
    const val PLACEHOLDER_HELP =
        "Placeholders: {imdb} {tmdb} {type} {tvtype} {title} {year}. " +
            "Most providers key on {imdb}."

    fun tmdbUrl(item: CachedItem, style: ArtStyle): String? = when (style) {
        ArtStyle.LANDSCAPE -> TmdbClient.backdrop(item.backdropPath) ?: TmdbClient.poster(item.posterPath)
        ArtStyle.POSTER -> TmdbClient.poster(item.posterPath) ?: TmdbClient.backdrop(item.backdropPath)
    }

    /**
     * Fill a custom pattern for this item, or null when it needs an id the item lacks —
     * a URL containing a literal "{imdb}" would just 404 into a blank tile.
     */
    fun fillPattern(pattern: String, item: CachedItem): String? {
        if (pattern.isBlank()) return null
        var out = pattern.trim()
        if (out.contains("{imdb}")) {
            val imdb = item.imdbId ?: return null
            out = out.replace("{imdb}", imdb)
        }
        out = out.replace("{tmdb}", item.tmdbId.toString())
        out = out.replace("{type}", item.mediaType)
        out = out.replace("{tvtype}", if (item.mediaType == "series") "tv" else "movie")
        out = out.replace("{title}", Uri.encode(item.title))
        out = out.replace("{year}", item.releaseDate?.take(4) ?: "")
        return out
    }

    /**
     * The URL a tile should use. [pattern] is the user's custom source, empty when off.
     * Custom art is only applied to poster-shaped rows: providers like btttr.cc render
     * 2:3 artwork, and stretching that into a 16:9 tile looks wrong.
     */
    fun urlFor(item: CachedItem, style: ArtStyle, pattern: String, customEnabled: Boolean): String? {
        if (customEnabled && style == ArtStyle.POSTER) {
            fillPattern(pattern, item)?.let { return it }
        }
        return tmdbUrl(item, style)
    }
}
