package com.endoapp.app.health

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The scripted JVM double for [HealthConnectController] — the connect-section + import-screen analog of
 * the Plan-03 `FakeHealthReader`. Lets the Bloom connect/import UI be driven through every availability
 * state, window-label branch, and terminal import state with no emulator. Records call counts so a test
 * can prove the denial path fires no re-request and the refresh action re-imports.
 *
 * @param availability the availability tier [availability] returns (AVAILABLE by default).
 * @param history the value [grantedHistory] returns — the AHC-04 window-label switch.
 * @param initial the [importState] the screen starts from (drive states directly without the flow).
 */
class FakeHealthConnectController(
    private val availability: HealthAvailability = HealthAvailability.AVAILABLE,
    private var history: Boolean = false,
    initial: HealthImportState = HealthImportState.Idle,
) : HealthConnectController {

    private val _importState = MutableStateFlow(initial)
    override val importState: StateFlow<HealthImportState> = _importState

    var importCalls: Int = 0
        private set
    var refreshCalls: Int = 0
        private set

    override suspend fun availability(): HealthAvailability = availability

    override suspend fun grantedHistory(): Boolean = history

    override suspend fun importNow() {
        importCalls++
    }

    override suspend fun refresh() {
        refreshCalls++
    }

    /** Drive the observable state directly (the terminal states a test asserts against). */
    fun setState(state: HealthImportState) {
        _importState.value = state
    }
}
