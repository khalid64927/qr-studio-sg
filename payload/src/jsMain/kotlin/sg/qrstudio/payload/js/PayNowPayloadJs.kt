package sg.qrstudio.payload.js

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import sg.qrstudio.payload.PayNowConfig
import sg.qrstudio.payload.PayNowPayloadBuilder
import sg.qrstudio.payload.PayloadResult
import sg.qrstudio.payload.ProxyType
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The npm-facing PayNow API.
 *
 * `:payload`'s real API (PayNowConfig, PayloadResult, ValidationIssue…) is not exported
 * directly: [PayNowConfig.expiry] is a kotlinx-datetime `LocalDate`, which `@JsExport`
 * cannot describe to TypeScript, and [PayloadResult] is a sealed interface, which
 * `@JsExport` does not support at all. This module exists only in the js(IR) source
 * set — wasmJs, JVM, Android and iOS never see it — and translates to and from plain
 * strings/booleans so a native JS/TS UI gets a clean, typed `.d.ts` instead of either
 * of those.
 *
 * Every other public type in `:qr` is directly `@JsExport`-safe, so its facade
 * ([sg.qrstudio.qr.js]) wraps far less.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class BuildPayNowQrResult internal constructor(
    val success: Boolean,
    val raw: String?,
    val normalisedProxy: String?,
    val proxyDisplay: String?,
    val amount: String?,
    val amountEditable: Boolean,
    val expiry: String?,
    val reference: String?,
    val merchantName: String?,
    val pointOfInitiation: String?,
    val errors: Array<String>,
    val warnings: Array<String>,
)

/**
 * Builds a PayNow EMVCo payload string.
 *
 * @param proxyType one of `"MOBILE"`, `"NRIC"`, `"UEN"`, `"VPA"` (case-insensitive). A VPA
 *   value is `<mobile-or-UEN>#<provider>`, e.g. `"+6591234567#GRAB"`.
 * @param expiry ISO-8601 date (`"2026-12-31"`), or `null`/omitted for the library's
 *   default of today + 5 years.
 * @param today ISO-8601 date to treat as "today" for expiry validation. Omit to use the
 *   system clock — tests should always pass this explicitly.
 */
@OptIn(ExperimentalJsExport::class, ExperimentalTime::class)
@JsExport
fun buildPayNowQr(
    proxyType: String,
    proxyValue: String,
    amount: String? = null,
    amountEditable: Boolean = false,
    expiry: String? = null,
    reference: String? = null,
    merchantName: String? = null,
    today: String? = null,
): BuildPayNowQrResult {
    val resolvedProxyType =
        ProxyType.entries.firstOrNull { it.name.equals(proxyType, ignoreCase = true) }
            ?: return BuildPayNowQrResult(
                success = false,
                raw = null,
                normalisedProxy = null,
                proxyDisplay = null,
                amount = null,
                amountEditable = amountEditable,
                expiry = null,
                reference = null,
                merchantName = null,
                pointOfInitiation = null,
                errors = arrayOf("Unknown proxy type '$proxyType'. Use MOBILE, NRIC, UEN or VPA."),
                warnings = emptyArray(),
            )

    val config =
        PayNowConfig(
            proxyType = resolvedProxyType,
            proxyValue = proxyValue,
            amount = amount,
            amountEditable = amountEditable,
            expiry = expiry?.let { LocalDate.parse(it) },
            reference = reference,
            merchantName = merchantName,
        )

    val resolvedToday = today?.let { LocalDate.parse(it) } ?: Clock.System.todayIn(TimeZone.currentSystemDefault())

    return when (val result = PayNowPayloadBuilder.build(config, resolvedToday)) {
        is PayloadResult.Success ->
            BuildPayNowQrResult(
                success = true,
                raw = result.payload.raw,
                normalisedProxy = result.payload.normalisedProxy,
                proxyDisplay = result.payload.proxyDisplay,
                amount = result.payload.amount,
                amountEditable = result.payload.amountEditable,
                expiry = result.payload.expiry.toString(),
                reference = result.payload.reference,
                merchantName = result.payload.merchantName,
                pointOfInitiation = result.payload.pointOfInitiation,
                errors = emptyArray(),
                warnings = result.warnings.map { it.message }.toTypedArray(),
            )

        is PayloadResult.Invalid ->
            BuildPayNowQrResult(
                success = false,
                raw = null,
                normalisedProxy = null,
                proxyDisplay = null,
                amount = null,
                amountEditable = amountEditable,
                expiry = null,
                reference = null,
                merchantName = null,
                pointOfInitiation = null,
                errors = result.errors.map { it.message }.toTypedArray(),
                warnings = result.warnings.map { it.message }.toTypedArray(),
            )
    }
}
