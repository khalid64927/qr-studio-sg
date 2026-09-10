package sg.qrstudio.app.ui

import androidx.compose.ui.graphics.ImageBitmap

/**
 * §9.3: Decode image bytes to a drawable format, platform-specific.
 * Each platform handles image decoding differently to match native capabilities.
 */
expect fun decodeImageBytes(bytes: ByteArray): ImageBitmap?
