package com.localwave.design

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

object LWIcons {
    val People = blank("People")
    val Settings = blank("Settings")
    val Lock = blank("Lock")
    val Frequency = blank("Frequency")

    private fun blank(name: String): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).build()
}
