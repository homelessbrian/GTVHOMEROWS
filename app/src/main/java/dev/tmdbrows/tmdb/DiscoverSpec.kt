package dev.tmdbrows.tmdb

import org.json.JSONArray
import org.json.JSONObject

/** What kind of content a row is built from. */
enum class SourceKind { LIST, DISCOVER, PRESET }

/** Movie or TV. Kept separate from the "movie"/"series" type used for deep links. */
enum class MediaKind(val api: String, val label: String) {
    MOVIE("movie", "Movies"),
    TV("tv", "TV shows");

    /** The type string Stremio/Nuvio expect. */
    val deepLinkType: String get() = if (this == TV) "series" else "movie"

    companion object {
        fun from(s: String?) = if (s == "tv") TV else MOVIE
    }
}

/** One of TMDB's ready-made endpoints — no filters needed. */
enum class Preset(val id: String, val label: String, val movieOnly: Boolean = false) {
    TRENDING_WEEK("trending_week", "Trending this week"),
    TRENDING_DAY("trending_day", "Trending today"),
    POPULAR("popular", "Popular"),
    TOP_RATED("top_rated", "Top rated"),
    NOW_PLAYING("now_playing", "In theaters now", movieOnly = true),
    UPCOMING("upcoming", "Upcoming", movieOnly = true),
    AIRING_TODAY("airing_today", "Airing today"),
    ON_THE_AIR("on_the_air", "On the air");

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: TRENDING_WEEK
        fun forKind(kind: MediaKind) = entries.filter {
            when (it) {
                NOW_PLAYING, UPCOMING -> kind == MediaKind.MOVIE
                AIRING_TODAY, ON_THE_AIR -> kind == MediaKind.TV
                else -> true
            }
        }
    }
}

data class SortOption(val value: String, val label: String)

val SORT_OPTIONS = listOf(
    SortOption("popularity.desc", "Most popular"),
    SortOption("vote_average.desc", "Highest rated"),
    SortOption("primary_release_date.desc", "Newest first"),
    SortOption("primary_release_date.asc", "Oldest first"),
    SortOption("revenue.desc", "Highest grossing"),
    SortOption("vote_count.desc", "Most voted")
)

/**
 * User-defined filter criteria, translated into TMDB /discover query parameters.
 * Serialized to JSON for storage in the row config.
 */
data class DiscoverSpec(
    val mediaKind: MediaKind = MediaKind.MOVIE,
    val genresInclude: List<Int> = emptyList(),
    val genresExclude: List<Int> = emptyList(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val minRating: Double? = null,
    val minVotes: Int? = null,
    val providers: List<Int> = emptyList(),
    val watchRegion: String = "US",
    val originalLanguage: String? = null,
    val runtimeMin: Int? = null,
    val runtimeMax: Int? = null,
    val sortBy: String = "popularity.desc",
    /** How many titles to put in the row (rounded up to whole TMDB pages of 20). */
    val maxItems: Int = 40
) {
    val pages: Int get() = ((maxItems + 19) / 20).coerceIn(1, 5)

    fun toQuery(): Map<String, String> {
        val q = linkedMapOf<String, String>()
        // TMDB uses different sort keys for TV
        q["sort_by"] = if (mediaKind == MediaKind.TV)
            sortBy.replace("primary_release_date", "first_air_date") else sortBy
        q["include_adult"] = "false"
        if (genresInclude.isNotEmpty()) q["with_genres"] = genresInclude.joinToString(",")
        if (genresExclude.isNotEmpty()) q["without_genres"] = genresExclude.joinToString(",")

        val dateField = if (mediaKind == MediaKind.TV) "first_air_date" else "primary_release_date"
        yearFrom?.let { q["$dateField.gte"] = "$it-01-01" }
        yearTo?.let { q["$dateField.lte"] = "$it-12-31" }

        minRating?.let { q["vote_average.gte"] = it.toString() }
        // Without a vote floor, a rating filter surfaces obscure titles with a handful of votes.
        q["vote_count.gte"] = (minVotes ?: if (minRating != null) 100 else 0).toString()

        if (providers.isNotEmpty()) {
            q["with_watch_providers"] = providers.joinToString("|")
            q["watch_region"] = watchRegion
        }
        originalLanguage?.takeIf { it.isNotBlank() }?.let { q["with_original_language"] = it }
        runtimeMin?.let { q["with_runtime.gte"] = it.toString() }
        runtimeMax?.let { q["with_runtime.lte"] = it.toString() }
        return q
    }

    /** Short human-readable summary for the settings list. */
    fun summary(genreNames: Map<Int, String>): String {
        val bits = mutableListOf<String>()
        bits += mediaKind.label
        if (genresInclude.isNotEmpty()) bits += genresInclude.mapNotNull { genreNames[it] }.joinToString("/")
        when {
            yearFrom != null && yearTo != null -> bits += "$yearFrom–$yearTo"
            yearFrom != null -> bits += "$yearFrom+"
            yearTo != null -> bits += "up to $yearTo"
        }
        minRating?.let { bits += "${it}+ rating" }
        if (providers.isNotEmpty()) bits += "${providers.size} provider(s)"
        bits += SORT_OPTIONS.firstOrNull { it.value == sortBy }?.label ?: sortBy
        return bits.joinToString(" · ")
    }

    fun toJson(): String = JSONObject().apply {
        put("mediaKind", mediaKind.api)
        put("genresInclude", JSONArray(genresInclude))
        put("genresExclude", JSONArray(genresExclude))
        yearFrom?.let { put("yearFrom", it) }
        yearTo?.let { put("yearTo", it) }
        minRating?.let { put("minRating", it) }
        minVotes?.let { put("minVotes", it) }
        put("providers", JSONArray(providers))
        put("watchRegion", watchRegion)
        originalLanguage?.let { put("originalLanguage", it) }
        runtimeMin?.let { put("runtimeMin", it) }
        runtimeMax?.let { put("runtimeMax", it) }
        put("sortBy", sortBy)
        put("maxItems", maxItems)
    }.toString()

    companion object {
        fun fromJson(json: String?): DiscoverSpec {
            if (json.isNullOrBlank()) return DiscoverSpec()
            return runCatching {
                val o = JSONObject(json)
                DiscoverSpec(
                    mediaKind = MediaKind.from(o.optString("mediaKind")),
                    genresInclude = o.optJSONArray("genresInclude").toIntList(),
                    genresExclude = o.optJSONArray("genresExclude").toIntList(),
                    yearFrom = o.optIntOrNull("yearFrom"),
                    yearTo = o.optIntOrNull("yearTo"),
                    minRating = if (o.has("minRating")) o.optDouble("minRating") else null,
                    minVotes = o.optIntOrNull("minVotes"),
                    providers = o.optJSONArray("providers").toIntList(),
                    watchRegion = o.optString("watchRegion", "US"),
                    originalLanguage = o.optString("originalLanguage").takeIf { it.isNotBlank() },
                    runtimeMin = o.optIntOrNull("runtimeMin"),
                    runtimeMax = o.optIntOrNull("runtimeMax"),
                    sortBy = o.optString("sortBy", "popularity.desc"),
                    maxItems = o.optInt("maxItems", 40)
                )
            }.getOrDefault(DiscoverSpec())
        }

        private fun JSONArray?.toIntList(): List<Int> {
            if (this == null) return emptyList()
            return (0 until length()).map { optInt(it) }.filter { it != 0 }
        }

        private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key)) optInt(key) else null
    }
}

data class Genre(val id: Int, val name: String)
data class Provider(val id: Int, val name: String)
