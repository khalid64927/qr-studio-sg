# Native-UI demos

Proof that `:payload` and `:qr` work as standalone libraries for a **native-UI** app —
SwiftUI, Node/React, Jetpack Views — with zero Compose Multiplatform involved. See the
root [README's Publishing section](../README.md#publishing) for what's actually
published and why.

Each demo builds a PayNow payload for the same inputs and encodes it into a QR module
matrix, so the outputs below are directly comparable across platforms.

| Platform | Skeleton | Consumes |
|---|---|---|
| iOS | `ios/` — a Swift Package executable target | the locally-built XCFrameworks |
| Web | `web/` — a real Next.js app | the locally-built npm package dirs, via a Server Action |
| Android | *(none — see below)* | `composeApp` already depends on `:payload`/`:qr` directly |

**Android has no skeleton here.** `composeApp` (repo root) already builds against
`:payload` and `:qr` as ordinary Gradle module dependencies — that's a real, continuously
tested Android consumer already, just not of the *published* AAR. Standing up a second,
separate native-Views Android app whose only job is to prove the AAR resolves would test
Gradle's Maven publishing plumbing, not the libraries — and that plumbing was already
verified directly (see the root README: `publishAndroidReleasePublicationToMavenLocal`
produces a real, correctly-coordinated AAR).

## iOS

```bash
# From the repo root — builds the XCFrameworks these demos link against
./gradlew :payload:assemblePayNowPayloadKitReleaseXCFramework \
          :qr:assembleQrStudioQrKitReleaseXCFramework

cd demo/ios
```

**If your Xcode has the iOS Simulator platform installed** (Xcode > Settings >
Components), the normal path works:

```bash
xcodebuild build -scheme PayNowDemoCLI -destination 'generic/platform=iOS Simulator'
```

**If it doesn't** (this is how it was actually verified in this repo's dev environment —
SDKs and simulator *runtimes* were present, but not the Xcode iOS *platform* component
xcodebuild's destination matching requires), `swift build` can cross-compile directly
against the simulator SDK, bypassing that requirement entirely:

```bash
swift build --triple arm64-apple-ios17.0-simulator \
  --sdk "$(xcrun --sdk iphonesimulator --show-sdk-path)"
```

Either way, a **successful build is the actual test**: it proves Kotlin/Native's
generated Objective-C/Swift API (`PayNowConfig`, `PayNowPayloadBuilder.shared.build`,
`QrEncoder.shared.encode`, `Kotlinx_datetimeLocalDate`, …) type-checks and links against
real Swift call sites — no `@JsExport`-style facade was needed here, unlike the web demo,
because Kotlin/Native's Objective-C export handles `LocalDate` and the sealed
`PayloadResult` natively.

To actually run it and see output (this repo's dev environment has real simulators
installed even without the Xcode *platform* component, so this worked end-to-end):

```bash
xcrun simctl boot <device-udid>          # xcrun simctl list devices available
xcrun simctl spawn <device-udid> .build/arm64-apple-ios-simulator/debug/PayNowDemoCLI
```

Captured output from exactly that:

```
Built payload: 00020101021226500009SG.PAYNOW010100211+659123456703010040820310916520400005303702540525.505802SG5913Demo Merchant6009Singapore62160112INV-DEMO-00163048F64
Normalised proxy: +65 9123 4567
QR module matrix: 61x61, mask pattern 2
Dark modules: 1362
```

## Web

A real Next.js (App Router) app — not just a script. Four sections mirror the Compose
app's own (Pay to / Payment / Branding / Appearance), each driving a debounced Server
Action:

- **Pay to / Payment** — proxy type, amount, reference, etc. — calls the real
  `PayNowPayloadBuilder`/`Validation` through `buildPayNowQr`. Errors and warnings shown
  are exactly what `:payload` returned; nothing is reimplemented in TypeScript.
- **Branding** — a centre logo toggle, size slider (bounds fetched from
  `logoSizeBounds()`, not hardcoded), and shape (square/rounded/circle), same mechanics
  and same "no real image upload yet" placeholder as the Compose app itself.
- **Appearance** — foreground/background/eye colour pickers and independent module/eye
  shape (square/rounded/dot). The WCAG contrast floor (FR-402/FR-407: blocked below 3:1,
  warned below 4.5:1) is checked by calling `checkContrast()` — the *same* `Contrast`
  object the Compose export gate uses — not reimplemented luminance math in JS.

`QrCanvas.tsx` ports `sg.qrstudio.app.ui.drawQrMatrix` (the Compose renderer) to Canvas
2D directly — same whole-pixel-flooring rule, same logo-exclusion-area formula, same
per-shape corner-radius/dot-radius constants — so a customization looks the same on both
platforms, not just structurally equivalent. The design borrows Adyen's public visual
language (Malachite green accent, Midnight navy ink, restrained card/border-based
layout) as a stand-in fintech aesthetic — see `src/app/globals.css` for the palette and
the comment on why it's not Adyen's actual design system or assets.

Screenshots — captured live from `npm run build && npm run start`, same inputs as the
iOS demo, [`../screenshots/`](../screenshots/):

| | | |
|---|---|---|
| [![Navy, rounded modules, dot eyes, logo](../screenshots/paynow-qr-navy-rounded-dot-logo.jpg)](../screenshots/paynow-qr-navy-rounded-dot-logo.jpg) | [![Malachite green dots, navy eyes, circle logo](../screenshots/paynow-qr-green-dot-circle-logo.jpg)](../screenshots/paynow-qr-green-dot-circle-logo.jpg) | [![Contrast blocked warning](../screenshots/paynow-qr-contrast-blocked-warning.jpg)](../screenshots/paynow-qr-contrast-blocked-warning.jpg) |
| Navy, rounded modules, dot eyes, logo | Malachite green dots, navy eyes, circle logo | Contrast blocked — FR-402 |

```bash
cd demo/web
./setup-local-packages.sh   # builds :payload/:qr, packs and installs them locally
npm run dev                 # http://localhost:3000
# or: npm run build && npm run start
```

`setup-local-packages.sh` runs the same `jsNodeProductionLibraryDistribution` Gradle
tasks as the other demos, then `npm pack`s each output directory into a real `.tgz` and
installs *that* — deliberately not a `file:`-directory dependency. npm symlinks a
`file:`-directory dependency, and Node resolves `require()` inside a symlinked package
relative to its *real* path, which is outside this app's `node_modules` entirely; a
`file:`-*tarball* dependency, like a registry install, extracts a real copy instead, so
none of that applies here. (The earlier CLI-only version of this demo used a plain `file:`
directory + `node --preserve-symlinks` to work around exactly this — a real Next.js build
has no equivalent flag, which is what prompted switching to tarballs.) Re-run the script
after any change to `:payload` or `:qr`.

Verified with a real browser session against `npm run build && npm run start`: typing a
mobile number produces a live QR render, the raw EMVCo payload, and the normalised proxy
display; switching to UEN and typing an invalid one surfaces the real validation error
inline ("A Singapore mobile number has 8 digits…" / UEN-specific messages); changing
foreground/background/eye colour, module/eye shape and the logo (toggle, size, shape) all
re-render live and correctly, including the contrast banner flipping to OK/WARNING/BLOCKED
in real time as colours change. No console errors. Same payload string as the iOS demo
for the same inputs.

`:qr`'s `js` facade (`qr/src/jsMain/kotlin/…/js/QrEncoderJs.kt`) grew three additions for
this: `QrModuleMatrixJs.isFinder(x, y)` (which modules are eyes, for `eyeStyle` to apply
to), `checkContrast(...)` (wraps `Contrast.ratio`/`Contrast.verdict`), and
`logoSizeBounds()` (wraps `LogoConfig`'s size-fraction constants). All three are plain
functions/classes, not top-level `@JsExport val`s or an exported `object` — both of those
looked correct in Kotlin but produced a broken or awkward-to-consume `.d.ts` in practice
(see the comments in that file for what was actually tried and why it didn't work).
