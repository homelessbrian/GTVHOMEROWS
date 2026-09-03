package dev.tmdbrows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.tmdbrows.db.CachedItem
import dev.tmdbrows.db.ListConfig
import dev.tmdbrows.launch.TargetApp
import dev.tmdbrows.tmdb.ArtStyle
import dev.tmdbrows.tmdb.Artwork
import dev.tmdbrows.tmdb.DiscoverSpec
import dev.tmdbrows.tmdb.MediaKind
import dev.tmdbrows.tmdb.Preset
import dev.tmdbrows.tmdb.SourceKind

@Composable
fun RowsScreen(vm: MainViewModel) {
    val configs by vm.configs.collectAsStateWithLifecycle()
    val targets by vm.targets.collectAsStateWithLifecycle()
    val artPattern by vm.artPattern.collectAsStateWithLifecycle()
    val artEnabled by vm.artEnabled.collectAsStateWithLifecycle()

    if (configs.isEmpty()) {
        EmptyRows(vm)
        return
    }

    Column {
        configs.forEach { cfg ->
            RowCard(vm, cfg, targets, artPattern, artEnabled)
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun EmptyRows(vm: MainViewModel) {
    Column(Modifier.padding(top = 40.dp)) {
        Text("No rows yet", color = T.text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text(
            "Add a ready-made row, build your own filter, or pull in a TMDB list.",
            color = T.textDim, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )
        PillRow {
            Pill("Browse ready-made rows", primary = true) { vm.openCatalog() }
            Pill("Build a filter") { vm.openBuilder() }
        }
    }
}

@Composable
private fun RowCard(
    vm: MainViewModel,
    cfg: ListConfig,
    targets: List<TargetApp>,
    artPattern: String,
    artEnabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val preview by remember(cfg.id) { vm.previewFor(cfg.id) }.collectAsStateWithLifecycle(emptyList())
    val total by remember(cfg.id) { vm.countFor(cfg.id) }.collectAsStateWithLifecycle(0)

    val kind = runCatching { SourceKind.valueOf(cfg.kind) }.getOrDefault(SourceKind.LIST)
    val style = ArtStyle.from(cfg.artStyle)
    val subtitle = when (kind) {
        SourceKind.LIST -> "TMDB list ${cfg.tmdbListId}"
        SourceKind.PRESET -> "${Preset.from(cfg.presetId).label} · ${MediaKind.from(cfg.presetMediaKind).label}"
        SourceKind.DISCOVER -> DiscoverSpec.fromJson(cfg.discoverJson).summary(vm.genreNames)
    }
    val targetLabel = targets.firstOrNull { it.packageName == cfg.targetPackage }?.label
        ?: if (cfg.targetPackage.isBlank()) "Default" else "Missing app"

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(T.raised)
            .border(1.dp, T.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(cfg.displayName, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = T.textDim, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(T.accentDim)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) { Text(targetLabel, color = T.accent, fontSize = 11.sp) }
        }

        if (preview.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PreviewStrip(preview, total, style, artPattern, artEnabled)
        } else if (cfg.channelId == null) {
            Spacer(Modifier.height(8.dp))
            Text("Not published yet", color = T.warn, fontSize = 12.sp)
        } else {
            Spacer(Modifier.height(8.dp))
            Text("Waiting for first sync…", color = T.textFaint, fontSize = 12.sp)
        }

        Spacer(Modifier.height(12.dp))
        PillRow {
            Pill(if (expanded) "Hide options" else "Options") { expanded = !expanded }
            if (kind == SourceKind.DISCOVER) Pill("Edit filters") { vm.openBuilder(cfg) }
        }

        if (expanded) {
            FieldLabel("Tile shape")
            PillRow {
                ArtStyle.entries.forEach { st ->
                    Pill(st.label, cfg.artStyle == st.id) { vm.setRowArtStyle(cfg, st) }
                }
            }
            FieldLabel("Open in")
            PillRow {
                Pill("Default", cfg.targetPackage.isBlank()) { vm.setListTarget(cfg, "") }
                targets.forEach { t ->
                    Pill(t.label, cfg.targetPackage == t.packageName) { vm.setListTarget(cfg, t.packageName) }
                }
            }
            Spacer(Modifier.height(14.dp))
            PillRow {
                Pill("Duplicate") { vm.duplicateRow(cfg) }
                Pill("Remove", danger = true) { confirm = true }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            containerColor = T.raised,
            onDismissRequest = { confirm = false },
            title = { Text("Remove \"${cfg.displayName}\"?", color = T.text, fontSize = 17.sp) },
            text = { Text("The row disappears from the home screen.", color = T.textDim, fontSize = 14.sp) },
            confirmButton = { TextButton(onClick = { confirm = false; vm.removeList(cfg) }) { Text("Remove", color = T.danger) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel", color = T.textDim) } }
        )
    }
}

/** Six thumbnails and a count — enough to tell at a glance whether the row is any good. */
@Composable
private fun PreviewStrip(
    items: List<CachedItem>,
    total: Int,
    style: ArtStyle,
    artPattern: String,
    artEnabled: Boolean
) {
    val w = if (style == ArtStyle.LANDSCAPE) 92.dp else 40.dp
    val h = if (style == ArtStyle.LANDSCAPE) 52.dp else 60.dp
    val shown = items.take(6)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { item ->
            val url = Artwork.urlFor(item, style, artPattern, artEnabled)
            Box(Modifier.width(w).height(h).clip(RoundedCornerShape(4.dp)).background(T.surface)) {
                if (url != null) {
                    AsyncImage(
                        model = url, contentDescription = item.title,
                        contentScale = ContentScale.Crop, modifier = Modifier.size(w, h)
                    )
                }
            }
        }
        val more = total - shown.size
        if (more > 0) {
            Box(
                Modifier.width(w).height(h).clip(RoundedCornerShape(4.dp)).background(T.surface),
                contentAlignment = Alignment.Center
            ) { Text("+$more", color = T.textDim, fontSize = 12.sp) }
        }
    }
}
