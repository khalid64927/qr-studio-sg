package sg.qrstudio.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
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

actual fun exportQrAsPng(
    matrix: ModuleMatrix,
    appearance: AppearanceConfig,
    logo: LogoConfig,
    decodedImage: ImageBitmap?,
    pixelSize: Int,
    fileName: String,
) {
    // SVG stands in for PNG here — see exportQrAsSvg; scanners don't care about format.
    writeQrFile(matrix, appearance, logo, fileName)
}

actual fun exportQrAsSvg(
    matrix: ModuleMatrix,
    appearance: AppearanceConfig,
    logo: LogoConfig,
    decodedImage: ImageBitmap?,
    fileName: String,
) {
    writeQrFile(matrix, appearance, logo, fileName)
}

private fun writeQrFile(
    matrix: ModuleMatrix,
    appearance: AppearanceConfig,
    logo: LogoConfig,
    fileName: String,
) {
    try {
        val paths =
            NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory,
                NSUserDomainMask,
                true,
            )
        val documentsPath = paths.firstOrNull() as? NSString ?: return
        val filePath = documentsPath.stringByAppendingPathComponent(fileName)

        val svg = QrSvgRenderer.render(matrix, appearance, logo, embeddedImageFor(logo))

        NSFileManager.defaultManager().createFileAtPath(
            filePath,
            contents = svg.encodeToByteArray().toNSData(),
            attributes = null,
        )
        println("✓ QR code saved to: $filePath")
    } catch (e: Exception) {
        println("❌ Export failed: ${e.message}")
    }
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
