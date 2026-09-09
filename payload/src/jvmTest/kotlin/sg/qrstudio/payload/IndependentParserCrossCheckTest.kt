package sg.qrstudio.payload

import com.emv.qrcode.decoder.mpm.DecoderMpm
import com.emv.qrcode.model.mpm.MerchantAccountInformationReservedAdditional
import com.emv.qrcode.model.mpm.MerchantPresentedMode
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TC-04 — independent cross-check against mvallim/emv-qrcode.
 *
 * This is the strongest correctness evidence available short of TC-05 bank testing.
 * The round-trip test in commonTest reads our payloads with our own parser, so a shared
 * misunderstanding of EMVCo would pass both sides. Here a completely separate Java
 * implementation of the EMVCo merchant-presented specification reads the same payloads
 * and must agree field for field.
 *
 * JVM test source set only. This library never ships in the app (§9.4).
 */
class IndependentParserCrossCheckTest {

    private fun decode(payload: String): MerchantPresentedMode =
        DecoderMpm.decode(payload, MerchantPresentedMode::class.java)

    private fun paynowTemplate(decoded: MerchantPresentedMode): MerchantAccountInformationReservedAdditional {
        val template = decoded.merchantAccountInformation[PayNowDetector.PAYNOW_TEMPLATE_TAG]
        assertNotNull(template, "Expected a merchant account template at tag 26")
        return template.getTypeValue(MerchantAccountInformationReservedAdditional::class.java)
    }

    @Test
    fun `the independent parser agrees on every field of a fully populated payload`() {
        val result = PayNowPayloadBuilder.build(
            PayNowConfig(
                proxyType = ProxyType.MOBILE,
                proxyValue = "9123 4567",
                amount = "1234.5",
                amountEditable = false,
                expiry = LocalDates.FAR_FUTURE,
                reference = "INV-2026-0042",
                merchantName = "Acme Bakery",
            ),
            today = LocalDates.TODAY,
        )
        val payload = (result as PayloadResult.Success).payload
        val decoded = decode(payload.raw)

        assertEquals("01", decoded.payloadFormatIndicator.value)
        assertEquals("12", decoded.pointOfInitiationMethod.value) // fixed, non-editable
        assertEquals("0000", decoded.merchantCategoryCode.value)
        assertEquals("702", decoded.transactionCurrency.value)
        assertEquals("1234.50", decoded.transactionAmount.value)
        assertEquals("SG", decoded.countryCode.value)
        assertEquals("Acme Bakery", decoded.merchantName.value)
        assertEquals("Singapore", decoded.merchantCity.value)

        val paynow = paynowTemplate(decoded)
        assertEquals("SG.PAYNOW", paynow.globallyUniqueIdentifier.value)
        assertEquals(ProxyType.MOBILE.code, paynow.paymentNetworkSpecific["01"]?.value)
        assertEquals("+6591234567", paynow.paymentNetworkSpecific["02"]?.value)
        assertEquals("0", paynow.paymentNetworkSpecific["03"]?.value)
        assertEquals("20301231", paynow.paymentNetworkSpecific["04"]?.value)

        // FR-112 / §5.1: the reference belongs in 62.01, the bill number.
        assertEquals("INV-2026-0042", decoded.additionalDataField.value.billNumber.value)

        assertEquals(payload.raw.takeLast(4), decoded.crc.value)
    }

    @Test
    fun `the independent parser agrees across a spread of random configurations`() {
        val random = Random(seed = 20260909)
        repeat(200) { iteration ->
            val useMobile = random.nextBoolean()
            val proxyValue = if (useMobile) {
                buildString {
                    append(if (random.nextBoolean()) '8' else '9')
                    repeat(7) { append(random.nextInt(10)) }
                }
            } else {
                buildString {
                    repeat(9) { append(random.nextInt(10)) }
                    append(('A'..'Z').random(random))
                }
            }
            val hasAmount = random.nextBoolean()
            val amount = if (hasAmount) Validation.formatCents(random.nextLong(1, 99_999_999)) else null
            val editable = random.nextBoolean()
            val reference = if (random.nextBoolean()) "REF${random.nextInt(1_000_000)}" else null
            val expiry = LocalDates.TODAY.plus(DatePeriod(days = random.nextInt(0, 3650)))

            val config = PayNowConfig(
                proxyType = if (useMobile) ProxyType.MOBILE else ProxyType.UEN,
                proxyValue = proxyValue,
                amount = amount,
                amountEditable = editable,
                expiry = expiry,
                reference = reference,
                merchantName = "Merchant $iteration",
            )
            val built = PayNowPayloadBuilder.build(config, LocalDates.TODAY)
            assertTrue(built is PayloadResult.Success, "Iteration $iteration: $built")
            val payload = built.payload

            val decoded = decode(payload.raw)
            val paynow = paynowTemplate(decoded)

            assertEquals("SG.PAYNOW", paynow.globallyUniqueIdentifier.value, "Iteration $iteration")
            assertEquals(payload.normalisedProxy, paynow.paymentNetworkSpecific["02"]?.value, "Iteration $iteration")
            assertEquals(
                PayNowPayloadBuilder.formatExpiry(expiry),
                paynow.paymentNetworkSpecific["04"]?.value,
                "Iteration $iteration",
            )
            assertEquals(payload.pointOfInitiation, decoded.pointOfInitiationMethod.value, "Iteration $iteration")
            assertEquals(payload.merchantName, decoded.merchantName.value, "Iteration $iteration")

            if (amount == null) {
                assertNull(decoded.transactionAmount, "Iteration $iteration: open amount must omit tag 54")
            } else {
                assertEquals(amount, decoded.transactionAmount.value, "Iteration $iteration")
            }

            if (reference == null) {
                assertNull(decoded.additionalDataField, "Iteration $iteration: FR-112")
            } else {
                assertEquals(reference, decoded.additionalDataField.value.billNumber.value, "Iteration $iteration")
            }
        }
    }

    /**
     * §10.3 from the other side: the independent parser happily reads the anti-vector,
     * because it *is* valid EMVCo. Only PayNow-specific detection rejects it. This is
     * precisely why structural validity is not sufficient evidence of correctness.
     */
    @Test
    fun `the anti-vector parses as valid EMVCo yet is not PayNow`() {
        val decoded = decode(GoldenVectors.ANTI_VECTOR)
        assertNotNull(decoded.merchantAccountInformation["36"], "Anti-vector puts the template at 36")
        assertNull(decoded.merchantAccountInformation["26"], "and nothing at 26")
        assertNull(PayNowDetector.detect(GoldenVectors.ANTI_VECTOR))
    }
}
