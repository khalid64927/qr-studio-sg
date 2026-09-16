package sg.qrstudio.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import sg.qrstudio.app.ui.QrStudioDarkColorScheme
import sg.qrstudio.app.ui.QrStudioLightColorScheme
import sg.qrstudio.app.ui.QrStudioScreen
import sg.qrstudio.app.ui.QrStudioViewModel

/**
 * FR-703: light and dark follow the system setting. Material 3 throughout, themed with
 * an Adyen-inspired palette (QrStudioTheme.kt) shared with the demo/web Next.js app, not
 * stock Material You colours.
 */
@Composable
fun App(modifier: Modifier = Modifier) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) QrStudioDarkColorScheme else QrStudioLightColorScheme,
    ) {
        Surface(modifier = modifier) {
            val viewModel: QrStudioViewModel = viewModel { QrStudioViewModel() }
            QrStudioScreen(state = viewModel.uiState, onIntent = viewModel::onIntent)
        }
    }
}
