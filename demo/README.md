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
| Web | `web/` — a plain Node script | the locally-built npm package dirs |
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

```bash
# From the repo root — builds the npm package directories these demos link against
./gradlew :payload:jsNodeProductionLibraryDistribution :qr:jsNodeProductionLibraryDistribution

cd demo/web
npm install
npm run demo
```

`package.json` depends on the two packages via `file:` paths into
`*/build/dist/js/productionLibrary`, not the registry — this is a local dev/test harness
for code that isn't published yet, same reasoning as the iOS demo's path-based
`binaryTarget`.

Two things only apply to the `file:`-path local setup, not a real `npm install` from the
registry:

- `@js-joda/core` (kotlinx-datetime's own npm dependency) is listed directly in this
  `package.json` too. Node resolves `require()` inside a `file:`-linked package relative
  to that package's *real* location, not this demo's `node_modules` — so without this,
  requiring it from inside `payload/build/dist/js/productionLibrary` fails to find a
  copy. A registry install wouldn't hit this: npm installs a package's own declared
  dependencies alongside it regardless.
- `npm run demo` passes `node --preserve-symlinks` for the same reason — without it, the
  same resolution behavior applies to the packages' own top-level modules.

Captured output — identical to the iOS demo's, same inputs:

```
Built payload: 00020101021226500009SG.PAYNOW010100211+659123456703010040820310916520400005303702540525.505802SG5913Demo Merchant6009Singapore62160112INV-DEMO-00163048F64
Normalised proxy: +65 9123 4567
QR module matrix: 61x61, mask pattern 2
Dark modules: 1362
```
