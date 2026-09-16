// swift-tools-version:5.9
import PackageDescription

// Binary distribution of :payload and :qr for native iOS/Swift apps — see
// .github/workflows/publish.yml and README's "Publishing" section for how these
// XCFrameworks are built and released, and why the checksums below are placeholders
// until the first tagged release.
//
// A SwiftUI (or UIKit) app adds this package directly:
//
//     .package(url: "https://github.com/khalid64927/qr-studio-sg", from: "0.1.0")
//
// and links whichever product(s) it needs — PayNowPayloadKit for the EMVCo/PayNow
// payload builder, QrStudioQrKit for the QR encoder and module matrix. Neither product
// depends on Compose Multiplatform or any UI framework; rendering is entirely up to the
// consuming app (SwiftUI Canvas, UIKit, etc).
let releaseVersion = "0.1.0"
let releaseTag = "v\(releaseVersion)"
let releaseURL = "https://github.com/khalid64927/qr-studio-sg/releases/download/\(releaseTag)"

let package = Package(
    name: "QRStudioSG",
    platforms: [.iOS(.v14)],
    products: [
        .library(name: "PayNowPayloadKit", targets: ["PayNowPayloadKit"]),
        .library(name: "QrStudioQrKit", targets: ["QrStudioQrKit"]),
    ],
    targets: [
        .binaryTarget(
            name: "PayNowPayloadKit",
            url: "\(releaseURL)/PayNowPayloadKit.xcframework.zip",
            // PLACEHOLDER — no release has been cut yet. The publish workflow prints the
            // real checksum after building the XCFramework; paste it in here and open a
            // PR before this file works. `swift package compute-checksum <zip>` computes
            // it by hand if needed.
            checksum: "0000000000000000000000000000000000000000000000000000000000000000"
        ),
        .binaryTarget(
            name: "QrStudioQrKit",
            url: "\(releaseURL)/QrStudioQrKit.xcframework.zip",
            // PLACEHOLDER — see PayNowPayloadKit's target above.
            checksum: "0000000000000000000000000000000000000000000000000000000000000000"
        ),
    ]
)
