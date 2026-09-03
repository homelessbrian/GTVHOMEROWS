package dev.tmdbrows.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tvprovider.media.tv.TvContractCompat
import dev.tmdbrows.db.ListConfig
import dev.tmdbrows.launch.TargetApp
import dev.tmdbrows.tmdb.DiscoverSpec
import dev.tmdbrows.tmdb.MediaKind
import dev.tmdbrows.tmdb.Preset
import dev.tmdbrows.tmdb.SourceKind
import dev.tmdbrows.tmdb.TmdbClient

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Color(0xFF0F1115))) {
                val context = LocalContext.current
                val toApprove by vm.channelToApprove.collectAsStateWithLifecycle()
                val toast by vm.toast.collectAsStateWithLifecycle()
                val builder by vm.builderSpec.collectAsStateWithLifecycle()

                LaunchedEffect(toApprove) {
                    toApprove?.let {
                        // System prompt asking the user to enable the new row on the home screen
                        TvContractCompat.requestChannelBrowsable(this@MainActivity, it)
                        vm.approvalHandled()
                    }
                }
                LaunchedEffect(toast) {
                    toast?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); vm.toastShown() }
                }

                if (builder != null) {
                    BackHandler { vm.closeBuilder() }
                    BuilderScreen(vm)
                } else {
                    SettingsScreen(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
        vm.ensureGenresLoaded()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val configs by vm.configs.collectAsStateWithLifecycle()
    val savedKey by vm.apiKey.collectAsStateWithLifecycle()
    val keyStatus by vm.keyStatus.collectAsStateWithLifecycle()
    val targets by vm.targets.collectAsStateWithLifecycle()
    val defaultTarget by vm.defaultTarget.collectAsStateWithLifecycle()
    val syncMessage by vm.syncMessage.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    var keyInput by remember(savedKey) { mutableStateOf(savedKey) }
    var showAddList by remember { mutableStateOf(false) }
    var showAddPreset by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        Text("TMDB Rows", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("Home screen rows built from TMDB lists, filters, or trending charts", color = Color.Gray)

        SectionTitle("TMDB API key")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = keyInput, onValueChange = { keyInput = it },
                label = { Text("v3 API key or v4 read token") },
                singleLine = true, modifier = Modifier.width(520.dp).tvFocus()
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = { vm.saveAndTestKey(keyInput) }, enabled = !busy, modifier = Modifier.tvFocus()) { Text("Save & test") }
        }
        Text(keyStatus, color = if (keyStatus.startsWith("Failed")) Color(0xFFFF7043) else Color.Gray,
            modifier = Modifier.padding(top = 6.dp))
        Text("Get one free at themoviedb.org → Settings → API", color = Color.DarkGray, fontSize = 13.sp)

        SectionTitle("Open items in (default)")
        ChipGroup {
            if (targets.isEmpty()) Text("No supported apps found (install Stremio or Nuvio)", color = Color(0xFFFFB74D))
            targets.forEach { t -> Chip(t.label, defaultTarget == t.packageName) { vm.setDefaultTarget(t.packageName) } }
        }

        SectionTitle("Rows")
        if (configs.isEmpty()) Text("No rows yet. Add one below.", color = Color.Gray)
        configs.forEach { cfg ->
            RowCard(
                cfg, targets, vm.genreNames,
                onTarget = { vm.setListTarget(cfg, it) },
                onEdit = { vm.openBuilder(cfg) },
                onDuplicate = { vm.duplicateRow(cfg) },
                onRemove = { vm.removeList(cfg) }
            )
        }

        SectionTitle("Add a row")
        ChipGroup {
            Button(onClick = { vm.openBuilder() }, enabled = !busy, modifier = Modifier.tvFocus()) { Text("Build a filter") }
            OutlinedButton(onClick = { showAddPreset = true }, enabled = !busy, modifier = Modifier.tvFocus()) { Text("Trending / popular") }
            OutlinedButton(onClick = { showAddList = true }, enabled = !busy, modifier = Modifier.tvFocus()) { Text("From a TMDB list") }
            OutlinedButton(onClick = { vm.syncAll() }, enabled = !busy, modifier = Modifier.tvFocus()) { Text("Sync now") }
        }
        Text(syncMessage, color = Color.Gray, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            "Tip: if a row doesn't appear, open your launcher's channel/row settings and enable it there.",
            color = Color.DarkGray, fontSize = 13.sp
        )
        Spacer(Modifier.height(40.dp))
    }

    if (showAddList) {
        AddListDialog(targets, onDismiss = { showAddList = false }) { input, name, pkg ->
            vm.addListRow(input, name, pkg); showAddList = false
        }
    }
    if (showAddPreset) {
        AddPresetDialog(targets, onDismiss = { showAddPreset = false }) { preset, kind, name, pkg, max ->
            vm.addPresetRow(preset, kind, name, pkg, max); showAddPreset = false
        }
    }
}

