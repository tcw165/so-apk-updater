package co.sodalabs.updaterengine.feature.statemachine

import co.sodalabs.updaterengine.UpdaterState

interface IUpdaterStateMachine {
    val state: UpdaterState
    val metadata: Map<String, Any>

    fun putState(state: UpdaterState)
    fun updateMetadata(keyValue: Pair<String, Any>)
    fun updateMetadata(metadata: Map<String, Any>)
}
