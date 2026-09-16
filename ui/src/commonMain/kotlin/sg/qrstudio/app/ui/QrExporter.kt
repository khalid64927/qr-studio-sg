package sg.qrstudio.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import sg.qrstudio.qr.ModuleMatrix

/**
 * §9.2: Cross-platform QR export to PNG and SVG formats.
 * Each platform handles file saving differently.
 */
expect fun exportQrAsPng(
    matrix: ModuleMatrix,
    appearance: sg.qrstudio.qr.AppearanceConfig,
    logo: sg.qrstudio.qr.LogoConfig,
    decodedImage: ImageBitmap?,
    pixelSize: Int = 4096,
    fileName: String = "qr-code.png",
)

expect fun exportQrAsSvg(
    matrix: ModuleMatrix,
    appearance: sg.qrstudio.qr.AppearanceConfig,
    logo: sg.qrstudio.qr.LogoConfig,
    decodedImage: ImageBitmap?,
    fileName: String = "qr-code.svg",
)
