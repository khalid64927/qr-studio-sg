package sg.qrstudio.qr

/**
 * What a module represents within the symbol. Renderers use this to style finder
 * patterns independently of data modules (FR-207, FR-407).
 */
enum class ModuleType {
    /** One of the three large corner squares. Scanners lock onto these first. */
    FINDER,

    /** The light ring isolating a finder pattern. Must never be drawn dark. */
    FINDER_SEPARATOR,

    /** A smaller alignment square. */
    ALIGNMENT,

    /** The dotted row and column linking the finder patterns. */
    TIMING,

    /** Payload and error-correction modules. */
    DATA,

    /** The mandatory clear border. Never drawn, never encroached upon. */
    QUIET_ZONE,
}

/**
 * The rendered form of a QR symbol: a grid of light and dark modules, each tagged with
 * what it represents.
 *
 * FR-206: this is the single structure consumed by *both* the Compose renderer and the
 * SVG writer. Raster and vector output are therefore identical by construction rather
 * than by two implementations agreeing — there is no second source of truth to drift.
 *
 * Coordinates include the quiet zone: (0, 0) is the top-left of the quiet zone, and the
 * symbol proper starts at ([quietZone], [quietZone]).
 */
class ModuleMatrix internal constructor(
    /** Width and height in modules, quiet zone included. */
    val size: Int,
    /** Width of the clear border, in modules. Always [QUIET_ZONE_MODULES]. */
    val quietZone: Int,
    private val dark: BooleanArray,
    private val types: Array<ModuleType>,
    /** The mask pattern chosen for this symbol, 0-7. Recorded for debugging. */
    val maskPattern: Int,
    /** The error correction level the payload was encoded at. */
    val errorCorrection: ErrorCorrection,
) {
    init {
        require(dark.size == size * size) { "dark array is ${dark.size}, expected ${size * size}" }
        require(types.size == size * size) { "types array is ${types.size}, expected ${size * size}" }
    }

    /** Width of the symbol itself, excluding the quiet zone. */
    val contentSize: Int get() = size - 2 * quietZone

    private fun index(x: Int, y: Int) = y * size + x

    /** True when the module at ([x], [y]) should be painted. */
    fun isDark(x: Int, y: Int): Boolean =
        if (x in 0 until size && y in 0 until size) dark[index(x, y)] else false

    fun typeAt(x: Int, y: Int): ModuleType =
        if (x in 0 until size && y in 0 until size) types[index(x, y)] else ModuleType.QUIET_ZONE

    /** Iterates every dark module, quiet zone excluded by definition. */
    inline fun forEachDarkModule(action: (x: Int, y: Int, type: ModuleType) -> Unit) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (isDark(x, y)) action(x, y, typeAt(x, y))
            }
        }
    }

    /** Debug rendering. Never used in the app itself. */
    fun toAsciiArt(dark: String = "██", light: String = "  "): String =
        (0 until size).joinToString("\n") { y ->
            (0 until size).joinToString("") { x -> if (isDark(x, y)) dark else light }
        }

    companion object {
        /**
         * FR-205 / FR-410: exactly four modules of clear space on every side, in the
         * preview and in every export. The user may add padding beyond this; nothing in
         * the UI may reduce it.
         */
        const val QUIET_ZONE_MODULES = 4
    }
}
