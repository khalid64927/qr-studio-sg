package sg.qrstudio.qr.js

import sg.qrstudio.qr.ErrorCorrection
import sg.qrstudio.qr.ModuleMatrix
import sg.qrstudio.qr.QrEncoder
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The npm-facing QR API.
 *
 * `:qr`'s real API ([ModuleMatrix], [ErrorCorrection], [QrEncoder]) is plain Kotlin with
 * no types `@JsExport` can't describe, but it still can't be exported directly from
 * commonMain: Kotlin/Wasm — one of this module's other targets — does not support
 * `@JsExport` on anything but top-level functions, so annotating the class/enum/object
 * there breaks the wasmJs compile this app's own web build depends on. This module
 * exists only in the js(IR) source set (never wasmJs, JVM, Android or iOS) and wraps the
 * real API for exactly that reason — not because the API itself needed changing.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class QrModuleMatrixJs internal constructor(
    private val matrix: ModuleMatrix,
) {
    val size: Int get() = matrix.size
    val quietZone: Int get() = matrix.quietZone
    val contentSize: Int get() = matrix.contentSize
    val maskPattern: Int get() = matrix.maskPattern

    /** True when the module at (x, y) should be painted, for a `<canvas>` or SVG renderer. */
    fun isDark(
        x: Int,
        y: Int,
    ): Boolean = matrix.isDark(x, y)
}

/**
 * Encodes [payload] into a QR module matrix.
 *
 * @param errorCorrectionLetter one of `"L"`, `"M"`, `"Q"`, `"H"` (case-insensitive).
 *   Defaults to `"M"`, matching [ErrorCorrection.DEFAULT].
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
fun encodeQr(
    payload: String,
    errorCorrectionLetter: String = "M",
): QrModuleMatrixJs {
    val level =
        ErrorCorrection.entries.firstOrNull { it.letter.equals(errorCorrectionLetter, ignoreCase = true) }
            ?: ErrorCorrection.DEFAULT
    return QrModuleMatrixJs(QrEncoder.encode(payload, level))
}
