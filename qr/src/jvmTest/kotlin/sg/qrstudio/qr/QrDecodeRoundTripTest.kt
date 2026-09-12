package sg.qrstudio.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TC-03 — encode, render, decode, and assert the decoded string equals the input.
 *
 * This is the check that matters most for the encoder. The structural tests confirm the
 * matrix looks like a QR code; only an independent decoder confirms it *is* one. ZXing
 * is Google's reference Java implementation and shares no code with our encoder or with
 * qrcode-kotlin, so agreement is real evidence.
 *
 * It also guards the mask selection added in [QrEncoder]: choosing a different mask than
 * the library's default rewrites both the symbol and its format information, and getting
 * that wrong would produce something that still looks like a QR code and decodes to
 * nothing. JVM test source set only — ZXing never ships in the app.
 */
class QrDecodeRoundTripTest {
    /** Renders at a generous module size so the test measures encoding, not resampling. */
    private fun ModuleMatrix.toImage(modulePixels: Int = 8): BufferedImage {
        val pixels = size * modulePixels
        val image = BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, pixels, pixels)
        graphics.color = Color.BLACK
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (isDark(x, y)) {
                    graphics.fillRect(x * modulePixels, y * modulePixels, modulePixels, modulePixels)
                }
            }
        }
        graphics.dispose()
        return image
    }

    private fun decode(image: BufferedImage): String {
        val source = BufferedImageLuminanceSource(image)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val result =
            MultiFormatReader().decode(
                bitmap,
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                ),
            )
        return result.text
    }

    private val paynowPayload =
        "00020101021126490009SG.PAYNOW010120210201403121W03011040820301231520400005303702" +
            "5802SG5913ACME Pte Ltd.6009Singapore6304B69E"

    @Test
    fun `a PayNow payload survives encode render and decode`() {
        val matrix = QrEncoder.encode(paynowPayload)
        assertEquals(paynowPayload, decode(matrix.toImage()))
    }

    @Test
    fun `every error correction level round-trips`() {
        ErrorCorrection.entries.forEach { level ->
            val matrix = QrEncoder.encode(paynowPayload, level)
            assertEquals(paynowPayload, decode(matrix.toImage()), "Failed at level ${level.letter}")
        }
    }

    @Test
    fun `payloads of varying length and content round-trip`() {
        val random = Random(seed = 20260909)
        val payloads =
            buildList {
                add("A")
                add("0".repeat(50))
                add(paynowPayload)
                repeat(12) {
                    val length = random.nextInt(20, 300)
                    add((1..length).map { (('A'..'Z') + ('0'..'9') + '.' + '-' + '+').random(random) }.joinToString(""))
                }
            }
        payloads.forEach { payload ->
            val matrix = QrEncoder.encode(payload, ErrorCorrection.HIGH)
            assertEquals(payload, decode(matrix.toImage()), "Failed for payload of length ${payload.length}")
        }
    }

    /**
     * AC-23 / FR-205: measure the exported image and confirm at least four clear modules
     * surround the symbol on every side.
     *
     * An earlier version of this test cropped the quiet zone away and asserted the result
     * failed to decode. It did not — ZXing locates a symbol without its quiet zone quite
     * happily. That made the test a statement about one decoder's tolerance rather than
     * about our output, so it measures the output directly instead. Real scanners are
     * less forgiving than ZXing, which is exactly why the requirement stands.
     */
    @Test
    fun `FR-205 the exported image carries at least four clear modules on every side`() {
        val modulePixels = 8
        val matrix = QrEncoder.encode(paynowPayload)
        val image = matrix.toImage(modulePixels)
        val white = Color.WHITE.rgb

        fun clearBorderModules(
            axisLength: Int,
            pixelAt: (step: Int, along: Int) -> Int,
        ): Int {
            var clearPixels = 0
            outer@ for (step in 0 until axisLength) {
                for (along in 0 until image.width) {
                    if (pixelAt(step, along) != white) break@outer
                }
                clearPixels++
            }
            return clearPixels / modulePixels
        }

        assertTrue(
            clearBorderModules(image.height) { y, x -> image.getRGB(x, y) } >= 4,
            "Top border has fewer than 4 clear modules",
        )
        assertTrue(
            clearBorderModules(image.height) { y, x -> image.getRGB(x, image.height - 1 - y) } >= 4,
            "Bottom border has fewer than 4 clear modules",
        )
        assertTrue(
            clearBorderModules(image.width) { x, y -> image.getRGB(x, y) } >= 4,
            "Left border has fewer than 4 clear modules",
        )
        assertTrue(
            clearBorderModules(image.width) { x, y -> image.getRGB(image.width - 1 - x, y) } >= 4,
            "Right border has fewer than 4 clear modules",
        )
    }

    /**
     * Regression guard. Forcing byte mode (FR-202) while letting the library size the
     * symbol from its *inferred* data type makes it compute an alphanumeric-sized symbol
     * that byte-mode data cannot fit, and encoding throws.
     *
     * Real PayNow payloads avoid this only by accident — merchant city is fixed to
     * "Singapore" and its lowercase letters force byte mode. These payloads are entirely
     * within the QR alphanumeric character set, so they take the path that used to break.
     */
    @Test
    fun `an all-uppercase payload encodes and decodes without overflowing the symbol`() {
        val alphanumericOnly =
            listOf(
                "HTTPS://EXAMPLE.SG/PAY",
                "0123456789 ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:",
                "A".repeat(200),
                "SG.PAYNOW+6591234567/INV-2026",
            )
        alphanumericOnly.forEach { payload ->
            val matrix = QrEncoder.encode(payload, ErrorCorrection.HIGH)
            assertEquals(payload, decode(matrix.toImage()), "Failed for '$payload'")
        }
    }

    /**
     * FR-311 / AC-08: a centre logo is composited over the modules and error correction
     * recovers them. At H, a 20% overlay covers roughly 4% of the symbol area and must
     * still decode. This is the mechanism the whole branding feature rests on.
     */
    @Test
    fun `FR-311 a centred overlay at twenty percent still decodes at level H`() {
        val matrix = QrEncoder.encode(paynowPayload, ErrorCorrection.HIGH)
        val image = matrix.toImage()

        val overlay = (image.width * 0.20).toInt()
        val origin = (image.width - overlay) / 2
        val graphics = image.createGraphics()
        graphics.color = Color.MAGENTA
        graphics.fillRect(origin, origin, overlay, overlay)
        graphics.dispose()

        assertEquals(paynowPayload, decode(image), "A 20% centre logo must not break decoding at H")
    }
}
