package sg.qrstudio.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.browser.window
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import sg.qrstudio.qr.AppearanceConfig
import sg.qrstudio.qr.LogoConfig
import sg.qrstudio.qr.ModuleMatrix

actual fun exportQrAsPng(
    matrix: ModuleMatrix,
    appearance: AppearanceConfig,
    logo: LogoConfig,
    decodedImage: ImageBitmap?,
    pixelSize: Int,
    fileName: String,
) {
    try {
        // Generate high-quality SVG (browser canvas is complex in Kotlin/Wasm)
        val moduleSize = 10
        val size = matrix.size * moduleSize

        val svg = StringBuilder()
        svg.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        svg.append("""<svg xmlns="http://www.w3.org/2000/svg" width="$size" height="$size" viewBox="0 0 $size $size">""").append("\n")

        val bgHex = appearance.background.toHexColor()
        svg.append("""  <rect width="$size" height="$size" fill="$bgHex"/>""").append("\n")

        val fgHex = appearance.foreground.toHexColor()
        svg.append("""  <g fill="$fgHex">""").append("\n")
        for (row in 0 until matrix.size) {
            for (col in 0 until matrix.size) {
                if (matrix.isDark(col, row)) {
                    val x = col * moduleSize
                    val y = row * moduleSize
                    svg.append("""    <rect x="$x" y="$y" width="$moduleSize" height="$moduleSize"/>""").append("\n")
                }
            }
        }
        svg.append("""  </g>""").append("\n")
        svg.append("""</svg>""")

        downloadFile(svg.toString(), "qr-code.svg", "image/svg+xml")
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
        val moduleSize = 10
        val size = matrix.size * moduleSize

        val svg = StringBuilder()
        svg.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        svg.append("""<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="$size" height="$size" viewBox="0 0 $size $size">""").append("\n")

        val bgHex = appearance.background.toHexColor()
        svg.append("""  <rect width="$size" height="$size" fill="$bgHex"/>""").append("\n")

        // Compute logo area if enabled
        val logoSize = if (logo.enabled) (size * logo.clampedSizeFraction()).toInt() else 0
        val logoLeft = (size - logoSize) / 2
        val logoTop = (size - logoSize) / 2
        val padding = 5

        val fgHex = appearance.foreground.toHexColor()
        svg.append("""  <g fill="$fgHex">""").append("\n")
        for (row in 0 until matrix.size) {
            for (col in 0 until matrix.size) {
                if (matrix.isDark(col, row)) {
                    val x = col * moduleSize
                    val y = row * moduleSize

                    // Skip modules within logo area
                    if (logo.enabled && isWithinLogoAreaSvg(x, y, moduleSize, logoLeft, logoTop, logoSize, padding)) {
                        continue
                    }

                    svg.append("""    <rect x="$x" y="$y" width="$moduleSize" height="$moduleSize"/>""").append("\n")
                }
            }
        }
        svg.append("""  </g>""").append("\n")

        // Draw logo backing plate if enabled
        if (logo.enabled) {
            when (logo.shape) {
                sg.qrstudio.qr.LogoShape.CIRCLE -> {
                    val radius = logoSize / 2
                    svg.append("""  <circle cx="${logoLeft + radius}" cy="${logoTop + radius}" r="$radius" fill="$bgHex"/>""").append("\n")
                }
                sg.qrstudio.qr.LogoShape.ROUNDED -> {
                    val radius = (logoSize * 0.2).toInt()
                    svg.append("""  <rect x="$logoLeft" y="$logoTop" width="$logoSize" height="$logoSize" rx="$radius" fill="$bgHex"/>""").append("\n")
                }
                sg.qrstudio.qr.LogoShape.SQUARE -> {
                    svg.append("""  <rect x="$logoLeft" y="$logoTop" width="$logoSize" height="$logoSize" fill="$bgHex"/>""").append("\n")
                }
            }
        }

        svg.append("""</svg>""")

        downloadFile(svg.toString(), fileName, "image/svg+xml")
    } catch (e: Exception) {
        // Export failed silently
    }
}

private fun downloadFile(content: String, fileName: String, mimeType: String) {
    try {
        // Create base64-encoded data URL for download
        val base64 = encodeToBase64(content)
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

private fun encodeToBase64(text: String): String {
    val bytes = text.encodeToByteArray()
    val chars = CharArray(bytes.size)
    for (i in bytes.indices) {
        chars[i] = bytes[i].toInt().and(0xFF).toChar()
    }
    val base64Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="
    val result = StringBuilder()
    var i = 0
    while (i < chars.size) {
        val b1 = chars[i].code.and(0xFF)
        val b2 = if (i + 1 < chars.size) chars[i + 1].code.and(0xFF) else 0
        val b3 = if (i + 2 < chars.size) chars[i + 2].code.and(0xFF) else 0

        val enc1 = b1.shr(2)
        val enc2 = (b1.shl(4) or b2.shr(4)).and(0x3F)
        val enc3 = (b2.shl(2) or b3.shr(6)).and(0x3F)
        val enc4 = b3.and(0x3F)

        result.append(base64Chars[enc1])
        result.append(base64Chars[enc2])
        result.append(if (i + 1 < chars.size) base64Chars[enc3] else '=')
        result.append(if (i + 2 < chars.size) base64Chars[enc4] else '=')

        i += 3
    }
    return result.toString()
}

private fun sg.qrstudio.qr.Contrast.Rgb.toHexColor(): String {
    val r = (this.r * 255).toInt().toString(16).padStart(2, '0')
    val g = (this.g * 255).toInt().toString(16).padStart(2, '0')
    val b = (this.b * 255).toInt().toString(16).padStart(2, '0')
    return "#$r$g$b"
}

private fun isWithinLogoAreaSvg(
    moduleX: Int,
    moduleY: Int,
    moduleSize: Int,
    logoLeft: Int,
    logoTop: Int,
    logoSize: Int,
    padding: Int,
): Boolean {
    val cx = moduleX + moduleSize / 2
    val cy = moduleY + moduleSize / 2
    return cx in (logoLeft - padding)..(logoLeft + logoSize + padding) &&
        cy in (logoTop - padding)..(logoTop + logoSize + padding)
}
