package com.localwave.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF66E3D4),
    onPrimary = Color(0xFF003734),
    secondary = Color(0xFFB8C8C4),
    background = Color(0xFF071413),
    surface = Color(0xFF0D1D1B),
    surfaceVariant = Color(0xFF21312F),
    error = Color(0xFFFFB4AB)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF006B60),
    onPrimary = Color.White,
    secondary = Color(0xFF48615D),
    background = Color(0xFFF5FBF8),
    surface = Color.White,
    surfaceVariant = Color(0xFFDCE7E4),
    error = Color(0xFFBA1A1A)
)

@Composable
fun LWTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = MaterialTheme.typography, content = content)
}

object LWSpacing {
    val screen = 20.dp
    val item = 12.dp
}

object LWColors {
    val signalStrong = Color(0xFF66E3D4)
    val warning = Color(0xFFFFD166)
}

val ColorScheme.success: Color get() = Color(0xFF8BE28B)
