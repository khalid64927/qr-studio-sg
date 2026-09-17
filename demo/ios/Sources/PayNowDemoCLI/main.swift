import Foundation
import PayNowPayloadKit
import QrStudioQrKit

// Skeleton native-Swift consumer: builds a PayNow QR the same way a real SwiftUI screen
// would — no Compose Multiplatform, no :ui or :composeApp, just the two published
// libraries' generated Swift API. Exercises all three PayNow proxy types.

struct ProxyExample {
    let label: String
    let proxyType: ProxyType
    let proxyValue: String
}

let examples = [
    ProxyExample(label: "Mobile", proxyType: .mobile, proxyValue: "91234567"),
    ProxyExample(label: "NRIC/FIN", proxyType: .nric, proxyValue: "S1234567D"),
    ProxyExample(label: "UEN", proxyType: .uen, proxyValue: "201403121W"),
]

let today = Kotlinx_datetimeLocalDate(year: 2026, month: 9, day: 17)

for example in examples {
    print("--- \(example.label) ---")

    let config = PayNowConfig(
        proxyType: example.proxyType,
        proxyValue: example.proxyValue,
        amount: "25.50",
        amountEditable: false,
        expiry: nil,
        reference: "INV-DEMO-001",
        merchantName: "Demo Merchant"
    )

    let result = PayNowPayloadBuilder.shared.build(config: config, today: today)

    switch result {
    case let success as PayloadResultSuccess:
        let payload = success.payload
        print("Built payload:", payload.raw)
        print("Normalised proxy:", payload.proxyDisplay)

        let matrix = QrEncoder.shared.encode(payload: payload.raw, errorCorrection: .medium)
        print("QR module matrix: \(matrix.size)x\(matrix.size), mask pattern \(matrix.maskPattern)")

        var darkCount = 0
        for y in 0..<Int(matrix.size) {
            for x in 0..<Int(matrix.size) {
                if matrix.isDark(x: Int32(x), y: Int32(y)) { darkCount += 1 }
            }
        }
        print("Dark modules:", darkCount)

    case let invalid as PayloadResultInvalid:
        print("Invalid config:", invalid.errors.map { $0.message })

    default:
        fatalError("Unreachable: PayloadResult has exactly two cases")
    }

    print("")
}
