package sg.qrstudio.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import sg.qrstudio.qr.AppearanceConfig
import sg.qrstudio.qr.Contrast
import sg.qrstudio.qr.LogoConfig
import sg.qrstudio.qr.LogoShape
import sg.qrstudio.qr.ModuleMatrix
import sg.qrstudio.qr.ModuleShape
import kotlin.math.floor
import kotlin.math.min

/**
 * Draws a [ModuleMatrix] onto a Compose canvas, styled per [appearance] and optionally
 * carrying a centre mark per [logo].
 *
 * FR-503: the preview is painted from the module matrix directly, never by generating and
 * decoding an image file. FR-206: this renderer and the eventual SVG writer consume the
 * same matrix, so raster and vector output cannot disagree about which modules are dark.
 */
@Composable
fun QrCanvas(
    matrix: ModuleMatrix,
    modifier: Modifier = Modifier,
    appearance: AppearanceConfig = AppearanceConfig(),
    logo: LogoConfig = LogoConfig(),
) {
    // Decode the image once and cache it
    val decodedImage = remember(logo.imageBytes, logo.placeholder, logo.enabled) {
        val bytes = logo.imageBytes
        if (logo.enabled && bytes != null && !logo.placeholder) {
            decodeImageBytes(bytes)
        } else {
            null
        }
    }

    Canvas(modifier = modifier) {
        drawQrMatrix(matrix, appearance, logo, decodedImage)
    }
}

/** [Contrast.Rgb] holds 0f..1f channels with no alpha; this is the only place they meet Compose's [Color]. */
private fun Contrast.Rgb.toColor(): Color = Color(r, g, b)

/**
 * FR-603: module edges are snapped to whole pixels.
 *
 * A QR module rendered across a fractional pixel boundary is anti-aliased into a grey
 * edge. Scanners binarise the image, and a symbol built from soft-edged modules loses
 * contrast exactly at the boundaries the decoder relies on. So the module size is floored
 * to a whole number of pixels and the whole symbol is centred on the remainder, which
 * trades a hairline of unused canvas for edges that stay crisp at any preview size.
 */
internal fun DrawScope.drawQrMatrix(
    matrix: ModuleMatrix,
    appearance: AppearanceConfig = AppearanceConfig(),
    logo: LogoConfig = LogoConfig(),
    decodedImage: androidx.compose.ui.graphics.ImageBitmap? = null,
) {
    val available = min(size.width, size.height)
    val modulePixels = floor(available / matrix.size)

    // Below one pixel per module there is nothing meaningful to draw, and rounding would
    // collapse the symbol. Leave the canvas blank rather than paint something misleading.
    if (modulePixels < 1f) return

    val rendered = modulePixels * matrix.size

    // The origin is floored, not just the module size — see git history for why this
    // matters: a centred-but-unfloored origin lands modules on half-pixel boundaries and
    // silently produces a symbol that looks fine and fails to decode (FR-603).
    val originX = floor((size.width - rendered) / 2f)
    val originY = floor((size.height - rendered) / 2f)

    val foreground = appearance.foreground.toColor()
    val background = appearance.background.toColor()
    val eyeColour = appearance.eyeColour.toColor()

    // The background covers the quiet zone too — it is part of the symbol, not padding
    // the surface behind happens to supply (FR-205).
    drawRect(color = background, topLeft = Offset(originX, originY), size = Size(rendered, rendered))

    // FR-311: logo geometry, computed before drawing modules so data modules under it can
    // be skipped — error correction reconstructs them, so nothing here removes a module
    // from the matrix, it only chooses not to paint over the backing plate (FR-308).
    val logoSize = if (logo.enabled) rendered * logo.clampedSizeFraction() else 0f
    val logoLeft = originX + (rendered - logoSize) / 2f
    val logoTop = originY + (rendered - logoSize) / 2f
    val padding = 5f // 5dp padding around the logo backing plate

    for (row in 0 until matrix.size) {
        for (column in 0 until matrix.size) {
            if (!matrix.isDark(column, row)) continue
            val x = originX + column * modulePixels
            val y = originY + row * modulePixels

            if (logo.enabled && withinLogoArea(x, y, modulePixels, logoLeft, logoTop, logoSize, padding)) {
                continue
            }

            val isEye = matrix.typeAt(column, row) == sg.qrstudio.qr.ModuleType.FINDER
            val colour = if (isEye) eyeColour else foreground
            val shape = if (isEye) appearance.eyeStyle.shape else appearance.moduleShape
            drawModule(x, y, modulePixels, colour, shape)
        }
    }

    if (logo.enabled) {
        if (decodedImage != null && !logo.placeholder) {
            // Draw the actual image with shape masking
            drawLogoImage(logoLeft, logoTop, logoSize, logo.shape, decodedImage, background)
        } else {
            // Draw the placeholder backing plate
            drawLogoPlaceholder(logoLeft, logoTop, logoSize, logo.shape, background)
        }
    }
}

