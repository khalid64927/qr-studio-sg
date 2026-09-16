package sg.qrstudio.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import sg.qrstudio.app.ui.QrStudioScreen
import sg.qrstudio.app.ui.QrStudioViewModel

/**
 * FR-703: light and dark follow the system setting. OQ-6: stock Material 3, no bespoke
 * design system for a single screen.
 */
@Composable
fun App(modifier: Modifier = Modifier) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
    ) {
        Surface(modifier = modifier) {
            val viewModel: QrStudioViewModel = viewModel { QrStudioViewModel() }
            QrStudioScreen(state = viewModel.uiState, onIntent = viewModel::onIntent)
        }
    }
}
