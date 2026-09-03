package dev.tmdbrows.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Accent = Color(0xFF01D2AA)

/** Highlights whatever the D-pad is focused on so TV navigation is obvious. */
@Composable
fun Modifier.tvFocus(): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .border(BorderStroke(2.dp, if (focused) Accent else Color.Transparent), MaterialTheme.shapes.small)
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = Modifier.tvFocus()) { Text(label) }
    else OutlinedButton(onClick = onClick, modifier = Modifier.tvFocus()) { Text(label) }
}

/** Wrapping row of chips — genres and providers can be long lists. */
@Composable
fun ChipGroup(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
fun FieldLabel(text: String) {
    Text(text, color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

/**
 * A value adjusted with ◀ / ▶ buttons. Typing numbers on a TV remote is miserable,
 * so years, ratings and counts all use this instead of a text field.
 */
@Composable
fun Stepper(value: String, onDecrement: () -> Unit, onIncrement: () -> Unit, onClear: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onDecrement, modifier = Modifier.tvFocus()) { Text("◀") }
        Text(value, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 8.dp))
        OutlinedButton(onClick = onIncrement, modifier = Modifier.tvFocus()) { Text("▶") }
        if (onClear != null) {
            OutlinedButton(onClick = onClear, modifier = Modifier.tvFocus()) { Text("Any") }
        }
    }
}
