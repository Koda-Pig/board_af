package za.co.boardaf.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BoardDark = Color(0xFF191C19)
val BoardInk = Color(0xFF272B27)
val BoardPaper = Color(0xFFF5F4EE)
val BoardSurface = Color(0xFFFCFBF7)
val BoardLine = Color(0xFFD9DAD2)
val BoardMuted = Color(0xFF777D75)
val Coral = Color(0xFFEA7D5F)
val Sky = Color(0xFF70C5DD)
val Gold = Color(0xFFE9BD62)
val Moss = Color(0xFF97C27F)

private val BoardColorScheme = lightColorScheme(
    primary = Coral,
    onPrimary = BoardDark,
    primaryContainer = Color(0xFFFFDACE),
    onPrimaryContainer = BoardInk,
    secondary = Sky,
    onSecondary = BoardDark,
    tertiary = Gold,
    background = BoardPaper,
    onBackground = BoardInk,
    surface = BoardSurface,
    onSurface = BoardInk,
    surfaceVariant = Color(0xFFECECE5),
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
