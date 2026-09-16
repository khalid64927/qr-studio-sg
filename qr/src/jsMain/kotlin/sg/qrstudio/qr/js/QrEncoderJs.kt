package sg.qrstudio.qr.js

import sg.qrstudio.qr.Contrast
import sg.qrstudio.qr.ErrorCorrection
import sg.qrstudio.qr.LogoConfig
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

    /**
     * True when (x, y) is part of a finder pattern (one of the three corner squares) —
     * the modules a renderer styles independently via an "eye style", separate from every
     * other dark module. [ModuleMatrix]'s other module types (alignment, timing, the
     * separator ring) all render the same as ordinary data modules, so this is the only
     * distinction a renderer actually needs; the full [sg.qrstudio.qr.ModuleType] enum
     * isn't exported for the same Kotlin/Wasm reason described on this file's other
     * declarations.
     */
    fun isFinder(
        x: Int,
        y: Int,
    ): Boolean = matrix.typeAt(x, y) == sg.qrstudio.qr.ModuleType.FINDER
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

/**
 * The FR-402/FR-407 contrast check — the same [Contrast] math the Compose renderer's
 * export gate runs, exposed so a JS/TS UI enforces the identical floor rather than
 * reimplementing WCAG luminance math independently and risking it drifting from the
 * Kotlin original.
 *
 * @param verdict `"OK"`, `"WARNING"` (export allowed, contrast is marginal) or
 *   `"BLOCKED"` (below the floor — FR-402 blocks export at this contrast).
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class ContrastCheckJs internal constructor(
    val contrastRatio: Double,
    val contrastVerdict: String,
    val eyeContrastRatio: Double,
    val eyeVerdict: String,
    val eyeMatchesBackground: Boolean,
    val backgroundDarkerThanForeground: Boolean,
)

/** Each channel 0f..1f, matching [Contrast.Rgb] — not 0-255. */
@OptIn(ExperimentalJsExport::class)
@JsExport
fun checkContrast(
    foregroundR: Double,
    foregroundG: Double,
    foregroundB: Double,
    backgroundR: Double,
    backgroundG: Double,
    backgroundB: Double,
    eyeR: Double,
    eyeG: Double,
    eyeB: Double,
): ContrastCheckJs {
    val foreground = Contrast.Rgb(foregroundR.toFloat(), foregroundG.toFloat(), foregroundB.toFloat())
    val background = Contrast.Rgb(backgroundR.toFloat(), backgroundG.toFloat(), backgroundB.toFloat())
    val eye = Contrast.Rgb(eyeR.toFloat(), eyeG.toFloat(), eyeB.toFloat())

    val contrastRatio = Contrast.ratio(foreground, background)
    val eyeContrastRatio = Contrast.ratio(eye, background)

    return ContrastCheckJs(
        contrastRatio = contrastRatio,
        contrastVerdict = Contrast.verdict(contrastRatio).name,
        eyeContrastRatio = eyeContrastRatio,
        eyeVerdict = Contrast.verdict(eyeContrastRatio).name,
        eyeMatchesBackground = eye == background,
        backgroundDarkerThanForeground = Contrast.relativeLuminance(background) < Contrast.relativeLuminance(foreground),
    )
}

/**
 * FR-305/FR-306 logo size-fraction bounds, so a JS/TS UI's slider range and warning
 * threshold can't drift from [LogoConfig]'s. A function, not top-level properties or an
 * exported `object` — `generateTypeScriptDefinitions()` currently emits a real, working
 * runtime getter for a top-level `@JsExport val` but omits it from the generated `.d.ts`
 * entirely (confirmed by reading the compiled output directly), and an exported `object`
 * has the reverse problem: `KtSingleton`-based codegen nests its members under a
 * `$metadata$.constructor` type that's awkward to consume from TypeScript. A function
 * returning a plain data class sidesteps both.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class LogoSizeBoundsJs internal constructor(
    val minFraction: Double,
    val maxFraction: Double,
    val defaultFraction: Double,
    val warningFraction: Double,
)

@OptIn(ExperimentalJsExport::class)
@JsExport
fun logoSizeBounds(): LogoSizeBoundsJs =
    LogoSizeBoundsJs(
        minFraction = LogoConfig.MIN_SIZE_FRACTION.toDouble(),
        maxFraction = LogoConfig.MAX_SIZE_FRACTION.toDouble(),
        defaultFraction = LogoConfig.DEFAULT_SIZE_FRACTION.toDouble(),
        warningFraction = LogoConfig.WARNING_SIZE_FRACTION.toDouble(),
    )
