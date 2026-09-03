package dev.tmdbrows.tmdb

/**
 * Ready-made rows the user can add in one press. Each entry is either a TMDB chart
 * (PRESET) or a pre-filled Discover query (DISCOVER) that stays fully editable afterwards.
 */
data class CatalogEntry(
    val id: String,
    val title: String,
    val blurb: String,
    val category: String,
    val preset: Preset? = null,
    val presetKind: MediaKind = MediaKind.MOVIE,
    val spec: DiscoverSpec? = null,
    /** Suggested tile shape; the user can change it per row afterwards. */
    val artStyle: ArtStyle = ArtStyle.POSTER
)

/** TMDB genre ids. Movie and TV use different sets for some categories. */
private object G {
    const val ACTION = 28
    const val ADVENTURE = 12
    const val ANIMATION = 16
    const val COMEDY = 35
    const val CRIME = 80
    const val DOCUMENTARY = 99
    const val DRAMA = 18
    const val FAMILY = 10751
    const val FANTASY = 14
    const val HISTORY = 36
    const val HORROR = 27
    const val MYSTERY = 9648
    const val ROMANCE = 10749
    const val SCIFI = 878
    const val THRILLER = 53
    const val WAR = 10752
    const val WESTERN = 37

    // TV-specific
    const val TV_ACTION_ADVENTURE = 10759
    const val TV_SCIFI_FANTASY = 10765
    const val TV_REALITY = 10764
}

private fun movies(
    genres: List<Int> = emptyList(),
    exclude: List<Int> = emptyList(),
    from: Int? = null,
    to: Int? = null,
    rating: Double? = null,
    votes: Int? = null,
    runtimeMin: Int? = null,
    runtimeMax: Int? = null,
    language: String? = null,
    sort: String = "popularity.desc",
    max: Int = 40
) = DiscoverSpec(
    mediaKind = MediaKind.MOVIE, genresInclude = genres, genresExclude = exclude,
    yearFrom = from, yearTo = to, minRating = rating, minVotes = votes,
    runtimeMin = runtimeMin, runtimeMax = runtimeMax, originalLanguage = language,
    sortBy = sort, maxItems = max
)

private fun shows(
    genres: List<Int> = emptyList(),
    from: Int? = null,
    rating: Double? = null,
    votes: Int? = null,
    language: String? = null,
    sort: String = "popularity.desc",
    max: Int = 40
) = DiscoverSpec(
    mediaKind = MediaKind.TV, genresInclude = genres, yearFrom = from,
    minRating = rating, minVotes = votes, originalLanguage = language,
    sortBy = sort, maxItems = max
)

object Catalog {

