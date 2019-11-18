package co.sodalabs.updaterengine

interface IUpdaterStateMachine {
    fun snapshotState(): IUpdaterState
    fun snapshotStateWithMetadata(): Pair<IUpdaterState, Map<String, String>>
    fun transitionToState(newState: IUpdaterState)
}