package sg.qrstudio.payload

import kotlinx.datetime.LocalDate

/**
 * Machine-readable identity for every validation outcome.
 *
 * FR-704: user-facing wording lives in one place ([PayloadStrings]) so it can be
 * localised later. Call sites branch on the code, never on the message text.
 */
enum class IssueCode {
    PROXY_EMPTY,
    MOBILE_BAD_FORMAT,
    MOBILE_BAD_PREFIX,
    UEN_EMPTY,
    UEN_BAD_LENGTH,
    UEN_BAD_CHARACTERS,
    UEN_UNRECOGNISED_PATTERN,
    AMOUNT_NOT_A_NUMBER,
    AMOUNT_TOO_SMALL,
    AMOUNT_TOO_LARGE,
    AMOUNT_TOO_MANY_DECIMALS,
    EXPIRY_IN_PAST,
    MERCHANT_NAME_NON_ASCII,
    MERCHANT_NAME_TOO_LONG,
    REFERENCE_NON_ASCII,
    REFERENCE_TOO_LONG,
    REFERENCE_UNUSUAL_CHARACTERS,
}

/** Which input a [ValidationIssue] belongs to, so the UI can attach it to the right field. */
enum class Field { PROXY, AMOUNT, EXPIRY, MERCHANT_NAME, REFERENCE }

/**
 * A problem with the user's input.
 *
 * [Severity.ERROR] blocks export (FR-151). [Severity.WARNING] is surfaced but never
 * blocks — FR-102 is explicit that borderline UEN patterns must warn rather than block,
 * because UEN formats evolve and a false rejection is worse than a soft caution.
 */
data class ValidationIssue(
    val field: Field,
    val code: IssueCode,
    val severity: Severity,
    val message: String,
) {
    enum class Severity { ERROR, WARNING }
}

data class ValidationResult(
    val issues: List<ValidationIssue> = emptyList(),
) {
    val errors: List<ValidationIssue> get() = issues.filter { it.severity == ValidationIssue.Severity.ERROR }
    val warnings: List<ValidationIssue> get() = issues.filter { it.severity == ValidationIssue.Severity.WARNING }

    /** FR-151: export stays blocked until this is true. */
    val isValid: Boolean get() = errors.isEmpty()
}

/** Centralised English copy. FR-704. */
object PayloadStrings {
    fun of(code: IssueCode): String =
        when (code) {
            IssueCode.PROXY_EMPTY -> "Enter the mobile number or UEN that should receive the payment."
            IssueCode.MOBILE_BAD_FORMAT -> "A Singapore mobile number has 8 digits, for example 9123 4567."
            IssueCode.MOBILE_BAD_PREFIX -> "Singapore mobile numbers start with 8 or 9."
            IssueCode.UEN_EMPTY -> "Enter your UEN."
            IssueCode.UEN_BAD_LENGTH -> "A UEN is 9 or 10 characters long."
            IssueCode.UEN_BAD_CHARACTERS -> "A UEN contains only letters and numbers."
            IssueCode.UEN_UNRECOGNISED_PATTERN ->
                "This does not look like a usual UEN format. Double-check it before you share the code."
            IssueCode.AMOUNT_NOT_A_NUMBER -> "Enter the amount as a number, for example 25.50."
            IssueCode.AMOUNT_TOO_SMALL -> "The smallest amount you can request is 0.01."
            IssueCode.AMOUNT_TOO_LARGE -> "The largest amount you can request is 999999.99."
            IssueCode.AMOUNT_TOO_MANY_DECIMALS -> "Amounts can have at most two decimal places."
            IssueCode.EXPIRY_IN_PAST -> "The expiry date has already passed. Pick today or a later date."
            IssueCode.MERCHANT_NAME_NON_ASCII ->
                "The name can only use ordinary letters, numbers and punctuation. Accents, emoji and " +
                    "non-Latin characters are not supported by the payment code."
            IssueCode.MERCHANT_NAME_TOO_LONG -> "The name will be shortened to 25 characters."
            IssueCode.REFERENCE_NON_ASCII ->
                "The reference can only use ordinary letters, numbers and punctuation."
            IssueCode.REFERENCE_TOO_LONG -> "The reference will be shortened to 25 characters."
            IssueCode.REFERENCE_UNUSUAL_CHARACTERS ->
                "Banks handle references differently. Letters, numbers, hyphens and underscores are safest."
        }
}

