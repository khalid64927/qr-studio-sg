import SwiftUI

// The same Adyen-inspired palette used in demo/web/src/app/globals.css and
// ui/src/commonMain/.../QrStudioTheme.kt — Malachite green accent, Midnight navy ink.
// Not Adyen's actual design system or assets; see those files' comments for why.
extension Color {
    static let adyenAccent = Color(red: 0x0A / 255, green: 0xBF / 255, blue: 0x53 / 255)
    static let adyenInk = Color(red: 0x00 / 255, green: 0x11 / 255, blue: 0x2C / 255)
    static let adyenBackground = Color(red: 0xF7 / 255, green: 0xF8 / 255, blue: 0xF9 / 255)
    static let adyenSurface = Color.white
    static let adyenBorder = Color(red: 0xE2 / 255, green: 0xE5 / 255, blue: 0xE9 / 255)
    static let adyenMuted = Color(red: 0x5B / 255, green: 0x65 / 255, blue: 0x72 / 255)
    static let adyenDanger = Color(red: 0xD9 / 255, green: 0x2D / 255, blue: 0x20 / 255)
    static let adyenDangerBg = Color(red: 0xFE / 255, green: 0xF3 / 255, blue: 0xF2 / 255)
}

extension View {
    /// A text-field look matching the web demo's inputs — flat, bordered, no default
    /// list-row chrome. Apply with `.textFieldStyle(.plain)` first.
    func adyenFieldStyle() -> some View {
        padding(10)
            .background(Color.adyenBackground)
            .cornerRadius(8)
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.adyenBorder))
    }
}
