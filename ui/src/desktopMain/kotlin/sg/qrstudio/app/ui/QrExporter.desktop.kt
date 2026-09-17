package sg.qrstudio.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import sg.qrstudio.qr.AppearanceConfig
import sg.qrstudio.qr.LogoConfig
import sg.qrstudio.qr.ModuleMatrix
import java.io.File

/**
 * The drawing itself is shared — see [QrBitmapRenderer] — so this only handles what's
 * genuinely Desktop-specific: the save-file dialog and encoding the result to PNG bytes.
 */
actual fun exportQrAsPng(
    matrix: ModuleMatrix,
    appearance: AppearanceConfig,
    logo: LogoConfig,
    decodedImage: ImageBitmap?,
    pixelSize: Int,
    fileName: String,
) {
    try {
        val fileDialog = java.awt.FileDialog(null as java.awt.Frame?, "Save QR Code as PNG", java.awt.FileDialog.SAVE)
        fileDialog.file = fileName
        fileDialog.isVisible = true

        val directory = fileDialog.directory
        val selectedFile = fileDialog.file
        if (directory != null && selectedFile != null) {
            val embeddedImage = if (logo.enabled && !logo.placeholder) decodedImage else null
            val bitmap = QrBitmapRenderer.render(matrix, appearance, logo, embeddedImage, pixelSize)

            val outputFile = File(directory, selectedFile)
            javax.imageio.ImageIO.write(bitmap.toAwtImage(), "png", outputFile)
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
        val fileDialog = java.awt.FileDialog(null as java.awt.Frame?, "Save QR Code as SVG", java.awt.FileDialog.SAVE)
        fileDialog.file = fileName
        fileDialog.isVisible = true

        val directory = fileDialog.directory
        val selectedFile = fileDialog.file
        if (directory != null && selectedFile != null) {
            val svg =
                sg.qrstudio.qr.QrSvgRenderer
                    .render(matrix, appearance, logo, embeddedImageFor(logo, decodedImage))
            val outputFile = File(directory, selectedFile)
            outputFile.writeText(svg)
            println("✓ QR code SVG saved to: ${outputFile.absolutePath}")
        }
    } catch (e: Exception) {
        println("❌ Export failed: ${e.message}")
    }
}

/**
 * [decodedImage] is already Skia-backed (desktop's own decodeImageBytes uses
 * org.jetbrains.skia.Image), so re-encoding it to a PNG data URI needs no extra decode.
 */
private fun embeddedImageFor(
    logo: LogoConfig,
    decodedImage: ImageBitmap?,
): sg.qrstudio.qr.QrSvgRenderer.EmbeddedLogoImage? {
    if (!logo.enabled || logo.placeholder || decodedImage == null) return null
    return try {
        val awtImage = decodedImage.toAwtImage()
        val pngBytes =
            java.io
                .ByteArrayOutputStream()
                .apply { javax.imageio.ImageIO.write(awtImage, "png", this) }
                .toByteArray()
        sg.qrstudio.qr.QrSvgRenderer.EmbeddedLogoImage(
            dataUri = "data:image/png;base64,${java.util.Base64.getEncoder().encodeToString(pngBytes)}",
            width = decodedImage.width,
            height = decodedImage.height,
        )
    } catch (e: Exception) {
        null
    }
}
