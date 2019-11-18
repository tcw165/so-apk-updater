package co.sodalabs.updaterengine

interface IUpdaterStateLisitener {
    fun onPostExit(exitingState: IUpdaterState)
    fun onPostEnter(enteringState: IUpdaterState)
}