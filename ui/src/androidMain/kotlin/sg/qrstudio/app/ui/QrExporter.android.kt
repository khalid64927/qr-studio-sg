package sg.qrstudio.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Environment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import sg.qrstudio.qr.AppearanceConfig
import sg.qrstudio.qr.LogoConfig
import sg.qrstudio.qr.ModuleMatrix
import java.io.File
import java.io.FileOutputStream

/**
 * Uses `android.graphics.Bitmap`/`Canvas`, the real Android rasterisation API — not
 * `java.awt.BufferedImage`/`Graphics2D`/`ImageIO`, which aren't part of the Android
 * runtime at all and would crash with `NoClassDefFoundError` on a real device (this was
 * the previous, broken implementation here).
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
        // Save to Downloads directory (requires permission or scoped storage)
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val outputFile = File(downloadsDir, fileName)

        val bitmap = Bitmap.createBitmap(pixelSize, pixelSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Fill background
        val bgColor = appearance.background.toAndroidColor()
        canvas.drawColor(bgColor)

        // Draw modules
        val modulePixels = pixelSize / matrix.size

        // Compute logo area if enabled
        val logoSize = if (logo.enabled) (pixelSize * logo.clampedSizeFraction()).toInt() else 0
        val logoLeft = (pixelSize - logoSize) / 2
        val logoTop = (pixelSize - logoSize) / 2
        val padding = 5

        val fgColor = appearance.foreground.toAndroidColor()
        val eyeColor = appearance.eyeColour.toAndroidColor()
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
                    paint.color = if (isEye) eyeColor else fgColor
                    val shape = if (isEye) appearance.eyeStyle.shape else appearance.moduleShape
                    drawModuleAndroid(canvas, paint, x, y, modulePixels, shape)
                }
            }
        }

        // Draw logo backing plate if enabled
        if (logo.enabled) {
            paint.color = bgColor
            when (logo.shape) {
                sg.qrstudio.qr.LogoShape.CIRCLE -> {
                    val radius = logoSize / 2f
                    canvas.drawCircle(logoLeft + radius, logoTop + radius, radius, paint)
                }
                sg.qrstudio.qr.LogoShape.ROUNDED -> {
                    val rect = RectF(logoLeft.toFloat(), logoTop.toFloat(), (logoLeft + logoSize).toFloat(), (logoTop + logoSize).toFloat())
                    canvas.drawRoundRect(rect, 20f, 20f, paint)
                }
                sg.qrstudio.qr.LogoShape.SQUARE -> {
                    canvas.drawRect(
                        logoLeft.toFloat(),
                        logoTop.toFloat(),
                        (logoLeft + logoSize).toFloat(),
                        (logoTop + logoSize).toFloat(),
                        paint,
                    )
                }
            }

            // Draw the actual image if available
            if (decodedImage != null && !logo.placeholder) {
                drawLogoImageOnCanvas(canvas, decodedImage, logoLeft, logoTop, logoSize, logo.shape)
            }
        }

        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
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

private fun sg.qrstudio.qr.Contrast.Rgb.toAndroidColor(): Int =
    android.graphics.Color.rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())

/** Mirrors QrSvgRenderer's svgModule: same three shapes, same corner/inset ratios. */
private fun drawModuleAndroid(
    canvas: Canvas,
    paint: Paint,
    x: Int,
    y: Int,
    size: Int,
    shape: sg.qrstudio.qr.ModuleShape,
) {
    when (shape) {
        sg.qrstudio.qr.ModuleShape.SQUARE -> canvas.drawRect(x.toFloat(), y.toFloat(), (x + size).toFloat(), (y + size).toFloat(), paint)
        sg.qrstudio.qr.ModuleShape.ROUNDED -> {
            val r = size * 0.3f
            val rect = RectF(x.toFloat(), y.toFloat(), (x + size).toFloat(), (y + size).toFloat())
            canvas.drawRoundRect(rect, r, r, paint)
        }
        sg.qrstudio.qr.ModuleShape.DOT -> {
            val cx = x + size / 2f
            val cy = y + size / 2f
            val radius = size / 2.2f
            canvas.drawCircle(cx, cy, radius, paint)
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

/** Same aspect-preserving fit-and-centre math as QrSvgRenderer's embeddedLogoMarkup. */
private fun drawLogoImageOnCanvas(
    canvas: Canvas,
    image: androidx.compose.ui.graphics.ImageBitmap,
    left: Int,
    top: Int,
    size: Int,
    shape: sg.qrstudio.qr.LogoShape,
) {
    val padding = 5
    val available = size - padding * 2
    val bitmap = image.asAndroidBitmap()
    val aspect = bitmap.width.toFloat() / bitmap.height
    val (scaledWidth, scaledHeight) =
        if (aspect > 1f) {
            available to (available / aspect).toInt()
        } else {
            (available * aspect).toInt() to available
        }
    val imageLeft = left + padding + (available - scaledWidth) / 2
    val imageTop = top + padding + (available - scaledHeight) / 2
    val dest =
        RectF(
            imageLeft.toFloat(),
            imageTop.toFloat(),
            (imageLeft + scaledWidth).toFloat(),
            (imageTop + scaledHeight).toFloat(),
        )
    canvas.drawBitmap(bitmap, null, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
}
