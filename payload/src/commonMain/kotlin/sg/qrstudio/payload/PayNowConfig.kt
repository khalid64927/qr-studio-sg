package sg.qrstudio.payload

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Which PayNow proxy identifies the recipient.
 *
 * D1 / FR-104: this is always an explicit user choice. The reference implementation
 * hardcodes UEN; that behaviour is deliberately not carried over.
 *
 * Proxy types 1 (NRIC) and 3 (VPA) exist in the scheme but are out of scope for v1.
 */
enum class ProxyType(val code: String) {
    MOBILE("0"),
    UEN("2"),
}

/**
 * Immutable description of the payment to encode.
 *
 * D8 / FR-101: the builder takes one of these and returns a string. It never mutates
 * the caller's object, and it holds no reference to platform or UI types.
 *
 * @param amount raw user input. Null or blank means "any amount" — the payer types it in.
 * @param amountEditable honoured only when [amount] is present; see FR-106.
 * @param expiry null selects the FR-108 default of today + 5 years.
 */
data class PayNowConfig(
    val proxyType: ProxyType,
    val proxyValue: String,
    val amount: String? = null,
    val amountEditable: Boolean = false,
    val expiry: LocalDate? = null,
    val reference: String? = null,
    val merchantName: String? = null,
) {
    /**
     * FR-106: with no amount the payer must be free to enter one, so the editable flag
     * is forced on and the UI toggle is disabled. With an amount, the user chooses.
     */
    val effectiveAmountEditable: Boolean
        get() = if (amount.isNullOrBlank()) true else amountEditable
}

object PayNowDefaults {

    /** OQ-4 / FR-110: merchant name is never empty on the wire. */
    const val MERCHANT_NAME = "NA"

    const val MERCHANT_NAME_MAX_LENGTH = 25
    const val REFERENCE_MAX_LENGTH = 25

    /** FR-105 amount bounds, inclusive. */
    const val MIN_AMOUNT_CENTS = 1L
    const val MAX_AMOUNT_CENTS = 99_999_999L

    const val DEFAULT_EXPIRY_YEARS = 5

    /** FR-108: "No expiry" in the UI means today + 5 years on the wire. */
    fun defaultExpiry(today: LocalDate): LocalDate =
        today.plus(DatePeriod(years = DEFAULT_EXPIRY_YEARS))
}
