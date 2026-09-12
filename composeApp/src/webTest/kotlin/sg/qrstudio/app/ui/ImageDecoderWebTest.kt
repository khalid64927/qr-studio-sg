package sg.qrstudio.app.ui

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Regression test for the bug where [decodeImageBytes] silently returned a fake 1x1
 * placeholder on web instead of actually decoding the picked image (see git history:
 * "Actually decode and embed logo images on web"). That defect compiled cleanly and
 * looked fine in a screenshot — a real decoded-image assertion is what would have
 * caught it, and is what would catch a regression back to that state.
 *
 * Runs under both wasmJsTest and jsTest via the shared webTest source set, so it
 * verifies the actual pipeline browser automation could not reliably drive.
 */
class ImageDecoderWebTest {
    // A real 4x2 truecolor PNG, not a 1x1 stand-in — width/height are asserted below
    // specifically so a decoder that fakes *any* fixed-size bitmap still fails this test.
    private val testPngBase64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAQAAAACCAIAAADwyuo0AAAAFElEQVR4nGP4z8DAAMb//0MZUA4Aep8J984HxOcAAAAASUVORK5CYII="

    @Test
    fun decodeImageBytesReturnsARealDecodedBitmap() {
        val bytes = Base64.decode(testPngBase64)

        val bitmap = decodeImageBytes(bytes)

        assertNotNull(bitmap, "decodeImageBytes must decode a valid PNG, not return null")
        assertEquals(4, bitmap.width, "decoded bitmap width must match the source PNG, not a fixed placeholder size")
        assertEquals(2, bitmap.height, "decoded bitmap height must match the source PNG, not a fixed placeholder size")
    }

    @Test
    fun decodeImageBytesReturnsNullForGarbageInput() {
        val garbage = byteArrayOf(1, 2, 3, 4, 5)

        val bitmap = decodeImageBytes(garbage)

        // Not a hard requirement of the contract, but documents the current behaviour:
        // invalid input fails closed (null) rather than throwing past the caller, which
        // is what lets QrCanvas fall back to the placeholder plate instead of crashing.
        assertEquals(null, bitmap)
    }
}
