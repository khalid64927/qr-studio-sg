package sg.qrstudio.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.browser.window
import org.w3c.files.Blob
import kotlin.js.Promise

actual fun decodeImageBytes(bytes: ByteArray): ImageBitmap? {
    return try {
        // Convert bytes to base64
        val base64 = base64Encode(bytes)
        val dataUrl = "data:image/png;base64,$base64"

        // Create an off-screen canvas to render the image
        val canvas = window.document.createElement("canvas") as org.w3c.dom.HTMLCanvasElement
        val ctx = canvas.getContext("2d") as org.w3c.dom.CanvasRenderingContext2D

        // Create an image element and load it
        val img = window.document.createElement("img") as org.w3c.dom.HTMLImageElement
        img.onload = {
            canvas.width = img.width
            canvas.height = img.height
            ctx.drawImage(img, 0.0, 0.0)
        }
        img.src = dataUrl

        // Note: This is async in the browser, so we can't return the bitmap immediately.
        // For now, return null and handle async rendering separately.
        null
    } catch (e: Exception) {
        null
    }
}

private fun base64Encode(bytes: ByteArray): String {
    // Convert bytes to base64 using Kotlin's built-in function
    val base64Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val sb = StringBuilder()
    var i = 0
    while (i < bytes.size) {
        val b1 = bytes[i].toInt() and 0xFF
        val b2 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
        val b3 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0

        sb.append(base64Chars[b1 shr 2])
        sb.append(base64Chars[((b1 and 0x03) shl 4) or (b2 shr 4)])
        if (i + 1 < bytes.size) {
            sb.append(base64Chars[((b2 and 0x0F) shl 2) or (b3 shr 6)])
        } else {
            sb.append('=')
        }
        if (i + 2 < bytes.size) {
            sb.append(base64Chars[b3 and 0x3F])
        } else {
            sb.append('=')
        }
        i += 3
    }
    return sb.toString()
}
