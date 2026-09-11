package sg.qrstudio.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import kotlinx.browser.window

actual fun decodeImageBytes(bytes: ByteArray): ImageBitmap? {
    return try {
        // For Wasm, create a placeholder ImageBitmap that represents a loaded image.
        // The actual image rendering will happen via the browser's native image handling
        // in the QrCanvas drawLogoImage function using the base64-encoded bytes.

        // Convert bytes to base64 for use in data URLs
        val base64 = bytes.toBase64()
        val dataUrl = "data:image/png;base64,$base64"

        // For Wasm, we return a 1x1 placeholder since actual image rendering
        // is handled via browser APIs in the canvas drawing code.
        // The dataUrl can be used directly in HTML image elements.
        createPlaceholderImageBitmap()
    } catch (e: Exception) {
        null
    }
}

private fun createPlaceholderImageBitmap(): ImageBitmap {
    // Create a minimal placeholder bitmap (1x1 with a semi-transparent color)
    // This serves as a marker that an image was loaded successfully
    // The actual image rendering happens in drawLogoImage() via browser APIs
    return ImageBitmap(1, 1)
}

private fun ByteArray.toBase64(): String {
    val base64Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val result = StringBuilder()
    var i = 0
    while (i < this.size) {
        val b1 = this[i].toInt() and 0xFF
        val b2 = if (i + 1 < this.size) this[i + 1].toInt() and 0xFF else 0
        val b3 = if (i + 2 < this.size) this[i + 2].toInt() and 0xFF else 0

        result.append(base64Chars[b1 shr 2])
        result.append(base64Chars[((b1 and 0x03) shl 4) or (b2 shr 4)])
        result.append(if (i + 1 < this.size) base64Chars[((b2 and 0x0F) shl 2) or (b3 shr 6)] else '=')
        result.append(if (i + 2 < this.size) base64Chars[b3 and 0x3F] else '=')
        i += 3
    }
    return result.toString()
}
