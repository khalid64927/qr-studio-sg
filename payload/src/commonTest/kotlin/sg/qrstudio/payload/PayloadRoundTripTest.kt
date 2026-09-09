package sg.qrstudio.payload

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TC-02 — property test. For a spread of randomly generated valid configurations,
 * re-parse the payload as TLV and assert every field survives the round trip.
 *
 * The seed is fixed so a failure is reproducible; widen the iteration count locally
 * when investigating.
 */
class PayloadRoundTripTest {

    private val random = Random(seed = 20260909)

    private val referenceAlphabet = ('A'..'Z') + ('0'..'9') + listOf('-', '_')
    private val nameAlphabet = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf(' ', '.', '&')

    @Test
    fun `every field round-trips through the parser`() {
        repeat(500) { iteration ->
            val proxyType = if (random.nextBoolean()) ProxyType.MOBILE else ProxyType.UEN
            val proxyValue = when (proxyType) {
                ProxyType.MOBILE -> buildString {
                    append(if (random.nextBoolean()) '8' else '9')
                    repeat(7) { append(random.nextInt(10)) }
                }

                ProxyType.UEN -> buildString {
                    repeat(9) { append(random.nextInt(10)) }
                    append(('A'..'Z').random(random))
                }
            }

            val hasAmount = random.nextBoolean()
            val cents = if (hasAmount) random.nextLong(1, 99_999_999) else null
            val amount = cents?.let { Validation.formatCents(it) }
            val editable = random.nextBoolean()

            val expiry = LocalDates.TODAY.plus(DatePeriod(days = random.nextInt(0, 3650)))
            val reference = if (random.nextBoolean()) {
                (1..random.nextInt(1, 26)).map { referenceAlphabet.random(random) }.joinToString("")
            } else {
                null
            }
            val merchantName = if (random.nextBoolean()) {
                (1..random.nextInt(1, 26)).map { nameAlphabet.random(random) }.joinToString("").trim()
                    .ifEmpty { null }
            } else {
                null
            }

            val config = PayNowConfig(
                proxyType = proxyType,
                proxyValue = proxyValue,
                amount = amount,
                amountEditable = editable,
                expiry = expiry,
                reference = reference,
                merchantName = merchantName,
            )

            val result = PayNowPayloadBuilder.build(config, LocalDates.TODAY)
            assertTrue(result is PayloadResult.Success, "Iteration $iteration: $config produced $result")
            val payload = result.payload
            val raw = payload.raw

            // Structure and checksum.
            val parsed = EmvTlvParser.parse(raw)
            assertTrue(parsed is ParseResult.Success, "Iteration $iteration: unparseable payload $raw")
            assertTrue(EmvTlvParser.verifyCrc(raw), "Iteration $iteration: bad CRC in $raw")

            val nodes = parsed.nodes
            fun node(tag: String) = nodes.firstOrNull { it.tag == tag }

            // Fixed fields.
            assertEquals("01", node("00")?.value)
            assertEquals("0000", node("52")?.value)
            assertEquals("702", node("53")?.value)
            assertEquals("SG", node("58")?.value)
            assertEquals("Singapore", node("60")?.value)

            // Merchant account template, at tag 26.
            val detected = PayNowDetector.detect(raw)
            assertNotNull(detected, "Iteration $iteration: not detected as PayNow")
            assertEquals(proxyType, detected.proxyType)
            assertEquals(payload.normalisedProxy, detected.proxyValue)
            assertEquals(PayNowPayloadBuilder.formatExpiry(expiry), detected.expiry)
            assertEquals(payload.amountEditable, detected.amountEditable)

            // Amount: present with two decimals, or absent entirely (D9).
            if (amount == null) {
                assertEquals(null, node("54"), "Iteration $iteration: open amount must omit tag 54")
            } else {
                assertEquals(amount, node("54")?.value)
                assertEquals(2, node("54")!!.value.substringAfter('.').length)
            }

            // FR-107.
            val expectedPoim = if (amount != null && !editable) "12" else "11"
            assertEquals(expectedPoim, node("01")?.value, "Iteration $iteration: wrong point of initiation")

            // FR-112.
            if (payload.reference == null) {
                assertEquals(null, node("62"), "Iteration $iteration: empty template must be omitted")
            } else {
                assertEquals(payload.reference, node("62")?.child("01")?.value)
            }

            // FR-110.
            assertEquals(payload.merchantName, node("59")?.value)
            assertTrue(node("59")!!.value.isNotEmpty())

            // FR-111: every declared length matches the actual UTF-8 byte count.
            nodes.forEach { assertEquals(it.value.encodeToByteArray().size, it.length, "Tag ${it.tag}") }
        }
    }
}
