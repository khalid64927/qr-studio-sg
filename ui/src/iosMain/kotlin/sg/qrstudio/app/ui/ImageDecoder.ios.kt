package sg.qrstudio.app.ui

import androidx.compose.ui.graphics.ImageBitmap

// §9.3: iOS image decoding deferred. Requires UIImage and Objective-C interop.
actual fun decodeImageBytes(bytes: ByteArray): ImageBitmap? {
    // TODO: Implement using UIImage and Objective-C interop
    return null
}
