package sg.qrstudio.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import sg.qrstudio.payload.PayNowConfig
import sg.qrstudio.payload.PayNowPayload
import sg.qrstudio.payload.PayNowPayloadBuilder
import sg.qrstudio.payload.PayloadResult
import sg.qrstudio.payload.ProxyType
import sg.qrstudio.payload.ValidationIssue
import sg.qrstudio.qr.AppearanceConfig
import sg.qrstudio.qr.Contrast
import sg.qrstudio.qr.ErrorCorrection
import sg.qrstudio.qr.LogoConfig
import sg.qrstudio.qr.ModuleMatrix
import sg.qrstudio.qr.QrEncoder

/**
 * Everything the screen renders.
 *
 * FR-701: state lives in memory only. Nothing here is written to disk, and closing the
 * app discards it all.
 */
data class QrStudioUiState(
    val proxyType: ProxyType = ProxyType.MOBILE,
    val proxyValue: String = "",
    val amount: String = "",
    val amountEditable: Boolean = false,
    val reference: String = "",
    val merchantName: String = "",
    val errorCorrection: ErrorCorrection = ErrorCorrection.DEFAULT,
    val appearance: AppearanceConfig = AppearanceConfig(),
    val logo: LogoConfig = LogoConfig(),
    /**
     * FR-310: the level in force before a logo forced it to H, so removing the logo can
     * restore it rather than leaving the user stuck at H.
     */
    val errorCorrectionBeforeLogo: ErrorCorrection = ErrorCorrection.DEFAULT,
    /** Null until the input is valid enough to encode. */
    val payload: PayNowPayload? = null,
    val matrix: ModuleMatrix? = null,
    val errors: List<ValidationIssue> = emptyList(),
    val warnings: List<ValidationIssue> = emptyList(),
) {
    /**
     * FR-151/FR-402: export stays blocked until there is a payload, no blocking input
     * error, and the appearance colours clear the contrast floor.
     */
    val canExport: Boolean
        get() = payload != null && errors.isEmpty() && appearance.contrastVerdict != Contrast.Verdict.BLOCKED

    /** FR-155: the UEN notice is shown whenever UEN mode is selected. */
    val showUenNotice: Boolean get() = proxyType == ProxyType.UEN
}

/** Explicit intents. §9.1: state is mutated only through these, never in place. */
sealed interface QrStudioIntent {
    data class ProxyTypeChanged(val proxyType: ProxyType) : QrStudioIntent
    data class ProxyValueChanged(val value: String) : QrStudioIntent
    data class AmountChanged(val value: String) : QrStudioIntent
    data class AmountEditableChanged(val editable: Boolean) : QrStudioIntent
    data class ReferenceChanged(val value: String) : QrStudioIntent
    data class MerchantNameChanged(val value: String) : QrStudioIntent
    data class ErrorCorrectionChanged(val level: ErrorCorrection) : QrStudioIntent

    /** FR-401/FR-407: colour and shape changes. The UI builds the new config with .copy(). */
    data class AppearanceChanged(val appearance: AppearanceConfig) : QrStudioIntent

    /** FR-301..FR-311: logo presence, size and shape. */
    data class LogoChanged(val logo: LogoConfig) : QrStudioIntent

    /** FR-404: restores every visual default in one action. */
    data object AppearanceReset : QrStudioIntent
}

/**
 * kotlinx-datetime 0.7 moved Clock to kotlin.time, where it is still experimental.
 * The opt-in is confined to this one function so it does not spread through the module,
 * and injecting [today] keeps the view model deterministic under test.
 */
