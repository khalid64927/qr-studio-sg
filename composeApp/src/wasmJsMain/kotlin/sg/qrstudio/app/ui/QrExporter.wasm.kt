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

        downloadFile(svg.toString(), fileName, "image/svg+xml")
    } catch (e: Exception) {
        // Export failed silently
    }
}

private fun downloadFile(content: String, fileName: String, mimeType: String) {
    val blob = Blob(arrayOf(content), BlobPropertyBag(type = mimeType))
    val url = org.w3c.dom.url.URL.createObjectURL(blob)
    val link = window.document.createElement("a") as org.w3c.dom.HTMLAnchorElement
    link.href = url
    link.setAttribute("download", fileName)
    link.style.display = "none"
    window.document.body?.appendChild(link)
    link.click()
    window.document.body?.removeChild(link)
    org.w3c.dom.url.URL.revokeObjectURL(url)
}

private fun sg.qrstudio.qr.Contrast.Rgb.toHexColor(): String {
    val r = (this.r * 255).toInt().toString(16).padStart(2, '0')
    val g = (this.g * 255).toInt().toString(16).padStart(2, '0')
    val b = (this.b * 255).toInt().toString(16).padStart(2, '0')
    return "#$r$g$b"
}
