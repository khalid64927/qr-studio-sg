package sg.qrstudio.app.ui

import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.launch

@Composable
actual fun WebFileInputButton(
    onFileSelected: (name: String, bytes: ByteArray) -> Unit,
    selectedFileName: String?,
    modifier: Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val filePickerLauncher = rememberFilePickerLauncher(type = PickerType.Image) { file ->
        if (file != null) {
            coroutineScope.launch {
                try {
                    val bytes = file.readBytes()
                    onFileSelected(file.name, bytes)
                } catch (e: Exception) {
                    android.util.Log.e("FileInput", "Error reading file: ${e.message}")
                }
            }
        }
    }

    SuggestionChip(
        onClick = { filePickerLauncher.launch() },
        label = {
            Text(
                if (selectedFileName != null) "✓ Image selected: $selectedFileName"
                else "📸 Choose image"
            )
        },
        modifier = modifier,
    )
}
