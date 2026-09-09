package sg.qrstudio.qr

/**
 * The ISO/IEC 18004 mask penalty score.
 *
 * A QR encoder is expected to try all eight mask patterns and keep the one that scores
 * lowest, which spreads dark and light modules evenly and avoids features a scanner can
 * mistake for a finder pattern.
 *
 * The underlying library does not do this — it hardcodes pattern 000 — so we do it here
 * (see [QrEncoder]). The output is valid either way, but an unmasked-looking symbol can
 * carry large uniform blobs and false finder patterns, and this app then covers the
 * middle of it with a logo. Choosing the best mask buys back scan reliability precisely
 * where FR-604 needs it.
 *
 * Scores are computed over the symbol only; the quiet zone is not part of the penalty.
 */
internal object MaskPenalty {

    private const val PENALTY_ADJACENT = 3      // rule 1 base, for a run of 5
    private const val PENALTY_BLOCK = 3         // rule 2, per 2x2 same-colour block
    private const val PENALTY_FINDER_LIKE = 40  // rule 3, per false finder pattern
    private const val PENALTY_BALANCE = 10      // rule 4, per 5% deviation from half dark

    /** The 1:1:3:1:1 finder signature, which must not appear outside the real ones. */
    private val FINDER_PATTERN = booleanArrayOf(true, false, true, true, true, false, true)

    fun score(dark: Array<BooleanArray>): Int =
        adjacentRuns(dark) + blocks(dark) + finderLike(dark) + balance(dark)

    /** Rule 1: five or more adjacent modules of the same colour, in rows and columns. */
    private fun adjacentRuns(dark: Array<BooleanArray>): Int {
        val size = dark.size
        var penalty = 0

        fun run(get: (Int, Int) -> Boolean) {
            for (a in 0 until size) {
                var runLength = 1
                var previous = get(a, 0)
                for (b in 1 until size) {
                    val current = get(a, b)
                    if (current == previous) {
                        runLength++
                    } else {
                        if (runLength >= 5) penalty += PENALTY_ADJACENT + (runLength - 5)
                        previous = current
                        runLength = 1
                    }
                }
                if (runLength >= 5) penalty += PENALTY_ADJACENT + (runLength - 5)
            }
        }

        run { row, col -> dark[row][col] }
        run { col, row -> dark[row][col] }
        return penalty
    }

    /** Rule 2: every 2x2 block of a single colour. */
    private fun blocks(dark: Array<BooleanArray>): Int {
        var penalty = 0
        for (row in 0 until dark.size - 1) {
            for (col in 0 until dark.size - 1) {
                val value = dark[row][col]
                if (dark[row][col + 1] == value &&
                    dark[row + 1][col] == value &&
                    dark[row + 1][col + 1] == value
                ) {
                    penalty += PENALTY_BLOCK
                }
            }
        }
        return penalty
    }

    /**
     * Rule 3: the 1:1:3:1:1 finder signature preceded or followed by four light modules.
     * These are what make a scanner think it has found a corner that is not there.
     */
    private fun finderLike(dark: Array<BooleanArray>): Int {
        val size = dark.size
        var penalty = 0

        fun matchesAt(get: (Int) -> Boolean, start: Int): Boolean {
            for (offset in FINDER_PATTERN.indices) {
                if (get(start + offset) != FINDER_PATTERN[offset]) return false
            }
            return true
        }

        fun scan(get: (Int) -> Boolean) {
            for (start in 0..size - FINDER_PATTERN.size) {
                if (!matchesAt(get, start)) continue
                // Four light modules on one side or the other.
                val before = (start - 4 until start).all { it < 0 || !get(it) }
                val afterStart = start + FINDER_PATTERN.size
                val after = (afterStart until afterStart + 4).all { it >= size || !get(it) }
                if (before || after) penalty += PENALTY_FINDER_LIKE
            }
        }

        for (index in 0 until size) {
            scan { position -> if (position in 0 until size) dark[index][position] else false }
            scan { position -> if (position in 0 until size) dark[position][index] else false }
        }
        return penalty
    }

    /** Rule 4: how far the proportion of dark modules strays from half. */
    private fun balance(dark: Array<BooleanArray>): Int {
        val total = dark.size * dark.size
        val darkCount = dark.sumOf { row -> row.count { it } }
        val percent = darkCount * 100 / total
        val deviation = if (percent > 50) percent - 50 else 50 - percent
        return deviation / 5 * PENALTY_BALANCE
    }
}
