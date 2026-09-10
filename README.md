# QR Studio SG

Generate Singapore **PayNow** payment QR codes, with optional branding, from a single
Compose Multiplatform codebase targeting Android, iOS, desktop and the web.

No accounts. No backend. No persistence. Everything runs on-device and offline.

> **Status: in development.** The payload core, QR encoder, Compose renderer, branding
> and appearance controls are complete and tested. The desktop app runs with live preview
> and real-time colour/shape/logo updates. The web (Wasm) build compiles and serves, but
> its interactivity has not been confirmed in an ordinary browser — see the note under
> [Running the web target](#running-the-web-wasm-target). Export (PNG/SVG) is not built
> yet. See [Roadmap](#roadmap).

---

## ⚠️ This app generates real payment instruments

A wrong digit sends money to the wrong recipient. The `payload/` module is treated
accordingly: golden vectors, a permanent anti-vector regression guard, a property test,
and a cross-check against a completely independent EMVCo implementation.

**Manual bank verification is mandatory before any release.** Automated decoding proves a
QR is *readable*, not that a bank *accepts* it.

## Disclaimer

This is an independent tool. It is **not affiliated with, endorsed by, or connected to**
any bank, the Association of Banks in Singapore (ABS), IMDA, or MAS. You are responsible
for verifying recipient details before sharing or using any generated code.

Codes produced by this app are **PayNow-scheme codes**. They are not "SGQR" labels — a
real SGQR label requires acquirer registration, an SGQR ID, and the printed multi-scheme
label. If you are a business displaying a payment QR at a physical counter, obtain an
SGQR label from your bank or acquirer rather than self-generating one.

---

## Project layout

```
payload/      EMVCo TLV builder, CRC-16, validators, parser              — pure Kotlin
qr/           QR encoder, module matrix, mask selection, appearance      — pure Kotlin
              (colour contrast, module/eye shapes, logo backing plate)
composeApp/   Compose Multiplatform UI, renderer, live preview, branding
              (Android, iOS, desktop, web/Wasm; export not yet built)
```

`payload/` and `qr/` are standalone Gradle modules with no dependency on the app. Either
can be extracted and published as a library without touching anything else.

## The payload core

The PayNow merchant account template is **tag 26**, with expiry at subtag `04` and the
reference number in field `62·01`.

This matters. The only existing Kotlin Multiplatform PayNow library places PayNow at
**tag 36**, with expiry at `05` and the reference inside the template. That output is
structurally valid EMVCo with a correct checksum — and no bank application will ever find
it, because they look under tag 26. Five independent implementations (JavaScript, PHP, Go,
Python, TypeScript) all converge on 26.

A payload in the tag-36 layout is kept in the test suite as a permanent **anti-vector**:
it must parse as valid EMVCo *and* fail PayNow detection. If that test ever flips, the app
has started producing codes that pay nobody.

### Verification

| Check | What it proves |
|---|---|
| Golden vectors A, B, C | Byte-exact reproduction, including the `0FC5` zero-padded CRC case |
| Vector C | Byte-for-byte agreement with an independent third-party implementation |
| Anti-vector | Tag-36 payloads are rejected, permanently |
| Property test (500 configs) | Every field round-trips through an independent parser |
| **TC-04 cross-check** | A separate Java EMVCo library agrees field-for-field on 200+ payloads |
| Kotlin/Native test run | The shared code behaves identically off the JVM |

Run them:

```bash
./gradlew :payload:jvmTest                 # includes the TC-04 cross-check
./gradlew :payload:iosSimulatorArm64Test   # the same suite on Kotlin/Native
./gradlew :qr:jvmTest                      # encode -> render -> decode, via ZXing
./gradlew :composeApp:desktopTest          # renders the real composable, then decodes it
./gradlew :composeApp:run                  # the desktop app, with live preview
```

### Running the web (Wasm) target

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # dev server, hot reload, http://localhost:8082
./gradlew :composeApp:wasmJsBrowserDistribution      # production bundle, for GitHub Pages (CD-03)
```

The production bundle lands in `composeApp/build/dist/wasmJs/productionExecutable/` and
can be served with anything static, e.g. `python3 -m http.server` from that directory.

There is no separate Kotlin/JS target — `wasmJs()` is the only web target this project
builds (§9.1 specifies Wasm, not plain JS), and it is what those two tasks produce.

> **Known issue, unresolved:** in this repository's automated browser-testing session,
> the compiled Wasm app loaded and painted a correct first frame with zero console errors,
> but the `<canvas id="ComposeTarget">` reported a 0×0 layout box to DOM measurement and
> did not visibly respond to clicks or scrolling, on both the dev server and the
> production build. Compose Web for Wasm renders through an `OffscreenCanvas`, which is a
> combination known to confuse some Chromium automation paths — so this may be an artifact
> of that automated session rather than a real defect, but it was **not verified working
> in an ordinary browser tab**. Confirm interactivity manually (open the dev server URL in
> a normal Chrome window and try entering a mobile number) before trusting the web build.

### The QR encoder

`qrcode-kotlin` does the Reed-Solomon and data placement. Two things it does not do are
done here: it hardcodes mask pattern 000 and performs no mask selection, so all eight are
scored with the ISO/IEC 18004 penalty rules and the best is kept; and its output carries
no quiet zone, so the mandatory four-module border is added and cannot be reduced.

Two of its traps are guarded by tests. Its `ErrorCorrectionLevel` names mislead — `HIGH`
is really Q and `VERY_HIGH` is the real H, so a forced "H" for a logo would silently get
25% recovery instead of 30%. And its default symbol sizing is computed from the *inferred*
data type while we force byte mode, which overflows for all-uppercase payloads.

### Deviations from the reference implementation

The payload builder is a Kotlin port of [`DHCertainty/PaynowQR`][ref] with deliberate
corrections. These are requirements, not preferences — please do not "fix" them back.

| # | Reference behaviour | This implementation |
|---|---|---|
| D1 | Proxy type hardcoded to UEN | User selects mobile or UEN |
| D2 | Amount emitted unformatted (`500`) | Two decimal places (`500.00`) |
| D3 | Point of initiation always `12` | `11` static / `12` dynamic |
| D4 | Throws above code point 255 | Validates and explains; never throws at the user |
| D5 | No length caps | 25 characters on name and reference |
| D6 | No UEN or mobile validation | Full validation, with warnings where formats evolve |
| D7 | Depends on `dayjs` | `kotlinx-datetime` |
| D8 | Mutates the caller's options | Immutable data class in, string out |
| D9 | Emits `54010` (a zero amount) when no amount is set | Omits tag 54 entirely |

**D9 is the one open question.** EMVCo treats the transaction amount as conditional —
absent when the payer will enter it — and a zero amount falls outside the valid range.
Omitting the field is believed correct, and is flagged for confirmation during bank
testing.

## Roadmap

- [x] Payload core: TLV builder, CRC-16, validation, parser, anti-vector guard
- [x] Independent cross-check (TC-04) and Kotlin/Native verification
- [x] CI across Android, JVM, Wasm and iOS
- [x] QR encoding and module matrix, with ISO 18004 mask selection
- [x] Compose renderer and live preview, verified by decoding the rendered output
- [x] Input UI with validation surfacing (Pay To, Payment sections)
- [x] Appearance and colour-contrast safety (WCAG 2.x verdicts, FR-402 export gate)
- [x] Branding logo composition (size 8–30%, shapes, backing plate, §9.3 image picker deferred)
- [ ] PNG and SVG export with platform actuals
- [ ] Export self-verification (decode-and-compare before enabling export)
- [ ] Image picker integration for real logo uploads (§9.3, Android/iOS/desktop/web)
- [ ] Release workflow, web deployment
- [ ] **Manual bank verification** — blocking for release

## Building and Running

Requires **JDK 17+**. Android SDK is needed for Android targets; Xcode for iOS.

> **Note:** Gradle cannot build from a path containing a colon (`:`) — it is the JVM
> classpath separator, and class loading fails. Keep this repository in a colon-free path.

### Testing all modules

```bash
# Payload module (EMVCo/PayNow core)
./gradlew :payload:jvmTest                   # JVM tests + TC-04 cross-check
./gradlew :payload:iosSimulatorArm64Test     # Kotlin/Native verification on iOS simulator
./gradlew :payload:androidUnitTest           # Android unit tests

# QR module (encoder, matrix, renderer)
./gradlew :qr:jvmTest                        # JVM: encode → render → decode via ZXing
./gradlew :qr:androidUnitTest                # Android unit tests

# Compose app tests (UI and integration)
./gradlew :composeApp:desktopTest            # Desktop: renders the real composable, decodes it
./gradlew :composeApp:testDebugUnitTest      # Android unit tests
```

### Desktop (Compose Desktop / JVM)

```bash
# Run the app with live preview
./gradlew :composeApp:run

# Build a distributable JAR
./gradlew :composeApp:packageDistributionForCurrentOS

# Run tests
./gradlew :composeApp:desktopTest
```

The desktop app launches with:
- Full Branding and Appearance controls
- Live QR preview updating in real-time as you adjust colours, shapes, and logo
- Module shape selector (Square/Rounded/Dot)
- Eye style customization
- WCAG contrast verdict with FR-402 export gating
- Colour sliders with RGB controls and preview swatches

### Android

```bash
# Build debug APK
./gradlew :composeApp:assembleDebug

# Build release APK
./gradlew :composeApp:assembleRelease

# Install and run on connected device/emulator
./gradlew :composeApp:installDebug
./gradlew :composeApp:run  # Also works for Android when a device is connected

# Run tests
./gradlew :composeApp:testDebugUnitTest
```

The Android app:
- Responsive layout (single column on phones, split view on tablets)
- All Branding and Appearance features (image picker button deferred to §9.3)
- Offline: no network calls, everything runs on-device

### iOS

Requires Xcode and an iOS development certificate.

```bash
# Build and run on iOS simulator
./gradlew :composeApp:iosSimulatorArm64Test  # Also builds the app
open composeApp/build/ios/iosSimulatorArm64App.app

# Or use Xcode directly (requires setup)
cd composeApp
pod install
open composeApp.xcworkspace
# Then build and run from Xcode
```

### Web (Kotlin/Wasm)

```bash
# Dev server with hot reload
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
# Open http://localhost:8080 in your browser

# Production bundle (for static hosting)
./gradlew :composeApp:wasmJsBrowserDistribution
# Output: composeApp/build/dist/wasmJs/productionExecutable/

# Serve production build locally
cd composeApp/build/dist/wasmJs/productionExecutable/
python3 -m http.server 8000
# Open http://localhost:8000 in your browser
```

> ⚠️ **Known issue:** The Wasm build's interactivity has not been verified in an ordinary
> browser tab. In automated testing, it loaded and painted correctly but did not respond
> to clicks. See [the note below](#running-the-web-wasm-target) for details. Manual
> verification in a real browser is needed.

### Usage: All Sections

#### Pay To (expanded by default)
- Proxy type: Mobile (9-digit) or UEN (8-digit with dash)
- Recipient identifier
- Merchant name (max 25 chars)

#### Payment
- Amount (SGD, optional for payer entry)
- Toggle: allow payer to enter amount
- Reference (max 20 chars, optional)
- Auto-calculated expiry quick-pick chips

#### Branding (collapsed by default)
- **Logo enabled:** toggle to add a centre logo
- **Logo size:** 8–30% of QR width (warning above 25%)
- **Logo shape:** Square, Rounded, or Circle backing plate
- **Image picker:** placeholder button, ready for §9.3 ImagePicker integration
  - Currently uses a stand-in mark so size/shape/placement mechanics are real
  - Real image picker will be wired when platform integrations (FileDialog, etc.) are added

#### Appearance (collapsed by default)
- **Foreground colour:** RGB sliders for the QR code (black by default)
- **Background colour:** RGB sliders for the background (white by default)
- **Contrast verdict:** ✓ OK / ⚠ WARNING / ❌ BLOCKED
  - Displays WCAG 2.x contrast ratio
  - BLOCKED state (< 3:1) prevents export (FR-402)
  - WARNING state (3:1 – 4.5:1) allows export with caution notice
- **Module shape:** Square, Rounded (rounded corners), or Dot (circles)
- **Eye style:** Independent shape selector for finder patterns (not tied to module style)
- **Eye colour:** Toggle to use module colour or pick a custom colour via RGB sliders
- **Reset to defaults:** One-click restore to black-on-white, square modules, no logo

#### Export Bar
- (Not yet implemented) Download button (primary pill)
- (Not yet implemented) Share link (secondary text)

The QR preview updates live as you edit any field — see the effect instantly.

## Licence

[Apache-2.0](LICENSE). This project is a derivative work of [`DHCertainty/PaynowQR`][ref]
(Apache-2.0); see [NOTICE](NOTICE) for attribution. The original approach derives from a
public gist by chengkiang.

Third-party components: [`qrcode-kotlin`][qr] (MIT) for QR encoding, and
[`mvallim/emv-qrcode`][emv] (Apache-2.0) as a test-only oracle — the latter never ships
in the app.

[ref]: https://github.com/DHCertainty/PaynowQR
[qr]: https://github.com/g0dkar/qrcode-kotlin
[emv]: https://github.com/mvallim/emv-qrcode
