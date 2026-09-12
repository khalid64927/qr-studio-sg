package sg.qrstudio.payload

import kotlinx.datetime.LocalDate

/**
 * The finished payload plus the normalised values the UI must echo back to the user
 * before the first export (FR-152, FR-153).
 */
data class PayNowPayload(
    /** The complete EMVCo string, CRC included. This is what gets encoded into the QR. */
    val raw: String,
    /** Wire form of the proxy, e.g. "+6591234567" or "201403121W". */
    val normalisedProxy: String,
    /** FR-153: grouped for reading, e.g. "+65 9123 4567", so a wrong digit is visible. */
    val proxyDisplay: String,
    /** Two-decimal amount, or null when the payer enters their own amount. */
    val amount: String?,
    val amountEditable: Boolean,
    val expiry: LocalDate,
    val reference: String?,
    val merchantName: String,
    /** "11" static or "12" dynamic. FR-107. */
    val pointOfInitiation: String,
)

sealed interface PayloadResult {
    /** [warnings] are non-blocking; the payload is safe to encode. */
    data class Success(
        val payload: PayNowPayload,
        val warnings: List<ValidationIssue>,
    ) : PayloadResult

    /** FR-151: export stays blocked while this is the outcome. */
    data class Invalid(
        val errors: List<ValidationIssue>,
        val warnings: List<ValidationIssue>,
    ) : PayloadResult
}

/**
 * Builds Singapore PayNow EMVCo merchant-presented payloads.
 *
 * The PayNow merchant account template is **tag 26**, with expiry at subtag 04 and the
 * reference in field 62 subtag 01 (BRD §5.1). Finding F2 records why: the one existing
 * Kotlin Multiplatform library places PayNow at tag 36, where no bank app will look for
 * it. Anti-vector §10.3 guards this permanently — see PayNowAntiVectorTest.
 *
 * FR-154: every call rebuilds the whole string and recomputes the CRC from scratch.
 * There is no code path anywhere that patches or splices an existing payload.
 */
object PayNowPayloadBuilder {
    private const val TAG_PAYLOAD_FORMAT = "00"
    private const val TAG_POINT_OF_INITIATION = "01"
    private const val TAG_MERCHANT_ACCOUNT = "26"
    private const val TAG_MERCHANT_CATEGORY = "52"
    private const val TAG_CURRENCY = "53"
    private const val TAG_AMOUNT = "54"
    private const val TAG_COUNTRY = "58"
    private const val TAG_MERCHANT_NAME = "59"
    private const val TAG_MERCHANT_CITY = "60"
    private const val TAG_ADDITIONAL_DATA = "62"
    private const val TAG_CRC = "63"

    private const val PAYLOAD_FORMAT_INDICATOR = "01"
    private const val POIM_STATIC = "11"
    private const val POIM_DYNAMIC = "12"
    private const val PAYNOW_GUID = "SG.PAYNOW"
    private const val MERCHANT_CATEGORY_CODE = "0000"
    private const val CURRENCY_SGD = "702"
    private const val COUNTRY_SG = "SG"
    private const val MERCHANT_CITY = "Singapore"

    fun build(
        config: PayNowConfig,
        today: LocalDate,
    ): PayloadResult {
        val validation = Validation.validate(config, today)
        if (!validation.isValid) {
            return PayloadResult.Invalid(validation.errors, validation.warnings)
        }

        // ---- Normalise every input once, up front (FR-113) ----------------------
        val normalisedProxy =
            when (config.proxyType) {
                ProxyType.MOBILE ->
                    Validation.normaliseMobile(config.proxyValue)
                        ?: return PayloadResult.Invalid(
                            listOf(
                                ValidationIssue(
                                    Field.PROXY,
                                    IssueCode.MOBILE_BAD_FORMAT,
                                    ValidationIssue.Severity.ERROR,
                                    PayloadStrings.of(IssueCode.MOBILE_BAD_FORMAT),
                                ),
                            ),
                            validation.warnings,
                        )

                ProxyType.UEN -> Validation.normaliseUen(config.proxyValue)
            }

        val cents =
            config.amount
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { Validation.parseAmountToCents(it) }
        val amount = cents?.let { Validation.formatCents(it) } // FR-105: "500" -> "500.00"
        val amountEditable = config.effectiveAmountEditable // FR-106

        val expiry = config.expiry ?: PayNowDefaults.defaultExpiry(today) // FR-108
        val merchantName =
            sanitiseText(config.merchantName, PayNowDefaults.MERCHANT_NAME_MAX_LENGTH)
                .ifEmpty { PayNowDefaults.MERCHANT_NAME } // FR-110 / OQ-4
        val reference = sanitiseText(config.reference, PayNowDefaults.REFERENCE_MAX_LENGTH).ifEmpty { null }

        val raw =
            assemble(
                proxyTypeCode = config.proxyType.code,
                proxyValue = normalisedProxy,
                amount = amount,
                amountEditable = amountEditable,
                expiry = formatExpiry(expiry),
                reference = reference,
                merchantName = merchantName,
            )

        return PayloadResult.Success(
            payload =
                PayNowPayload(
                    raw = raw,
                    normalisedProxy = normalisedProxy,
                    proxyDisplay = displayProxy(config.proxyType, normalisedProxy),
                    amount = amount,
                    amountEditable = amountEditable,
                    expiry = expiry,
                    reference = reference,
                    merchantName = merchantName,
                    pointOfInitiation = pointOfInitiation(amount, amountEditable),
                ),
            warnings = validation.warnings,
        )
    }

