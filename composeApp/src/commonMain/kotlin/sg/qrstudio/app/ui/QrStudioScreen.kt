package sg.qrstudio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import sg.qrstudio.payload.Field
import sg.qrstudio.payload.ProxyType

/**
 * §8 breakpoint. Below this the screen is a single scrolling column; at or above it, the
 * preview pins to the left and never scrolls away, with a separate scrollable control
 * rail on the right — Compose has no notion of viewport width on its own, so [Dp] width
 * from [BoxWithConstraints] is the actual signal, not a platform check.
 */
private val EXPANDED_BREAKPOINT = 600.dp

/**
 * The single screen, laid out compact or expanded depending on measured width — including
 * in a resizable desktop window or a browser tab, not just by platform. §8: "Pay to" is
 * the only section expanded on first launch, so a user wanting a plain open-amount QR
 * finishes in two fields.
 *
 * Appearance controls and export are not built yet — Branding and Appearance render as
 * honest placeholders rather than fake controls for features that don't exist.
 */
@Composable
fun QrStudioScreen(
    state: QrStudioUiState,
    onIntent: (QrStudioIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var paymentExpanded by remember { mutableStateOf(false) }
    var payToExpanded by remember { mutableStateOf(true) } // §8: expanded on first launch
    var brandingExpanded by remember { mutableStateOf(false) }
    var appearanceExpanded by remember { mutableStateOf(false) }

    val sections: @Composable () -> Unit = {
        ExpandableSection(
            title = Strings.SECTION_PAY_TO,
            summary = paySummary(state),
            expanded = payToExpanded,
            onToggle = { payToExpanded = !payToExpanded },
            leadingIcon = { Icon(Icons.Filled.AccountBalance, contentDescription = null) },
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ProxyType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = state.proxyType == type,
                        onClick = { onIntent(QrStudioIntent.ProxyTypeChanged(type)) },
                        shape = SegmentedButtonDefaults.itemShape(index, ProxyType.entries.size),
                    ) {
                        Text(
                            if (type == ProxyType.MOBILE) Strings.PROXY_TYPE_MOBILE else Strings.PROXY_TYPE_UEN,
                        )
                    }
                }
            }

            val isMobile = state.proxyType == ProxyType.MOBILE
            OutlinedTextField(
                value = state.proxyValue,
                onValueChange = { onIntent(QrStudioIntent.ProxyValueChanged(it)) },
                label = { Text(Strings.proxyLabel(isMobile)) },
                placeholder = { Text(Strings.proxyPlaceholder(isMobile)) },
                // FR-153-adjacent: a small worked example under the field, independent of
                // the placeholder, so it stays visible once the user starts typing.
                supportingText = { Text(Strings.proxyExample(isMobile)) },
                isError = state.errors.any { it.field == Field.PROXY },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // FR-155 / AC-19: the UEN nudge toward an acquirer-issued SGQR label.
            if (state.showUenNotice) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(Strings.UEN_NOTICE, style = MaterialTheme.typography.bodySmall)
                        Text(
                            Strings.UEN_NOTICE_LINK,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.merchantName,
                onValueChange = { onIntent(QrStudioIntent.MerchantNameChanged(it)) },
                label = { Text(Strings.MERCHANT_NAME_LABEL) },
                placeholder = { Text(Strings.MERCHANT_NAME_PLACEHOLDER) },
                supportingText = {
                    // FR-110 counter alongside the reassurance copy from the design pass.
                    Text("${Strings.MERCHANT_NAME_HELP}  ${state.merchantName.length}/25")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ExpandableSection(
            title = Strings.SECTION_PAYMENT,
            summary = paymentSummary(state),
            expanded = paymentExpanded,
            onToggle = { paymentExpanded = !paymentExpanded },
            leadingIcon = { Icon(Icons.Filled.Payments, contentDescription = null) },
        ) {
            OutlinedTextField(
                value = state.amount,
                onValueChange = { onIntent(QrStudioIntent.AmountChanged(it)) },
                label = { Text(Strings.AMOUNT_LABEL) },
                placeholder = { Text(Strings.AMOUNT_PLACEHOLDER) },
                isError = state.errors.any { it.field == Field.AMOUNT },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // FR-106: with no amount the payer must be able to enter one, so the toggle is
            // forced on and disabled rather than merely defaulted.
            val amountEmpty = state.amount.isBlank()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (amountEmpty) Strings.AMOUNT_EDITABLE_FORCED else Strings.AMOUNT_EDITABLE,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = amountEmpty || state.amountEditable,
                    onCheckedChange = { onIntent(QrStudioIntent.AmountEditableChanged(it)) },
                    enabled = !amountEmpty,
                )
            }

            OutlinedTextField(
                value = state.reference,
                onValueChange = { onIntent(QrStudioIntent.ReferenceChanged(it)) },
                label = { Text(Strings.REFERENCE_LABEL) },
                placeholder = { Text(Strings.REFERENCE_PLACEHOLDER) },
                supportingText = { Text(Strings.REFERENCE_HELP) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Branding and Appearance are not implemented yet (roadmap steps 4-5). Shown
        // collapsed and locked rather than left out, so the eventual section order is
        // visible now and nothing here claims a feature that doesn't exist.
        ExpandableSection(
            title = Strings.SECTION_BRANDING,
            summary = Strings.COMING_SOON,
            expanded = brandingExpanded,
            onToggle = { brandingExpanded = !brandingExpanded },
            leadingIcon = { Icon(Icons.Filled.Stars, contentDescription = null) },
        ) {
            Text(Strings.BRANDING_PREVIEW, style = MaterialTheme.typography.bodySmall)
        }

        ExpandableSection(
            title = Strings.SECTION_APPEARANCE,
            summary = Strings.COMING_SOON,
            expanded = appearanceExpanded,
            onToggle = { appearanceExpanded = !appearanceExpanded },
            leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) },
        ) {
            Text(
                "Colours, module and eye styles, frames and captions — all gated by a " +
                    "contrast and scan check, never a raw picker with no guardrail.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        state.errors.forEach { issue ->
            Text(
                issue.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        state.warnings.forEach { issue ->
            Text(issue.message, style = MaterialTheme.typography.bodySmall)
        }

        // FR-115: the raw payload, for debugging. Collapsible panel comes with step 3.
        state.payload?.let { payload ->
            Text(Strings.PAYLOAD_PANEL, style = MaterialTheme.typography.titleSmall)
            Text(payload.raw, style = MaterialTheme.typography.bodySmall)
            Text(
                "Recipient ${payload.proxyDisplay}", // FR-153: grouped for reading
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth < EXPANDED_BREAKPOINT) {
            CompactLayout(state, sections)
        } else {
            ExpandedLayout(state, sections)
        }
    }
}

/** Phones and narrow browser windows: preview near the top, everything scrolls together. */
@Composable
private fun CompactLayout(state: QrStudioUiState, sections: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppTitleBar()
        QrPreviewPanel(state)
        sections()
    }
}

/**
 * Tablet and desktop, and any browser window at or above [EXPANDED_BREAKPOINT]: the
 * preview pins to the left and never scrolls away, with the section rail on the right
 * scrolling independently. This is the layout the wasm build should show once the browser
 * window is wide — a phone-shaped window still gets [CompactLayout], correctly.
 */
@Composable
private fun ExpandedLayout(state: QrStudioUiState, sections: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppTitleBar()
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(modifier = Modifier.widthIn(max = 480.dp).width(420.dp)) {
                QrPreviewPanel(state)
            }
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                sections()
            }
        }
    }
}

/** App identity plus an "Offline" badge — a trust cue that this tool makes no network calls. */
@Composable
private fun AppTitleBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.QrCode2, contentDescription = null)
            Text(Strings.APP_NAME, style = MaterialTheme.typography.headlineSmall)
        }
        SuggestionChip(
            onClick = {},
            label = { Text(Strings.OFFLINE_BADGE) },
            icon = { Icon(Icons.Filled.CloudOff, contentDescription = null) },
            colors = SuggestionChipDefaults.suggestionChipColors(),
        )
    }
}

/** FR-504: the code sits on a neutral surface with visible bounds. */
@Composable
private fun QrPreviewPanel(state: QrStudioUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val matrix = state.matrix
                if (matrix != null) {
                    QrCanvas(
                        matrix = matrix,
                        modifier = Modifier.fillMaxSize().background(Color.White),
                    )
                } else {
                    Text(
                        if (state.errors.isEmpty()) Strings.PREVIEW_EMPTY else Strings.PREVIEW_BLOCKED,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (state.matrix != null) {
            Text(
                Strings.LIVE_PREVIEW_CAPTION,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

private fun paySummary(state: QrStudioUiState): String? {
    if (state.proxyValue.isBlank()) return null
    val type = if (state.proxyType == ProxyType.MOBILE) "Mobile" else "UEN"
    return "$type · ${state.proxyValue}"
}

private fun paymentSummary(state: QrStudioUiState): String? {
    val amount = state.amount.ifBlank { "Any amount" }
    val reference = state.reference.ifBlank { "No reference" }
    return "$amount · $reference"
}
