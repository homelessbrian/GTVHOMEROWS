package dev.tmdbrows.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.tmdbrows.channels.ChannelPublisher
import dev.tmdbrows.data.Prefs
import dev.tmdbrows.db.AppDatabase
import dev.tmdbrows.db.CachedItem
import dev.tmdbrows.tmdb.DiscoverSpec
import dev.tmdbrows.tmdb.MediaKind
import dev.tmdbrows.tmdb.Preset
import dev.tmdbrows.tmdb.SourceKind
import dev.tmdbrows.tmdb.TmdbClient
import dev.tmdbrows.tmdb.TmdbItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        val key = prefs.tmdbKey
        if (key.isBlank()) {
            prefs.lastSyncMessage = "No TMDB API key set"
            return Result.success()
        }
        val db = AppDatabase.get(ctx)
        val client = TmdbClient(key)
        val onlyConfig = inputData.getLong(KEY_CONFIG_ID, -1L).takeIf { it > 0 }
        val configs = db.configs().getAll().filter { onlyConfig == null || it.id == onlyConfig }

        var errors = 0
        var tiles = 0
        for (cfg in configs) {
            try {
                val fetched: List<TmdbItem> = when (SourceKind.valueOf(cfg.kind)) {
                    SourceKind.LIST -> client.fetchList(cfg.tmdbListId).items
                    SourceKind.DISCOVER -> client.discover(DiscoverSpec.fromJson(cfg.discoverJson))
                    SourceKind.PRESET -> client.preset(
                        Preset.from(cfg.presetId),
                        MediaKind.from(cfg.presetMediaKind),
                        cfg.presetMaxItems
                    )
                }
                val channelId = ChannelPublisher.ensureChannel(ctx, cfg)
                if (cfg.channelId != channelId) db.configs().update(cfg.copy(channelId = channelId))

                val cached = db.items().forConfig(cfg.id).associateBy { "${it.mediaType}:${it.tmdbId}" }
                val items = fetched.map { t ->
                    val prev = cached["${t.mediaType}:${t.tmdbId}"]
                    // Only hit external_ids once per item; IMDb ids don't change.
                    val imdb = prev?.imdbId ?: client.imdbId(t.tmdbId, t.mediaType)
                    CachedItem(
                        configId = cfg.id, tmdbId = t.tmdbId, mediaType = t.mediaType,
                        title = t.title, overview = t.overview,
                        posterPath = t.posterPath, backdropPath = t.backdropPath,
                        releaseDate = t.releaseDate, rating = t.rating, imdbId = imdb,
                        programId = prev?.programId
                    )
                }
                val published = ChannelPublisher.publishPrograms(ctx, cfg.copy(channelId = channelId), channelId, items)
                db.items().deleteNotIn(cfg.id, published.map { it.tmdbId }.ifEmpty { listOf(-1L) })
                db.items().upsertAll(published)
                tiles += published.size
            } catch (e: Exception) {
                errors++
                prefs.lastSyncMessage = "Error on \"${cfg.displayName}\": ${e.message}"
            }
        }
        if (errors == 0) {
            val t = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date())
            prefs.lastSyncMessage = "Synced ${configs.size} list(s), $tiles tiles at $t"
        }
        return Result.success()
    }

    companion object { const val KEY_CONFIG_ID = "config_id" }
}
