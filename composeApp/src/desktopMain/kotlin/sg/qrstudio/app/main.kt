package sg.qrstudio.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import sg.qrstudio.app.ui.Strings

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = Strings.APP_NAME,
        state = rememberWindowState(size = DpSize(1100.dp, 900.dp)),
    ) {
        App()
    }
}
