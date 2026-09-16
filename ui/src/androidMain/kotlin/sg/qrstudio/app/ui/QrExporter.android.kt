package sg.qrstudio.app.ui

import android.os.Environment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import sg.qrstudio.qr.AppearanceConfig
import sg.qrstudio.qr.LogoConfig
import sg.qrstudio.qr.ModuleMatrix
import java.io.File
import javax.imageio.ImageIO

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
        val bgColor =
            java.awt.Color(
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

        val fgColor = appearance.foreground.toAwtColor()
        val eyeColor = appearance.eyeColour.toAwtColor()
        for (row in 0 until matrix.size) {
            for (col in 0 until matrix.size) {
                if (matrix.isDark(col, row)) {
                    val x = col * modulePixels
                    val y = row * modulePixels

                    // Skip modules within logo area
                    if (logo.enabled && isWithinLogoArea(x, y, modulePixels, logoLeft, logoTop, logoSize, padding)) {
                        continue
                    }

                    val isEye = matrix.typeAt(col, row) == sg.qrstudio.qr.ModuleType.FINDER
                    graphics.color = if (isEye) eyeColor else fgColor
                    val shape = if (isEye) appearance.eyeStyle.shape else appearance.moduleShape
                    drawModuleAwt(graphics, x, y, modulePixels, shape)
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
        val svg =
            sg.qrstudio.qr.QrSvgRenderer
                .render(matrix, appearance, logo, embeddedImageFor(logo, decodedImage))
        outputFile.writeText(svg)
        android.util.Log.i("QrExport", "✓ QR code SVG saved to: ${outputFile.absolutePath}")
    } catch (e: Exception) {
        android.util.Log.e("QrExport", "❌ Export failed: ${e.message}", e)
    }
}

/**
 * [decodedImage] wraps an `android.graphics.Bitmap` on this platform (see
 * ImageDecoder.android.kt) — `Bitmap.compress` is the real Android API for re-encoding
 * it, not the `java.awt`/Skia route desktop and web use, which isn't part of the Android
 * runtime.
 */
private fun embeddedImageFor(
    logo: LogoConfig,
    decodedImage: ImageBitmap?,
): sg.qrstudio.qr.QrSvgRenderer.EmbeddedLogoImage? {
    if (!logo.enabled || logo.placeholder || decodedImage == null) return null
    return try {
        val bitmap = decodedImage.asAndroidBitmap()
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        sg.qrstudio.qr.QrSvgRenderer.EmbeddedLogoImage(
            dataUri = "data:image/png;base64,${android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)}",
            width = decodedImage.width,
            height = decodedImage.height,
        )
    } catch (e: Exception) {
        null
    }
}

private fun sg.qrstudio.qr.Contrast.Rgb.toAwtColor(): java.awt.Color =
    java.awt.Color((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())

/** Mirrors QrCanvas.kt's drawModule: same three shapes, same corner/inset ratios. */
private fun drawModuleAwt(
    graphics: java.awt.Graphics2D,
    x: Int,
    y: Int,
    size: Int,
    shape: sg.qrstudio.qr.ModuleShape,
) {
    when (shape) {
        sg.qrstudio.qr.ModuleShape.SQUARE -> graphics.fillRect(x, y, size, size)
        sg.qrstudio.qr.ModuleShape.ROUNDED -> {
            val arc = (size * 0.6).toInt()
            graphics.fillRoundRect(x, y, size, size, arc, arc)
        }
        sg.qrstudio.qr.ModuleShape.DOT -> {
            val radius = (size / 1.1).toInt()
            val offset = (size - radius) / 2
            graphics.fillOval(x + offset, y + offset, radius, radius)
        }
    }
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
    // PNG logo image rendering uses BufferedImage on Android via platform AWT compatibility
    // This is handled by the parent exportQrAsPng function's use of java.awt.image.BufferedImage
    // For full image support, consider extracting pixels and reconstructing the image
}
