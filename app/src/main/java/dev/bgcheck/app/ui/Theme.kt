package dev.bgcheck.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val Ink = Color(0xFF101513)
internal val Panel = Color(0xFF1B231E)
internal val PanelRaised = Color(0xFF252F28)
internal val Lime = Color(0xFFC7F4A5)
internal val Cream = Color(0xFFF3F4E9)
internal val Muted = Color(0xFFA6B2A8)
internal val Outline = Color(0xFF354139)
internal val Amber = Color(0xFFF0CE91)

@Composable
fun BackgroundCheckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Lime,
            onPrimary = Ink,
            primaryContainer = Color(0xFF34472C),
            onPrimaryContainer = Lime,
            secondary = Color(0xFFB9CEC0),
            onSecondary = Ink,
            secondaryContainer = PanelRaised,
            onSecondaryContainer = Cream,
            tertiary = Amber,
            background = Ink,
            onBackground = Cream,
            surface = Panel,
            onSurface = Cream,
            surfaceVariant = PanelRaised,
            onSurfaceVariant = Muted,
            surfaceContainer = Panel,
            surfaceContainerHigh = PanelRaised,
            outline = Outline,
            outlineVariant = Outline,
            error = Color(0xFFFFB4AB),
            errorContainer = Color(0xFF482B28),
            onErrorContainer = Color(0xFFFFDAD5),
        ),
        typography = Typography(
            displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 64.sp, lineHeight = 70.sp, letterSpacing = (-3).sp),
            headlineLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-1).sp),
            headlineMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 27.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp),
            titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
            labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp),
            labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 1.sp),
        ),
        content = content,
    )
}
