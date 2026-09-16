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

A real Next.js (App Router) app — not just a script. A form (Pay to / Payment, mirroring
the Compose app's own sections) drives a debounced Server Action that calls straight into
the published packages' `js` facades and returns a PayNow payload plus a QR module
matrix, rendered live on a `<canvas>`. Validation errors and warnings come straight from
`:payload`'s real `Validation` object — nothing about them is reimplemented in
TypeScript. The design borrows Adyen's public visual language (Malachite green accent,
Midnight navy ink, restrained card/border-based layout) as a stand-in fintech aesthetic —
see `src/app/globals.css` for the palette and the comment on why it's not Adyen's actual
design system or assets.

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
inline ("A Singapore mobile number has 8 digits…" / UEN-specific messages) with no
console errors. Same payload string as the iOS demo for the same inputs.
