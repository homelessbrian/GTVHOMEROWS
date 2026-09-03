package dev.tmdbrows.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tvprovider.media.tv.TvContractCompat
import dev.tmdbrows.db.ListConfig
import dev.tmdbrows.launch.TargetApp

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF01D2AA), background = Color(0xFF0F1115))) {
                val toApprove by vm.channelToApprove.collectAsStateWithLifecycle()
                LaunchedEffect(toApprove) {
                    toApprove?.let {
                        // System prompt asking the user to enable the new row on the home screen
                        TvContractCompat.requestChannelBrowsable(this@MainActivity, it)
                        vm.approvalHandled()
                    }
                }
                SettingsScreen(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}

/** Highlight whatever the D-pad is focused on so TV navigation is obvious. */
@Composable
fun Modifier.tvFocus(): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .border(BorderStroke(2.dp, if (focused) Color(0xFF01D2AA) else Color.Transparent), MaterialTheme.shapes.small)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
private fun TargetPicker(targets: List<TargetApp>, selected: String, allowDefault: Boolean, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allowDefault) {
            TargetChip("Default", selected.isBlank()) { onSelect("") }
        }
        if (targets.isEmpty()) {
            Text("No supported apps found (install Stremio or Nuvio)", color = Color(0xFFFFB74D))
        }
        targets.forEach { t -> TargetChip(t.label, selected == t.packageName) { onSelect(t.packageName) } }
    }
}

@Composable
private fun TargetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = Modifier.tvFocus()) { Text(label) }
    else OutlinedButton(onClick = onClick, modifier = Modifier.tvFocus()) { Text(label) }
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
    var showAdd by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        Text("TMDB Rows", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("Show TMDB lists as rows on your home screen", color = Color.Gray)

        SectionTitle("TMDB API key")
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = keyInput, onValueChange = { keyInput = it },
                label = { Text("v3 API key or v4 read token") },
                singleLine = true, modifier = Modifier.width(520.dp).tvFocus()
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = { vm.saveAndTestKey(keyInput) }, enabled = !busy, modifier = Modifier.tvFocus()) { Text("Save & test") }
        }
        Text(keyStatus, color = if (keyStatus.startsWith("Failed")) Color(0xFFFF7043) else Color.Gray, modifier = Modifier.padding(top = 6.dp))
        Text("Get one free at themoviedb.org → Settings → API", color = Color.DarkGray, fontSize = 13.sp)

        SectionTitle("Open items in (default)")
        TargetPicker(targets, defaultTarget, allowDefault = false) { vm.setDefaultTarget(it) }

        SectionTitle("Lists")
        if (configs.isEmpty()) Text("No lists yet. Add one below.", color = Color.Gray)
        configs.forEach { cfg ->
            ListRow(cfg, targets, onTarget = { vm.setListTarget(cfg, it) }, onRemove = { vm.removeList(cfg) })
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { showAdd = true }, enabled = !busy, modifier = Modifier.tvFocus()) { Text("+ Add list") }
            OutlinedButton(onClick = { vm.syncAll() }, enabled = !busy, modifier = Modifier.tvFocus()) { Text("Sync now") }
        }
        Text(syncMessage, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            "Tip: if a row doesn't appear, open your launcher's channel/row settings and enable it.",
            color = Color.DarkGray, fontSize = 13.sp
        )
    }

    if (showAdd) {
        AddListDialog(targets, onDismiss = { showAdd = false }) { input, name, pkg ->
            vm.addList(input, name, pkg) { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
            showAdd = false
        }
    }
}

@Composable
private fun ListRow(cfg: ListConfig, targets: List<TargetApp>, onTarget: (String) -> Unit, onRemove: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1E27))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(cfg.displayName, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("List ${cfg.tmdbListId}" + (if (cfg.channelId == null) " · not published yet" else ""), color = Color.Gray, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { confirm = true }, modifier = Modifier.tvFocus(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF7043))
                ) { Text("Remove") }
            }
            Spacer(Modifier.height(10.dp))
            Text("Open in:", color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            TargetPicker(targets, cfg.targetPackage, allowDefault = true, onSelect = onTarget)
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
                    label = { Text("List URL or ID (e.g. 8231164)") }, modifier = Modifier.fillMaxWidth().tvFocus()
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Row name (optional — uses the list's name)") }, modifier = Modifier.fillMaxWidth().tvFocus()
                )
                Text("Open in:", color = Color.Gray, fontSize = 13.sp)
                TargetPicker(targets, pkg, allowDefault = true) { pkg = it }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(input, name, pkg) }, modifier = Modifier.tvFocus()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.tvFocus()) { Text("Cancel") } }
    )
}