/**
 * Input validation and normalisation for [PayNowConfig].
 *
 * Everything here is pure: no clock, no locale, no platform calls. The caller supplies
 * "today" so expiry checks are deterministic under test.
 */
object Validation {
    private val UEN_BUSINESS = Regex("^\\d{8}[A-Z]$") // nnnnnnnnX
    private val UEN_LOCAL_COMPANY = Regex("^\\d{9}[A-Z]$") // yyyynnnnnX
    private val UEN_OTHER_ENTITY = Regex("^[TSR]\\d{2}[A-Z]{2}\\d{4}[A-Z]$") // TyyPQnnnnX
    private val ALPHANUMERIC = Regex("^[A-Z0-9]+$")
    private val SAFE_REFERENCE = Regex("^[A-Za-z0-9_-]+$") // FR-114

    /** Printable ASCII, 0x20..0x7E. FR-111. */
    fun isPrintableAscii(text: String): Boolean = text.all { it.code in 0x20..0x7E }

    /**
     * FR-103: accept an 8-digit Singapore mobile however it was typed — with or without
     * a +65 or 65 prefix, and with any spaces, dashes, parentheses or dots — and
     * normalise it to the wire format +65XXXXXXXX.
     *
     * Returns null when the input cannot be read as a Singapore mobile number.
     */
    fun normaliseMobile(raw: String): String? {
        var digits = raw.filter { it.isDigit() }
        // A local 8-digit number never begins with 6, so a leading 65 on a 10-digit
        // string is unambiguously the country code.
        if (digits.length == 10 && digits.startsWith("65")) digits = digits.drop(2)
        if (digits.length != 8) return null
        if (digits[0] != '8' && digits[0] != '9') return null
        return "+65$digits"
    }

    /** FR-102: uppercase and strip incidental whitespace before matching. */
    fun normaliseUen(raw: String): String = raw.trim().filter { !it.isWhitespace() }.uppercase()

    /**
     * FR-105: exactly two decimal places, no thousands separators and no currency symbol.
     * Parsed through integer cents so no floating-point rounding can reach the payload.
     *
     * Returns null when the input is not a number.
     */
    fun parseAmountToCents(raw: String): Long? {
        val cleaned =
            raw
                .trim()
                .removePrefix("S$")
                .removePrefix("$")
                .filter { it != ',' && it != ' ' }
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split('.')
        if (parts.size > 2) return null
        val whole = parts[0].ifEmpty { "0" }
        val fraction = parts.getOrNull(1).orEmpty()
        if (!whole.all { it.isDigit() } || !fraction.all { it.isDigit() }) return null
        if (fraction.length > 2) return null
        val paddedFraction = fraction.padEnd(2, '0')
        val wholeValue = whole.toLongOrNull() ?: return null
        if (wholeValue > 9_999_999L) return null // guard before multiplying
        return wholeValue * 100 + paddedFraction.toLong()
    }

    /** FR-105: renders cents as the wire format, e.g. 50000 -> "500.00". */
    fun formatCents(cents: Long): String = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

    fun validate(
        config: PayNowConfig,
        today: LocalDate,
    ): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        fun error(
            field: Field,
            code: IssueCode,
        ) = issues.add(ValidationIssue(field, code, ValidationIssue.Severity.ERROR, PayloadStrings.of(code)))

        fun warn(
            field: Field,
            code: IssueCode,
        ) = issues.add(ValidationIssue(field, code, ValidationIssue.Severity.WARNING, PayloadStrings.of(code)))

