package sg.qrstudio.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import sg.qrstudio.qr.AppearanceConfig
import sg.qrstudio.qr.LogoConfig
import sg.qrstudio.qr.ModuleMatrix

actual fun exportQrAsPng(
    matrix: ModuleMatrix,
    appearance: AppearanceConfig,
    logo: LogoConfig,
    decodedImage: ImageBitmap?,
    pixelSize: Int,
    fileName: String,
) {
    // §9.2: Android export to Downloads folder using MediaStore
    // TODO: Implement using MediaStore.Images.Media.insertImage or scoped storage
}

actual fun exportQrAsSvg(
    matrix: ModuleMatrix,
    appearance: AppearanceConfig,
    logo: LogoConfig,
    decodedImage: ImageBitmap?,
    fileName: String,
) {
    // §9.2: Android export to Downloads folder
    // TODO: Implement using scoped storage
}
