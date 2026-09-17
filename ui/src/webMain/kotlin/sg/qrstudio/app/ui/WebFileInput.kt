package sg.qrstudio.app.ui

import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.browser.document
import org.w3c.files.FileList
import kotlin.io.encoding.Base64
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.unsafeCast

@Composable
actual fun WebFileInputButton(
    onFileSelected: (name: String, bytes: ByteArray) -> Unit,
    selectedFileName: String?,
    modifier: Modifier,
) {
    SuggestionChip(
        onClick = { triggerFileInput(onFileSelected) },
        label = {
            Text(
                if (selectedFileName != null) {
                    "✓ Image selected: $selectedFileName"
                } else {
                    "📸 Choose image"
                },
            )
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun triggerFileInput(onFileSelected: (name: String, bytes: ByteArray) -> Unit) {
    // Appended to the DOM (hidden, not just created-and-discarded) rather than clicked
    // while detached: some browser/security-policy combinations only honour a synthetic
    // .click() on a file input that is actually part of the document, and a persistent,
    // reachable element is also what makes this driveable by end-to-end tooling.
    val input = document.createElement("input") as org.w3c.dom.HTMLInputElement
    input.type = "file"
    input.accept = "image/*"
    input.id = "qr-logo-file-input"
    input.style.position = "fixed"
    input.style.opacity = "0"
    input.style.setProperty("pointer-events", "none")
    input.style.width = "1px"
    input.style.height = "1px"

    input.onchange = { event ->
        val files: FileList? = input.files
        if (files != null && files.length > 0) {
            val file = files.item(0)
            if (file != null) {
                val reader = org.w3c.files.FileReader()
                reader.onload = { _ ->
                    try {
                        // reader.result is JsAny? on wasmJs (kotlinx-browser) and dynamic
                        // on js(IR) (kotlin-dom-api-compat) — two different actual types
                        // behind the same expect-ish org.w3c.files.FileReader API. A plain
                        // `as? String` against a wasmJs JsAny is not a real cast (JsAny is
                        // an opaque external reference, never a native Kotlin String) and
                        // always evaluated to null, so this branch silently never ran and
                        // onFileSelected was never called. unsafeCast<JsString>() + the
                        // resulting toString() is the portable conversion: on wasmJs it
                        // performs the actual JS-string-to-Kotlin-string bridge; on js(IR),
                        // JsString is a typealias for String so it's a no-op identity.
                        val result = reader.result?.unsafeCast<JsString>()?.toString()
                        if (result != null) {
                            // Convert data URL to ByteArray using the stdlib decoder — the
                            // same one ImageDecoderWebTest already proves correct, rather
                            // than a hand-rolled decoder that had never itself been tested.
                            val base64Data = result.substringAfter(",")
                            val bytes = Base64.decode(base64Data)
                            onFileSelected(file.name, bytes)
                        }
                    } catch (e: Exception) {
                        println("Error reading file: ${e.message}")
                    } finally {
                        input.remove()
                    }
                }
                reader.readAsDataURL(file)
            } else {
                input.remove()
            }
        } else {
            input.remove()
        }
        Unit
    }

    document.body?.appendChild(input)
    input.click()
}
