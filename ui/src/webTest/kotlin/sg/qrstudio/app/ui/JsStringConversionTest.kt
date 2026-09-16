package sg.qrstudio.app.ui

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.toJsString
import kotlin.js.unsafeCast
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression test for the bug where WebFileInput.kt's `reader.result as? String` always
 * evaluated to null on wasmJs, so a picked logo image never reached the app at all (no
 * "Image selected" confirmation, nothing drawn) — see git history: the `result` property
 * of FileReader is JsAny? on wasmJs (kotlinx-browser) but dynamic on js(IR)
 * (kotlin-dom-api-compat), two different actual types behind the same nominal
 * org.w3c.files.FileReader API, and only one of them tolerates a bare `as? String`.
 *
 * This proves the fix — `jsAny.unsafeCast<JsString>().toString()` — round-trips a real
 * string correctly on whichever of the two the current target actually provides, using
 * the same conversion pair used in WebFileInput.kt's onload handler.
 */
class JsStringConversionTest {
    @OptIn(ExperimentalWasmJsInterop::class)
    @Test
    fun jsAnyToKotlinStringRoundTripsCorrectly() {
        // toJsString()/JsAny mirrors exactly what a browser API handing back a JS string
        // value looks like from Kotlin's side — this is not a special-cased test double.
        val original = "data:image/png;base64,iVBORw0KGgo="
        val asJsAny: JsAny = original.toJsString()

        val recovered = asJsAny.unsafeCast<JsString>().toString()

        assertEquals(original, recovered)
    }
}
