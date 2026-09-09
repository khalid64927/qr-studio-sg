package sg.qrstudio.payload

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TC-01: validators, normalisation and amount formatting. */
class ValidationTest {

    private fun codes(config: PayNowConfig) =
        Validation.validate(config, LocalDates.TODAY).issues.map { it.code }

    // ---- FR-103 mobile ---------------------------------------------------------

    @Test
    fun `FR-103 a mobile number normalises from every reasonable input format`() {
        listOf(
            "91234567", "9123 4567", "9123-4567", "+6591234567", "+65 9123 4567",
            "6591234567", "65 9123 4567", "(+65) 9123-4567", " 9123.4567 ",
        ).forEach { input ->
            assertEquals("+6591234567", Validation.normaliseMobile(input), "Failed for '$input'")
        }
    }

    @Test
    fun `FR-103 non-Singapore mobile shapes are rejected`() {
        listOf("1234567", "912345678", "71234567", "", "abcdefgh").forEach { input ->
            assertNull(Validation.normaliseMobile(input), "Expected rejection for '$input'")
        }
    }

    @Test
    fun `FR-103 a mobile not starting with 8 or 9 is reported as a prefix problem`() {
        assertTrue(IssueCode.MOBILE_BAD_PREFIX in codes(PayNowConfig(ProxyType.MOBILE, "71234567")))
    }

    // ---- FR-102 UEN ------------------------------------------------------------

    @Test
    fun `FR-102 all three UEN shapes are accepted without warnings`() {
        listOf(
            "12345678A",   // business, nnnnnnnnX
            "201403121W",  // local company, yyyynnnnnX
            "T09LL0001B",  // other entity, TyyPQnnnnX
        ).forEach { uen ->
            assertEquals(emptyList(), codes(PayNowConfig(ProxyType.UEN, uen)), "Unexpected issue for $uen")
        }
    }

    @Test
    fun `FR-102 UEN input is uppercased`() {
        assertEquals("201403121W", Validation.normaliseUen(" 201403121w "))
    }

    @Test
    fun `FR-102 a gross mismatch is an error but an odd-but-plausible UEN only warns`() {
        // Wrong length: gross mismatch, blocked.
        assertTrue(IssueCode.UEN_BAD_LENGTH in codes(PayNowConfig(ProxyType.UEN, "123")))
        // Non-alphanumeric: gross mismatch, blocked.
        assertTrue(IssueCode.UEN_BAD_CHARACTERS in codes(PayNowConfig(ProxyType.UEN, "2014-3121W")))

        // Right length and charset, unfamiliar shape: warn, never block, because UEN
        // formats evolve and a false rejection is worse than a soft caution.
        val result = Validation.validate(PayNowConfig(ProxyType.UEN, "AB345678Z"), LocalDates.TODAY)
        assertTrue(result.isValid, "An unfamiliar but plausible UEN must not block export")
        assertTrue(result.warnings.any { it.code == IssueCode.UEN_UNRECOGNISED_PATTERN })
    }

    // ---- FR-105 amount ---------------------------------------------------------

    @Test
    fun `FR-105 amounts format to exactly two decimal places`() {
        mapOf(
            "500" to "500.00", "500.5" to "500.50", "500.50" to "500.50",
            "0.01" to "0.01", "1,234.56" to "1234.56", "S$25" to "25.00",
            "999999.99" to "999999.99", ".5" to "0.50",
        ).forEach { (input, expected) ->
            val cents = Validation.parseAmountToCents(input)
            assertEquals(expected, cents?.let { Validation.formatCents(it) }, "Failed for '$input'")
        }
    }

    @Test
    fun `FR-105 amounts outside the supported range are rejected`() {
        assertTrue(IssueCode.AMOUNT_TOO_SMALL in codes(PayNowConfig(ProxyType.MOBILE, "91234567", amount = "0")))
        assertTrue(
            IssueCode.AMOUNT_TOO_LARGE in codes(PayNowConfig(ProxyType.MOBILE, "91234567", amount = "1000000")),
        )
        assertTrue(
            IssueCode.AMOUNT_TOO_MANY_DECIMALS in
                codes(PayNowConfig(ProxyType.MOBILE, "91234567", amount = "10.999")),
        )
        assertTrue(
            IssueCode.AMOUNT_NOT_A_NUMBER in codes(PayNowConfig(ProxyType.MOBILE, "91234567", amount = "abc")),
        )
    }

    @Test
    fun `FR-105 amount parsing never goes through floating point`() {
        // 0.1 + 0.2 style drift would show up here as 8.129999... or similar.
        assertEquals("8.13", Validation.formatCents(Validation.parseAmountToCents("8.13")!!))
        assertEquals("0.07", Validation.formatCents(Validation.parseAmountToCents("0.07")!!))
    }

    // ---- FR-106 ----------------------------------------------------------------

    @Test
    fun `FR-106 an absent amount forces the editable flag on`() {
        assertTrue(PayNowConfig(ProxyType.MOBILE, "91234567", amountEditable = false).effectiveAmountEditable)
        assertTrue(
            PayNowConfig(ProxyType.MOBILE, "91234567", amount = "  ", amountEditable = false)
                .effectiveAmountEditable,
        )
        assertEquals(
            false,
            PayNowConfig(ProxyType.MOBILE, "91234567", amount = "5.00", amountEditable = false)
                .effectiveAmountEditable,
        )
    }

    // ---- FR-111 / FR-114 -------------------------------------------------------

    @Test
    fun `FR-111 non-ASCII text is rejected in both free-text fields`() {
        assertTrue(
            IssueCode.MERCHANT_NAME_NON_ASCII in
                codes(PayNowConfig(ProxyType.MOBILE, "91234567", merchantName = "Café Ünicode")),
        )
        assertTrue(
            IssueCode.REFERENCE_NON_ASCII in
                codes(PayNowConfig(ProxyType.MOBILE, "91234567", reference = "订单123")),
        )
    }

    @Test
    fun `FR-114 an unusual reference warns but does not block`() {
        val result = Validation.validate(
            PayNowConfig(ProxyType.MOBILE, "91234567", reference = "INV/2026#1"),
            LocalDates.TODAY,
        )
        assertTrue(result.isValid)
        assertTrue(result.warnings.any { it.code == IssueCode.REFERENCE_UNUSUAL_CHARACTERS })
    }

    @Test
    fun `FR-108 an expiry of today is accepted`() {
        val result = Validation.validate(
            PayNowConfig(ProxyType.MOBILE, "91234567", expiry = LocalDates.TODAY),
            LocalDates.TODAY,
        )
        assertTrue(result.isValid)
    }
}
