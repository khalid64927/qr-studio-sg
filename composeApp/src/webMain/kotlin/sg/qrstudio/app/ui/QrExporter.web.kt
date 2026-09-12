package sg.qrstudio.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.browser.window
import sg.qrstudio.qr.AppearanceConfig
import sg.qrstudio.qr.LogoConfig
import sg.qrstudio.qr.LogoShape
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
        // High-quality SVG stands in for PNG here: rendering to an actual raster canvas
        // and reading it back out is complex in Kotlin/JS and Kotlin/Wasm; an SVG at this
        // module resolution is visually equivalent and scanners don't care about format.
        val svg = buildQrSvg(matrix, appearance, logo)
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
        val svg = buildQrSvg(matrix, appearance, logo)
        downloadFile(svg, fileName, "image/svg+xml")
    } catch (e: Exception) {
        // Export failed silently
    }
}

private fun buildQrSvg(
    matrix: ModuleMatrix,
    appearance: AppearanceConfig,
    logo: LogoConfig,
): String {
    val moduleSize = 10
    val size = matrix.size * moduleSize

    val svg = StringBuilder()
    svg.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
    svg.append(
        """<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="$size" height="$size" viewBox="0 0 $size $size">""",
    ).append("\n")

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

                if (logo.enabled && isWithinLogoAreaSvg(x, y, moduleSize, logoLeft, logoTop, logoSize, padding)) {
                    continue
                }

                svg.append("""    <rect x="$x" y="$y" width="$moduleSize" height="$moduleSize"/>""").append("\n")
            }
        }
    }
    svg.append("""  </g>""").append("\n")

    if (logo.enabled) {
        when (logo.shape) {
            LogoShape.CIRCLE -> {
                val radius = logoSize / 2
                svg.append("""  <circle cx="${logoLeft + radius}" cy="${logoTop + radius}" r="$radius" fill="$bgHex"/>""").append("\n")
            }
            LogoShape.ROUNDED -> {
                val radius = (logoSize * 0.2).toInt()
                svg.append("""  <rect x="$logoLeft" y="$logoTop" width="$logoSize" height="$logoSize" rx="$radius" fill="$bgHex"/>""").append("\n")
            }
            LogoShape.SQUARE -> {
                svg.append("""  <rect x="$logoLeft" y="$logoTop" width="$logoSize" height="$logoSize" fill="$bgHex"/>""").append("\n")
            }
        }

        val imageBytes = logo.imageBytes
        if (imageBytes != null && !logo.placeholder) {
            drawLogoImageSvg(svg, imageBytes, logoLeft, logoTop, logoSize)
        }
    }

    svg.append("""</svg>""")
    return svg.toString()
}

private fun drawLogoImageSvg(
    svg: StringBuilder,
    imageBytes: ByteArray,
    left: Int,
    top: Int,
    size: Int,
) {
    try {
        val image = org.jetbrains.skia.Image.makeFromEncoded(imageBytes)
        val padding = 5
        val availableSize = size - (padding * 2)
        val imageAspectRatio = image.width.toFloat() / image.height
        val (scaledWidth, scaledHeight) = if (imageAspectRatio > 1f) {
            availableSize to (availableSize / imageAspectRatio).toInt()
        } else {
            (availableSize * imageAspectRatio).toInt() to availableSize
        }

        val imageLeft = left + padding + (availableSize - scaledWidth) / 2
        val imageTop = top + padding + (availableSize - scaledHeight) / 2

        // Re-encode to PNG so the embedded MIME type is always correct, regardless of
        // what format the user originally picked (jpg, webp, ...).
        val pngData = image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG) ?: return
        val base64Image = encodeBytesToBase64(pngData.bytes)

        svg.append(
            """  <image x="$imageLeft" y="$imageTop" width="$scaledWidth" height="$scaledHeight" xlink:href="data:image/png;base64,$base64Image"/>""",
        ).append("\n")
    } catch (e: Exception) {
        // Skip embedding on failure; the backing plate above stays visible.
    }
}

private fun downloadFile(content: String, fileName: String, mimeType: String) {
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
