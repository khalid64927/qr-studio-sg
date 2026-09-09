package sg.qrstudio.qr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QrEncoderTest {

    private val samplePayload =
        "00020101021126490009SG.PAYNOW010120210201403121W03011040820301231520400005303702" +
            "5802SG5913ACME Pte Ltd.6009Singapore6304B69E"

    @Test
    fun `FR-205 the quiet zone is exactly four modules on every side`() {
        val matrix = QrEncoder.encode(samplePayload)
        assertEquals(4, matrix.quietZone)
        assertEquals(matrix.contentSize + 8, matrix.size)

        for (i in 0 until matrix.size) {
            for (border in 0 until 4) {
                assertTrue(!matrix.isDark(i, border), "Top quiet zone row $border is not clear")
                assertTrue(!matrix.isDark(i, matrix.size - 1 - border), "Bottom quiet zone not clear")
                assertTrue(!matrix.isDark(border, i), "Left quiet zone column $border is not clear")
                assertTrue(!matrix.isDark(matrix.size - 1 - border, i), "Right quiet zone not clear")
            }
        }
    }

    @Test
    fun `quiet zone modules are typed as quiet zone`() {
        val matrix = QrEncoder.encode(samplePayload)
        assertEquals(ModuleType.QUIET_ZONE, matrix.typeAt(0, 0))
        assertEquals(ModuleType.QUIET_ZONE, matrix.typeAt(matrix.size - 1, matrix.size - 1))
    }

    @Test
    fun `the symbol is square and sized 4n plus 17 modules`() {
        val matrix = QrEncoder.encode(samplePayload)
        val content = matrix.contentSize
        assertTrue((content - 17) % 4 == 0, "Content size $content is not a valid QR version size")
        assertTrue(content in 21..177, "Content size $content is outside the QR version range")
    }

    /**
     * The three finder patterns are what a scanner locks onto first. Each is a 7x7 block
     * whose outer ring and 3x3 core are dark. Checking all three corners catches an
     * off-by-one in the quiet-zone offset, which would otherwise only show up as a code
     * that mysteriously fails to scan.
     */
    @Test
    fun `all three finder patterns are present and correctly formed`() {
        val matrix = QrEncoder.encode(samplePayload)
        val q = matrix.quietZone
        val last = q + matrix.contentSize - 7

        listOf(q to q, last to q, q to last).forEach { (originX, originY) ->
            for (dy in 0 until 7) {
                for (dx in 0 until 7) {
                    val onOuterRing = dx == 0 || dx == 6 || dy == 0 || dy == 6
                    val inCore = dx in 2..4 && dy in 2..4
                    val expectedDark = onOuterRing || inCore
                    assertEquals(
                        expectedDark,
                        matrix.isDark(originX + dx, originY + dy),
                        "Finder pattern at ($originX, $originY) wrong at offset ($dx, $dy)",
                    )
                    if (expectedDark) {
                        assertEquals(ModuleType.FINDER, matrix.typeAt(originX + dx, originY + dy))
                    }
                }
            }
        }
    }

    @Test
    fun `there is no fourth finder pattern in the bottom-right corner`() {
        val matrix = QrEncoder.encode(samplePayload)
        val q = matrix.quietZone
        val last = q + matrix.contentSize - 7
        // A real QR symbol has exactly three. A dark 7x7 ring here would mean the
        // matrix had been transposed or mis-copied.
        val allDarkRing = (0 until 7).all { matrix.isDark(last + it, last) }
        assertTrue(!allDarkRing, "Bottom-right corner must not contain a finder pattern")
    }

    @Test
    fun `higher error correction produces a denser or equal symbol`() {
        val low = QrEncoder.encode(samplePayload, ErrorCorrection.LOW)
        val high = QrEncoder.encode(samplePayload, ErrorCorrection.HIGH)
        assertTrue(
            high.contentSize >= low.contentSize,
            "H (${high.contentSize}) should need at least as many modules as L (${low.contentSize})",
        )
        assertEquals(ErrorCorrection.HIGH, high.errorCorrection)
    }

    @Test
    fun `the chosen mask is recorded and within range`() {
        val matrix = QrEncoder.encode(samplePayload)
        assertTrue(matrix.maskPattern in 0..7, "Mask ${matrix.maskPattern} out of range")
    }

    /**
     * Mask selection must be deterministic. The preview re-renders constantly, and a
     * symbol that changed shape between renders for identical input would look like
     * corruption to the user.
     */
    @Test
    fun `encoding is deterministic for identical input`() {
        val first = QrEncoder.encode(samplePayload)
        val second = QrEncoder.encode(samplePayload)
        assertEquals(first.maskPattern, second.maskPattern)
        assertEquals(first.toAsciiArt(), second.toAsciiArt())
    }

    @Test
    fun `an empty payload is refused rather than producing an empty symbol`() {
        assertFailsWith<IllegalArgumentException> { QrEncoder.encode("") }
    }

    @Test
    fun `out-of-bounds reads are treated as light quiet zone rather than crashing`() {
        val matrix = QrEncoder.encode(samplePayload)
        assertTrue(!matrix.isDark(-1, -1))
        assertTrue(!matrix.isDark(matrix.size, matrix.size))
        assertEquals(ModuleType.QUIET_ZONE, matrix.typeAt(-5, 0))
    }
}
