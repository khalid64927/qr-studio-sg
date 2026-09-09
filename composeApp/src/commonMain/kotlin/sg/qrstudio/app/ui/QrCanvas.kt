package sg.qrstudio.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import sg.qrstudio.qr.ModuleMatrix
import kotlin.math.floor
import kotlin.math.min

/**
 * Draws a [ModuleMatrix] onto a Compose canvas.
 *
 * FR-503: the preview is painted from the module matrix directly. It is never produced
 * by generating an image file and decoding it back, which would be slower and would put
 * a second, divergent rendering path between the user and what they export.
 *
 * FR-206: this renderer and the SVG writer consume the same matrix, so the raster preview
 * and the vector export cannot disagree about which modules are dark.
 */
@Composable
fun QrCanvas(
    matrix: ModuleMatrix,
    modifier: Modifier = Modifier,
    foreground: Color = Color.Black,
    background: Color = Color.White,
) {
    Canvas(modifier = modifier) {
        drawQrMatrix(matrix, foreground, background)
    }
}

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
    foreground: Color,
    background: Color,
) {
    val available = min(size.width, size.height)
    val modulePixels = floor(available / matrix.size)

    // Below one pixel per module there is nothing meaningful to draw, and rounding would
    // collapse the symbol. Leave the canvas blank rather than paint something misleading.
    if (modulePixels < 1f) return

    val rendered = modulePixels * matrix.size

    // The origin is floored, not just the module size.
    //
    // Flooring the module size alone is not enough: the leftover space is halved to
    // centre the symbol, and half of an odd remainder is a half-pixel. Every module then
    // straddles a pixel boundary and is anti-aliased into soft grey edges, which is
    // exactly what a binarising decoder cannot read.
    //
    // This is not hypothetical — it cost a real decode failure. At a 700px canvas, error
    // correction levels L, M and H happen to produce even remainders and scan; Q produces
    // 700-671=29, an origin of 14.5, and fails to decode while looking perfectly fine to
    // the eye. Flooring here keeps every module on whole pixels (FR-603).
    val originX = floor((size.width - rendered) / 2f)
    val originY = floor((size.height - rendered) / 2f)

    // The background covers the quiet zone too — it is part of the symbol, not padding
    // the surface behind happens to supply (FR-205).
    drawRect(
        color = background,
        topLeft = androidx.compose.ui.geometry.Offset(originX, originY),
        size = androidx.compose.ui.geometry.Size(rendered, rendered),
    )

    for (row in 0 until matrix.size) {
        for (column in 0 until matrix.size) {
            if (!matrix.isDark(column, row)) continue
            drawRect(
                color = foreground,
                topLeft = androidx.compose.ui.geometry.Offset(
                    originX + column * modulePixels,
                    originY + row * modulePixels,
                ),
                size = androidx.compose.ui.geometry.Size(modulePixels, modulePixels),
            )
        }
    }
}

/** Convenience for callers that want the preview to land on an exact module boundary. */
fun idealPreviewSize(matrix: ModuleMatrix, target: Dp): Dp {
    val modules = matrix.size
    val perModule = floor(target.value / modules).coerceAtLeast(1f)
    return Dp(perModule * modules)
}
