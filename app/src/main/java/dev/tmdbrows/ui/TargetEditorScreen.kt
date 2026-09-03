package dev.tmdbrows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tmdbrows.launch.Targets

@Composable
fun TargetEditorScreen(vm: MainViewModel) {
    val draft = vm.targetEditor.collectAsStateWithLifecycle().value ?: return
    val apps by vm.installedApps.collectAsStateWithLifecycle()
    val result by vm.targetTestResult.collectAsStateWithLifecycle()
    val existing = draft.id != 0L

    Column(
        Modifier.fillMaxSize().background(T.bg)
            .verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        Text(if (existing) "Edit app" else "Add an app", color = T.text, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        Text("Point tiles at any installed app using its own link format.", color = T.textDim, fontSize = 14.sp)

        FieldLabel("App")
        if (apps.isEmpty()) Text("Loading installed apps…", color = T.textFaint, fontSize = 13.sp)
        ChipGroup {
            apps.forEach { (pkg, label) ->
                Chip(label, draft.packageName == pkg) {
                    vm.updateTargetDraft { it.copy(packageName = pkg, label = it.label.ifBlank { label }) }
                }
            }
        }

        FieldLabel("Link template")
        DarkField(draft.template, { v -> vm.updateTargetDraft { it.copy(template = v) } },
            "e.g. myapp://detail/{type}/{imdb}", Modifier.width(560.dp))
        Hint("Placeholders: {imdb} {tmdb} {type} {tvtype} {title} {year}. {type} is movie or series; {tvtype} is movie or tv.")

        FieldLabel("Starting points")
        Text(
            "Unverified guesses at each app's format — try one, then edit until Test lands.",
            color = T.textFaint, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)
        )
        ChipGroup {
            Targets.suggestedTemplates.forEach { (name, tpl) ->
                Chip(name, draft.template == tpl) { vm.updateTargetDraft { it.copy(template = tpl) } }
            }
        }

        FieldLabel("Display name")
        DarkField(draft.label, { v -> vm.updateTargetDraft { it.copy(label = v) } },
            "Shown on the row's Open in picker", Modifier.width(560.dp))

        Spacer(Modifier.height(20.dp))
        PillRow {
            Pill("Test with Inception") { vm.testTarget() }
            Pill("Save", primary = true) { vm.saveTarget() }
            Pill("Cancel") { vm.closeTargetEditor() }
            if (existing) Pill("Delete", danger = true) { vm.deleteTarget(draft) }
        }
        if (result.isNotBlank()) {
            Text(result, color = T.textDim, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        }
        Hint("Test opens the app for real. Landing on Inception means the template works; the home screen or nothing means it doesn't.")
        Spacer(Modifier.height(40.dp))
    }
}
