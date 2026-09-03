package dev.tmdbrows.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tmdbrows.tmdb.MediaKind
import dev.tmdbrows.tmdb.SORT_OPTIONS
import java.util.Calendar

private val THIS_YEAR = Calendar.getInstance().get(Calendar.YEAR)
private const val EARLIEST_YEAR = 1900

@Composable
fun BuilderScreen(vm: MainViewModel) {
    val spec = vm.builderSpec.collectAsStateWithLifecycle().value ?: return
    val genres by vm.genres.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val count by vm.matchCount.collectAsStateWithLifecycle()
    val targets by vm.targets.collectAsStateWithLifecycle()
    val name by vm.builderName.collectAsStateWithLifecycle()
    val target by vm.builderTarget.collectAsStateWithLifecycle()
    val editing by vm.builderEditing.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().background(T.bg)
            .verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (editing == null) "New filtered row" else "Edit row",
                color = T.text, fontSize = 26.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
            )
            Text(count, color = if (count.startsWith("no titles")) T.warn else T.accent, fontSize = 15.sp)
        }

        FieldLabel("Content type")
        ChipGroup {
            MediaKind.entries.forEach { k ->
                Chip(k.label, spec.mediaKind == k) { vm.updateSpec { it.copy(mediaKind = k) } }
            }
        }

        FieldLabel("Include genres")
        if (genres.isEmpty()) Text("Loading genres…", color = T.textFaint, fontSize = 13.sp)
        ChipGroup {
            genres.forEach { g ->
                Chip(g.name, g.id in spec.genresInclude) {
                    vm.updateSpec { s ->
                        s.copy(
                            genresInclude = s.genresInclude.toggle(g.id),
                            genresExclude = s.genresExclude - g.id
                        )
                    }
                }
            }
        }

        FieldLabel("Exclude genres")
        ChipGroup {
            genres.forEach { g ->
                Chip(g.name, g.id in spec.genresExclude) {
                    vm.updateSpec { s ->
                        s.copy(
                            genresExclude = s.genresExclude.toggle(g.id),
                            genresInclude = s.genresInclude - g.id
                        )
                    }
                }
            }
        }

        FieldLabel("Released from")
        Stepper(
            value = spec.yearFrom?.toString() ?: "Any",
            onDecrement = { vm.updateSpec { s -> s.copy(yearFrom = ((s.yearFrom ?: THIS_YEAR) - 1).coerceAtLeast(EARLIEST_YEAR)) } },
            onIncrement = { vm.updateSpec { s -> s.copy(yearFrom = ((s.yearFrom ?: (THIS_YEAR - 1)) + 1).coerceAtMost(THIS_YEAR)) } },
            onClear = { vm.updateSpec { s -> s.copy(yearFrom = null) } }
        )

        FieldLabel("Released up to")
        Stepper(
            value = spec.yearTo?.toString() ?: "Any",
            onDecrement = { vm.updateSpec { s -> s.copy(yearTo = ((s.yearTo ?: THIS_YEAR) - 1).coerceAtLeast(EARLIEST_YEAR)) } },
            onIncrement = { vm.updateSpec { s -> s.copy(yearTo = ((s.yearTo ?: (THIS_YEAR - 1)) + 1).coerceAtMost(THIS_YEAR)) } },
            onClear = { vm.updateSpec { s -> s.copy(yearTo = null) } }
        )

        FieldLabel("Minimum rating (out of 10)")
        Stepper(
            value = spec.minRating?.let { "%.1f".format(it) } ?: "Any",
            onDecrement = { vm.updateSpec { s -> s.copy(minRating = ((s.minRating ?: 7.0) - 0.5).coerceAtLeast(0.0).takeIf { it > 0 }) } },
            onIncrement = { vm.updateSpec { s -> s.copy(minRating = ((s.minRating ?: 6.5) + 0.5).coerceAtMost(9.5)) } },
            onClear = { vm.updateSpec { s -> s.copy(minRating = null) } }
        )
        if (spec.minRating != null) {
            Text(
                "Also requiring at least ${spec.minVotes ?: 100} votes, so obscure titles with a handful of ratings don't dominate.",
                color = T.textFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(6.dp))
            Stepper(
                value = "${spec.minVotes ?: 100} votes",
                onDecrement = { vm.updateSpec { s -> s.copy(minVotes = ((s.minVotes ?: 100) - 50).coerceAtLeast(0)) } },
                onIncrement = { vm.updateSpec { s -> s.copy(minVotes = (s.minVotes ?: 100) + 50) } }
            )
        }

        FieldLabel("Runtime (minutes)")
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                    Text("At least", color = T.textDim, fontSize = 12.sp)
                Stepper(
                    value = spec.runtimeMin?.toString() ?: "Any",
                    onDecrement = { vm.updateSpec { s -> s.copy(runtimeMin = ((s.runtimeMin ?: 90) - 10).coerceAtLeast(0).takeIf { it > 0 }) } },
                    onIncrement = { vm.updateSpec { s -> s.copy(runtimeMin = (s.runtimeMin ?: 80) + 10) } },
                    onClear = { vm.updateSpec { s -> s.copy(runtimeMin = null) } }
                )
            }
            Column {
                    Text("At most", color = T.textDim, fontSize = 12.sp)
                Stepper(
                    value = spec.runtimeMax?.toString() ?: "Any",
                    onDecrement = { vm.updateSpec { s -> s.copy(runtimeMax = ((s.runtimeMax ?: 130) - 10).coerceAtLeast(10)) } },
                    onIncrement = { vm.updateSpec { s -> s.copy(runtimeMax = (s.runtimeMax ?: 110) + 10) } },
                    onClear = { vm.updateSpec { s -> s.copy(runtimeMax = null) } }
                )
            }
        }

        FieldLabel("Streaming on (${spec.watchRegion})")
        if (providers.isEmpty()) Text("Loading providers…", color = T.textFaint, fontSize = 13.sp)
        ChipGroup {
            providers.forEach { p ->
                Chip(p.name, p.id in spec.providers) {
                    vm.updateSpec { s -> s.copy(providers = s.providers.toggle(p.id)) }
                }
            }
        }
        if (spec.providers.isNotEmpty()) {
            Hint("Matches titles on any of the selected services.")
        }

        FieldLabel("Original language")
        ChipGroup {
            Chip("Any", spec.originalLanguage == null) { vm.updateSpec { s -> s.copy(originalLanguage = null) } }
            listOf("en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German",
                "ja" to "Japanese", "ko" to "Korean", "it" to "Italian", "hi" to "Hindi").forEach { (code, label) ->
                Chip(label, spec.originalLanguage == code) { vm.updateSpec { s -> s.copy(originalLanguage = code) } }
            }
        }

        FieldLabel("Sort by")
        ChipGroup {
            SORT_OPTIONS.forEach { opt ->
                Chip(opt.label, spec.sortBy == opt.value) { vm.updateSpec { s -> s.copy(sortBy = opt.value) } }
            }
        }

        FieldLabel("Titles in this row")
        ChipGroup {
            listOf(20, 40, 60, 80, 100).forEach { n ->
                Chip("$n", spec.maxItems == n) { vm.updateSpec { s -> s.copy(maxItems = n) } }
            }
        }

        FieldLabel("Row name")
        DarkField(name, { vm.builderName.value = it }, "Leave blank to name it from the filters",
            Modifier.width(560.dp))

        FieldLabel("Open items in")
        ChipGroup {
            Chip("Default", target.isBlank()) { vm.builderTarget.value = "" }
            targets.forEach { t -> Chip(t.label, target == t.packageName) { vm.builderTarget.value = t.packageName } }
        }

        Spacer(Modifier.height(28.dp))
        PillRow {
            Pill(if (editing == null) "Create row" else "Save changes", primary = true) {
                if (!busy) vm.saveBuilder()
            }
            Pill("Cancel") { vm.closeBuilder() }
        }
        Spacer(Modifier.height(40.dp))
    }
}

private fun List<Int>.toggle(id: Int) = if (id in this) this - id else this + id
