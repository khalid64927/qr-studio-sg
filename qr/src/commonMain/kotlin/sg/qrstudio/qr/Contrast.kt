package sg.qrstudio.qr

import kotlin.math.pow

/**
 * WCAG 2.x relative-luminance contrast ratio, computed over plain sRGB components so this
 * module stays UI-free — no dependency on any platform colour type.
 *
 * FR-402: export is blocked below 3:1 and warned between 3:1 and 4.5:1. These thresholds
 * are lower than WCAG's own text-contrast guidance (4.5:1 / 3:1 for large text) because a
 * QR decoder binarises the image rather than reading it the way a human reads text — but
 * the same luminance-ratio math is the right tool, and staying below it is a genuine sign
 * the two colours will not binarise apart reliably.
 */
object Contrast {

    /** Minimum ratio required to keep exporting. Below this, FR-402 blocks. */
    const val BLOCK_THRESHOLD = 3.0

    /** Minimum ratio required before the warning clears. Between block and this, FR-402 warns. */
    const val WARN_THRESHOLD = 4.5

    /** Each channel 0f..1f. */
    data class Rgb(val r: Float, val g: Float, val b: Float)

    /** Relative luminance per WCAG 2.x, using the standard sRGB gamma-correction curve. */
    fun relativeLuminance(colour: Rgb): Double {
        fun channel(c: Float): Double {
            val cs = c.toDouble()
            return if (cs <= 0.03928) cs / 12.92 else ((cs + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(colour.r) + 0.7152 * channel(colour.g) + 0.0722 * channel(colour.b)
    }

    /** Always >= 1.0, regardless of which colour is passed first. */
    fun ratio(a: Rgb, b: Rgb): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    enum class Verdict { BLOCKED, WARNING, OK }

    fun verdict(ratio: Double): Verdict = when {
        ratio < BLOCK_THRESHOLD -> Verdict.BLOCKED
        ratio < WARN_THRESHOLD -> Verdict.WARNING
        else -> Verdict.OK
    }
}
