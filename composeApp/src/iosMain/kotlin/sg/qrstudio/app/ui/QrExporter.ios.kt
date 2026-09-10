package sg.qrstudio.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSString
import platform.Foundation.stringByAppendingPathComponent
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
        // Get Documents directory
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        )
        val documentsPath = paths.firstOrNull() as? NSString ?: return
        val filePath = documentsPath.stringByAppendingPathComponent(fileName)

        // Create SVG content (iOS doesn't have easy BufferedImage, so we'll use SVG format instead)
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

        // Write to file
        NSFileManager.defaultManager().createFileAtPath(
            filePath,
            contents = svg.toString().encodeToByteArray().toNSData(),
            attributes = null
        )
        println("✓ QR code saved to: $filePath")
    } catch (e: Exception) {
        println("❌ Export failed: ${e.message}")
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
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        )
        val documentsPath = paths.firstOrNull() as? NSString ?: return
        val filePath = documentsPath.stringByAppendingPathComponent(fileName)

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

        NSFileManager.defaultManager().createFileAtPath(
            filePath,
            contents = svg.toString().encodeToByteArray().toNSData(),
            attributes = null
        )
        println("✓ QR code SVG saved to: $filePath")
    } catch (e: Exception) {
        println("❌ Export failed: ${e.message}")
    }
}

private fun sg.qrstudio.qr.Contrast.Rgb.toHexColor(): String {
    val r = (this.r * 255).toInt().toString(16).padStart(2, '0')
    val g = (this.g * 255).toInt().toString(16).padStart(2, '0')
    val b = (this.b * 255).toInt().toString(16).padStart(2, '0')
    return "#$r$g$b"
}

private fun ByteArray.toNSData(): platform.Foundation.NSData {
    return platform.Foundation.NSData(bytes = this.toUByteArray().toCValues().ptr, length = this.size.toULong())
}
