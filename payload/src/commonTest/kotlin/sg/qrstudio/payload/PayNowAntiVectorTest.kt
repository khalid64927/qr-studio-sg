package sg.qrstudio.payload

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §10.3 — permanent regression guard against the F2 tag confusion.
 *
 * The anti-vector is valid EMVCo with a correct CRC. That is exactly what makes it
 * dangerous: nothing structural flags it, yet a bank application searching for
 * SG.PAYNOW under tag 26 will never find it under 36. If this test ever goes green
 * on a detection, the app has started producing codes that do not pay anyone.
 */
class PayNowAntiVectorTest {

    @Test
    fun `anti-vector is structurally valid EMVCo with a correct CRC`() {
        assertTrue(EmvTlvParser.parse(GoldenVectors.ANTI_VECTOR) is ParseResult.Success)
        assertTrue(EmvTlvParser.verifyCrc(GoldenVectors.ANTI_VECTOR))
    }

    @Test
    fun `anti-vector is NOT detected as PayNow because it sits at tag 36`() {
        assertNull(
            PayNowDetector.detect(GoldenVectors.ANTI_VECTOR),
            "A payload with SG.PAYNOW at tag 36 must never be accepted as PayNow (finding F2)",
        )
    }

    @Test
    fun `real vectors are detected as PayNow at tag 26`() {
        listOf(GoldenVectors.VECTOR_A, GoldenVectors.VECTOR_B, GoldenVectors.VECTOR_C).forEach {
            assertNotNull(PayNowDetector.detect(it), "Expected PayNow detection for $it")
        }
    }

    @Test
    fun `this builder never emits tag 36`() {
        val result = PayNowPayloadBuilder.build(
            PayNowConfig(ProxyType.MOBILE, "91234567"),
            today = LocalDates.TODAY,
        )
        val payload = (result as PayloadResult.Success).payload.raw
        val nodes = (EmvTlvParser.parse(payload) as ParseResult.Success).nodes
        assertTrue(nodes.none { it.tag == "36" }, "PayNow must be at tag 26, never 36")
        assertEquals(PayNowDetector.PAYNOW_TEMPLATE_TAG, nodes.first { it.value.contains("SG.PAYNOW") }.tag)
    }
}
