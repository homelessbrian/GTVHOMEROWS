package dev.tmdbrows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tmdbrows.tmdb.Catalog
import dev.tmdbrows.tmdb.CatalogEntry

@Composable
fun CatalogScreen(vm: MainViewModel) {
    val configs by vm.configs.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val addedIds = configs.map { it.catalogId }.filter { it.isNotBlank() }.toSet()

    Column(
        Modifier.fillMaxSize().background(T.bg)
            .verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        Text("Ready-made rows", color = T.text, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        Text("Add one in a press. Filter rows stay editable afterwards.", color = T.textDim, fontSize = 14.sp)

        Catalog.categories.forEach { category ->
            SectionTitle(category)
            Catalog.entries.filter { it.category == category }.forEach { entry ->
                EntryCard(entry, entry.id in addedIds, busy) { vm.addFromCatalog(entry) }
            }
        }

        Spacer(Modifier.height(24.dp))
        PillRow { Pill("Done", primary = true) { vm.closeCatalog() } }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun EntryCard(entry: CatalogEntry, added: Boolean, busy: Boolean, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp)
            .clip(RoundedCornerShape(10.dp)).background(T.raised)
            .border(1.dp, T.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.title, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(entry.blurb, color = T.textDim, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        if (added) Text("Added", color = T.accent, fontSize = 13.sp)
        else Pill("Add") { if (!busy) onAdd() }
    }
}
