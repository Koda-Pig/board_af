package za.co.boardaf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BoardDark = Color(0xFF273338)
val BoardInk = Color(0xFF273338)
val BoardPaper = Color(0xFFF3F5EC)
val BoardSurface = Color(0xFFFAFBF4)
val BoardLine = Color(0xFFD6DCCB)
val BoardMuted = Color(0xFF6E7A6E)
val Sage = Color(0xFF9CB080)
val Fern = Color(0xFF618764)
val Forest = Color(0xFF2B5748)

// Functional colors: hold roles, saved problem accents, and error/warning states.
// Deliberately outside the chrome palette so they stay distinguishable on the board.
val Coral = Color(0xFFEA7D5F)
val Sky = Color(0xFF70C5DD)
val Gold = Color(0xFFE9BD62)
val Moss = Color(0xFF97C27F)

/**
 * Coral darkened for *text* on the light paper background: Coral itself is ~2.6:1
 * there, under the 4.5:1 body-text guideline. Fills and markers keep [Coral].
 * Surfaced as the light scheme's `error`, so text reads it via the theme.
 */
val CoralInk = Color(0xFFB5492C)

private val BoardLightColorScheme = lightColorScheme(
    primary = Forest,
    onPrimary = BoardSurface,
    primaryContainer = Color(0xFFDCE6CF),
    onPrimaryContainer = BoardInk,
    secondary = Fern,
    onSecondary = BoardSurface,
    tertiary = Sage,
    background = BoardPaper,
    onBackground = BoardInk,
    surface = BoardSurface,
    onSurface = BoardInk,
    surfaceVariant = Color(0xFFE9EDDE),
    onSurfaceVariant = BoardMuted,
    outline = BoardLine,
    outlineVariant = BoardLine,
    // Selected chips, the inactive slider track and dialogs/menus all read these.
    // Left unset, Material's baseline lavender shows through everywhere they're used.
    secondaryContainer = Color(0xFFDCE6CF),
    onSecondaryContainer = BoardInk,
    tertiaryContainer = Color(0xFFE4EBD8),
    onTertiaryContainer = BoardInk,
    surfaceTint = Forest,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = BoardSurface,
    surfaceContainer = Color(0xFFF0F3E8),
    surfaceContainerHigh = Color(0xFFEBEFE2),
    surfaceContainerHighest = Color(0xFFE5EADB),
    error = CoralInk,
    onError = BoardSurface,
    errorContainer = Color(0xFFF8DCD3),
    onErrorContainer = BoardInk,
    // Grade pills and other "flipped" chips: dark on light, light on dark.
    inverseSurface = BoardDark,
    inverseOnSurface = BoardPaper,
    // Snackbar action text sits on inverseSurface.
    inversePrimary = Sage,
)

private val BoardDarkColorScheme = darkColorScheme(
    primary = Sage,
    onPrimary = BoardDark,
    primaryContainer = Color(0xFF3A4A3A),
    onPrimaryContainer = BoardPaper,
    secondary = Fern,
    onSecondary = BoardPaper,
    tertiary = Moss,
    background = Color(0xFF1A1F1C),
    onBackground = BoardPaper,
    surface = Color(0xFF242A26),
    onSurface = BoardPaper,
    surfaceVariant = Color(0xFF323932),
    onSurfaceVariant = Color(0xFFB0B8A8),
    outline = Color(0xFF4A5248),
    outlineVariant = Color(0xFF4A5248),
    secondaryContainer = Color(0xFF3A4A3A),
    onSecondaryContainer = BoardPaper,
    tertiaryContainer = Color(0xFF3A4A3A),
    onTertiaryContainer = BoardPaper,
    surfaceTint = Sage,
    surfaceContainerLowest = Color(0xFF151916),
    surfaceContainerLow = Color(0xFF1F2420),
    surfaceContainer = Color(0xFF242A26),
    surfaceContainerHigh = Color(0xFF2B322D),
    surfaceContainerHighest = Color(0xFF323932),
    // Coral already clears 5:1 on the dark backgrounds.
    error = Coral,
    onError = BoardDark,
    errorContainer = Color(0xFF5A3026),
    onErrorContainer = BoardPaper,
    inverseSurface = BoardPaper,
    inverseOnSurface = BoardDark,
    inversePrimary = Forest,
)

@Composable
fun BoardAfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) BoardDarkColorScheme else BoardLightColorScheme,
        content = content,
    )
}
