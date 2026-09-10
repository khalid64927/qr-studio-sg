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
                val bgColor = java.awt.Color(
                    (appearance.background.r * 255).toInt(),
                    (appearance.background.g * 255).toInt(),
                    (appearance.background.b * 255).toInt(),
                )
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
                val bgHex = appearance.background.toHexColor()
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

private fun drawLogoImageOnCanvas(
    graphics: java.awt.Graphics2D,
    image: androidx.compose.ui.graphics.ImageBitmap,
    left: Int,
    top: Int,
    size: Int,
    shape: sg.qrstudio.qr.LogoShape,
) {
    try {
        val awtImage = image.toAwtImage()
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

        graphics.drawImage(awtImage, imageLeft, imageTop, scaledWidth, scaledHeight, null)
    } catch (e: Exception) {
        // Silently fail if image rendering fails
    }
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

private fun drawLogoImageSvg(
    svg: StringBuilder,
    image: androidx.compose.ui.graphics.ImageBitmap,
    left: Int,
    top: Int,
    size: Int,
    shape: sg.qrstudio.qr.LogoShape,
) {
    try {
        val awtImage = image.toAwtImage()
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

        // Convert BufferedImage to base64 data URI
        val base64Image = java.util.Base64.getEncoder().encodeToString(
            java.io.ByteArrayOutputStream().apply {
                javax.imageio.ImageIO.write(awtImage, "png", this)
            }.toByteArray()
        )

        svg.append("""  <image x="$imageLeft" y="$imageTop" width="$scaledWidth" height="$scaledHeight" xlink:href="data:image/png;base64,$base64Image"/>""").append("\n")
    } catch (e: Exception) {
        // Silently fail if image embedding fails
    }
}