    /**
     * FR-107: a fixed, non-editable amount is a one-off payment request, so the code is
     * dynamic ("12"). Everything else is reusable and therefore static ("11"). The
     * reference implementation hardcodes "12" (deviation D3).
     *
     * Fallback recorded for TC-05: if bank testing shows any scanner rejecting "11",
     * return POIM_DYNAMIC unconditionally here and note the bank and app version.
     */
    internal fun pointOfInitiation(
        amount: String?,
        amountEditable: Boolean,
    ): String = if (amount != null && !amountEditable) POIM_DYNAMIC else POIM_STATIC

    /**
     * Pure EMVCo assembly over already-normalised values.
     *
     * Kept separate from [build] so the encoder can be exercised against third-party
     * golden vectors whose proxy values are test data rather than registrable UENs —
     * see the vector C cross-check. Callers other than [build] must normalise first.
     */
    internal fun assemble(
        proxyTypeCode: String,
        proxyValue: String,
        amount: String?,
        amountEditable: Boolean,
        expiry: String,
        reference: String?,
        merchantName: String,
    ): String {
        val merchantAccount =
            Tlv.template(
                TAG_MERCHANT_ACCOUNT,
                listOf(
                    Tlv.field("00", PAYNOW_GUID),
                    Tlv.field("01", proxyTypeCode), // FR-104
                    Tlv.field("02", proxyValue), // FR-102 / FR-103
                    Tlv.field("03", if (amountEditable) "1" else "0"),
                    Tlv.field("04", expiry), // FR-108, subtag 04 (not 05)
                ),
            )

        val pointOfInitiation = pointOfInitiation(amount, amountEditable)

        val builder =
            StringBuilder()
                .append(Tlv.field(TAG_PAYLOAD_FORMAT, PAYLOAD_FORMAT_INDICATOR))
                .append(Tlv.field(TAG_POINT_OF_INITIATION, pointOfInitiation))
                .append(merchantAccount)
                .append(Tlv.field(TAG_MERCHANT_CATEGORY, MERCHANT_CATEGORY_CODE))
                .append(Tlv.field(TAG_CURRENCY, CURRENCY_SGD))

        // D9: the reference implementation emits "54010" (an amount of zero) when no amount
        // is set. EMVCo treats tag 54 as conditional — absent when the amount is not known
        // and the payer will enter it — and FR-105 puts 0 outside the valid range anyway,
        // so an open-amount code omits the field entirely. Confirm under TC-05 bank testing.
        if (amount != null) {
            builder.append(Tlv.field(TAG_AMOUNT, amount))
        }

        builder
            .append(Tlv.field(TAG_COUNTRY, COUNTRY_SG))
            .append(Tlv.field(TAG_MERCHANT_NAME, merchantName))
            .append(Tlv.field(TAG_MERCHANT_CITY, MERCHANT_CITY))

        // FR-112: no reference means no template at all, never an empty one.
        if (reference != null) {
            builder.append(Tlv.template(TAG_ADDITIONAL_DATA, listOf(Tlv.field("01", reference))))
        }

        // FR-109: the CRC covers the trailing "6304" as well, so append it before hashing.
        builder.append(TAG_CRC).append("04")
        return builder.toString() + Crc16.ccittFalseHex(builder.toString().encodeToByteArray())
    }

    /** FR-108: expiry goes on the wire as YYYYMMDD. */
    internal fun formatExpiry(date: LocalDate): String =
        date.year.toString().padStart(4, '0') +
            date.monthNumber.toString().padStart(2, '0') +
            date.dayOfMonth.toString().padStart(2, '0')

    /**
     * D4 / FR-111: never throw at the user. Anything outside printable ASCII is dropped
     * rather than raising, so a paste of emoji degrades to a clear validation message
     * upstream instead of a crash or a corrupt length prefix here.
     *
     * Truncation is safe at character level precisely because the result is ASCII-only,
     * where one character is exactly one UTF-8 byte.
     */
    internal fun sanitiseText(
        raw: String?,
        maxLength: Int,
    ): String =
        raw
            .orEmpty()
            .trim()
            .filter { it.code in 0x20..0x7E }
            .take(maxLength)

    /** FR-153: group the digits so a mistyped one stands out at a glance. */
    internal fun displayProxy(
        type: ProxyType,
        normalised: String,
    ): String =
        when (type) {
            ProxyType.MOBILE -> {
                val digits = normalised.removePrefix("+65")
                "+65 ${digits.take(4)} ${digits.drop(4)}"
            }

            ProxyType.UEN -> normalised
        }
}
