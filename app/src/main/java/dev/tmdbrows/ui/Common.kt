package dev.tmdbrows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Section heading. */
@Composable
fun SectionTitle(text: String, topPad: Int = 26) {
    Text(text, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = topPad.dp, bottom = 10.dp))
}

@Composable
fun FieldLabel(text: String) {
    Text(text, color = T.textDim, fontSize = 13.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

@Composable
fun Hint(text: String) {
    Text(text, color = T.textFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
}

/**
 * A focusable pill. Focus is shown by filling with the accent rather than by an outline,
 * so where you are is unmistakable from across a room.
 */
@Composable
fun Pill(
    label: String,
    selected: Boolean = false,
    danger: Boolean = false,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> T.accent
        selected || primary -> T.accentDim
        else -> T.surface
    }
    val fg = when {
        focused -> T.accentInk
        selected || primary -> T.accent
        danger -> T.danger
        else -> T.text
    }
    Box(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, if (focused) T.accent else T.border, RoundedCornerShape(20.dp))
            .clickableRow(onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PillRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

/** Numeric value with ◀ / ▶ — typing digits on a remote is miserable. */
@Composable
fun Stepper(value: String, onDecrement: () -> Unit, onIncrement: () -> Unit, onClear: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("◀", onClick = onDecrement)
        Text(value, color = T.text, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 6.dp))
        Pill("▶", onClick = onIncrement)
        if (onClear != null) Pill("Any", onClick = onClear)
    }
}

@Composable
fun DarkField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, singleLine = true,
        label = { Text(label, fontSize = 13.sp) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = T.accent,
            unfocusedBorderColor = T.border,
            focusedTextColor = T.text,
            unfocusedTextColor = T.text,
            focusedLabelColor = T.accent,
            unfocusedLabelColor = T.textDim,
            cursorColor = T.accent,
            focusedContainerColor = T.surface,
            unfocusedContainerColor = T.surface
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    )
}

/** Old call sites used Chip/tvFocus; keep thin aliases so every screen compiles. */
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) = Pill(label, selected = selected, onClick = onClick)

@Composable
fun ChipGroup(content: @Composable FlowRowScope.() -> Unit) = PillRow(content)

@Composable
fun Modifier.tvFocus(): Modifier = this

/**
 * Focusable and clickable. clickable() already makes an element focusable, so the D-pad
 * centre key acts as a press; the ripple is dropped because focus is shown by the fill.
 */
@Composable
fun Modifier.clickableRow(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}
