package dev.tmdbrows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AppsScreen(vm: MainViewModel) {
    val targets by vm.targets.collectAsStateWithLifecycle()
    val defaultTarget by vm.defaultTarget.collectAsStateWithLifecycle()
    val customTargets by vm.customTargets.collectAsStateWithLifecycle()

    Column {
        Text("Which app opens a title when you select it. Rows can override this individually.",
            color = T.textDim, fontSize = 14.sp)

        SectionTitle("Default app")
        if (targets.isEmpty()) {
            Text("Nothing configured yet. Install Stremio or Nuvio, or add an app below.",
                color = T.warn, fontSize = 13.sp)
        }
        PillRow {
            targets.forEach { t ->
                Pill(t.label, defaultTarget == t.packageName) { vm.setDefaultTarget(t.packageName) }
            }
        }

        SectionTitle("Your apps")
        if (customTargets.isEmpty()) {
            Text("Stremio and Nuvio are detected automatically. Add any other app by giving it a link format.",
                color = T.textFaint, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
        }
        customTargets.forEach { ct ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(10.dp)).background(T.raised)
                    .border(1.dp, T.border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(ct.label, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(ct.template, color = T.textFaint, fontSize = 12.sp)
                }
                Pill("Edit") { vm.openTargetEditor(ct) }
            }
        }
        Spacer(Modifier.height(6.dp))
        PillRow { Pill("Add an app", primary = true) { vm.openTargetEditor() } }
        Spacer(Modifier.height(28.dp))
    }
}
