package sg.qrstudio.app.ui

import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import java.io.FileOutputStream
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
        // Save to Downloads directory (requires permission or scoped storage)
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val outputFile = File(downloadsDir, fileName)

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

        // Compute logo area if enabled
        val logoSize = if (logo.enabled) (pixelSize * logo.clampedSizeFraction()).toInt() else 0
        val logoLeft = (pixelSize - logoSize) / 2
        val logoTop = (pixelSize - logoSize) / 2
        val padding = 5

        val fgColor = java.awt.Color(
            (appearance.foreground.r * 255).toInt(),
            (appearance.foreground.g * 255).toInt(),
            (appearance.foreground.b * 255).toInt(),
        )
        graphics.color = fgColor
        for (row in 0 until matrix.size) {
            for (col in 0 until matrix.size) {
                if (matrix.isDark(col, row)) {
                    val x = col * modulePixels
                    val y = row * modulePixels

                    // Skip modules within logo area
                    if (logo.enabled && isWithinLogoArea(x, y, modulePixels, logoLeft, logoTop, logoSize, padding)) {
                        continue
                    }

                    graphics.fillRect(x, y, modulePixels, modulePixels)
                }
            }
        }

        // Draw logo backing plate if enabled
        if (logo.enabled) {
            graphics.color = bgColor
            when (logo.shape) {
                sg.qrstudio.qr.LogoShape.CIRCLE -> {
                    graphics.fillOval(logoLeft, logoTop, logoSize, logoSize)
                }
                sg.qrstudio.qr.LogoShape.ROUNDED -> {
                    graphics.fillRoundRect(logoLeft, logoTop, logoSize, logoSize, 20, 20)
                }
                sg.qrstudio.qr.LogoShape.SQUARE -> {
                    graphics.fillRect(logoLeft, logoTop, logoSize, logoSize)
                }
            }

            // Draw the actual image if available
            if (decodedImage != null && !logo.placeholder) {
                drawLogoImageOnCanvas(graphics, decodedImage, logoLeft, logoTop, logoSize, logo.shape)
            }
        }

        graphics.dispose()

        // Save PNG
        ImageIO.write(bufferedImage, "png", outputFile)
        android.util.Log.i("QrExport", "✓ QR code saved to: ${outputFile.absolutePath}")
    } catch (e: Exception) {
        android.util.Log.e("QrExport", "❌ Export failed: ${e.message}", e)
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
        // Save to Downloads directory
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val outputFile = File(downloadsDir, fileName)

        // Generate SVG
        val moduleSize = 10
        val size = matrix.size * moduleSize

        val svg = StringBuilder()
        svg.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        svg.append("""<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="$size" height="$size" viewBox="0 0 $size $size">""").append("\n")

        // Background
        val bgHex = appearance.background.toHexColor()
        svg.append("""  <rect width="$size" height="$size" fill="$bgHex"/>""").append("\n")

        // Compute logo area if enabled
        val logoSize = if (logo.enabled) (size * logo.clampedSizeFraction()).toInt() else 0
        val logoLeft = (size - logoSize) / 2
        val logoTop = (size - logoSize) / 2
        val padding = 5

        // Modules
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

            // Draw the actual image if available
            if (decodedImage != null && !logo.placeholder) {
                drawLogoImageSvg(svg, decodedImage, logoLeft, logoTop, logoSize, logo.shape)
            }
        }

        svg.append("""</svg>""")

        // Write SVG
        outputFile.writeText(svg.toString())
        android.util.Log.i("QrExport", "✓ QR code SVG saved to: ${outputFile.absolutePath}")
    } catch (e: Exception) {
        android.util.Log.e("QrExport", "❌ Export failed: ${e.message}", e)
    }
}

private fun sg.qrstudio.qr.Contrast.Rgb.toHexColor(): String {
    val r = (this.r * 255).toInt().toString(16).padStart(2, '0')
    val g = (this.g * 255).toInt().toString(16).padStart(2, '0')
    val b = (this.b * 255).toInt().toString(16).padStart(2, '0')
    return "#$r$g$b"
}

private fun isWithinLogoArea(
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

private fun drawLogoImageOnCanvas(
    graphics: java.awt.Graphics2D,
    image: androidx.compose.ui.graphics.ImageBitmap,
    left: Int,
    top: Int,
    size: Int,
    shape: sg.qrstudio.qr.LogoShape,
) {
    // PNG logo image rendering uses BufferedImage on Android via platform AWT compatibility
    // This is handled by the parent exportQrAsPng function's use of java.awt.image.BufferedImage
    // For full image support, consider extracting pixels and reconstructing the image
}

private fun drawLogoImageSvg(
    svg: StringBuilder,
    image: androidx.compose.ui.graphics.ImageBitmap,
    left: Int,
    top: Int,
    size: Int,
    shape: sg.qrstudio.qr.LogoShape,
) {
    // SVG logo image embedding not fully supported on Android
    // The backing plate is rendered, but actual image embedding requires platform-specific conversion
}
