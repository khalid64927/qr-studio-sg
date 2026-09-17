package sg.qrstudio.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import sg.qrstudio.qr.AppearanceConfig
import sg.qrstudio.qr.Contrast
import sg.qrstudio.qr.LogoConfig
import sg.qrstudio.qr.LogoShape
import sg.qrstudio.qr.ModuleMatrix
import sg.qrstudio.qr.ModuleShape
import sg.qrstudio.qr.ModuleType

/**
 * Rasterises a [ModuleMatrix] to an [ImageBitmap] styled per [appearance], optionally
 * with a centre logo — the raster-export counterpart to [sg.qrstudio.qr.QrSvgRenderer].
 *
 * `androidx.compose.ui.graphics.Canvas`/`ImageBitmap`/`Paint` are themselves multiplatform
 * (backed by `android.graphics` on Android, Skia/skiko everywhere else Compose
 * Multiplatform targets), so this one drawing algorithm covers every platform that wants
 * a raster PNG — unlike `QrSvgRenderer`, which is pure Kotlin with no Compose dependency,
 * this lives in `:ui` (not `:qr`) because it needs the Compose graphics runtime. Before
 * this existed, Android and Desktop each carried their own ~90-line copy of this same
 * algorithm using their *native* graphics API directly (`android.graphics.Canvas` /
 * `java.awt.Graphics2D`) — Android's copy used `java.awt`, which isn't part of the
 * Android runtime at all and crashed on-device; see QrExporter.android.kt's history for
 * that bug. Each platform's `exportQrAsPng` actual now only handles what's genuinely
 * platform-specific: encoding the finished [ImageBitmap] to PNG bytes and choosing where
 * to save it (Downloads directory, a save dialog, etc).
 */
object QrBitmapRenderer {
    private const val LOGO_PADDING = 5

    fun render(
        matrix: ModuleMatrix,
        appearance: AppearanceConfig,
        logo: LogoConfig,
        embeddedImage: ImageBitmap?,
        pixelSize: Int,
    ): ImageBitmap {
        val bitmap = ImageBitmap(pixelSize, pixelSize)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        val bgColor = appearance.background.toComposeColor()
        paint.color = bgColor
        canvas.drawRect(0f, 0f, pixelSize.toFloat(), pixelSize.toFloat(), paint)

        val modulePixels = pixelSize / matrix.size
        val logoSize = if (logo.enabled) (pixelSize * logo.clampedSizeFraction()).toInt() else 0
        val logoLeft = (pixelSize - logoSize) / 2
        val logoTop = (pixelSize - logoSize) / 2

        val fgColor = appearance.foreground.toComposeColor()
        val eyeColor = appearance.eyeColour.toComposeColor()
        for (row in 0 until matrix.size) {
            for (col in 0 until matrix.size) {
                if (!matrix.isDark(col, row)) continue
                val x = col * modulePixels
                val y = row * modulePixels

                if (logo.enabled && isWithinLogoArea(x, y, modulePixels, logoLeft, logoTop, logoSize, LOGO_PADDING)) {
                    continue
                }

                val isEye = matrix.typeAt(col, row) == ModuleType.FINDER
                paint.color = if (isEye) eyeColor else fgColor
                val shape = if (isEye) appearance.eyeStyle.shape else appearance.moduleShape
                drawModule(canvas, paint, x, y, modulePixels, shape)
            }
        }

        if (logo.enabled) {
            paint.color = bgColor
            drawLogoPlate(canvas, paint, logoLeft, logoTop, logoSize, logo.shape)

            if (embeddedImage != null && !logo.placeholder) {
                drawLogoImage(canvas, embeddedImage, logoLeft, logoTop, logoSize)
            }
        }

        return bitmap
    }

    private fun Contrast.Rgb.toComposeColor(): Color = Color(r, g, b)

    /** Mirrors QrSvgRenderer's svgModule: same three shapes, same corner/inset ratios. */
    private fun drawModule(
        canvas: Canvas,
        paint: Paint,
        x: Int,
        y: Int,
        size: Int,
        shape: ModuleShape,
    ) {
        when (shape) {
            ModuleShape.SQUARE -> canvas.drawRect(x.toFloat(), y.toFloat(), (x + size).toFloat(), (y + size).toFloat(), paint)
            ModuleShape.ROUNDED -> {
                val r = size * 0.3f
                canvas.drawRoundRect(x.toFloat(), y.toFloat(), (x + size).toFloat(), (y + size).toFloat(), r, r, paint)
            }
            ModuleShape.DOT -> {
                val center = Offset(x + size / 2f, y + size / 2f)
                canvas.drawCircle(center, size / 2.2f, paint)
            }
        }
    }

    /** Mirrors QrSvgRenderer's logoPlate: same corner ratio for the rounded case. */
    private fun drawLogoPlate(
        canvas: Canvas,
        paint: Paint,
        left: Int,
        top: Int,
        size: Int,
        shape: LogoShape,
    ) {
        when (shape) {
            LogoShape.CIRCLE -> {
                val radius = size / 2f
                canvas.drawCircle(Offset(left + radius, top + radius), radius, paint)
            }
            LogoShape.ROUNDED -> {
                val r = size * 0.2f
                canvas.drawRoundRect(left.toFloat(), top.toFloat(), (left + size).toFloat(), (top + size).toFloat(), r, r, paint)
            }
            LogoShape.SQUARE -> canvas.drawRect(left.toFloat(), top.toFloat(), (left + size).toFloat(), (top + size).toFloat(), paint)
        }
    }

    /** Same aspect-preserving fit-and-centre math as QrSvgRenderer's embeddedLogoMarkup. */
    private fun drawLogoImage(
        canvas: Canvas,
        image: ImageBitmap,
        left: Int,
        top: Int,
        size: Int,
    ) {
        val available = size - LOGO_PADDING * 2
        val aspect = image.width.toFloat() / image.height
        val (scaledWidth, scaledHeight) =
            if (aspect > 1f) {
                available to (available / aspect).toInt()
            } else {
                (available * aspect).toInt() to available
            }
        val imageLeft = left + LOGO_PADDING + (available - scaledWidth) / 2
        val imageTop = top + LOGO_PADDING + (available - scaledHeight) / 2

        canvas.drawImageRect(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(imageLeft, imageTop),
            dstSize = IntSize(scaledWidth, scaledHeight),
            paint = Paint(),
        )
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
}
