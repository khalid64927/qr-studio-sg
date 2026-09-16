import SwiftUI

// The real SwiftUI counterpart to demo/web — Pay to / Payment / Appearance, a live
// Canvas-based QR preview, Adyen-inspired theming — built on the exact same
// PayNowPayloadKit / QrStudioQrKit XCFrameworks as PayNowDemoCLI, just driving them from
// an interactive UI instead of a fixed console loop. See ../../README.md for how this is
// built and packaged into a runnable .app without Xcode's iOS platform component.
@main
struct PayNowDemoApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
