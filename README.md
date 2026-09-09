# QR Studio SG

Generate Singapore **PayNow** payment QR codes, with optional branding, from a single
Compose Multiplatform codebase targeting Android, iOS, desktop and the web.

No accounts. No backend. No persistence. Everything runs on-device and offline.

> **Status: in development.** The payload core, QR encoder and Compose renderer are
> complete and tested, and the desktop app runs with a live preview. Branding, appearance
> controls and export are not built yet. See [Roadmap](#roadmap).

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
payload/      EMVCo TLV builder, CRC-16, validators, parser  — pure Kotlin, no UI deps
qr/           QR encoder, module matrix, mask selection       — pure Kotlin, no UI deps
composeApp/   Compose UI, renderer, live preview  (export and branding still to come)
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
- [ ] Input UI with validation surfacing
- [ ] Appearance and colour-contrast safety
- [ ] Branding logo composition
- [ ] PNG and SVG export with platform actuals
- [ ] Export self-verification (decode-and-compare before enabling export)
- [ ] Release workflow, web deployment
- [ ] **Manual bank verification** — blocking for release

## Building

Requires JDK 17. The Android SDK is needed for Android targets; Xcode for iOS.

```bash
./gradlew :payload:jvmTest
```

> **Note:** Gradle cannot build from a path containing a colon (`:`) — it is the JVM
> classpath separator, and class loading fails. Keep this repository in a colon-free path.

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
