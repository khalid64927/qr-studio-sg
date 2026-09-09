package sg.qrstudio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import sg.qrstudio.payload.ProxyType

/**
 * The single screen, in its step-2 form: inputs on the left of the data flow, a live
 * preview driven entirely by [QrStudioUiState].
 *
 * The responsive two-pane layout (§8), appearance controls and export bar are not built
 * yet. What is here is the preview pipeline end to end — payload, encoding, rendering —
 * so the parts that follow have something real to attach to.
 */
@Composable
fun QrStudioScreen(
    state: QrStudioUiState,
    onIntent: (QrStudioIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(Strings.APP_NAME, style = MaterialTheme.typography.headlineSmall)

        QrPreviewPanel(state)

        Text(Strings.SECTION_PAY_TO, style = MaterialTheme.typography.titleMedium)

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
            isError = state.errors.any { it.field.name == "PROXY" },
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

        Text(Strings.SECTION_PAYMENT, style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = state.amount,
            onValueChange = { onIntent(QrStudioIntent.AmountChanged(it)) },
            label = { Text(Strings.AMOUNT_LABEL) },
            placeholder = { Text(Strings.AMOUNT_PLACEHOLDER) },
            isError = state.errors.any { it.field.name == "AMOUNT" },
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
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.merchantName,
            onValueChange = { onIntent(QrStudioIntent.MerchantNameChanged(it)) },
            label = { Text(Strings.MERCHANT_NAME_LABEL) },
            placeholder = { Text(Strings.MERCHANT_NAME_PLACEHOLDER) },
            supportingText = { Text("${state.merchantName.length} / 25") }, // FR-110 counter
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

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
}

/** FR-504: the code sits on a neutral surface with visible bounds. */
@Composable
private fun QrPreviewPanel(state: QrStudioUiState) {
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
}
