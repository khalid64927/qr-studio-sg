import SwiftUI
import PayNowPayloadKit
import QrStudioQrKit

/// Pay to / Payment / Appearance — a native counterpart to demo/web's page.tsx, driving
/// the exact same PayNowPayloadBuilder/QrEncoder Kotlin API (via Objective-C export, no
/// facade needed — see ../../README.md) instead of a Server Action. Scoped smaller than
/// the web demo: no Branding/logo section and eye colour/shape follow the module shape
/// rather than having independent controls, since the goal here is proving the shared
/// libraries drive a real interactive native UI, not re-deriving every web feature.
struct ContentView: View {
    enum ProxyOption: String, CaseIterable, Identifiable {
        case mobile = "Mobile"
        case nric = "NRIC/FIN"
        case uen = "UEN"
        var id: String { rawValue }

        var kotlinType: ProxyType {
            switch self {
            case .mobile: return .mobile
            case .nric: return .nric
            case .uen: return .uen
            }
        }

        var placeholder: String {
            switch self {
            case .mobile: return "9123 4567"
            case .nric: return "S1234567D"
            case .uen: return "201403121W"
            }
        }
    }

    enum ShapeOption: String, CaseIterable, Identifiable {
        case square = "Square", rounded = "Rounded", dot = "Dot"
        var id: String { rawValue }
    }

    @State private var proxyOption: ProxyOption = .mobile
    @State private var proxyValue = ""
    @State private var merchantName = "Demo Merchant"
    @State private var amount = "25.50"
    @State private var amountEditable = false
    @State private var reference = "INV-DEMO-001"

    @State private var foreground = Color.adyenInk
    @State private var background = Color.white
    @State private var moduleShape: ShapeOption = .square

