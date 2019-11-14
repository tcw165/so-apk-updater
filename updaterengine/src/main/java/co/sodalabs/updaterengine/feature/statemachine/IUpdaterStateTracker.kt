package co.sodalabs.updaterengine.feature.statemachine

import co.sodalabs.updaterengine.UpdaterState

interface IUpdaterStateTracker {
    val state: UpdaterState
    val metadata: Map<String, String>

    fun putState(state: UpdaterState)
    fun addMetadata(metadata: Map<String, String>)
}
