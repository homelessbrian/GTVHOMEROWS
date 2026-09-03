package dev.tmdbrows.tmdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class TmdbListInfo(val name: String, val items: List<TmdbItem>)

data class TmdbItem(
    val tmdbId: Long,
    /** "movie" or "series" */
    val mediaType: String,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String?,
    val rating: Double?
)

class TmdbException(message: String) : IOException(message)

/**
 * Minimal TMDB client using only OkHttp + org.json.
 * Accepts either a v3 API key (32 hex chars) or a v4 read-access token (JWT, starts with "eyJ").
 */
class TmdbClient(private val credential: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val isBearer get() = credential.startsWith("eyJ")

    private fun url(path: String, query: Map<String, String> = emptyMap()): String {
        val q = query.toMutableMap()
        if (!isBearer) q["api_key"] = credential
        val qs = q.entries.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" }
        return "https://api.themoviedb.org$path" + if (qs.isNotEmpty()) "?$qs" else ""
    }

    private fun get(path: String, query: Map<String, String> = emptyMap()): JSONObject {
        val b = Request.Builder().url(url(path, query)).header("Accept", "application/json")
        if (isBearer) b.header("Authorization", "Bearer $credential")
        http.newCall(b.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching { JSONObject(body).optString("status_message") }.getOrNull()
                throw TmdbException("TMDB ${resp.code}: ${msg?.ifBlank { null } ?: body.take(120)}")
            }
            return JSONObject(body)
        }
    }

    /** Throws on invalid credential; returns normally on success. */
    suspend fun validate() = withContext(Dispatchers.IO) {
        get("/3/configuration")
        Unit
    }

    /** Fetch a list. Tries the v3 endpoint first, then v4 (paginated). */
    suspend fun fetchList(listId: String): TmdbListInfo = withContext(Dispatchers.IO) {
        val id = listId.trim()
        try {
            parseV3(get("/3/list/$id"))
        } catch (e: TmdbException) {
            // v3 may 404 on newer v4 lists; fall back to v4 (needs a v4 token for private lists)
            fetchV4(id)
        }
    }

    private fun parseV3(o: JSONObject): TmdbListInfo {
        val items = o.optJSONArray("items") ?: JSONArray()
        return TmdbListInfo(o.optString("name", "TMDB List"), parseItems(items))
    }

    private fun fetchV4(id: String): TmdbListInfo {
        val first = get("/4/list/$id", mapOf("page" to "1"))
        val all = mutableListOf<TmdbItem>()
        all += parseItems(first.optJSONArray("results") ?: JSONArray())
        val pages = first.optInt("total_pages", 1)
        for (p in 2..pages.coerceAtMost(20)) {
            val page = get("/4/list/$id", mapOf("page" to p.toString()))
            all += parseItems(page.optJSONArray("results") ?: JSONArray())
        }
        return TmdbListInfo(first.optString("name", "TMDB List"), all)
    }

    private fun parseItems(arr: JSONArray): List<TmdbItem> {
        val out = mutableListOf<TmdbItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val rawType = o.optString("media_type", if (o.has("first_air_date")) "tv" else "movie")
            val type = if (rawType == "tv") "series" else "movie"
            val title = o.optString("title").ifBlank { o.optString("name") }
            if (title.isBlank()) continue
            out += TmdbItem(
                tmdbId = o.optLong("id"),
                mediaType = type,
                title = title,
                overview = o.optString("overview"),
                posterPath = o.optString("poster_path").takeIf { s -> s.isNotBlank() && s != "null" },
                backdropPath = o.optString("backdrop_path").takeIf { s -> s.isNotBlank() && s != "null" },
                releaseDate = o.optString("release_date").ifBlank { o.optString("first_air_date") }.takeIf { s -> s.isNotBlank() },
                rating = o.optDouble("vote_average").takeIf { d -> !d.isNaN() && d > 0 }
            )
        }
        return out
    }

    /** Returns the IMDb id (tt...) for a movie or series, or null if TMDB has none. */
    suspend fun imdbId(tmdbId: Long, mediaType: String): String? = withContext(Dispatchers.IO) {
        val path = if (mediaType == "series") "/3/tv/$tmdbId/external_ids" else "/3/movie/$tmdbId/external_ids"
        runCatching { get(path).optString("imdb_id").takeIf { it.startsWith("tt") } }.getOrNull()
    }

    companion object {
        const val IMAGE_BASE = "https://image.tmdb.org/t/p/"
        fun poster(path: String?) = path?.let { "${IMAGE_BASE}w500$it" }
        fun backdrop(path: String?) = path?.let { "${IMAGE_BASE}w780$it" }

        /** Accepts a raw id or any themoviedb.org list URL and returns the list id. */
        fun parseListId(input: String): String? {
            val s = input.trim()
            if (s.isEmpty()) return null
            Regex("""/list/(\d+)""").find(s)?.let { return it.groupValues[1] }
            return s.takeIf { it.all(Char::isDigit) }
        }
    }
}
