package dev.tmdbrows.launch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dev.tmdbrows.db.CachedItem
import dev.tmdbrows.db.CustomTarget

/**
 * An app a tile can open in. Either one of the built-ins (discovered by probing for a
 * URI scheme it registers) or a user-defined target with its own URI template.
 */
data class TargetApp(
    val packageName: String,
    val label: String,
    /** Built-in scheme, or empty for a custom target. */
    val scheme: String = "",
    /** Set for custom targets: the URI template with {imdb}/{tmdb}/{type}/{title} placeholders. */
    val template: String? = null,
    val customId: Long? = null
) {
    val isCustom get() = template != null
}

object Targets {

    /** Probe URIs used to find which installed apps register each built-in scheme. */
    private val probes = listOf(
        "nuvio" to "nuvio://tmdb/movie/1",
        "stremio" to "stremio:///detail/movie/tt0111161/tt0111161"
    )

    /**
     * Starting points for apps whose schemes we haven't verified. The user picks the app,
     * edits the template if needed, and presses Test to see whether it actually works.
     */
    val suggestedTemplates: List<Pair<String, String>> = listOf(
        "Syncler style" to "syncler://detail/{type}/{imdb}",
        "CloudStream style" to "cloudstream://open?imdb={imdb}&type={type}",
        "Vidi style" to "vidi://detail/{type}/{imdb}",
        "Trakt style" to "trakt://{type}/{imdb}",
        "JustWatch style" to "https://www.justwatch.com/us/search?q={title}",
        "Search by title" to "{scheme}:///search?search={title}"
    )

    /** Apps that register one of our built-in schemes. */
    fun builtIns(context: Context): List<TargetApp> {
        val pm = context.packageManager
        val found = linkedMapOf<String, TargetApp>()
        for ((scheme, probe) in probes) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(probe))
            for (ri in pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName || found.containsKey(pkg)) continue
                found[pkg] = TargetApp(pkg, ri.loadLabel(pm)?.toString() ?: pkg, scheme)
            }
        }
        return found.values.toList()
    }

    /** Every installed app that can be launched — the pool a custom target is chosen from. */
    fun launchableApps(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val seen = linkedMapOf<String, String>()
        listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER).forEach { category ->
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName || seen.containsKey(pkg)) continue
                seen[pkg] = ri.loadLabel(pm)?.toString() ?: pkg
            }
        }
        return seen.entries.map { it.key to it.value }.sortedBy { it.second.lowercase() }
    }

    fun all(context: Context, custom: List<CustomTarget>): List<TargetApp> =
        builtIns(context) + custom.map {
            TargetApp(it.packageName, it.label, template = it.template, customId = it.id)
        }

    fun find(context: Context, custom: List<CustomTarget>, packageName: String): TargetApp? =
        all(context, custom).firstOrNull { it.packageName == packageName }

    /**
     * Fill a template's placeholders. Returns null when the template needs an id the item
     * doesn't have — better to fall back than to fire a URI with a literal "{imdb}" in it.
     */
    fun fillTemplate(template: String, item: CachedItem): String? {
        var out = template
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

    /** Build the launch intent for an item in a given target, or null if unsupported. */
    fun intentFor(target: TargetApp, item: CachedItem): Intent? {
        val uri = if (target.isCustom) {
            Uri.parse(fillTemplate(target.template!!, item) ?: return null)
        } else when (target.scheme) {
            "nuvio" -> Uri.parse("nuvio://tmdb/${item.mediaType}/${item.tmdbId}")
            "stremio" -> {
                val imdb = item.imdbId
                when {
                    imdb == null -> Uri.parse("stremio:///search?search=${Uri.encode(item.title)}")
                    item.mediaType == "movie" -> Uri.parse("stremio:///detail/movie/$imdb/$imdb")
                    else -> Uri.parse("stremio:///detail/series/$imdb")
                }
            }
            else -> return null
        }
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(target.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** A stand-in item used by the Test button so the user sees a real result immediately. */
    fun sampleItem() = CachedItem(
        configId = -1, tmdbId = 27205, mediaType = "movie", title = "Inception",
        overview = "", posterPath = null, backdropPath = null,
        releaseDate = "2010-07-15", rating = 8.4, imdbId = "tt1375666"
    )
}
