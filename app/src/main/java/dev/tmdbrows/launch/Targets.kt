package dev.tmdbrows.launch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dev.tmdbrows.db.CachedItem

data class TargetApp(val packageName: String, val label: String, val scheme: String)

/** Discovers installed apps that can receive our deep links and builds the right link per app. */
object Targets {

    private val probes = listOf(
        "nuvio" to "nuvio://tmdb/movie/1",
        "stremio" to "stremio:///detail/movie/tt0111161/tt0111161"
    )

    fun installed(context: Context): List<TargetApp> {
        val pm = context.packageManager
        val found = linkedMapOf<String, TargetApp>()
        for ((scheme, probe) in probes) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(probe))
            val infos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            for (ri in infos) {
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName || found.containsKey(pkg)) continue
                val label = ri.loadLabel(pm)?.toString() ?: pkg
                found[pkg] = TargetApp(pkg, label, scheme)
            }
        }
        return found.values.toList()
    }

    fun find(context: Context, packageName: String): TargetApp? =
        installed(context).firstOrNull { it.packageName == packageName }

    /** Build the launch intent for an item in a given target app, or null if unsupported. */
    fun intentFor(target: TargetApp, item: CachedItem): Intent? {
        val uri = when (target.scheme) {
            "nuvio" -> Uri.parse("nuvio://tmdb/${item.mediaType}/${item.tmdbId}")
            "stremio" -> {
                val imdb = item.imdbId
                if (imdb != null) {
                    if (item.mediaType == "movie") Uri.parse("stremio:///detail/movie/$imdb/$imdb")
                    else Uri.parse("stremio:///detail/series/$imdb")
                } else {
                    Uri.parse("stremio:///search?search=${Uri.encode(item.title)}")
                }
            }
            else -> return null
        }
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(target.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
