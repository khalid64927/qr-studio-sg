package sg.qrstudio.payload

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PayNowPayloadBuilderTest {

    private fun succeed(config: PayNowConfig, today: LocalDate = LocalDates.TODAY): PayNowPayload {
        val result = PayNowPayloadBuilder.build(config, today)
        assertTrue(result is PayloadResult.Success, "Expected success but got $result")
        return result.payload
    }

    private fun fail(config: PayNowConfig, today: LocalDate = LocalDates.TODAY): PayloadResult.Invalid {
        val result = PayNowPayloadBuilder.build(config, today)
        assertTrue(result is PayloadResult.Invalid, "Expected failure but got $result")
        return result
    }

    // ---- AC-06: golden vectors -------------------------------------------------

    /**
     * Vector A with the two documented deviations applied: the amount gains its two
     * decimal places (D2) and the point of initiation becomes "11" because the amount
     * is editable and therefore not a fixed one-off request (D3).
     */
    @Test
    fun `AC-06 vector A reproduces exactly with D2 and D3 corrections`() {
        val payload = succeed(
            PayNowConfig(
                proxyType = ProxyType.UEN,
                proxyValue = "201403121W",
                amount = "500",
                amountEditable = true,
                expiry = LocalDate(2020, 12, 31),
                reference = "TQINV-10001",
                merchantName = "ACME Pte Ltd.",
            ),
            today = LocalDate(2020, 1, 1),
        )
        assertEquals(
            "00020101021126490009SG.PAYNOW010120210201403121W03011040820201231520400005303702" +
                "5406500.005802SG5913ACME Pte Ltd.6009Singapore62150111TQINV-100016304815C",
            payload.raw,
        )
    }

    /** Vector B: no amount, no reference. Field 54 and template 62 both disappear. */
    @Test
    fun `AC-06 vector B reproduces exactly with D2 D3 and D9 corrections`() {
        val payload = succeed(
            PayNowConfig(
                proxyType = ProxyType.UEN,
                proxyValue = "201403121W",
                expiry = LocalDate(2030, 12, 31),
                merchantName = "ACME Pte Ltd.",
            ),
        )
        assertEquals(
            "00020101021126490009SG.PAYNOW010120210201403121W03011040820301231520400005303702" +
                "5802SG5913ACME Pte Ltd.6009Singapore6304B69E",
            payload.raw,
        )
    }

    /**
     * Vector C is the strongest evidence available: an independently produced,
     * CRC-verified third-party payload that this builder must reproduce byte for byte
     * with no corrections at all.
     */
    @Test
    fun `AC-06 vector C reproduces byte-for-byte with no deviations`() {
        // Vector C's proxy, "12345678", is third-party test data rather than a
        // registrable UEN, so it is fed to the encoder directly. What it proves is
        // encoder equivalence with an independent implementation: tag 26, expiry at
        // subtag 04, reference at 62.01, two-decimal amount, and a matching CRC.
        val raw = PayNowPayloadBuilder.assemble(
            proxyTypeCode = ProxyType.UEN.code,
            proxyValue = "12345678",
            amount = "0.99",
            amountEditable = false,
            expiry = "20260304",
            reference = "testordernumber12345678",
            merchantName = "testcompany",
        )
        assertEquals(GoldenVectors.VECTOR_C, raw)
    }

    // ---- Acceptance criteria ---------------------------------------------------

    @Test
    fun `AC-01 a bare UEN produces a valid payload`() {
        val payload = succeed(PayNowConfig(ProxyType.UEN, "201403121W"))
        assertTrue(EmvTlvParser.verifyCrc(payload.raw))
        val detected = PayNowDetector.detect(payload.raw)
        assertEquals(ProxyType.UEN, detected?.proxyType)
        assertEquals("201403121W", detected?.proxyValue)
    }

    @Test
    fun `AC-02 a mobile number is normalised to +65 with proxy type 0`() {
        val payload = succeed(PayNowConfig(ProxyType.MOBILE, "91234567"))
        assertTrue(payload.raw.contains("+6591234567"))
        assertEquals(ProxyType.MOBILE, PayNowDetector.detect(payload.raw)?.proxyType)
        assertTrue(payload.raw.contains("0101" + "0"), "Proxy type 0 must be present at 26.01")
    }

    @Test
    fun `AC-03 an amount of 500 is encoded as 5406500_00`() {
        val payload = succeed(PayNowConfig(ProxyType.MOBILE, "91234567", amount = "500"))
        assertTrue(payload.raw.contains("5406500.00"), "Expected 5406500.00 in ${payload.raw}")
    }

    @Test
    fun `AC-04 point of initiation is 12 for a fixed amount and 11 otherwise`() {
        val fixed = succeed(
            PayNowConfig(ProxyType.MOBILE, "91234567", amount = "12.50", amountEditable = false),
        )
        assertEquals("12", fixed.pointOfInitiation)
        assertTrue(fixed.raw.startsWith("000201010212"))

        val open = succeed(PayNowConfig(ProxyType.MOBILE, "91234567"))
        assertEquals("11", open.pointOfInitiation)
        assertTrue(open.raw.startsWith("000201010211"))
        // FR-106: with no amount the payer must be able to enter one.
        assertTrue(open.amountEditable)
        assertEquals(true, PayNowDetector.detect(open.raw)?.amountEditable)
    }

    @Test
    fun `AC-05 no reference number means no field 62 at all`() {
        val payload = succeed(PayNowConfig(ProxyType.MOBILE, "91234567"))
        val nodes = (EmvTlvParser.parse(payload.raw) as ParseResult.Success).nodes
        assertTrue(nodes.none { it.tag == "62" }, "FR-112: an empty template must never be emitted")
        assertNull(payload.reference)
    }

    @Test
    fun `AC-16 an expiry in the past is rejected`() {
        val result = fail(PayNowConfig(ProxyType.MOBILE, "91234567", expiry = LocalDates.YESTERDAY))
        assertTrue(result.errors.any { it.code == IssueCode.EXPIRY_IN_PAST })
    }

    @Test
    fun `AC-15 a merchant name with emoji is rejected with a message not a crash`() {
        val result = fail(PayNowConfig(ProxyType.MOBILE, "91234567", merchantName = "Café 🍰 Bakery"))
        assertTrue(result.errors.any { it.code == IssueCode.MERCHANT_NAME_NON_ASCII })
        assertTrue(result.errors.first().message.isNotBlank())
    }

    // ---- FR-level behaviour ----------------------------------------------------

    @Test
    fun `FR-108 expiry defaults to today plus five years`() {
        val payload = succeed(PayNowConfig(ProxyType.MOBILE, "91234567"))
        assertEquals(LocalDate(2031, 9, 9), payload.expiry)
        assertEquals("20310909", PayNowDetector.detect(payload.raw)?.expiry)
    }

    @Test
    fun `FR-110 a blank merchant name falls back to NA and is never empty`() {
        assertEquals("NA", succeed(PayNowConfig(ProxyType.MOBILE, "91234567")).merchantName)
        assertEquals("NA", succeed(PayNowConfig(ProxyType.MOBILE, "91234567", merchantName = "   ")).merchantName)
    }

    @Test
    fun `FR-110 a long merchant name is truncated to 25 characters`() {
        val payload = succeed(
            PayNowConfig(ProxyType.MOBILE, "91234567", merchantName = "A".repeat(40)),
        )
        assertEquals(25, payload.merchantName.length)
        assertTrue(payload.raw.contains("5925" + "A".repeat(25)))
    }

    @Test
    fun `FR-113 surrounding whitespace is trimmed before encoding`() {
        val payload = succeed(
            PayNowConfig(ProxyType.UEN, "  201403121W  ", merchantName = "  Acme  ", reference = "  INV-1  "),
        )
        assertEquals("201403121W", payload.normalisedProxy)
        assertEquals("Acme", payload.merchantName)
        assertEquals("INV-1", payload.reference)
    }

    @Test
    fun `FR-154 every build recomputes the payload from scratch`() {
        val config = PayNowConfig(ProxyType.MOBILE, "91234567", amount = "10.00")
        val first = succeed(config)
        val second = succeed(config.copy(amount = "20.00"))
        val third = succeed(config)
        assertEquals(first.raw, third.raw, "Rebuilding an identical config must be deterministic")
        assertFalse(first.raw == second.raw)
        assertTrue(EmvTlvParser.verifyCrc(second.raw))
    }

    @Test
    fun `FR-153 the proxy is echoed back in grouped readable form`() {
        assertEquals("+65 9123 4567", succeed(PayNowConfig(ProxyType.MOBILE, "91234567")).proxyDisplay)
        assertEquals("201403121W", succeed(PayNowConfig(ProxyType.UEN, "201403121w")).proxyDisplay)
    }
}
