package dev.tmdbrows.ui

import androidx.compose.ui.graphics.Color

/** Flat dark palette. One accent, used only for focus and primary actions. */
object T {
    val bg = Color(0xFF0B0D10)
    val surface = Color(0xFF14171D)
    val raised = Color(0xFF1B1F27)
    val border = Color(0xFF262B35)
    val accent = Color(0xFF01D2AA)
    val accentInk = Color(0xFF04342C)
    val accentDim = Color(0xFF0F3B33)
    val text = Color(0xFFF2F4F8)
    val textDim = Color(0xFF8A93A5)
    val textFaint = Color(0xFF5C6474)
    val warn = Color(0xFFFFB74D)
    val danger = Color(0xFFFF7043)
}

/** Kept for the screens that referenced the old name. */
val Accent = T.accent

/** Nav rail destinations. */
enum class Dest(val label: String, val glyph: String) {
    ROWS("Rows", "▤"),
    ADD("Add", "＋"),
    APPS("Apps", "▶"),
    SETTINGS("Settings", "⚙")
}
