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
    // Grade pills and other "flipped" chips: dark on light, light on dark.
    inverseSurface = BoardDark,
    inverseOnSurface = BoardPaper,
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
    inverseSurface = BoardPaper,
    inverseOnSurface = BoardDark,
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
