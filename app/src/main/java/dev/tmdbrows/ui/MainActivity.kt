package dev.tmdbrows.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tvprovider.media.tv.TvContractCompat

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = T.accent, background = T.bg)) {
                val context = LocalContext.current
                val toApprove by vm.channelToApprove.collectAsStateWithLifecycle()
                val toast by vm.toast.collectAsStateWithLifecycle()
                val builder by vm.builderSpec.collectAsStateWithLifecycle()
                val catalogOpen by vm.catalogOpen.collectAsStateWithLifecycle()
                val targetEditor by vm.targetEditor.collectAsStateWithLifecycle()

                LaunchedEffect(toApprove) {
                    toApprove?.let {
                        TvContractCompat.requestChannelBrowsable(this@MainActivity, it)
                        vm.approvalHandled()
                    }
                }
                LaunchedEffect(toast) {
                    toast?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); vm.toastShown() }
                }

                Box(Modifier.fillMaxSize().background(T.bg)) {
                    when {
                        builder != null -> {
                            BackHandler { vm.closeBuilder() }
                            BuilderScreen(vm)
                        }
                        catalogOpen -> {
                            BackHandler { vm.closeCatalog() }
                            CatalogScreen(vm)
                        }
                        targetEditor != null -> {
                            BackHandler { vm.closeTargetEditor() }
                            TargetEditorScreen(vm)
                        }
                        else -> Shell(vm)
                    }
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

/** Nav rail plus the active screen. */
@Composable
private fun Shell(vm: MainViewModel) {
    val dest by vm.destination.collectAsStateWithLifecycle()
    val configs by vm.configs.collectAsStateWithLifecycle()
    val syncMessage by vm.syncMessage.collectAsStateWithLifecycle()

    if (dest != Dest.ROWS) BackHandler { vm.go(Dest.ROWS) }

    Row(Modifier.fillMaxSize()) {
        NavRail(dest) { vm.go(it) }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 32.dp, end = 48.dp, top = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (dest) {
                            Dest.ROWS -> "Your rows"
                            Dest.ADD -> "Add a row"
                            Dest.APPS -> "Apps"
                            Dest.SETTINGS -> "Settings"
                        },
                        color = T.text, fontSize = 26.sp, fontWeight = FontWeight.Medium
                    )
                    if (dest == Dest.ROWS && configs.isNotEmpty()) {
                        Text(
                            "${configs.size} row${if (configs.size == 1) "" else "s"} published",
                            color = T.textDim, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                if (dest == Dest.ROWS) {
                    Text(syncMessage, color = T.textFaint, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            when (dest) {
                Dest.ROWS -> RowsScreen(vm)
                Dest.ADD -> AddScreen(vm)
                Dest.APPS -> AppsScreen(vm)
                Dest.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}

@Composable
private fun NavRail(current: Dest, onSelect: (Dest) -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(72.dp).background(T.surface).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(T.accent),
            contentAlignment = Alignment.Center
        ) { Text("T", color = T.accentInk, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        Spacer(Modifier.height(10.dp))
        Dest.entries.forEach { d -> RailItem(d, current == d) { onSelect(d) } }
    }
}

@Composable
private fun RailItem(dest: Dest, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> T.accent
        selected -> T.accentDim
        else -> T.surface
    }
    val fg = when {
        focused -> T.accentInk
        selected -> T.accent
        else -> T.textFaint
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .onFocusChanged { focused = it.isFocused }
                .size(42.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(bg)
                .border(1.dp, if (focused) T.accent else T.surface, RoundedCornerShape(11.dp))
                .clickableRow(onClick),
            contentAlignment = Alignment.Center
        ) { Text(dest.glyph, color = fg, fontSize = 18.sp) }
        Text(
            dest.label, color = if (selected || focused) T.accent else T.textFaint,
            fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp)
        )
    }
}
