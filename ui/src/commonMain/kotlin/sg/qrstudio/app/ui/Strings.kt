package sg.qrstudio.app.ui

/**
 * FR-704: every user-facing string lives here so the app can be localised later.
 * English only in v1. Validation copy lives in the payload module's PayloadStrings.
 */
object Strings {
    const val APP_NAME = "QR Studio SG"

    const val SECTION_PAY_TO = "Pay to"
    const val SECTION_PAYMENT = "Payment"

    const val PROXY_TYPE_MOBILE = "Mobile number"
    const val PROXY_TYPE_UEN = "UEN"
    const val PROXY_LABEL_MOBILE = "Mobile number"
    const val PROXY_LABEL_UEN = "UEN"
    const val PROXY_PLACEHOLDER_MOBILE = "9123 4567"
    const val PROXY_PLACEHOLDER_UEN = "201403121W"

    const val AMOUNT_LABEL = "Amount (SGD)"
    const val AMOUNT_PLACEHOLDER = "Leave blank for any amount"
    const val AMOUNT_EDITABLE = "Let the payer change the amount"
    const val AMOUNT_EDITABLE_FORCED = "The payer enters the amount"

    const val REFERENCE_LABEL = "Reference"
    const val REFERENCE_PLACEHOLDER = "INV-2026-001"
    const val REFERENCE_HELP = "Optional. Helps you match payments to invoices."
    const val MERCHANT_NAME_LABEL = "Name payers will see"
    const val MERCHANT_NAME_PLACEHOLDER = "Shown to the payer"
    const val MERCHANT_NAME_HELP = "Shown in the payer's app before they confirm."

    const val PROXY_EXAMPLE_MOBILE = "e.g. 9123 4567"
    const val PROXY_EXAMPLE_UEN = "e.g. 201403121W"

    const val ERROR_CORRECTION_LABEL = "Error correction"

    const val OFFLINE_BADGE = "Offline"
    const val LIVE_PREVIEW_CAPTION = "Live preview — updates as you type"

    const val PREVIEW_EMPTY = "Enter a mobile number to see your code"
    const val PREVIEW_BLOCKED = "Fix the highlighted fields to see your code"

    const val SECTION_BRANDING = "Branding"
    const val SECTION_APPEARANCE = "Appearance"
    const val COMING_SOON = "Coming soon"
    const val BRANDING_PREVIEW =
        "Add a centre logo, choose module and eye styles, or add a frame. Every change is " +
            "checked against a scan test before it can be exported — appearance never wins over " +
            "correctness."

    const val PAYLOAD_PANEL = "Payload"

    /** FR-155 — shown whenever UEN mode is selected. See LC-02 and LC-03. */
    const val UEN_NOTICE =
        "Self-generated codes are intended for digital delivery — invoices, email or " +
            "messaging. A business displaying a payment QR at a physical counter should " +
            "obtain an SGQR label from its bank or acquirer."
    const val UEN_NOTICE_LINK = "https://www.mas.gov.sg/development/e-payments/sgqr/for-merchants"

    /** LC-04 — shown in About and in the store or site listing. */
    const val DISCLAIMER =
        "Independent tool. Not affiliated with or endorsed by any bank, ABS, IMDA or MAS. " +
            "You are responsible for verifying recipient details before use."

    fun proxyLabel(isMobile: Boolean) = if (isMobile) PROXY_LABEL_MOBILE else PROXY_LABEL_UEN

    fun proxyPlaceholder(isMobile: Boolean) = if (isMobile) PROXY_PLACEHOLDER_MOBILE else PROXY_PLACEHOLDER_UEN

    fun proxyExample(isMobile: Boolean) = if (isMobile) PROXY_EXAMPLE_MOBILE else PROXY_EXAMPLE_UEN
}
