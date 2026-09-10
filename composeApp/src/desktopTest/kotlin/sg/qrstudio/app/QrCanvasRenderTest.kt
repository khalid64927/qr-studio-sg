package sg.qrstudio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.jetbrains.skia.EncodedImageFormat
import sg.qrstudio.app.ui.QrCanvas
import sg.qrstudio.qr.ErrorCorrection
import sg.qrstudio.qr.ModuleMatrix
import sg.qrstudio.qr.QrEncoder
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Renders [QrCanvas] — the actual composable the preview uses — into an offscreen image
 * and decodes it with ZXing.
 *
 * The encoder tests in :qr prove the *matrix* is correct. This proves the *renderer*
 * faithfully paints it: pixel snapping (FR-603), quiet zone (FR-205) and module geometry
 * all survive the trip to a real canvas. A renderer that drew every module half a pixel
 * out would pass every test in :qr and still produce codes that fail to scan.
 *
 * It is also the groundwork for FR-604 self-verification, which has to do exactly this
 * before enabling export.
 */
class QrCanvasRenderTest {

    private val paynowPayload =
        "00020101021126490009SG.PAYNOW010120210201403121W03011040820301231520400005303702" +
            "5802SG5913ACME Pte Ltd.6009Singapore6304B69E"

    private fun renderToImage(matrix: ModuleMatrix, pixels: Int): BufferedImage =
        ImageComposeScene(width = pixels, height = pixels, density = Density(1f)) {
            // Opaque white behind the canvas, matching QrPreviewPanel. Without it the
            // scene's untouched pixels stay transparent, and a luminance source reads
            // transparent as black — which erases the quiet zone and hides a working
            // renderer behind a decode failure.
            Box(Modifier.fillMaxSize().background(Color.White)) {
                QrCanvas(
                    matrix = matrix,
                    modifier = Modifier.fillMaxSize(),
                    // appearance and logo use defaults (black on white, no logo)
                )
            }
        }.use { scene ->
            val skiaImage = scene.render()
            val encoded = skiaImage.encodeToData(EncodedImageFormat.PNG)
                ?: error("Failed to encode the rendered scene")
            val decoded = ImageIO.read(encoded.bytes.inputStream())
            BufferedImage(decoded.width, decoded.height, BufferedImage.TYPE_INT_RGB).also { opaque ->
                opaque.createGraphics().run {
                    drawImage(decoded, 0, 0, java.awt.Color.WHITE, null)
                    dispose()
                }
            }
        }

    private fun decode(image: BufferedImage): String =
        MultiFormatReader().decode(
            BinaryBitmap(HybridBinarizer(BufferedImageLuminanceSource(image))),
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            ),
        ).text

    @Test
    fun `the Compose renderer produces a scannable code`() {
        val matrix = QrEncoder.encode(paynowPayload)
        assertEquals(paynowPayload, decode(renderToImage(matrix, 512)))
    }

    /**
     * Every error correction level at every awkward canvas size.
     *
     * This matrix exists because a single size hid a real defect. The renderer floored
     * the module size but centred the symbol on the *unfloored* remainder, so whenever
     * that remainder was odd the whole symbol sat on half-pixel boundaries and blurred
     * into an unscannable code. At 700px only level Q produced an odd remainder, so three
     * levels out of four passed and the renderer looked correct.
     *
     * Sizes are chosen to produce both even and odd remainders across the four levels.
     */
    @Test
    fun `every level stays scannable at every canvas size`() {
        val sizes = listOf(300, 397, 512, 640, 700, 701, 823, 1024)
        ErrorCorrection.entries.forEach { level ->
            val matrix = QrEncoder.encode(paynowPayload, level)
            sizes.forEach { pixels ->
                assertEquals(
                    paynowPayload,
                    decode(renderToImage(matrix, pixels)),
                    "Failed at error correction ${level.letter} rendered at ${pixels}px " +
                        "(matrix ${matrix.size} modules)",
                )
            }
        }
    }

    /** FR-603: with the origin floored, module edges land on whole pixels. */
    @Test
    fun `the symbol origin lands on a whole pixel for every level and size`() {
        listOf(300, 397, 512, 640, 700, 701, 823, 1024).forEach { pixels ->
            ErrorCorrection.entries.forEach { level ->
                val matrix = QrEncoder.encode(paynowPayload, level)
                val modulePixels = kotlin.math.floor(pixels.toFloat() / matrix.size)
                val origin = kotlin.math.floor((pixels - modulePixels * matrix.size) / 2f)
                assertEquals(
                    origin,
                    kotlin.math.floor(origin),
                    "Origin must be a whole pixel at ${level.letter}/${pixels}px",
                )
            }
        }
    }
}
