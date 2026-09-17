package sg.qrstudio.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringByAppendingPathComponent
import sg.qrstudio.qr.AppearanceConfig
import sg.qrstudio.qr.LogoConfig
import sg.qrstudio.qr.ModuleMatrix
import sg.qrstudio.qr.QrSvgRenderer

/**
 * The drawing itself is shared — see [QrBitmapRenderer] — which is backed by Skia here
 * too (Compose Multiplatform's iOS target renders via skiko, the same as Desktop), so
 * this produces a real raster PNG now instead of the SVG-under-a-.png-name this used to
 * fall back to.
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
        val embeddedImage = if (logo.enabled && !logo.placeholder) decodedImage else null
        val bitmap = QrBitmapRenderer.render(matrix, appearance, logo, embeddedImage, pixelSize)
        val pngData = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG) ?: return

        writeToDocuments(fileName, pngData.bytes.toNSData())
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
        val svg = QrSvgRenderer.render(matrix, appearance, logo, embeddedImageFor(logo))
        writeToDocuments(fileName, svg.encodeToByteArray().toNSData())
    } catch (e: Exception) {
        println("❌ Export failed: ${e.message}")
    }
}

private fun writeToDocuments(
    fileName: String,
    contents: NSData,
) {
    val paths =
        NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true,
        )
    val documentsPath = paths.firstOrNull() as? NSString ?: return
    val filePath = documentsPath.stringByAppendingPathComponent(fileName)

    NSFileManager.defaultManager().createFileAtPath(
        filePath,
        contents = contents,
        attributes = null,
    )
    println("✓ QR code saved to: $filePath")
}

/** Skia (skiko) decodes on iOS the same as desktop/web — see ImageDecoder.ios.kt. */
private fun embeddedImageFor(logo: LogoConfig): QrSvgRenderer.EmbeddedLogoImage? {
    val bytes = logo.imageBytes
    if (!logo.enabled || logo.placeholder || bytes == null) return null
    return try {
        val image =
            org.jetbrains.skia.Image
                .makeFromEncoded(bytes)
        val pngData = image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG) ?: return null
        QrSvgRenderer.EmbeddedLogoImage(
            dataUri = "data:image/png;base64,${encodeBytesToBase64(pngData.bytes)}",
            width = image.width,
            height = image.height,
        )
    } catch (e: Exception) {
        null
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

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData =
    memScoped {
        NSData.create(bytes = allocArrayOf(this@toNSData), length = this@toNSData.size.toULong())
    }
