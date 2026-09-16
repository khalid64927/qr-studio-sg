// swift-tools-version:5.9
import PackageDescription

// A skeleton native-Swift consumer of PayNowPayloadKit and QrStudioQrKit — proof that a
// real iOS/SwiftUI app can build a PayNow payload and encode it into a QR module matrix
// using only these two libraries, with zero Compose Multiplatform involved.
//
// Points at the *locally built* XCFrameworks (path-based binaryTarget), not the
// repo-root Package.swift's GitHub Release URLs — this is a local dev/test harness, not
// itself a published package, so it never needs the real release checksums. Build the
// XCFrameworks first:
//
//     ./gradlew :payload:assemblePayNowPayloadKitReleaseXCFramework \
//               :qr:assembleQrStudioQrKitReleaseXCFramework
//
// The XCFrameworks only carry iOS device/simulator slices (no macOS slice — this repo
// doesn't target macOS), so this package only builds for iOS, and only through Xcode's
// toolchain rather than plain `swift run` (which builds for the host Mac by default):
//
//     xcodebuild build -scheme PayNowDemoCLI -destination 'generic/platform=iOS Simulator'
//
// A clean build is the actual test here: it proves the generated Swift API (from
// Kotlin's Objective-C export) type-checks and links against real Swift call sites, the
// same as it would in a full SwiftUI app. See README.md in this directory for a captured
// build log and what it demonstrates.
let package = Package(
    name: "PayNowDemoCLI",
    platforms: [.iOS(.v15)],
    targets: [
        .executableTarget(
            name: "PayNowDemoCLI",
            dependencies: ["PayNowPayloadKit", "QrStudioQrKit"]
        ),
        // A real SwiftUI app — Pay to / Payment / Appearance, live QR preview — not just
        // a console script. See README.md for how it's packaged into a runnable .app
        // and screenshotted without Xcode.
        .executableTarget(
            name: "PayNowDemoApp",
            dependencies: ["PayNowPayloadKit", "QrStudioQrKit"]
        ),
        .binaryTarget(
            name: "PayNowPayloadKit",
            path: "../../payload/build/XCFrameworks/release/PayNowPayloadKit.xcframework"
        ),
        .binaryTarget(
            name: "QrStudioQrKit",
            path: "../../qr/build/XCFrameworks/release/QrStudioQrKit.xcframework"
        ),
    ]
)
