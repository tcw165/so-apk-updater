package co.sodalabs.updaterengine.feature.statemachine

import co.sodalabs.updaterengine.UpdaterState

interface IUpdaterStateTracker {
    fun snapshotState(): UpdaterState
    fun snapshotStateWithMetadata(): Pair<UpdaterState, Map<String, String>>
    fun putState(state: UpdaterState, metadata: Map<String, String>)
    fun addStateMetadata(metadata: Map<String, String>)
}
