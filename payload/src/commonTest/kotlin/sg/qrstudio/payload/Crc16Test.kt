package sg.qrstudio.payload

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TC-01: CRC-16/CCITT-FALSE against known vectors, including the padding case. */
class Crc16Test {
    @Test
    fun check_string_from_the_specification() {
        // The canonical CRC-16/CCITT-FALSE check value for "123456789" is 0x29B1.
        assertEquals(0x29B1, Crc16.ccittFalse("123456789".encodeToByteArray()))
    }

    @Test
    fun golden_vectors_reproduce_their_own_checksums() {
        listOf(
            GoldenVectors.VECTOR_A,
            GoldenVectors.VECTOR_B,
            GoldenVectors.VECTOR_C,
            GoldenVectors.ANTI_VECTOR,
        ).forEach { vector ->
            assertTrue(EmvTlvParser.verifyCrc(vector), "CRC mismatch for $vector")
        }
    }

    @Test
    fun `FR-109 short checksums are left padded to four digits`() {
        // Vector B is the reason this test exists: raw 0xFC5 must render as "0FC5".
        val body = GoldenVectors.VECTOR_B.dropLast(4)
        assertEquals("0FC5", Crc16.ccittFalseHex(body.encodeToByteArray()))
        assertEquals(4, Crc16.ccittFalseHex(body.encodeToByteArray()).length)
    }

    @Test
    fun `FR-109 output is uppercase hexadecimal`() {
        val hex = Crc16.ccittFalseHex(GoldenVectors.VECTOR_A.dropLast(4).encodeToByteArray())
        assertEquals(hex.uppercase(), hex)
        assertEquals("4001", hex)
    }
}
