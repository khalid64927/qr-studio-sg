package sg.qrstudio.payload

/**
 * EMVCo tag-length-value encoding: ID (2 chars) + length (2 chars, zero-padded) + value.
 * Templates such as 26 and 62 nest the very same structure.
 *
 * FR-111: the length prefix is a **UTF-8 byte count**, never `String.length`.
 * Kotlin's `String.length` counts UTF-16 code units, so "Café Ünicode" is 12 units
 * but 14 bytes. A prefix that disagrees with the bytes on the wire makes the whole
 * payload unparseable from that field onward. Callers additionally restrict free-text
 * fields to printable ASCII (see [Validation]), but the byte count is computed here
 * regardless so the encoder is correct by construction rather than by convention.
 */
internal object Tlv {
    /** Maximum value length representable by a two-digit EMVCo length prefix. */
    const val MAX_VALUE_BYTES = 99

    fun field(
        id: String,
        value: String,
    ): String {
        require(id.length == 2) { "EMVCo tag must be exactly 2 characters, got '$id'" }
        val byteCount = value.encodeToByteArray().size // UTF-8 — FR-111
        require(byteCount <= MAX_VALUE_BYTES) {
            "Value for tag $id is $byteCount bytes; a 2-digit length prefix caps it at $MAX_VALUE_BYTES"
        }
        return id + byteCount.toString().padStart(2, '0') + value
    }

    /** Builds a nested template (e.g. 26, 62) from already-encoded child fields. */
    fun template(
        id: String,
        children: List<String>,
    ): String = field(id, children.joinToString(separator = ""))
}
