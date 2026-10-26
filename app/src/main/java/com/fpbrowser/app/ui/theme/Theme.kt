package com.fpbrowser.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF5B8DEF)
val BgDark = Color(0xFF0F1115)
val SurfaceDark = Color(0xFF161A22)
val SurfaceVariantDark = Color(0xFF1F2430)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Color(0xFF3FB68B),
    background = BgDark,
    onBackground = Color(0xFFE6E8ED),
    surface = SurfaceDark,
    onSurface = Color(0xFFE6E8ED),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFB4BAC6),
    error = Color(0xFFE06C75),
)

@Composable
fun FpBrowserTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        content = content,
    )
}
