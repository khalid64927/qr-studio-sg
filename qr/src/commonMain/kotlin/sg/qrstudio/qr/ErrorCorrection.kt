package sg.qrstudio.qr

import qrcode.raw.ErrorCorrectionLevel

/**
 * QR error correction levels, in the conventional L/M/Q/H naming (FR-203).
 *
 * [recoverableFraction] is the approximate share of the symbol that can be destroyed
 * while remaining decodable. It is what makes a centre logo viable at all: the logo
 * covers modules, and error correction reconstructs them (FR-311).
 */
enum class ErrorCorrection(
    val letter: String,
    val recoverableFraction: Double,
) {
    LOW("L", 0.07),
    MEDIUM("M", 0.15),
    QUARTILE("Q", 0.25),
    HIGH("H", 0.30),
    ;

    /**
     * Maps to the underlying library's levels.
     *
     * Read this carefully: qrcode-kotlin's names do not mean what they appear to.
     * Its enum carries the ISO/IEC 18004 format-information bits as `value`, and those
     * are L=1, M=0, Q=3, H=2. So its `HIGH` (value 3) is really **Q at 25%**, and its
     * `VERY_HIGH` (value 2) is the real **H at 30%**.
     *
     * Mapping our HIGH to its `HIGH` would silently encode 25% recovery wherever FR-204
     * forces H for a logo — a code with a 30% logo over 25% recovery is one that may not
     * scan. ErrorCorrectionTest pins the format-bit values so this cannot regress.
     */
    internal fun toLibraryLevel(): ErrorCorrectionLevel =
        when (this) {
            LOW -> ErrorCorrectionLevel.LOW // value 1 = L
            MEDIUM -> ErrorCorrectionLevel.MEDIUM // value 0 = M
            QUARTILE -> ErrorCorrectionLevel.HIGH // value 3 = Q
            HIGH -> ErrorCorrectionLevel.VERY_HIGH // value 2 = H
        }

    companion object {
        /** FR-203: the default when no logo is present. */
        val DEFAULT = MEDIUM

        /** FR-204: forced whenever a logo is applied. */
        val WITH_LOGO = HIGH
    }
}
