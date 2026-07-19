package za.co.boardaf.ui.theme

import androidx.compose.material3.MaterialTheme
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

private val BoardColorScheme = lightColorScheme(
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
)

@Composable
fun BoardAfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BoardColorScheme,
        content = content,
    )
}
