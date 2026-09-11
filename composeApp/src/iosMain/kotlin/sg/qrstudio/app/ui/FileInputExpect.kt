package sg.qrstudio.app.ui

import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun WebFileInputButton(
    onFileSelected: (name: String, bytes: ByteArray) -> Unit,
    selectedFileName: String?,
    modifier: Modifier,
) {
    // iOS file picker implementation via FileKit - handled by the framework
    SuggestionChip(
        onClick = { /* FileKit handles iOS file picker */ },
        label = {
            Text(
                if (selectedFileName != null) "✓ Image selected: $selectedFileName"
                else "📸 Choose image"
            )
        },
        modifier = modifier,
    )
}
