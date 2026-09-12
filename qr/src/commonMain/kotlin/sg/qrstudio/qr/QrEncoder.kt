package sg.qrstudio.qr

import qrcode.internals.QRCodeSquare
import qrcode.internals.QRCodeSquareType
import qrcode.raw.MaskPattern
import qrcode.raw.QRCodeDataType
import qrcode.raw.QRCodeProcessor

/**
 * Turns a payload string into a [ModuleMatrix].
 *
 * FR-201: this runs in common code, so all four targets share one encoder and cannot
 * drift apart. FR-202: standard QR per ISO/IEC 18004 in byte mode, never Micro QR.
 *
 * The heavy lifting — Reed-Solomon error correction, data placement, format information
 * — comes from qrcode-kotlin, which is well established. Two things are done here that
 * it does not do:
 *
 *  1. **Mask selection.** The library hardcodes pattern 000. We evaluate all eight and
 *     keep the lowest-penalty symbol, as the specification intends. See [MaskPenalty].
 *  2. **The quiet zone.** Its output is the bare symbol, so the four-module clear border
 *     required by FR-205 is added here and is not optional.
 */
object QrEncoder {
    /**
     * Encodes [payload] at [errorCorrection].
     *
     * Byte mode is forced rather than letting the library pick a compacter numeric or
     * alphanumeric mode. FR-202 requires it, and it keeps output deterministic: a
     * merchant name is enough to change the inferred mode and therefore the whole
     * symbol, which would make golden-image comparisons meaningless.
     */
    fun encode(
        payload: String,
        errorCorrection: ErrorCorrection = ErrorCorrection.DEFAULT,
    ): ModuleMatrix {
        require(payload.isNotEmpty()) { "Cannot encode an empty payload" }

        val processor =
            QRCodeProcessor(
                data = payload,
                errorCorrectionLevel = errorCorrection.toLibraryLevel(),
                dataType = QRCodeDataType.DEFAULT, // byte mode — FR-202
            )

        // The symbol version must be computed for the data type we actually encode with.
        //
        // encode()'s default version parameter is derived from the *inferred* data type,
        // which for an all-uppercase payload is alphanumeric — a denser mode needing
        // fewer bits. Forcing byte mode while accepting that default asks the library to
        // fit byte-mode data into an alphanumeric-sized symbol, and it throws.
        //
        // PayNow payloads happen to dodge this today only because merchant city is fixed
        // to "Singapore", whose lowercase letters force byte mode anyway. That is luck,
        // not design: an all-uppercase payload (the deferred plain URL/text mode, or any
        // change to the fixed fields) would hit it. Passing the data type explicitly
        // keeps the two calculations in agreement.
        val infoDensity =
            QRCodeProcessor.infoDensityForDataAndECL(
                data = payload,
                errorCorrectionLevel = errorCorrection.toLibraryLevel(),
                dataType = QRCodeDataType.DEFAULT,
            )

        // Try every mask and keep the best-scoring symbol (ISO/IEC 18004 §8.8.2).
        var best: Array<Array<QRCodeSquare>>? = null
        var bestPattern = 0
        var bestScore = Int.MAX_VALUE

        MaskPattern.entries.forEachIndexed { patternIndex, pattern ->
            val squares = processor.encode(type = infoDensity, maskPattern = pattern)
            val score = MaskPenalty.score(squares.toDarkGrid())
            if (score < bestScore) {
                bestScore = score
                best = squares
                bestPattern = patternIndex
            }
        }

        val squares = checkNotNull(best) { "Encoder produced no candidate symbols" }
        return squares.toModuleMatrix(bestPattern, errorCorrection)
    }

    private fun Array<Array<QRCodeSquare>>.toDarkGrid(): Array<BooleanArray> =
        Array(size) { row -> BooleanArray(this[row].size) { col -> this[row][col].dark } }

    /**
     * Copies the library's grid into our own structure, adding the quiet zone and
     * classifying each module so renderers can style finder patterns separately.
     */
    private fun Array<Array<QRCodeSquare>>.toModuleMatrix(
        maskPattern: Int,
        errorCorrection: ErrorCorrection,
    ): ModuleMatrix {
        val quietZone = ModuleMatrix.QUIET_ZONE_MODULES
        val contentSize = size
        val fullSize = contentSize + 2 * quietZone

        val dark = BooleanArray(fullSize * fullSize)
        val types = Array(fullSize * fullSize) { ModuleType.QUIET_ZONE }

        for (row in 0 until contentSize) {
            for (col in 0 until contentSize) {
                val square = this[row][col]
                val x = col + quietZone
                val y = row + quietZone
                val index = y * fullSize + x
                dark[index] = square.dark
                types[index] = square.moduleType()
            }
        }

        return ModuleMatrix(
            size = fullSize,
            quietZone = quietZone,
            dark = dark,
            types = types,
            maskPattern = maskPattern,
            errorCorrection = errorCorrection,
        )
    }

    /**
     * The library marks the light separator ring around each finder pattern as part of
     * the position probe, so a light probe module is the separator and a dark one is the
     * finder proper. Keeping them distinct matters because FR-407 lets the user colour
     * the eyes, and the separator must stay background-coloured or the eye stops reading.
     */
    private fun QRCodeSquare.moduleType(): ModuleType =
        when (squareInfo.type) {
            QRCodeSquareType.POSITION_PROBE -> if (dark) ModuleType.FINDER else ModuleType.FINDER_SEPARATOR
            QRCodeSquareType.POSITION_ADJUST -> ModuleType.ALIGNMENT
            QRCodeSquareType.TIMING_PATTERN -> ModuleType.TIMING
            QRCodeSquareType.DEFAULT -> ModuleType.DATA
        }
}
