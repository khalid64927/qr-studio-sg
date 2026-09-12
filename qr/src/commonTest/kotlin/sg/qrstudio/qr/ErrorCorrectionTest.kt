package sg.qrstudio.qr

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the mapping onto the underlying library's levels.
 *
 * This exists because qrcode-kotlin's enum names are actively misleading: it calls
 * Q "HIGH" and H "VERY_HIGH". The ISO/IEC 18004 format-information bits are the
 * unambiguous identity — L=1, M=0, Q=3, H=2 — so the test asserts those rather than
 * the names. If FR-204 ever forces "H" and gets 25% recovery instead of 30%, a code
 * with a large logo may stop scanning, and nothing else in the suite would notice.
 */
class ErrorCorrectionTest {
    @Test
    fun `each level maps to the correct ISO 18004 format bits`() {
        assertEquals(1, ErrorCorrection.LOW.toLibraryLevel().value, "L must be format value 1")
        assertEquals(0, ErrorCorrection.MEDIUM.toLibraryLevel().value, "M must be format value 0")
        assertEquals(3, ErrorCorrection.QUARTILE.toLibraryLevel().value, "Q must be format value 3")
        assertEquals(2, ErrorCorrection.HIGH.toLibraryLevel().value, "H must be format value 2")
    }

    @Test
    fun `the letters and recoverable fractions match the specification`() {
        assertEquals(listOf("L", "M", "Q", "H"), ErrorCorrection.entries.map { it.letter })
        assertEquals(0.30, ErrorCorrection.HIGH.recoverableFraction)
    }

    @Test
    fun `FR-203 and FR-204 defaults`() {
        assertEquals(ErrorCorrection.MEDIUM, ErrorCorrection.DEFAULT)
        assertEquals(ErrorCorrection.HIGH, ErrorCorrection.WITH_LOGO)
    }
}
