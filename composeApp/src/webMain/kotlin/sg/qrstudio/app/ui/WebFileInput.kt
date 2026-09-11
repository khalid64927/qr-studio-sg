package sg.qrstudio.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.browser.document
import org.w3c.files.FileList

@Composable
actual fun WebFileInputButton(
    onFileSelected: (name: String, bytes: ByteArray) -> Unit,
    selectedFileName: String?,
    modifier: Modifier,
) {
    SuggestionChip(
        onClick = { triggerFileInput(onFileSelected) },
        label = {
            Text(
                if (selectedFileName != null) "✓ Image selected: $selectedFileName"
                else "📸 Choose image"
            )
        },
        modifier = modifier,
    )
}

private fun triggerFileInput(onFileSelected: (name: String, bytes: ByteArray) -> Unit) {
    val input = document.createElement("input") as org.w3c.dom.HTMLInputElement
    input.type = "file"
    input.accept = "image/*"

    input.onchange = { event ->
        val files: FileList? = input.files
        if (files != null && files.length > 0) {
            val file = files.item(0)
            if (file != null) {
                val reader = org.w3c.files.FileReader()
                reader.onload = { _ ->
                    try {
                        val result = reader.result as? String
                        if (result != null) {
                            // Convert data URL to ByteArray
                            val base64Data = result.substringAfter(",")
                            val bytes = decodeBase64ToByteArray(base64Data)
                            onFileSelected(file.name, bytes)
                        }
                    } catch (e: Exception) {
                        println("Error reading file: ${e.message}")
                    }
                }
                reader.readAsDataURL(file)
            }
        }
        Unit
    }

    input.click()
}

private fun decodeBase64ToByteArray(base64: String): ByteArray {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="
    val result = mutableListOf<Byte>()
    var i = 0

    while (i < base64.length) {
        val b1 = chars.indexOf(base64.getOrNull(i) ?: '=')
        val b2 = chars.indexOf(base64.getOrNull(i + 1) ?: '=')
        val b3 = chars.indexOf(base64.getOrNull(i + 2) ?: '=')
        val b4 = chars.indexOf(base64.getOrNull(i + 3) ?: '=')

        val byte1 = ((b1 and 0x3F) shl 2 or ((b2 and 0x30) shr 4)).toByte()
        result.add(byte1)

        if (b3 != 64) {
            val byte2 = (((b2 and 0x0F) shl 4) or ((b3 and 0x3C) shr 2)).toByte()
            result.add(byte2)
        }

        if (b4 != 64) {
            val byte3 = (((b3 and 0x03) shl 6) or (b4 and 0x3F)).toByte()
            result.add(byte3)
        }

        i += 4
    }

    return result.toByteArray()
}
