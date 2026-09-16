package sg.qrstudio.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

// Compose Multiplatform renders iOS through Skia (skiko) the same as desktop and web —
// there's no need for UIImage/Objective-C interop here at all, just the same decoder
// desktop and web already use.
actual fun decodeImageBytes(bytes: ByteArray): ImageBitmap? =
    try {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (e: Exception) {
        null
    }
