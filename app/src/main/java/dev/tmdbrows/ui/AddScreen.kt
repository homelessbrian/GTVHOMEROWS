package dev.tmdbrows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AddScreen(vm: MainViewModel) {
    var listInput by remember { mutableStateOf("") }
    var listName by remember { mutableStateOf("") }

    Column {
        Text("Three ways to build a row.", color = T.textDim, fontSize = 14.sp)

        Spacer(Modifier.height(18.dp))
        Option(
            "Ready-made rows",
            "32 curated rows — trending, by decade, by mood, by language. One press each.",
            "Browse"
        ) { vm.openCatalog() }

        Option(
            "Build a filter",
            "Pick genres, years, ratings, runtime, streaming service. Live count as you go.",
            "Open builder"
        ) { vm.openBuilder() }

        Column(
            Modifier.fillMaxWidth().padding(bottom = 10.dp)
                .clip(RoundedCornerShape(12.dp)).background(T.raised)
                .border(1.dp, T.border, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text("From a TMDB list", color = T.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("Paste a list URL or its numeric id.", color = T.textDim, fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            DarkField(listInput, { listInput = it }, "themoviedb.org/list/…", Modifier.width(520.dp))
            Spacer(Modifier.height(8.dp))
            DarkField(listName, { listName = it }, "Row name (optional)", Modifier.width(520.dp))
            Spacer(Modifier.height(12.dp))
            PillRow {
                Pill("Add list", primary = true) {
                    vm.addListRow(listInput, listName, "")
                    listInput = ""; listName = ""
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun Option(title: String, blurb: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp)
            .clip(RoundedCornerShape(12.dp)).background(T.raised)
            .border(1.dp, T.border, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(blurb, color = T.textDim, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Pill(action, primary = true, onClick = onClick)
    }
}
