package dev.tmdbrows.channels

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import dev.tmdbrows.db.CachedItem
import dev.tmdbrows.db.ListConfig
import dev.tmdbrows.tmdb.TmdbClient

/** Creates/updates Android TV home-screen channels (rows) and their programs (tiles). */
object ChannelPublisher {

    /** Ensures a channel exists for this config; returns its id. */
    fun ensureChannel(context: Context, config: ListConfig): Long {
        val resolver = context.contentResolver
        val existingId = config.channelId?.takeIf { channelExists(context, it) }
        val settingsUri = Uri.parse("tmdbrows://settings")

        val builder = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(config.displayName)
            .setAppLinkIntentUri(settingsUri)
            .setInternalProviderId("config-${config.id}")

        return if (existingId != null) {
            resolver.update(
                TvContractCompat.buildChannelUri(existingId),
                builder.build().toContentValues(), null, null
            )
            existingId
        } else {
            val uri = resolver.insert(TvContractCompat.Channels.CONTENT_URI, builder.build().toContentValues())
                ?: error("Failed to create channel")
            val id = ContentUris.parseId(uri)
            ChannelLogoUtils.storeChannelLogo(context, id, makeLogo(config.displayName))
            id
        }
    }

    fun channelExists(context: Context, channelId: Long): Boolean {
        context.contentResolver.query(
            TvContractCompat.buildChannelUri(channelId),
            arrayOf(TvContractCompat.Channels._ID), null, null, null
        )?.use { return it.count > 0 }
        return false
    }

    fun deleteChannel(context: Context, channelId: Long) {
        context.contentResolver.delete(TvContractCompat.buildChannelUri(channelId), null, null)
    }

    /**
     * Replace the channel's programs with [items], reusing existing program rows where possible.
     * Returns the items with their programId filled in.
     */
    fun publishPrograms(context: Context, config: ListConfig, channelId: Long, items: List<CachedItem>): List<CachedItem> {
        val resolver = context.contentResolver
        val existing = existingPrograms(context, channelId) // internalProviderId -> programId
        val keep = mutableSetOf<Long>()
        val out = mutableListOf<CachedItem>()

        items.forEachIndexed { index, item ->
            val key = "${item.mediaType}:${item.tmdbId}"
            val program = buildProgram(config, channelId, item, index)
            val values = program.toContentValues()
            val programId = existing[key]?.also { id ->
                resolver.update(TvContractCompat.buildPreviewProgramUri(id), values, null, null)
            } ?: resolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, values)
                ?.let { ContentUris.parseId(it) }
            if (programId != null) keep += programId
            out += item.copy(programId = programId)
        }

        // Remove tiles for items no longer in the list
        existing.values.filterNot { it in keep }.forEach { id ->
            resolver.delete(TvContractCompat.buildPreviewProgramUri(id), null, null)
        }
        return out
    }

    private fun buildProgram(config: ListConfig, channelId: Long, item: CachedItem, weight: Int): PreviewProgram {
        val intentUri = Uri.parse("tmdbrows://open?config=${config.id}&type=${item.mediaType}&tmdb=${item.tmdbId}")
        val b = PreviewProgram.Builder()
            .setChannelId(channelId)
            .setType(if (item.mediaType == "series") TvContractCompat.PreviewPrograms.TYPE_TV_SERIES else TvContractCompat.PreviewPrograms.TYPE_MOVIE)
            .setTitle(item.title)
            .setDescription(item.overview.take(500))
            .setIntentUri(intentUri)
            .setInternalProviderId("${item.mediaType}:${item.tmdbId}")
            .setWeight(Int.MAX_VALUE - weight) // preserve list order

        val poster = TmdbClient.poster(item.posterPath)
        val backdrop = TmdbClient.backdrop(item.backdropPath)
        if (poster != null) {
            b.setPosterArtUri(Uri.parse(poster))
            b.setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
        } else if (backdrop != null) {
            b.setPosterArtUri(Uri.parse(backdrop))
            b.setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
        }
        if (backdrop != null) b.setThumbnailUri(Uri.parse(backdrop))
        item.releaseDate?.let { b.setReleaseDate(it) }
        item.rating?.let {
            b.setReviewRatingStyle(TvContractCompat.PreviewPrograms.REVIEW_RATING_STYLE_PERCENTAGE)
            b.setReviewRating(String.format(java.util.Locale.US, "%.0f", it * 10))
        }
        return b.build()
    }

    private fun existingPrograms(context: Context, channelId: Long): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        context.contentResolver.query(
            TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
            arrayOf(TvContractCompat.PreviewPrograms._ID, TvContractCompat.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val key = c.getString(1) ?: continue
                map[key] = c.getLong(0)
            }
        }
        return map
    }

    /** Simple generated logo so the channel is accepted by the launcher (logo is required). */
    private fun makeLogo(name: String): Bitmap {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(14, 22, 44))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 120f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "T"
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        c.drawText(initial, size / 2f, y, paint)
        return bmp
    }
}
