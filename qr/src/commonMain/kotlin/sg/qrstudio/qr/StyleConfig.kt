package sg.qrstudio.qr

/** FR-207: data-module shape. Finder patterns are styled separately via [EyeStyle]. */
enum class ModuleShape { SQUARE, ROUNDED, DOT }

/** FR-307: the mask applied to a centre logo. */
enum class LogoShape { SQUARE, ROUNDED, CIRCLE }

/**
 * FR-407: finder-eye shape and colour, independent of the module colour and of each
 * other's contrast requirement — the outer ring and inner dot are checked separately
 * against the background, and neither may equal the background colour.
 */
data class EyeStyle(
    val shape: ModuleShape = ModuleShape.SQUARE,
    /** Null means "use the module foreground colour." */
    val colour: Contrast.Rgb? = null,
)

/**
 * Colour and shape choices for the symbol. Governed throughout by FR-402/FR-403: nothing
 * here may be applied if it would drop below the contrast floor.
 */
data class AppearanceConfig(
    val foreground: Contrast.Rgb = Contrast.Rgb(0f, 0f, 0f),
    val background: Contrast.Rgb = Contrast.Rgb(1f, 1f, 1f),
    val moduleShape: ModuleShape = ModuleShape.SQUARE,
    val eyeStyle: EyeStyle = EyeStyle(),
) {
    val contrastRatio: Double get() = Contrast.ratio(foreground, background)
    val contrastVerdict: Contrast.Verdict get() = Contrast.verdict(contrastRatio)

    /** FR-403: warn when the background reads darker than the foreground. */
    val backgroundDarkerThanForeground: Boolean
        get() = Contrast.relativeLuminance(background) < Contrast.relativeLuminance(foreground)

    val eyeColour: Contrast.Rgb get() = eyeStyle.colour ?: foreground
    val eyeContrastRatio: Double get() = Contrast.ratio(eyeColour, background)
    val eyeMatchesBackground: Boolean get() = eyeColour == background
}

/**
 * FR-301..FR-311: an optional centre logo. [sizeFraction] is a share of the QR's width,
 * clamped to the FR-305 range. [placeholder] is true until real image picking is wired up
 * (§9.3 ImagePicker) — the renderer draws a stand-in mark rather than a picked image so
 * the size, shape-mask and backing-plate mechanics are real and testable today.
 */
data class LogoConfig(
    val enabled: Boolean = false,
    val sizeFraction: Float = DEFAULT_SIZE_FRACTION,
    val shape: LogoShape = LogoShape.ROUNDED,
    val placeholder: Boolean = true,
) {
    companion object {
        const val MIN_SIZE_FRACTION = 0.08f
        const val MAX_SIZE_FRACTION = 0.30f
        const val DEFAULT_SIZE_FRACTION = 0.20f
        /** FR-306: amber warning above this share; MAX_SIZE_FRACTION is the hard cap. */
        const val WARNING_SIZE_FRACTION = 0.25f
    }

    /** FR-305: the slider range is 8-30%; anything outside that is a programming error. */
    fun clampedSizeFraction(): Float =
        sizeFraction.coerceIn(MIN_SIZE_FRACTION, MAX_SIZE_FRACTION)

    val showsSizeWarning: Boolean get() = enabled && sizeFraction > WARNING_SIZE_FRACTION
}
