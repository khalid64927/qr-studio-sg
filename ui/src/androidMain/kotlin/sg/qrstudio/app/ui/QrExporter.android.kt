package sg.qrstudio.app.ui

import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import sg.qrstudio.qr.AppearanceConfig
import sg.qrstudio.qr.LogoConfig
import sg.qrstudio.qr.ModuleMatrix
import java.io.File
import java.io.FileOutputStream

/**
 * The drawing itself is shared — see [QrBitmapRenderer] — so this only handles what's
 * genuinely Android-specific: encoding the result to PNG bytes and choosing where to
 * save it. (Previously this duplicated the whole drawing algorithm using
 * `java.awt.BufferedImage`/`Graphics2D`, which isn't part of the Android runtime at all
 * and crashed with `NoClassDefFoundError` on-device.)
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
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val outputFile = File(downloadsDir, fileName)

        val embeddedImage = if (logo.enabled && !logo.placeholder) decodedImage else null
        val bitmap = QrBitmapRenderer.render(matrix, appearance, logo, embeddedImage, pixelSize)

        FileOutputStream(outputFile).use { out ->
            bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
        }
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
