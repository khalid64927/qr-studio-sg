package sg.qrstudio.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

actual fun decodeImageBytes(bytes: ByteArray): ImageBitmap? {
    return try {
        // Use Skia to decode the image (supports PNG, JPG, GIF, BMP, etc.)
        val skiaImage = Image.makeFromEncoded(bytes)
        skiaImage?.toComposeImageBitmap()
    } catch (e: Exception) {
        null
    }
}
