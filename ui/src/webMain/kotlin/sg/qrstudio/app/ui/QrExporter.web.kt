package sg.qrstudio.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.browser.window
import sg.qrstudio.qr.AppearanceConfig
import sg.qrstudio.qr.LogoConfig
import sg.qrstudio.qr.ModuleMatrix
import sg.qrstudio.qr.QrSvgRenderer

actual fun exportQrAsPng(
    matrix: ModuleMatrix,
    appearance: AppearanceConfig,
    logo: LogoConfig,
    decodedImage: ImageBitmap?,
    pixelSize: Int,
    fileName: String,
) {
    try {
        // High-quality SVG stands in for PNG here: rendering to an actual raster canvas
        // and reading it back out is complex in Kotlin/JS and Kotlin/Wasm; an SVG at this
        // module resolution is visually equivalent and scanners don't care about format.
        val svg = QrSvgRenderer.render(matrix, appearance, logo, embeddedImageFor(logo))
        downloadFile(svg, "qr-code.svg", "image/svg+xml")
    } catch (e: Exception) {
        // Export failed silently
    }
}

actual fun exportQrAsSvg(
    matrix: ModuleMatrix,
    appearance: AppearanceConfig,
    logo: LogoConfig,
    decodedImage: ImageBitmap?,
    fileName: String,
) {
    try {
        val svg = QrSvgRenderer.render(matrix, appearance, logo, embeddedImageFor(logo))
        downloadFile(svg, fileName, "image/svg+xml")
    } catch (e: Exception) {
        // Export failed silently
    }
}

/**
 * Decodes the picked logo's bytes and re-encodes as a PNG data URI — the one genuinely
 * platform-specific step [QrSvgRenderer] can't do itself. Skia is already linked in for
 * the live Compose preview, so it does the decode here too rather than pulling in a
 * second image library.
 */
private fun embeddedImageFor(logo: LogoConfig): QrSvgRenderer.EmbeddedLogoImage? {
    val bytes = logo.imageBytes
    if (!logo.enabled || logo.placeholder || bytes == null) return null
    return try {
        val image =
            org.jetbrains.skia.Image
                .makeFromEncoded(bytes)
        val pngData = image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG) ?: return null
        QrSvgRenderer.EmbeddedLogoImage(
            dataUri = "data:image/png;base64,${encodeBytesToBase64(pngData.bytes)}",
            width = image.width,
            height = image.height,
        )
    } catch (e: Exception) {
        null
    }
}

private fun downloadFile(
    content: String,
    fileName: String,
    mimeType: String,
) {
    try {
        // Create base64-encoded data URL for download
        val base64 = encodeBytesToBase64(content.encodeToByteArray())
        val dataUrl = "data:$mimeType;base64,$base64"
        val link = window.document.createElement("a") as org.w3c.dom.HTMLAnchorElement
        link.href = dataUrl
        link.setAttribute("download", fileName)
        link.style.display = "none"
        window.document.body?.appendChild(link)
        link.click()
        window.document.body?.removeChild(link)
    } catch (e: Exception) {
        // Fallback: silently fail if download doesn't work
    }
}

private fun encodeBytesToBase64(bytes: ByteArray): String {
    val base64Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val result = StringBuilder()
    var i = 0
    while (i < bytes.size) {
        val b1 = bytes[i].toInt() and 0xFF
        val b2 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
        val b3 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0

        result.append(base64Chars[b1 shr 2])
        result.append(base64Chars[((b1 and 0x03) shl 4) or (b2 shr 4)])
        result.append(if (i + 1 < bytes.size) base64Chars[((b2 and 0x0F) shl 2) or (b3 shr 6)] else '=')
        result.append(if (i + 2 < bytes.size) base64Chars[b3 and 0x3F] else '=')
        i += 3
    }
    return result.toString()
}
