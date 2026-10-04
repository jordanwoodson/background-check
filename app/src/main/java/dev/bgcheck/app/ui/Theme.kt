package dev.bgcheck.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bgcheck.app.R

// Dracula's terminal palette. Muted body text is lighter than the comment color
// so it remains readable on Android; the comment color is reserved for borders.
internal val Ink = Color(0xFF282A36)
internal val Panel = Color(0xFF21222C)
internal val PanelRaised = Color(0xFF343746)
internal val Foreground = Color(0xFFF8F8F2)
internal val Muted = Color(0xFFB4BEDB)
internal val Outline = Color(0xFF44475A)
internal val Comment = Color(0xFF6272A4)
internal val Purple = Color(0xFFBD93F9)
internal val Cyan = Color(0xFF8BE9FD)
internal val Green = Color(0xFF50FA7B)
internal val Orange = Color(0xFFFFB86C)
internal val Pink = Color(0xFFFF79C6)
internal val Red = Color(0xFFFF5555)

private val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private fun terminalStyle(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
    letterSpacing = 0.sp,
)

@Composable
fun BackgroundCheckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Purple,
            onPrimary = Ink,
            primaryContainer = PanelRaised,
            onPrimaryContainer = Foreground,
            secondary = Cyan,
            onSecondary = Ink,
            secondaryContainer = PanelRaised,
            onSecondaryContainer = Cyan,
            tertiary = Pink,
            onTertiary = Ink,
            tertiaryContainer = PanelRaised,
            onTertiaryContainer = Foreground,
            background = Ink,
            onBackground = Foreground,
            surface = Panel,
            onSurface = Foreground,
            surfaceVariant = PanelRaised,
            onSurfaceVariant = Muted,
            surfaceDim = Panel,
            surfaceBright = PanelRaised,
            surfaceContainerLowest = Panel,
            surfaceContainerLow = Panel,
            surfaceContainer = Panel,
            surfaceContainerHigh = PanelRaised,
            surfaceContainerHighest = PanelRaised,
            surfaceTint = Purple,
            inverseSurface = PanelRaised,
            inverseOnSurface = Foreground,
            inversePrimary = Cyan,
            outline = Comment,
            outlineVariant = Outline,
            error = Red,
            onError = Ink,
            errorContainer = Color(0xFF442A35),
            onErrorContainer = Color(0xFFFFB1C8),
        ),
        typography = Typography(
            displayLarge = terminalStyle(60, 68, FontWeight.Medium),
            displayMedium = terminalStyle(44, 52, FontWeight.Medium),
            displaySmall = terminalStyle(36, 44, FontWeight.Medium),
            headlineLarge = terminalStyle(28, 36, FontWeight.Medium),
            headlineMedium = terminalStyle(23, 31, FontWeight.Medium),
            headlineSmall = terminalStyle(21, 29, FontWeight.Medium),
            titleLarge = terminalStyle(19, 27, FontWeight.Medium),
            titleMedium = terminalStyle(15, 23, FontWeight.SemiBold),
            titleSmall = terminalStyle(14, 22, FontWeight.Medium),
            bodyLarge = terminalStyle(15, 24),
            bodyMedium = terminalStyle(13, 21),
            bodySmall = terminalStyle(12, 19),
            labelLarge = terminalStyle(13, 21, FontWeight.SemiBold),
            labelMedium = terminalStyle(12, 18, FontWeight.Medium),
            labelSmall = terminalStyle(10, 16, FontWeight.Medium),
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(8.dp),
            large = RoundedCornerShape(10.dp),
            extraLarge = RoundedCornerShape(12.dp),
        ),
        content = content,
    )
}
