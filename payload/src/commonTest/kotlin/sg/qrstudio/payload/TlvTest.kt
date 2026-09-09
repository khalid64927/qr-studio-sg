package sg.qrstudio.payload

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** TC-01: TLV assembly, length-prefix padding, and UTF-8 byte counts (FR-111). */
class TlvTest {

    @Test
    fun `length prefixes are zero-padded to two digits`() {
        assertEquals("0002SG", Tlv.field("00", "SG"))
        assertEquals("0009SG.PAYNOW", Tlv.field("00", "SG.PAYNOW"))
        assertEquals("59" + "10" + "A".repeat(10), Tlv.field("59", "A".repeat(10)))
    }

    /**
     * FR-111, the defect that KTQRam ships: "Café Ünicode" is 12 UTF-16 units but 14
     * UTF-8 bytes. A prefix of 12 leaves two stray bytes that shift every subsequent
     * field, making the payload unparseable rather than merely wrong.
     */
    @Test
    fun `FR-111 length prefixes count UTF-8 bytes not UTF-16 units`() {
        val text = "Café Ünicode"
        assertEquals(12, text.length, "Precondition: this string is 12 UTF-16 units")
        assertEquals(14, text.encodeToByteArray().size, "Precondition: and 14 UTF-8 bytes")
        assertEquals("5914$text", Tlv.field("59", text))
    }

    @Test
    fun `FR-111 a prefix that used String_length would corrupt the parse`() {
        val text = "Café Ünicode"
        val correct = Tlv.field("59", text)
        val broken = "59" + text.length.toString().padStart(2, '0') + text

        // The correctly prefixed field round-trips.
        val parsed = EmvTlvParser.parse(correct)
        assertTrue(parsed is ParseResult.Success)
        assertEquals(text, parsed.nodes.single().value)

        // The String.length version does not describe its own bytes, so it either fails
        // to parse or silently yields a different value. Either way it never round-trips.
        assertTrue(broken != correct)
        val brokenParse = EmvTlvParser.parse(broken)
        val brokenValue = (brokenParse as? ParseResult.Success)?.nodes?.firstOrNull()?.value
        assertTrue(
            brokenValue != text,
            "A String.length prefix must not round-trip; got $brokenValue",
        )
    }

    @Test
    fun `values too long for a two-digit prefix are refused rather than silently truncated`() {
        assertFailsWith<IllegalArgumentException> { Tlv.field("59", "A".repeat(100)) }
    }

    @Test
    fun `templates nest the same structure`() {
        val template = Tlv.template("62", listOf(Tlv.field("01", "INV-1")))
        assertEquals("6209" + "0105INV-1", template)
        val parsed = (EmvTlvParser.parse(template) as ParseResult.Success).nodes.single()
        assertEquals("62", parsed.tag)
        assertEquals("INV-1", parsed.child("01")?.value)
    }

    @Test
    fun `the parser reports truncated input instead of throwing`() {
        assertTrue(EmvTlvParser.parse("0002S") is ParseResult.Failure)
        assertTrue(EmvTlvParser.parse("00XX01") is ParseResult.Failure)
    }
}
