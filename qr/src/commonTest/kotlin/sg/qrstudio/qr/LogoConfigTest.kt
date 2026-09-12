package sg.qrstudio.qr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogoConfigTest {
    @Test
    fun `FR-305 size is clamped to the 8 to 30 percent range`() {
        assertEquals(LogoConfig.MIN_SIZE_FRACTION, LogoConfig(sizeFraction = 0.01f).clampedSizeFraction())
        assertEquals(LogoConfig.MAX_SIZE_FRACTION, LogoConfig(sizeFraction = 0.9f).clampedSizeFraction())
        assertEquals(0.2f, LogoConfig(sizeFraction = 0.2f).clampedSizeFraction())
    }

    @Test
    fun `FR-306 the warning threshold sits between the default and the hard cap`() {
        assertTrue(LogoConfig.WARNING_SIZE_FRACTION > LogoConfig.DEFAULT_SIZE_FRACTION)
        assertTrue(LogoConfig.WARNING_SIZE_FRACTION < LogoConfig.MAX_SIZE_FRACTION)
    }

    @Test
    fun `FR-306 the size warning only shows when enabled and above 25 percent`() {
        assertFalse(LogoConfig(enabled = false, sizeFraction = 0.3f).showsSizeWarning)
        assertFalse(LogoConfig(enabled = true, sizeFraction = 0.2f).showsSizeWarning)
        assertTrue(LogoConfig(enabled = true, sizeFraction = 0.26f).showsSizeWarning)
    }

    @Test
    fun `the default configuration is disabled at 20 percent`() {
        val config = LogoConfig()
        assertFalse(config.enabled)
        assertEquals(0.20f, config.sizeFraction)
    }
}
