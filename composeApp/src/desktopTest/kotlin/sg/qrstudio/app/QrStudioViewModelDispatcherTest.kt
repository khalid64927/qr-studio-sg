package sg.qrstudio.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.datetime.LocalDate
import sg.qrstudio.app.ui.QrStudioIntent
import sg.qrstudio.app.ui.QrStudioViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Regression guard for a real crash: on desktop, `Dispatchers.Main` does not exist
 * without the `kotlinx-coroutines-swing` artifact. `viewModelScope.launch()` (used by
 * [QrStudioViewModel.scheduleRegeneration]) throws `IllegalStateException` the instant
 * any UI intent fires — in practice, the first keystroke in any field. The app compiled
 * and ran, so this was invisible until someone actually typed something.
 *
 * `Dispatchers.setMain` is unavailable outside `kotlinx-coroutines-test`'s Android
 * artifact, so this test instead exercises the real production path directly: it
 * requires `kotlinx.coroutines.swing.Swing` to be resolvable (compileDesktopTest fails
 * otherwise) and drives an actual ViewModel intent through to a populated payload,
 * proving `viewModelScope.launch` completes rather than throwing.
 */
class QrStudioViewModelDispatcherTest {
    @BeforeTest
    fun ensureSwingDispatcherIsLinked() {
        // Referencing it is enough to fail the build if kotlinx-coroutines-swing is ever
        // removed from desktopMain — the whole point of this test.
        Dispatchers.Swing
    }

    private val viewModel = QrStudioViewModel(today = { LocalDate(2026, 9, 9) })

    @AfterTest
    fun clear() {
        viewModel.onIntent(QrStudioIntent.ProxyValueChanged(""))
    }

    @Test
    fun `an intent regenerates the payload without throwing on desktop`() {
        runBlocking {
            viewModel.onIntent(QrStudioIntent.ProxyTypeChanged(sg.qrstudio.payload.ProxyType.MOBILE))
            viewModel.onIntent(QrStudioIntent.ProxyValueChanged("91234567"))

            // The regeneration coroutine is debounced (FR-502) and launched on
            // viewModelScope, i.e. Dispatchers.Main.immediate. Give it time to run.
            delay(500)
        }

        assertNotNull(
            viewModel.uiState.payload,
            "Expected a payload after a valid intent; if this is null the regeneration " +
                "coroutine likely threw (missing Dispatchers.Main) rather than completed",
        )
    }
}
