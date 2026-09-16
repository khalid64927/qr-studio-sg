package sg.qrstudio.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun WebFileInputButton(
    onFileSelected: (name: String, bytes: ByteArray) -> Unit,
    selectedFileName: String?,
    modifier: Modifier = Modifier,
)