private fun withinLogoArea(
    moduleX: Float,
    moduleY: Float,
    moduleSize: Float,
    logoLeft: Float,
    logoTop: Float,
    logoSize: Float,
    padding: Float,
): Boolean {
    val cx = moduleX + moduleSize / 2f
    val cy = moduleY + moduleSize / 2f
    return cx in (logoLeft - padding)..(logoLeft + logoSize + padding) &&
        cy in (logoTop - padding)..(logoTop + logoSize + padding)
}

private fun DrawScope.drawModule(x: Float, y: Float, size: Float, colour: Color, shape: ModuleShape) {
    when (shape) {
        ModuleShape.SQUARE -> drawRect(colour, topLeft = Offset(x, y), size = Size(size, size))
        ModuleShape.ROUNDED -> drawRoundRect(
            color = colour,
            topLeft = Offset(x, y),
            size = Size(size, size),
            cornerRadius = CornerRadius(size * 0.3f, size * 0.3f),
        )
        ModuleShape.DOT -> drawCircle(colour, radius = size / 2.2f, center = Offset(x + size / 2f, y + size / 2f))
    }
}

/**
 * §9.3: Draw the actual picked image with a backing plate behind it for contrast.
 * The image is scaled to fit within the logo area while maintaining aspect ratio,
 * centered in the available space.
 */
private fun DrawScope.drawLogoImage(
    left: Float,
    top: Float,
    size: Float,
    shape: LogoShape,
    image: androidx.compose.ui.graphics.ImageBitmap,
    plateColour: Color,
) {
    val center = Offset(left + size / 2f, top + size / 2f)

    // Draw the backing plate first
    when (shape) {
        LogoShape.CIRCLE -> drawCircle(plateColour, radius = size / 2f, center = center)
        LogoShape.SQUARE -> drawRect(plateColour, topLeft = Offset(left, top), size = Size(size, size))
        LogoShape.ROUNDED -> drawRoundRect(
            color = plateColour,
            topLeft = Offset(left, top),
            size = Size(size, size),
            cornerRadius = CornerRadius(size * 0.2f, size * 0.2f),
        )
    }

    // Calculate scaled image size maintaining aspect ratio, with 5dp padding for safe area
    val padding = 5f // 5dp padding around image edges
    val availableSize = size - (padding * 2)
    val imageAspectRatio = image.width.toFloat() / image.height
    val (scaledWidth, scaledHeight) = if (imageAspectRatio > 1f) {
        // Wider than tall
        availableSize to (availableSize / imageAspectRatio)
    } else {
        // Taller than wide
        (availableSize * imageAspectRatio) to availableSize
    }

    // Center the scaled image
    val imageLeft = center.x - (scaledWidth / 2f)
    val imageTop = center.y - (scaledHeight / 2f)

    // Draw the image scaled and centered
    drawImage(
        image = image,
        dstOffset = IntOffset(imageLeft.toInt(), imageTop.toInt()),
        dstSize = IntSize(scaledWidth.toInt(), scaledHeight.toInt()),
    )
}

/**
 * FR-308/FR-309: a centred backing plate matching the background, behind the mark.
 *
 * This draws the plate and shape mask for real, which is what FR-604 self-verification
 * and the contrast/size gates all act on. When a real image is selected, drawLogoImage
 * renders it with this backing plate underneath (§9.3).
 */
private fun DrawScope.drawLogoPlaceholder(left: Float, top: Float, size: Float, shape: LogoShape, plateColour: Color) {
    val center = Offset(left + size / 2f, top + size / 2f)
    when (shape) {
        LogoShape.CIRCLE -> drawCircle(plateColour, radius = size / 2f, center = center)
        LogoShape.SQUARE -> drawRect(plateColour, topLeft = Offset(left, top), size = Size(size, size))
        LogoShape.ROUNDED -> drawRoundRect(
            color = plateColour,
            topLeft = Offset(left, top),
            size = Size(size, size),
            cornerRadius = CornerRadius(size * 0.2f, size * 0.2f),
        )
    }
}

/**
 * The visible stand-in mark drawn over the backing plate, as a normal composable rather
 * than canvas drawing, so it can use Material icon glyphs and text without hand-rolled
 * vector paths. Positioned by the caller to sit exactly over the plate area.
 */
@Composable
fun LogoPlaceholderMark(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Icon(
            imageVector = Icons.Filled.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Convenience for callers that want the preview to land on an exact module boundary. */
fun idealPreviewSize(matrix: ModuleMatrix, target: Dp): Dp {
    val modules = matrix.size
    val perModule = floor(target.value / modules).coerceAtLeast(1f)
    return Dp(perModule * modules)
}