@OptIn(ExperimentalTime::class)
private fun systemToday(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

class QrStudioViewModel(
    private val today: () -> LocalDate = ::systemToday,
) : ViewModel() {

    var uiState by mutableStateOf(QrStudioUiState())
        private set

    private var regenerateJob: Job? = null

    fun onIntent(intent: QrStudioIntent) {
        uiState = when (intent) {
            is QrStudioIntent.ProxyTypeChanged ->
                // FR-104: switching proxy type clears the value. A mobile number is never
                // a valid UEN, and silently carrying it across reads as a glitch.
                uiState.copy(proxyType = intent.proxyType, proxyValue = "")

            is QrStudioIntent.ProxyValueChanged -> uiState.copy(proxyValue = intent.value)
            is QrStudioIntent.AmountChanged -> uiState.copy(amount = intent.value)
            is QrStudioIntent.AmountEditableChanged -> uiState.copy(amountEditable = intent.editable)
            is QrStudioIntent.ReferenceChanged -> uiState.copy(reference = intent.value)
            is QrStudioIntent.MerchantNameChanged -> uiState.copy(merchantName = intent.value)
            is QrStudioIntent.ErrorCorrectionChanged -> uiState.copy(errorCorrection = intent.level)
            is QrStudioIntent.AppearanceChanged -> uiState.copy(appearance = intent.appearance)
            is QrStudioIntent.LogoChanged -> applyLogoChange(intent.logo)
            QrStudioIntent.AppearanceReset -> uiState.copy(
                appearance = AppearanceConfig(),
                logo = LogoConfig(),
                errorCorrection = uiState.errorCorrectionBeforeLogo,
            )
        }
        scheduleRegeneration(debounce = intent.isTextEdit)
    }

    /**
     * FR-204: turning a logo on forces error correction to H, remembering whatever level
     * was active so FR-310 can restore it. Sliding the size or changing the shape while
     * already enabled does not re-trigger this — only the enabled transition does.
     */
    private fun applyLogoChange(newLogo: LogoConfig): QrStudioUiState {
        val current = uiState
        val turningOn = newLogo.enabled && !current.logo.enabled
        val turningOff = !newLogo.enabled && current.logo.enabled
        return when {
            turningOn -> current.copy(
                logo = newLogo,
                errorCorrectionBeforeLogo = current.errorCorrection,
                errorCorrection = ErrorCorrection.WITH_LOGO,
            )

            turningOff -> current.copy(logo = newLogo, errorCorrection = current.errorCorrectionBeforeLogo)
            else -> current.copy(logo = newLogo)
        }
    }

    private val QrStudioIntent.isTextEdit: Boolean
        get() = this is QrStudioIntent.ProxyValueChanged ||
            this is QrStudioIntent.AmountChanged ||
            this is QrStudioIntent.ReferenceChanged ||
            this is QrStudioIntent.MerchantNameChanged

    /**
     * FR-502: text edits are debounced by 250 ms before the payload is rebuilt, because
     * every keystroke would otherwise re-run encoding and mask selection. Toggles and
     * selections regenerate immediately — there is no burst of them to absorb.
     */
    private fun scheduleRegeneration(debounce: Boolean) {
        regenerateJob?.cancel()
        regenerateJob = viewModelScope.launch {
            if (debounce) delay(DEBOUNCE_MILLIS)
            regenerate()
        }
    }

    private fun regenerate() {
        val current = uiState
        val config = PayNowConfig(
            proxyType = current.proxyType,
            proxyValue = current.proxyValue,
            amount = current.amount.ifBlank { null },
            amountEditable = current.amountEditable,
            reference = current.reference.ifBlank { null },
            merchantName = current.merchantName.ifBlank { null },
        )

        // An empty proxy is the starting state, not a mistake to shout about. Leave the
        // preview blank and stay quiet until the user has actually typed something.
        if (current.proxyValue.isBlank()) {
            uiState = current.copy(payload = null, matrix = null, errors = emptyList(), warnings = emptyList())
            return
        }

        // FR-154: the payload and its CRC are rebuilt from scratch on every change.
        uiState = when (val result = PayNowPayloadBuilder.build(config, today())) {
            is PayloadResult.Success -> current.copy(
                payload = result.payload,
                matrix = QrEncoder.encode(result.payload.raw, current.errorCorrection),
                errors = emptyList(),
                warnings = result.warnings,
            )

            is PayloadResult.Invalid -> current.copy(
                payload = null,
                matrix = null,
                errors = result.errors,
                warnings = result.warnings,
            )
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 250L
    }
}
