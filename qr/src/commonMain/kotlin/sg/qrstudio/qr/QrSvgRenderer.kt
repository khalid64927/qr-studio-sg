package sg.qrstudio.qr

/**
 * Builds the SVG markup for a [ModuleMatrix] styled per [appearance], optionally with a
 * centre logo. Pure Kotlin, no platform dependency — this exists so every Compose
 * platform's SVG export and the npm-published `qr` facade render from exactly the same
 * code, rather than each hand-porting the algorithm independently. That's how this file
 * came to exist: composeApp's four platform actuals (android/desktop/ios/web) each
 * carried their own ~80-line copy of this exact algorithm, already drifting apart in
 * small ways (Android's `svgModule` used a different dot radius divisor than the other
 * three, for instance) before being consolidated here.
 */
object QrSvgRenderer {
    private const val LOGO_PADDING = 5

    /**
     * A picked logo image, already decoded and re-encoded to a `data:` URI. Decoding raw
     * image bytes is genuinely platform-specific (Skia, AWT, UIKit, a browser's own
     * Image/Canvas), so that step stays in each caller; this renderer only needs the
     * finished URI and the image's natural pixel dimensions, to scale it into the logo
     * area while preserving aspect ratio.
     */
    data class EmbeddedLogoImage(
        val dataUri: String,
        val width: Int,
        val height: Int,
    )

    /** [moduleSize] is the SVG-unit width of one module; the whole symbol is `matrix.size * moduleSize`. */
    fun render(
        matrix: ModuleMatrix,
        appearance: AppearanceConfig,
        logo: LogoConfig,
        embeddedImage: EmbeddedLogoImage? = null,
        moduleSize: Int = 10,
    ): String {
        val size = matrix.size * moduleSize

        val svg = StringBuilder()
        svg.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        svg
            .append(
                """<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="$size" height="$size" viewBox="0 0 $size $size">""",
            ).append('\n')

        val bgHex = appearance.background.toHexColor()
        svg.append("""  <rect width="$size" height="$size" fill="$bgHex"/>""").append('\n')

        val logoSize = if (logo.enabled) (size * logo.clampedSizeFraction()).toInt() else 0
        val logoLeft = (size - logoSize) / 2
        val logoTop = (size - logoSize) / 2

        val fgHex = appearance.foreground.toHexColor()
        val eyeHex = appearance.eyeColour.toHexColor()
        for (row in 0 until matrix.size) {
            for (col in 0 until matrix.size) {
                if (!matrix.isDark(col, row)) continue
                val x = col * moduleSize
                val y = row * moduleSize

                if (logo.enabled && isWithinLogoArea(x, y, moduleSize, logoLeft, logoTop, logoSize, LOGO_PADDING)) {
                    continue
                }

                val isEye = matrix.typeAt(col, row) == ModuleType.FINDER
                val colour = if (isEye) eyeHex else fgHex
                val shape = if (isEye) appearance.eyeStyle.shape else appearance.moduleShape
                svg.append(svgModule(x, y, moduleSize, colour, shape)).append('\n')
            }
        }

        if (logo.enabled) {
            svg.append(logoPlate(logoLeft, logoTop, logoSize, logo.shape, bgHex)).append('\n')
            if (embeddedImage != null && !logo.placeholder) {
                svg.append(embeddedLogoMarkup(embeddedImage, logoLeft, logoTop, logoSize)).append('\n')
            }
        }

        svg.append("""</svg>""")
        return svg.toString()
    }

    private fun Contrast.Rgb.toHexColor(): String {
        val r = (this.r * 255).toInt().toString(16).padStart(2, '0')
        val g = (this.g * 255).toInt().toString(16).padStart(2, '0')
        val b = (this.b * 255).toInt().toString(16).padStart(2, '0')
        return "#$r$g$b"
    }

    /** Mirrors QrCanvas.kt's drawModule: same three shapes, same corner/inset ratios. */
    private fun svgModule(
        x: Int,
        y: Int,
        size: Int,
        colourHex: String,
        shape: ModuleShape,
    ): String =
        when (shape) {
            ModuleShape.SQUARE -> """    <rect x="$x" y="$y" width="$size" height="$size" fill="$colourHex"/>"""
            ModuleShape.ROUNDED -> {
                val r = (size * 0.3).toInt()
                """    <rect x="$x" y="$y" width="$size" height="$size" rx="$r" ry="$r" fill="$colourHex"/>"""
            }
            ModuleShape.DOT -> {
                val cx = x + size / 2.0
                val cy = y + size / 2.0
                val radius = size / 2.2
                """    <circle cx="$cx" cy="$cy" r="$radius" fill="$colourHex"/>"""
            }
        }

    /** Mirrors QrCanvas.kt's drawLogoPlaceholder: same corner ratio for the rounded case. */
    private fun logoPlate(
        left: Int,
        top: Int,
        size: Int,
        shape: LogoShape,
        bgHex: String,
    ): String =
        when (shape) {
            LogoShape.CIRCLE -> {
                val radius = size / 2
                """  <circle cx="${left + radius}" cy="${top + radius}" r="$radius" fill="$bgHex"/>"""
            }
            LogoShape.ROUNDED -> {
                val radius = (size * 0.2).toInt()
                """  <rect x="$left" y="$top" width="$size" height="$size" rx="$radius" fill="$bgHex"/>"""
            }
            LogoShape.SQUARE -> """  <rect x="$left" y="$top" width="$size" height="$size" fill="$bgHex"/>"""
        }

    /** Same aspect-preserving fit-and-centre math as QrCanvas.kt's drawLogoImage. */
    private fun embeddedLogoMarkup(
        image: EmbeddedLogoImage,
        left: Int,
        top: Int,
        size: Int,
    ): String {
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
        return """  <image x="$imageLeft" y="$imageTop" width="$scaledWidth" height="$scaledHeight" xlink:href="${image.dataUri}"/>"""
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
