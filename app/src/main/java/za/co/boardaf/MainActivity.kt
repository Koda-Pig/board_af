package za.co.boardaf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import za.co.boardaf.ui.BoardAfApp
import za.co.boardaf.ui.theme.BoardAfTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BoardAfTheme {
                BoardAfApp()
            }
        }
    }
}
