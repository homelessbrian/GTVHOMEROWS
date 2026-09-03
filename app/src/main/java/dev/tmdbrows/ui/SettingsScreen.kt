package dev.tmdbrows.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tmdbrows.tmdb.Artwork

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val savedKey by vm.apiKey.collectAsStateWithLifecycle()
    val keyStatus by vm.keyStatus.collectAsStateWithLifecycle()
    val artPattern by vm.artPattern.collectAsStateWithLifecycle()
    val artEnabled by vm.artEnabled.collectAsStateWithLifecycle()
    val artCheck by vm.artCheckResult.collectAsStateWithLifecycle()
    val syncMessage by vm.syncMessage.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var keyInput by remember(savedKey) { mutableStateOf(savedKey) }

    Column {
        SectionTitle("TMDB API key", topPad = 0)
        DarkField(keyInput, { keyInput = it }, "v3 API key or v4 read token", Modifier.width(560.dp))
        Spacer(Modifier.height(10.dp))
        PillRow { Pill("Save and test", primary = true) { if (!busy) vm.saveAndTestKey(keyInput) } }
        Text(
            keyStatus, fontSize = 13.sp,
            color = if (keyStatus.startsWith("Failed")) T.danger else T.textDim,
            modifier = Modifier.padding(top = 10.dp)
        )
        Hint("Free at themoviedb.org → Settings → API")

        SectionTitle("Poster artwork")
        Text(
            "Tiles use TMDB artwork by default. Poster rows can pull from another provider instead, " +
                "such as btttr.cc or RPDB.",
            color = T.textDim, fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        DarkField(artPattern, { vm.setArtPattern(it) }, "Artwork URL containing {imdb}", Modifier.width(560.dp))
        Hint(Artwork.PLACEHOLDER_HELP)
        Hint("For btttr.cc: set your options at btttr.cc/configure, copy the poster URL, and swap the title id for {imdb}.")
        Spacer(Modifier.height(12.dp))
        PillRow {
            Pill("Save") { vm.saveArtPattern() }
            Pill("Check URL") { vm.checkArtPattern() }
            Pill(if (artEnabled) "Custom artwork on" else "Custom artwork off", selected = artEnabled) {
                vm.setArtEnabled(!artEnabled)
            }
        }
        if (artCheck.isNotBlank()) {
            Text(
                artCheck, fontSize = 13.sp,
                color = if (artCheck.startsWith("Works") || artCheck == "Saved") T.accent else T.warn,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        Hint("Landscape rows always use TMDB backdrops — these providers render tall posters.")

        SectionTitle("Sync")
        Text(syncMessage, color = T.textDim, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        PillRow { Pill("Sync now") { vm.syncAll() } }
        Hint("Rows refresh every 6 hours on their own, and after a reboot.")
        Hint("If a row doesn't appear, enable it in your launcher's channel settings.")
        Spacer(Modifier.height(28.dp))
    }
}
