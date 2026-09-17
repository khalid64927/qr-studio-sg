import SwiftUI
import QrStudioQrKit

/// Draws a `ModuleMatrix` directly via SwiftUI `Canvas` — SwiftUI has no native SVG
/// rendering surface, so unlike the web demo (which consumes QrSvgRenderer's SVG string
/// through QrModuleMatrixJs.toSvg), this draws per-module using the matrix's own
/// `isDark(x:y:)`, mirroring the same square/rounded/dot shape algorithm QrSvgRenderer
/// uses (qr/src/commonMain/.../QrSvgRenderer.kt) so the three renderers stay visually
/// consistent even though this one isn't literally sharing code with them.
struct QrCanvasView: View {
    let matrix: ModuleMatrix
    let foreground: Color
    let background: Color
    let moduleShape: ContentView.ShapeOption

    var body: some View {
        Canvas { context, size in
            let count = Int(matrix.size)
            guard count > 0 else { return }
            let moduleSize = min(size.width, size.height) / CGFloat(count)

            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(background))

            for y in 0..<count {
                for x in 0..<count {
                    guard matrix.isDark(x: Int32(x), y: Int32(y)) else { continue }
                    let rect = CGRect(
                        x: CGFloat(x) * moduleSize,
                        y: CGFloat(y) * moduleSize,
                        width: moduleSize,
                        height: moduleSize
                    )
                    context.fill(path(for: rect, size: moduleSize), with: .color(foreground))
                }
            }
        }
    }

    private func path(for rect: CGRect, size: CGFloat) -> Path {
        switch moduleShape {
        case .square:
            return Path(rect)
        case .rounded:
            return Path(roundedRect: rect, cornerRadius: size * 0.3)
        case .dot:
            let radius = size / 2.2
            let center = CGPoint(x: rect.midX, y: rect.midY)
            return Path(ellipseIn: CGRect(
                x: center.x - radius, y: center.y - radius,
                width: radius * 2, height: radius * 2
            ))
        }
    }
}
