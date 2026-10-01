package com.tobfd.tsuzuki.core.testing

import com.tobfd.tsuzuki.core.data.update.UpdateRepository
import com.tobfd.tsuzuki.core.model.AppUpdate
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [UpdateRepository]; set [checkResult] for [checkNow]. */
class FakeUpdateRepository(override val isEnabled: Boolean = true) : UpdateRepository {
    override val autoCheck = MutableStateFlow(true)
    override val availableUpdate = MutableStateFlow<AppUpdate?>(null)

    var checkResult: Result<AppUpdate?> = Result.success(null)
    var startChecks = 0
        private set
    var manualChecks = 0
        private set
    val dismissed = mutableListOf<AppUpdate>()

    override suspend fun setAutoCheck(enabled: Boolean) {
        autoCheck.value = enabled
    }

    override suspend fun dismiss(update: AppUpdate) {
        dismissed += update
        if (availableUpdate.value == update) availableUpdate.value = null
    }

    override suspend fun checkOnStart() {
        startChecks++
    }

    override suspend fun checkNow(): Result<AppUpdate?> {
        manualChecks++
        return checkResult
    }
}
