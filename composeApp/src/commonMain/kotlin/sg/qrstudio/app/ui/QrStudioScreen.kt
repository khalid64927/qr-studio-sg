package sg.qrstudio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import sg.qrstudio.qr.AppearanceConfig
import sg.qrstudio.qr.Contrast
import sg.qrstudio.qr.EyeStyle
import sg.qrstudio.qr.LogoConfig
import sg.qrstudio.qr.LogoShape
import sg.qrstudio.qr.ModuleShape
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

        ExpandableSection(
            title = Strings.SECTION_BRANDING,
            summary = if (state.logo.enabled) "Enabled · ${(state.logo.sizeFraction * 100).toInt()}%" else "Disabled",
            expanded = brandingExpanded,
            onToggle = { brandingExpanded = !brandingExpanded },
            leadingIcon = { Icon(Icons.Filled.Stars, contentDescription = null) },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Logo enabled", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = state.logo.enabled,
                    onCheckedChange = { enabled ->
                        onIntent(QrStudioIntent.LogoChanged(state.logo.copy(enabled = enabled)))
                    },
                )
            }

            if (state.logo.enabled) {
                Text("Size: ${(state.logo.sizeFraction * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = state.logo.sizeFraction,
                    onValueChange = { size ->
                        onIntent(QrStudioIntent.LogoChanged(state.logo.copy(sizeFraction = size)))
                    },
                    valueRange = LogoConfig.MIN_SIZE_FRACTION..LogoConfig.MAX_SIZE_FRACTION,
                    steps = 10,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.logo.showsSizeWarning) {
                    Text(
                        "⚠ Large logo may affect scannability",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Text("Shape", style = MaterialTheme.typography.bodySmall)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    LogoShape.entries.forEachIndexed { index, shape ->
                        SegmentedButton(
                            selected = state.logo.shape == shape,
                            onClick = { onIntent(QrStudioIntent.LogoChanged(state.logo.copy(shape = shape))) },
                            shape = SegmentedButtonDefaults.itemShape(index, LogoShape.entries.size),
                        ) {
                            Text(shape.name)
                        }
                    }
                }

                // §9.3: ImagePicker integration deferred. The logo.placeholder flag stays true
                // until real image picking is wired up. For now, show a placeholder stand-in
                // so the size, shape and backing-plate mechanics are real and testable.
                SuggestionChip(
                    onClick = { /* TODO §9.3: wire platform-specific image picker */ },
                    label = { Text("Choose image") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        ExpandableSection(
            title = Strings.SECTION_APPEARANCE,
            summary = when (state.appearance.contrastVerdict) {
                Contrast.Verdict.BLOCKED -> "❌ Too dark — export blocked"
                Contrast.Verdict.WARNING -> "⚠ Below 4.5:1"
                Contrast.Verdict.OK -> "✓ ${(state.appearance.contrastRatio * 10).toInt() / 10.0}:1"
            },
            expanded = appearanceExpanded,
            onToggle = { appearanceExpanded = !appearanceExpanded },
            leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) },
        ) {
            Text("Foreground (QR code)", style = MaterialTheme.typography.bodySmall)
            ColorSliders(
                color = state.appearance.foreground,
                onColorChanged = { newFg ->
                    onIntent(QrStudioIntent.AppearanceChanged(state.appearance.copy(foreground = newFg)))
                },
            )

            Text("Background", style = MaterialTheme.typography.bodySmall)
            ColorSliders(
                color = state.appearance.background,
                onColorChanged = { newBg ->
                    onIntent(QrStudioIntent.AppearanceChanged(state.appearance.copy(background = newBg)))
                },
            )

            // FR-402/FR-403 gates
            val verdict = state.appearance.contrastVerdict
            if (verdict == Contrast.Verdict.BLOCKED) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "❌ Contrast ratio ${(state.appearance.contrastRatio * 10).toInt() / 10.0}:1 is below 3:1 — export is blocked. " +
                            "Lighten the foreground or darken the background.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else if (verdict == Contrast.Verdict.WARNING) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "⚠ Contrast ratio ${(state.appearance.contrastRatio * 10).toInt() / 10.0}:1 is below WCAG AA (4.5:1). " +
                            "The code may be hard to scan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (state.appearance.backgroundDarkerThanForeground) {
                Text(
                    "Note: background is darker than foreground — unusual but allowed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text("Module shape", style = MaterialTheme.typography.bodySmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ModuleShape.entries.forEachIndexed { index, shape ->
                    SegmentedButton(
                        selected = state.appearance.moduleShape == shape,
                        onClick = { onIntent(QrStudioIntent.AppearanceChanged(state.appearance.copy(moduleShape = shape))) },
                        shape = SegmentedButtonDefaults.itemShape(index, ModuleShape.entries.size),
                    ) {
                        Text(shape.name)
                    }
                }
            }

            Text("Eye style", style = MaterialTheme.typography.bodySmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ModuleShape.entries.forEachIndexed { index, shape ->
                    SegmentedButton(
                        selected = state.appearance.eyeStyle.shape == shape,
                        onClick = {
                            onIntent(
                                QrStudioIntent.AppearanceChanged(
                                    state.appearance.copy(eyeStyle = state.appearance.eyeStyle.copy(shape = shape)),
                                ),
                            )
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, ModuleShape.entries.size),
                    ) {
                        Text(shape.name)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Eye uses module colour", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = state.appearance.eyeStyle.colour == null,
                    onCheckedChange = { useModuleColour ->
                        onIntent(
                            QrStudioIntent.AppearanceChanged(
                                state.appearance.copy(
                                    eyeStyle = state.appearance.eyeStyle.copy(
                                        colour = if (useModuleColour) null else state.appearance.foreground,
                                    ),
                                ),
                            ),
                        )
                    },
                )
            }

            if (state.appearance.eyeStyle.colour != null) {
                Text("Eye colour", style = MaterialTheme.typography.bodySmall)
                ColorSliders(
                    color = state.appearance.eyeStyle.colour!!,
                    onColorChanged = { newEyeColor ->
                        onIntent(
                            QrStudioIntent.AppearanceChanged(
                                state.appearance.copy(
                                    eyeStyle = state.appearance.eyeStyle.copy(colour = newEyeColor),
                                ),
                            ),
                        )
                    },
                )
            }

            SuggestionChip(
                onClick = { onIntent(QrStudioIntent.AppearanceReset) },
                label = { Text("Reset to defaults") },
                modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.fillMaxSize().background(state.appearance.background.toComposeColor()),
                        appearance = state.appearance,
                        logo = state.logo,
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

@Composable
private fun ColorSliders(
    color: Contrast.Rgb,
    onColorChanged: (Contrast.Rgb) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Red slider
        Text(
            "R: ${(color.r * 255).toInt()}",
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = color.r,
            onValueChange = { r -> onColorChanged(color.copy(r = r)) },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )

        // Green slider
        Text(
            "G: ${(color.g * 255).toInt()}",
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = color.g,
            onValueChange = { g -> onColorChanged(color.copy(g = g)) },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )

        // Blue slider
        Text(
            "B: ${(color.b * 255).toInt()}",
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = color.b,
            onValueChange = { b -> onColorChanged(color.copy(b = b)) },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )

        // Preview swatch
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(color.toComposeColor())
                .border(1.dp, MaterialTheme.colorScheme.outline),
        )
    }
}

private fun Contrast.Rgb.toComposeColor(): Color =
    Color(red = r, green = g, blue = b)