        // ---- Proxy -------------------------------------------------------------
        val rawProxy = config.proxyValue.trim() // FR-113
        when (config.proxyType) {
            ProxyType.MOBILE ->
                when {
                    rawProxy.isEmpty() -> error(Field.PROXY, IssueCode.PROXY_EMPTY)
                    else -> {
                        val digits =
                            rawProxy
                                .filter { it.isDigit() }
                                .let { if (it.length == 10 && it.startsWith("65")) it.drop(2) else it }
                        when {
                            digits.length != 8 -> error(Field.PROXY, IssueCode.MOBILE_BAD_FORMAT)
                            digits[0] != '8' && digits[0] != '9' -> error(Field.PROXY, IssueCode.MOBILE_BAD_PREFIX)
                        }
                    }
                }

            ProxyType.UEN -> {
                val uen = normaliseUen(rawProxy)
                when {
                    uen.isEmpty() -> error(Field.PROXY, IssueCode.UEN_EMPTY)
                    uen.length !in 9..10 -> error(Field.PROXY, IssueCode.UEN_BAD_LENGTH)
                    !ALPHANUMERIC.matches(uen) -> error(Field.PROXY, IssueCode.UEN_BAD_CHARACTERS)
                    // FR-102: a well-formed but unfamiliar UEN warns instead of blocking,
                    // because the registry's formats change over time.
                    !UEN_BUSINESS.matches(uen) &&
                        !UEN_LOCAL_COMPANY.matches(uen) &&
                        !UEN_OTHER_ENTITY.matches(uen) ->
                        warn(Field.PROXY, IssueCode.UEN_UNRECOGNISED_PATTERN)
                }
            }
        }

        // ---- Amount ------------------------------------------------------------
        val rawAmount = config.amount?.trim()
        if (!rawAmount.isNullOrEmpty()) {
            val cleaned = rawAmount.removePrefix("S$").removePrefix("$").filter { it != ',' && it != ' ' }
            val fractionDigits = cleaned.substringAfter('.', "").length
            val cents = parseAmountToCents(rawAmount)
            when {
                cents == null && fractionDigits > 2 -> error(Field.AMOUNT, IssueCode.AMOUNT_TOO_MANY_DECIMALS)
                cents == null -> error(Field.AMOUNT, IssueCode.AMOUNT_NOT_A_NUMBER)
                cents < PayNowDefaults.MIN_AMOUNT_CENTS -> error(Field.AMOUNT, IssueCode.AMOUNT_TOO_SMALL)
                cents > PayNowDefaults.MAX_AMOUNT_CENTS -> error(Field.AMOUNT, IssueCode.AMOUNT_TOO_LARGE)
            }
        }

        // ---- Expiry ------------------------------------------------------------
        // FR-108: today counts as valid; only a date strictly before today is rejected.
        config.expiry?.let { if (it < today) error(Field.EXPIRY, IssueCode.EXPIRY_IN_PAST) }

        // ---- Merchant name -----------------------------------------------------
        val name = config.merchantName?.trim()
        if (!name.isNullOrEmpty()) {
            // FR-111 / AC-15: a clear message, never a crash and never a corrupt length prefix.
            if (!isPrintableAscii(name)) error(Field.MERCHANT_NAME, IssueCode.MERCHANT_NAME_NON_ASCII)
            if (name.length > PayNowDefaults.MERCHANT_NAME_MAX_LENGTH) {
                warn(Field.MERCHANT_NAME, IssueCode.MERCHANT_NAME_TOO_LONG)
            }
        }

        // ---- Reference ---------------------------------------------------------
        val reference = config.reference?.trim()
        if (!reference.isNullOrEmpty()) {
            if (!isPrintableAscii(reference)) {
                error(Field.REFERENCE, IssueCode.REFERENCE_NON_ASCII)
            } else if (!SAFE_REFERENCE.matches(reference)) {
                warn(Field.REFERENCE, IssueCode.REFERENCE_UNUSUAL_CHARACTERS) // FR-114
            }
            if (reference.length > PayNowDefaults.REFERENCE_MAX_LENGTH) {
                warn(Field.REFERENCE, IssueCode.REFERENCE_TOO_LONG)
            }
        }

        return ValidationResult(issues)
    }
}