    val entries: List<CatalogEntry> = listOf(

        // ---- What's on now -------------------------------------------------
        CatalogEntry(
            "trending_movies", "Trending movies", "What everyone's watching this week",
            "What's on now", preset = Preset.TRENDING_WEEK, presetKind = MediaKind.MOVIE,
            artStyle = ArtStyle.LANDSCAPE
        ),
        CatalogEntry(
            "trending_tv", "Trending shows", "The week's most-watched series",
            "What's on now", preset = Preset.TRENDING_WEEK, presetKind = MediaKind.TV,
            artStyle = ArtStyle.LANDSCAPE
        ),
        CatalogEntry(
            "in_theaters", "In theaters", "Playing in cinemas right now",
            "What's on now", preset = Preset.NOW_PLAYING, presetKind = MediaKind.MOVIE
        ),
        CatalogEntry(
            "coming_soon", "Coming soon", "Releasing over the next few weeks",
            "What's on now", preset = Preset.UPCOMING, presetKind = MediaKind.MOVIE
        ),
        CatalogEntry(
            "on_the_air", "Airing now", "Series with episodes this week",
            "What's on now", preset = Preset.ON_THE_AIR, presetKind = MediaKind.TV
        ),

        // ---- The best ------------------------------------------------------
        CatalogEntry(
            "top_rated_movies", "Top rated films", "TMDB's all-time highest rated",
            "The best", preset = Preset.TOP_RATED, presetKind = MediaKind.MOVIE
        ),
        CatalogEntry(
            "top_rated_tv", "Top rated series", "The best-reviewed shows ever made",
            "The best", preset = Preset.TOP_RATED, presetKind = MediaKind.TV
        ),
        CatalogEntry(
            "acclaimed_recent", "Acclaimed lately", "7.5+ rated, released since 2020",
            "The best", spec = movies(from = 2020, rating = 7.5, votes = 400, sort = "vote_average.desc")
        ),
        CatalogEntry(
            "modern_classics", "Modern classics", "Standouts from 2000 to 2015",
            "The best", spec = movies(from = 2000, to = 2015, rating = 7.5, votes = 1000, sort = "vote_average.desc")
        ),
        CatalogEntry(
            "hidden_gems", "Hidden gems", "Well rated but never went mainstream",
            "The best", spec = movies(rating = 7.0, votes = 150, sort = "vote_average.desc", max = 60)
        ),

        // ---- By decade -----------------------------------------------------
        CatalogEntry(
            "seventies", "The seventies", "1970s cinema worth revisiting",
            "By decade", spec = movies(from = 1970, to = 1979, rating = 7.0, votes = 200, sort = "vote_average.desc")
        ),
        CatalogEntry(
            "eighties_action", "Eighties action", "Big, loud, and unapologetic",
            "By decade", spec = movies(genres = listOf(G.ACTION), from = 1980, to = 1989, rating = 6.0, votes = 100)
        ),
        CatalogEntry(
            "nineties_scifi", "Nineties sci-fi", "The decade that shaped the genre",
            "By decade", spec = movies(genres = listOf(G.SCIFI), from = 1990, to = 1999, rating = 6.5, votes = 150)
        ),
        CatalogEntry(
            "two_thousands", "The 2000s", "Everything from the millennium decade",
            "By decade", spec = movies(from = 2000, to = 2009, rating = 6.5, votes = 300)
        ),

        // ---- By mood -------------------------------------------------------
        CatalogEntry(
            "feel_good", "Feel-good", "Comedies that actually land",
            "By mood", spec = movies(genres = listOf(G.COMEDY), rating = 6.5, votes = 300)
        ),
        CatalogEntry(
            "after_dark", "After dark", "Horror worth staying up for",
            "By mood", spec = movies(genres = listOf(G.HORROR), rating = 6.0, votes = 200)
        ),
        CatalogEntry(
            "mind_benders", "Mind-benders", "Sci-fi and mystery that keep you guessing",
            "By mood", spec = movies(genres = listOf(G.SCIFI, G.MYSTERY), rating = 7.0, votes = 300)
        ),
        CatalogEntry(
            "crime_thrillers", "Crime thrillers", "Tense, twisty, and well made",
            "By mood", spec = movies(genres = listOf(G.CRIME, G.THRILLER), rating = 6.8, votes = 300)
        ),
        CatalogEntry(
            "family_night", "Family night", "Something everyone can sit through",
            "By mood", spec = movies(genres = listOf(G.FAMILY), exclude = listOf(G.HORROR), rating = 6.5, votes = 200)
        ),
        CatalogEntry(
            "true_stories", "True stories", "Documentaries and history",
            "By mood", spec = movies(genres = listOf(G.DOCUMENTARY), rating = 7.0, votes = 50)
        ),
        CatalogEntry(
            "epic_sweep", "Epic sweep", "Adventure and history on a grand scale",
            "By mood", spec = movies(genres = listOf(G.ADVENTURE, G.HISTORY), rating = 7.0, votes = 400, runtimeMin = 130)
        ),

        // ---- By length -----------------------------------------------------
        CatalogEntry(
            "short_and_sweet", "Under 90 minutes", "When you don't have all night",
            "By length", spec = movies(rating = 6.5, votes = 200, runtimeMax = 95)
        ),
        CatalogEntry(
            "long_haul", "The long haul", "Three-hour commitments, worth it",
            "By length", spec = movies(rating = 7.5, votes = 400, runtimeMin = 165, sort = "vote_average.desc")
        ),

        // ---- Around the world ----------------------------------------------
        CatalogEntry(
            "korean", "Korean cinema", "From the country that made Parasite",
            "Around the world", spec = movies(language = "ko", rating = 7.0, votes = 100)
        ),
        CatalogEntry(
            "japanese", "Japanese film", "Live action and animation both",
            "Around the world", spec = movies(language = "ja", rating = 7.0, votes = 100)
        ),
        CatalogEntry(
            "french", "French film", "Drama, comedy, and everything between",
            "Around the world", spec = movies(language = "fr", rating = 7.0, votes = 100)
        ),
        CatalogEntry(
            "spanish", "Spanish language", "Films from Spain and Latin America",
            "Around the world", spec = movies(language = "es", rating = 6.8, votes = 100)
        ),
        CatalogEntry(
            "anime_series", "Anime series", "Japanese animated television",
            "Around the world", spec = shows(genres = listOf(G.ANIMATION), language = "ja", rating = 7.0, votes = 50)
        ),

        // ---- Television ----------------------------------------------------
        CatalogEntry(
            "prestige_drama", "Prestige drama", "The shows people tell you to watch",
            "Television", spec = shows(genres = listOf(G.DRAMA), rating = 8.0, votes = 200, sort = "vote_average.desc")
        ),
        CatalogEntry(
            "sitcoms", "Comfort sitcoms", "Half-hour comedies to leave on",
            "Television", spec = shows(genres = listOf(G.COMEDY), rating = 7.0, votes = 150)
        ),
        CatalogEntry(
            "genre_tv", "Sci-fi and fantasy TV", "Worlds worth getting lost in",
            "Television", spec = shows(genres = listOf(G.TV_SCIFI_FANTASY), rating = 7.0, votes = 150)
        ),
        CatalogEntry(
            "new_shows", "New series", "Started in the last two years",
            "Television", spec = shows(from = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - 1,
                rating = 6.5, votes = 50)
        )
    )

    val categories: List<String> get() = entries.map { it.category }.distinct()
}
