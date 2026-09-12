package sg.qrstudio.payload

/**
 * CRC-16/CCITT-FALSE — polynomial 0x1021, initial value 0xFFFF, no final XOR,
 * no input/output reflection. Required by FR-109.
 *
 * The checksum is computed over the whole payload *including* the trailing "6304"
 * tag-and-length of field 63 itself, and rendered as four uppercase hex digits
 * left-padded with zeroes.
 *
 * The padding is load-bearing: golden vector B (BRD §10.2) produces a raw value of
 * 0xFC5, which must be emitted as "0FC5". An implementation that skips the padding
 * passes vector A and then fails silently on roughly one code in sixteen.
 */
internal object Crc16 {
    fun ccittFalse(bytes: ByteArray): Int {
        var crc = 0xFFFF
        for (byte in bytes) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc =
                    if (crc and 0x8000 != 0) {
                        ((crc shl 1) xor 0x1021) and 0xFFFF
                    } else {
                        (crc shl 1) and 0xFFFF
                    }
            }
        }
        return crc and 0xFFFF
    }

    /** Four uppercase hex digits, left-padded to width 4. See FR-109. */
    fun ccittFalseHex(bytes: ByteArray): String = ccittFalse(bytes).toString(16).uppercase().padStart(4, '0')
}
