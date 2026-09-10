package sg.qrstudio.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import java.io.File
import javax.imageio.ImageIO
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
        // Show file save dialog
        val fileDialog = java.awt.FileDialog(null as java.awt.Frame?, "Save QR Code as PNG", 1) // 1 = SAVE
        fileDialog.file = fileName
        fileDialog.isVisible = true

        val directory = fileDialog.directory
        val selectedFile = fileDialog.file
        if (directory != null && selectedFile != null) {
            // Create image and render QR code
            val bufferedImage = java.awt.image.BufferedImage(pixelSize, pixelSize, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val graphics = bufferedImage.createGraphics()

            // Fill background
            val bgColor = java.awt.Color(
                (appearance.background.r * 255).toInt(),
                (appearance.background.g * 255).toInt(),
                (appearance.background.b * 255).toInt(),
            )
            graphics.color = bgColor
            graphics.fillRect(0, 0, pixelSize, pixelSize)

            // Draw modules
            val modulePixels = pixelSize / matrix.size
            val fgColor = java.awt.Color(
                (appearance.foreground.r * 255).toInt(),
                (appearance.foreground.g * 255).toInt(),
                (appearance.foreground.b * 255).toInt(),
            )
            graphics.color = fgColor
            for (row in 0 until matrix.size) {
                for (col in 0 until matrix.size) {
                    if (matrix.isDark(col, row)) {
                        graphics.fillRect(col * modulePixels, row * modulePixels, modulePixels, modulePixels)
                    }
                }
            }

            graphics.dispose()

            // Save PNG
            val outputFile = File(directory, selectedFile)
            ImageIO.write(bufferedImage, "png", outputFile)
            println("✓ QR code saved to: ${outputFile.absolutePath}")
        }
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
        // Show file save dialog
        val fileDialog = java.awt.FileDialog(null as java.awt.Frame?, "Save QR Code as SVG", 1) // 1 = SAVE
        fileDialog.file = fileName
        fileDialog.isVisible = true

        val directory = fileDialog.directory
        val selectedFile = fileDialog.file
        if (directory != null && selectedFile != null) {
            // Generate SVG as text
            val moduleSize = 10 // pixels per module in SVG
            val size = matrix.size * moduleSize

            val svg = StringBuilder()
            svg.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
            svg.append("""<svg xmlns="http://www.w3.org/2000/svg" width="$size" height="$size" viewBox="0 0 $size $size">""").append("\n")

            // Background
            val bgHex = appearance.background.toHexColor()
            svg.append("""  <rect width="$size" height="$size" fill="$bgHex"/>""").append("\n")

            // Modules
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

            // Save SVG
            val outputFile = File(directory, selectedFile)
            outputFile.writeText(svg.toString())
            println("✓ QR code SVG saved to: ${outputFile.absolutePath}")
        }
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
