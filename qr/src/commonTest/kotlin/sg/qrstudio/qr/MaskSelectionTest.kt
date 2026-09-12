package sg.qrstudio.qr

import qrcode.raw.MaskPattern
import qrcode.raw.QRCodeDataType
import qrcode.raw.QRCodeProcessor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The underlying library hardcodes mask pattern 000 and does no selection at all, so
 * [QrEncoder] evaluates all eight and keeps the lowest-penalty symbol as ISO/IEC 18004
 * intends.
 *
 * These tests assert that the selection is real and that it is an improvement, rather
 * than merely asserting a mask index was recorded.
 */
class MaskSelectionTest {
    private val paynowPayload =
        "00020101021126490009SG.PAYNOW010120210201403121W03011040820301231520400005303702" +
            "5802SG5913ACME Pte Ltd.6009Singapore6304B69E"

    private fun penaltyFor(
        payload: String,
        level: ErrorCorrection,
        pattern: MaskPattern,
    ): Int {
        val processor =
            QRCodeProcessor(
                data = payload,
                errorCorrectionLevel = level.toLibraryLevel(),
                dataType = QRCodeDataType.DEFAULT,
            )
        val density =
            QRCodeProcessor.infoDensityForDataAndECL(
                data = payload,
                errorCorrectionLevel = level.toLibraryLevel(),
                dataType = QRCodeDataType.DEFAULT,
            )
        val squares = processor.encode(type = density, maskPattern = pattern)
        return MaskPenalty.score(
            Array(squares.size) { row -> BooleanArray(squares[row].size) { col -> squares[row][col].dark } },
        )
    }

    @Test
    fun `the chosen mask scores no worse than every other mask`() {
        val matrix = QrEncoder.encode(paynowPayload)
        val chosenPenalty = penaltyFor(paynowPayload, ErrorCorrection.DEFAULT, MaskPattern.entries[matrix.maskPattern])
        MaskPattern.entries.forEach { pattern ->
            assertTrue(
                chosenPenalty <= penaltyFor(paynowPayload, ErrorCorrection.DEFAULT, pattern),
                "Mask ${matrix.maskPattern} (penalty $chosenPenalty) lost to $pattern",
            )
        }
    }

    /**
     * If selection were a no-op this would be indistinguishable from the library default.
     * For this payload the default is genuinely not the best choice.
     */
    @Test
    fun `selection actually improves on the hardcoded pattern 000 for a real payload`() {
        val matrix = QrEncoder.encode(paynowPayload)
        val defaultPenalty = penaltyFor(paynowPayload, ErrorCorrection.DEFAULT, MaskPattern.PATTERN000)
        val chosenPenalty = penaltyFor(paynowPayload, ErrorCorrection.DEFAULT, MaskPattern.entries[matrix.maskPattern])
        assertTrue(
            chosenPenalty < defaultPenalty,
            "Expected an improvement over pattern 000 ($defaultPenalty), but chose $chosenPenalty",
        )
    }

    @Test
    fun `penalty scoring rewards an even spread over a uniform block`() {
        val size = 21
        val allDark = Array(size) { BooleanArray(size) { true } }
        val checkerboard = Array(size) { row -> BooleanArray(size) { col -> (row + col) % 2 == 0 } }
        assertTrue(
            MaskPenalty.score(checkerboard) < MaskPenalty.score(allDark),
            "A uniform block must score far worse than an even spread",
        )
    }

    @Test
    fun `a fully uniform symbol is penalised on every rule`() {
        val size = 21
        val allLight = Array(size) { BooleanArray(size) { false } }
        // Runs, 2x2 blocks and the 100% imbalance all contribute.
        assertTrue(MaskPenalty.score(allLight) > 1000, "Expected a large penalty for a blank grid")
    }

    @Test
    fun `every mask produces a symbol of the same size`() {
        val sizes =
            MaskPattern.entries.map { pattern ->
                val processor =
                    QRCodeProcessor(
                        data = paynowPayload,
                        errorCorrectionLevel = ErrorCorrection.DEFAULT.toLibraryLevel(),
                        dataType = QRCodeDataType.DEFAULT,
                    )
                val density =
                    QRCodeProcessor.infoDensityForDataAndECL(
                        data = paynowPayload,
                        errorCorrectionLevel = ErrorCorrection.DEFAULT.toLibraryLevel(),
                        dataType = QRCodeDataType.DEFAULT,
                    )
                processor.encode(type = density, maskPattern = pattern).size
            }
        assertEquals(1, sizes.distinct().size, "Masking must not change the symbol version")
    }
}