@Composable
private fun RowCard(
    cfg: ListConfig,
    targets: List<TargetApp>,
    genreNames: Map<Int, String>,
    onTarget: (String) -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit
) {
    var confirm by remember { mutableStateOf(false) }
    val kind = runCatching { SourceKind.valueOf(cfg.kind) }.getOrDefault(SourceKind.LIST)
    val subtitle = when (kind) {
        SourceKind.LIST -> "TMDB list ${cfg.tmdbListId}"
        SourceKind.PRESET -> "${Preset.from(cfg.presetId).label} · ${MediaKind.from(cfg.presetMediaKind).label}"
        SourceKind.DISCOVER -> DiscoverSpec.fromJson(cfg.discoverJson).summary(genreNames)
    }

    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1E27))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(cfg.displayName, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle + (if (cfg.channelId == null) " · not published yet" else ""),
                color = Color.Gray, fontSize = 13.sp)

            Spacer(Modifier.height(10.dp))
            Text("Open in:", color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            ChipGroup {
                Chip("Default", cfg.targetPackage.isBlank()) { onTarget("") }
                targets.forEach { t -> Chip(t.label, cfg.targetPackage == t.packageName) { onTarget(t.packageName) } }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (kind == SourceKind.DISCOVER) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.tvFocus()) { Text("Edit filters") }
                }
                OutlinedButton(onClick = onDuplicate, modifier = Modifier.tvFocus()) { Text("Duplicate") }
                OutlinedButton(
                    onClick = { confirm = true }, modifier = Modifier.tvFocus(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF7043))
                ) { Text("Remove") }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Remove \"${cfg.displayName}\"?") },
            text = { Text("The row will be removed from the home screen.") },
            confirmButton = { TextButton(onClick = { confirm = false; onRemove() }, modifier = Modifier.tvFocus()) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirm = false }, modifier = Modifier.tvFocus()) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AddListDialog(targets: List<TargetApp>, onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var input by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a TMDB list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it }, singleLine = true,
                    label = { Text("List URL or ID") }, modifier = Modifier.fillMaxWidth().tvFocus()
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Row name (optional)") }, modifier = Modifier.fillMaxWidth().tvFocus()
                )
                Text("Open in:", color = Color.Gray, fontSize = 13.sp)
                ChipGroup {
                    Chip("Default", pkg.isBlank()) { pkg = "" }
                    targets.forEach { t -> Chip(t.label, pkg == t.packageName) { pkg = t.packageName } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(input, name, pkg) }, modifier = Modifier.tvFocus()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.tvFocus()) { Text("Cancel") } }
    )
}

@Composable
private fun AddPresetDialog(
    targets: List<TargetApp>,
    onDismiss: () -> Unit,
    onAdd: (Preset, MediaKind, String, String, Int) -> Unit
) {
    var kind by remember { mutableStateOf(MediaKind.MOVIE) }
    var preset by remember { mutableStateOf(Preset.TRENDING_WEEK) }
    var name by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("") }
    var max by remember { mutableStateOf(40) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trending & popular") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Content type", color = Color.Gray, fontSize = 13.sp)
                ChipGroup {
                    MediaKind.entries.forEach { k ->
                        Chip(k.label, kind == k) {
                            kind = k
                            if (preset !in Preset.forKind(k)) preset = Preset.TRENDING_WEEK
                        }
                    }
                }
                Text("Chart", color = Color.Gray, fontSize = 13.sp)
                ChipGroup {
                    Preset.forKind(kind).forEach { p -> Chip(p.label, preset == p) { preset = p } }
                }
                Text("Titles in row", color = Color.Gray, fontSize = 13.sp)
                ChipGroup { listOf(20, 40, 60).forEach { n -> Chip("$n", max == n) { max = n } } }
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Row name (optional)") }, modifier = Modifier.fillMaxWidth().tvFocus()
                )
                Text("Open in:", color = Color.Gray, fontSize = 13.sp)
                ChipGroup {
                    Chip("Default", pkg.isBlank()) { pkg = "" }
                    targets.forEach { t -> Chip(t.label, pkg == t.packageName) { pkg = t.packageName } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(preset, kind, name, pkg, max) }, modifier = Modifier.tvFocus()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.tvFocus()) { Text("Cancel") } }
    )
}
