package sg.qrstudio.qr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContrastTest {
    private val black = Contrast.Rgb(0f, 0f, 0f)
    private val white = Contrast.Rgb(1f, 1f, 1f)

    @Test
    fun `black on white is the maximum ratio, 21 to 1`() {
        assertEquals(21.0, Contrast.ratio(black, white), absoluteTolerance = 0.01)
    }

    @Test
    fun `ratio is symmetric regardless of argument order`() {
        assertEquals(Contrast.ratio(black, white), Contrast.ratio(white, black), absoluteTolerance = 0.0001)
    }

    @Test
    fun `identical colours have a ratio of exactly 1`() {
        assertEquals(1.0, Contrast.ratio(white, white), absoluteTolerance = 0.0001)
    }

    @Test
    fun `FR-402 thresholds classify correctly`() {
        assertEquals(Contrast.Verdict.OK, Contrast.verdict(21.0))
        assertEquals(Contrast.Verdict.OK, Contrast.verdict(4.5))
        assertEquals(Contrast.Verdict.WARNING, Contrast.verdict(4.49))
        assertEquals(Contrast.Verdict.WARNING, Contrast.verdict(3.0))
        assertEquals(Contrast.Verdict.BLOCKED, Contrast.verdict(2.99))
        assertEquals(Contrast.Verdict.BLOCKED, Contrast.verdict(1.0))
    }

    @Test
    fun `AC-10 a light grey on white is blocked`() {
        val lightGrey = Contrast.Rgb(0xCC / 255f, 0xCC / 255f, 0xCC / 255f)
        val config = AppearanceConfig(foreground = lightGrey, background = white)
        assertEquals(Contrast.Verdict.BLOCKED, config.contrastVerdict)
    }

    @Test
    fun `default black on white passes with room to spare`() {
        val config = AppearanceConfig()
        assertEquals(Contrast.Verdict.OK, config.contrastVerdict)
        assertTrue(config.contrastRatio > Contrast.WARN_THRESHOLD)
    }

    @Test
    fun `FR-403 a darker background than foreground is flagged`() {
        val inverted = AppearanceConfig(foreground = white, background = black)
        assertTrue(inverted.backgroundDarkerThanForeground)
        assertTrue(!AppearanceConfig().backgroundDarkerThanForeground)
    }

    @Test
    fun `AC-21 an eye colour equal to the background is detected`() {
        val config =
            AppearanceConfig(
                background = white,
                eyeStyle = EyeStyle(colour = white),
            )
        assertTrue(config.eyeMatchesBackground)
    }

    @Test
    fun `a null eye colour falls back to the module foreground`() {
        val config = AppearanceConfig(foreground = black, eyeStyle = EyeStyle(colour = null))
        assertEquals(black, config.eyeColour)
    }
}