    @State private var raw: String?
    @State private var proxyDisplay: String?
    @State private var matrix: ModuleMatrix?
    @State private var errors: [String] = []

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                header
                qrCard
                payToCard
                paymentCard
                appearanceCard
            }
            .padding(20)
        }
        .background(Color.adyenBackground.ignoresSafeArea())
        .onAppear(perform: generate)
        .onChange(of: proxyOption) { _ in proxyValue = ""; generate() }
        .onChange(of: proxyValue) { _ in generate() }
        .onChange(of: merchantName) { _ in generate() }
        .onChange(of: amount) { _ in generate() }
        .onChange(of: amountEditable) { _ in generate() }
        .onChange(of: reference) { _ in generate() }
        .onChange(of: foreground) { _ in generate() }
        .onChange(of: background) { _ in generate() }
        .onChange(of: moduleShape) { _ in generate() }
    }

    private var header: some View {
        HStack(spacing: 10) {
            RoundedRectangle(cornerRadius: 3)
                .fill(Color.adyenAccent)
                .frame(width: 6, height: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text("PayNow QR Demo").font(.title2.bold()).foregroundColor(.adyenInk)
                Text("Native SwiftUI — PayNowPayloadKit + QrStudioQrKit")
                    .font(.caption).foregroundColor(.adyenMuted)
            }
        }
    }

    private var qrCard: some View {
        Card {
            VStack(spacing: 12) {
                if let matrix, !proxyValue.trimmingCharacters(in: .whitespaces).isEmpty, errors.isEmpty {
                    QrCanvasView(matrix: matrix, foreground: foreground, background: background, moduleShape: moduleShape)
                        .aspectRatio(1, contentMode: .fit)
                        .frame(maxWidth: 240)
                        .background(Color.white)
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.adyenBorder))
                } else {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.white)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.adyenBorder))
                        .frame(height: 240)
                        .overlay(
                            Text(errors.isEmpty
                                ? "Enter a \(proxyOption.rawValue.lowercased()) to see the QR code"
                                : "Fix the highlighted fields")
                                .font(.footnote).foregroundColor(.adyenMuted)
                                .multilineTextAlignment(.center).padding()
                        )
                }

                if let proxyDisplay, let raw {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("PROXY").font(.caption2).foregroundColor(.adyenMuted)
                        Text(proxyDisplay).font(.system(.footnote, design: .monospaced)).foregroundColor(.adyenInk)
                        Text("RAW PAYLOAD").font(.caption2).foregroundColor(.adyenMuted).padding(.top, 4)
                        Text(raw).font(.system(size: 9, design: .monospaced)).foregroundColor(.adyenMuted)
                            .padding(8).background(Color.adyenBackground).cornerRadius(8)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                if !errors.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(errors, id: \.self) { Text($0).font(.caption) }
                    }
                    .foregroundColor(.adyenDanger)
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.adyenDangerBg)
                    .cornerRadius(8)
                }
            }
        }
    }

    private var payToCard: some View {
        Card {
            SectionTitle("Pay to")
            Picker("Recipient type", selection: $proxyOption) {
                ForEach(ProxyOption.allCases) { Text($0.rawValue).tag($0) }
            }
            .pickerStyle(.segmented)

            LabeledField(label: proxyOption.rawValue) {
                TextField(proxyOption.placeholder, text: $proxyValue)
                    .textFieldStyle(.plain)
                    .adyenFieldStyle()
                    .autocapitalization(.allCharacters)
                    .disableAutocorrection(true)
            }
            LabeledField(label: "Name payers will see") {
                TextField("Shown to the payer", text: $merchantName)
                    .textFieldStyle(.plain)
                    .adyenFieldStyle()
            }
        }
    }

    private var paymentCard: some View {
        Card {
            SectionTitle("Payment")
            LabeledField(label: "Amount (SGD)") {
                TextField("Leave blank for any amount", text: $amount)
                    .keyboardType(.decimalPad)
                    .textFieldStyle(.plain)
                    .adyenFieldStyle()
            }
            Toggle(isOn: Binding(
                get: { amount.isEmpty || amountEditable },
                set: { amountEditable = $0 }
            )) {
                Text(amount.isEmpty ? "The payer enters the amount" : "Let the payer change the amount")
                    .font(.subheadline)
            }
            .disabled(amount.isEmpty)
            .tint(.adyenAccent)

            LabeledField(label: "Reference") {
                TextField("INV-2026-001", text: $reference)
                    .textFieldStyle(.plain)
                    .adyenFieldStyle()
            }
        }
    }

    private var appearanceCard: some View {
        Card {
            SectionTitle("Appearance")
            ColorPicker("Foreground colour", selection: $foreground)
            ColorPicker("Background colour", selection: $background)
            Picker("Module shape", selection: $moduleShape) {
                ForEach(ShapeOption.allCases) { Text($0.rawValue).tag($0) }
            }
            .pickerStyle(.segmented)
        }
    }

    private func generate() {
        let trimmed = proxyValue.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else {
            raw = nil; proxyDisplay = nil; matrix = nil; errors = []
            return
        }

        let config = PayNowConfig(
            proxyType: proxyOption.kotlinType,
            proxyValue: proxyValue,
            amount: amount.isEmpty ? nil : amount,
            amountEditable: amount.isEmpty ? true : amountEditable,
            expiry: nil,
            reference: reference.isEmpty ? nil : reference,
            merchantName: merchantName.isEmpty ? nil : merchantName
        )
        let today = Kotlinx_datetimeLocalDate(year: 2026, month: 9, day: 17)
        let result = PayNowPayloadBuilder.shared.build(config: config, today: today)

        switch result {
        case let success as PayloadResultSuccess:
            let payload = success.payload
            raw = payload.raw
            proxyDisplay = payload.proxyDisplay
            errors = []
            matrix = QrEncoder.shared.encode(payload: payload.raw, errorCorrection: .medium)
        case let invalid as PayloadResultInvalid:
            raw = nil; proxyDisplay = nil; matrix = nil
            errors = invalid.errors.map { $0.message }
        default:
            break
        }
    }
}

// MARK: - Styling helpers

private struct Card<Content: View>: View {
    @ViewBuilder let content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 14) { content }
            .padding(16)
            .background(Color.adyenSurface)
            .cornerRadius(16)
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.adyenBorder))
    }
}

private struct SectionTitle: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text.uppercased()).font(.caption.bold()).foregroundColor(.adyenMuted)
    }
}

private struct LabeledField<Content: View>: View {
    let label: String
    @ViewBuilder let content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.subheadline).foregroundColor(.adyenInk)
            content
        }
    }
}
