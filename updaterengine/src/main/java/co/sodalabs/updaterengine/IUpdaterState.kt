package co.sodalabs.updaterengine

interface IUpdaterState {
    fun enter()
    fun exit()
    fun snapshotMetadata()
}