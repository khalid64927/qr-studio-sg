package sg.qrstudio.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * A Material 3 [ColorScheme] built from the same Adyen-inspired fintech palette as
 * `demo/web` (see that project's `src/app/globals.css`) — Malachite green (`#0ABF53`)
 * as the one accent colour, Midnight navy (`#00112C`) for ink, everything else neutral
 * gray/white. Not Adyen's actual design system or any of its assets — the same
 * restrained, high-contrast look, applied consistently across both the web demo and
 * this app so the two read as one product family rather than two unrelated UIs.
 */
private val Accent = Color(0xFF0ABF53)
private val Ink = Color(0xFF00112C)

private val LightBackground = Color(0xFFF7F8F9)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFF0F2F4)
private val LightMuted = Color(0xFF5B6572)
private val LightOutline = Color(0xFFE2E5E9)
private val LightDanger = Color(0xFFD92D20)
private val LightDangerContainer = Color(0xFFFEF3F2)
private val LightWarning = Color(0xFFB54708)
private val LightWarningContainer = Color(0xFFFFFAEB)

private val DarkBackground = Color(0xFF0B1220)
private val DarkSurface = Color(0xFF121A2B)
private val DarkSurfaceVariant = Color(0xFF1B2536)
private val DarkInk = Color(0xFFE7EAEE)
private val DarkMuted = Color(0xFF9AA4B2)
private val DarkOutline = Color(0xFF2A3344)
private val DarkDanger = Color(0xFFF29187)
private val DarkDangerContainer = Color(0xFF3A1512)
private val DarkWarning = Color(0xFFE8A45C)
private val DarkWarningContainer = Color(0xFF3A2A12)

val QrStudioLightColorScheme: ColorScheme =
    lightColorScheme(
        primary = Accent,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDFF6E7),
        onPrimaryContainer = Ink,
        secondary = Ink,
        onSecondary = Color.White,
        background = LightBackground,
        onBackground = Ink,
        surface = LightSurface,
        onSurface = Ink,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightMuted,
        outline = LightOutline,
        outlineVariant = LightOutline,
        error = LightDanger,
        onError = Color.White,
        errorContainer = LightDangerContainer,
        onErrorContainer = LightDanger,
        tertiary = LightWarning,
        onTertiary = Color.White,
        tertiaryContainer = LightWarningContainer,
        onTertiaryContainer = LightWarning,
    )

val QrStudioDarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = Accent,
        onPrimary = Color.White,
        primaryContainer = Color(0xFF123723),
        onPrimaryContainer = Color(0xFFB7EFC8),
        secondary = DarkInk,
        onSecondary = DarkBackground,
        background = DarkBackground,
        onBackground = DarkInk,
        surface = DarkSurface,
        onSurface = DarkInk,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkMuted,
        outline = DarkOutline,
        outlineVariant = DarkOutline,
        error = DarkDanger,
        onError = Color(0xFF3A0A06),
        errorContainer = DarkDangerContainer,
        onErrorContainer = DarkDanger,
        tertiary = DarkWarning,
        onTertiary = Color(0xFF3A2A12),
        tertiaryContainer = DarkWarningContainer,
        onTertiaryContainer = DarkWarning,
    )
