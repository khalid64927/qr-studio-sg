package sg.qrstudio.payload

/** One decoded EMVCo data object. [children] is populated for nested templates. */
data class TlvNode(
    val tag: String,
    val length: Int,
    val value: String,
    val children: List<TlvNode> = emptyList(),
) {
    fun child(tag: String): TlvNode? = children.firstOrNull { it.tag == tag }
}

sealed interface ParseResult {
    data class Success(
        val nodes: List<TlvNode>,
    ) : ParseResult

    data class Failure(
        val reason: String,
        val offset: Int,
    ) : ParseResult
}

/**
 * A tolerant reader for EMVCo merchant-presented payloads.
 *
 * Used to re-parse our own output — the TC-02 round-trip property test, the §10.3
 * anti-vector guard, and structural self-checks before export. It is deliberately
 * separate from the builder so a shared bug cannot make a round-trip test pass falsely.
 */
object EmvTlvParser {
    /** Templates whose values are themselves TLV sequences. */
    private val NESTED_TAGS = setOf("26", "27", "28", "29", "30", "31", "62", "64", "80", "81")

    /**
     * Parses over the UTF-8 **bytes**, never the UTF-16 characters.
     *
     * FR-111 cuts both ways: the encoder writes byte counts, so a reader that slices by
     * `String` index disagrees with its own encoder the moment a value leaves ASCII.
     * The two coincide for every payload this app produces — free text is ASCII-restricted
     * upstream — but a parser that is only accidentally correct is not a useful check on
     * an encoder, so it reads bytes.
     */
    fun parse(payload: String): ParseResult = parse(payload.encodeToByteArray())

    fun parse(bytes: ByteArray): ParseResult {
        val nodes = mutableListOf<TlvNode>()
        var offset = 0
        while (offset < bytes.size) {
            if (offset + 4 > bytes.size) {
                return ParseResult.Failure("Truncated tag or length prefix", offset)
            }
            val tag = bytes.decodeToString(offset, offset + 2)
            val lengthText = bytes.decodeToString(offset + 2, offset + 4)
            val length =
                lengthText.toIntOrNull()
                    ?: return ParseResult.Failure("Length prefix '$lengthText' is not numeric", offset + 2)
            val valueStart = offset + 4
            val valueEnd = valueStart + length
            if (valueEnd > bytes.size) {
                return ParseResult.Failure("Value for tag $tag runs past the end of the payload", valueStart)
            }
            val valueBytes = bytes.copyOfRange(valueStart, valueEnd)
            val value = valueBytes.decodeToString()
            val children =
                if (tag in NESTED_TAGS) {
                    when (val nested = parse(valueBytes)) {
                        is ParseResult.Success -> nested.nodes
                        is ParseResult.Failure -> emptyList() // not every template nests; treat as opaque
                    }
                } else {
                    emptyList()
                }
            nodes.add(TlvNode(tag, length, value, children))
            offset = valueEnd
        }
        return ParseResult.Success(nodes)
    }

    /** Verifies the trailing field 63 against a freshly computed CRC. FR-109. */
    fun verifyCrc(payload: String): Boolean {
        if (payload.length < 8) return false
        val body = payload.dropLast(4)
        if (!body.endsWith("6304")) return false
        return Crc16.ccittFalseHex(body.encodeToByteArray()) == payload.takeLast(4).uppercase()
    }
}

/**
 * Recognises a PayNow payload the way a bank application would.
 *
 * F2 / §10.3: PayNow lives under merchant account template **26**. A payload carrying
 * "SG.PAYNOW" under tag 36 is well-formed EMVCo with a valid CRC and is still not a
 * PayNow code — no bank app looks for it there. This detector must reject it, and
 * PayNowAntiVectorTest holds that line permanently.
 */
object PayNowDetector {
    const val PAYNOW_TEMPLATE_TAG = "26"
    const val PAYNOW_GUID = "SG.PAYNOW"

    data class Detected(
        val proxyType: ProxyType,
        val proxyValue: String,
        val amountEditable: Boolean,
        val expiry: String,
    )

    fun detect(payload: String): Detected? {
        val nodes = (EmvTlvParser.parse(payload) as? ParseResult.Success)?.nodes ?: return null
        val template = nodes.firstOrNull { it.tag == PAYNOW_TEMPLATE_TAG } ?: return null
        if (template.child("00")?.value != PAYNOW_GUID) return null
        val proxyType =
            when (template.child("01")?.value) {
                ProxyType.MOBILE.code -> ProxyType.MOBILE
                ProxyType.UEN.code -> ProxyType.UEN
                else -> return null
            }
        val proxyValue = template.child("02")?.value ?: return null
        return Detected(
            proxyType = proxyType,
            proxyValue = proxyValue,
            amountEditable = template.child("03")?.value == "1",
            expiry = template.child("04")?.value.orEmpty(),
        )
    }
}
