package co.sodalabs.apkupdater.feature.heartbeat

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import co.sodalabs.privilegedinstaller.RxLocalBroadcastReceiver
import co.sodalabs.updaterengine.AppUpdaterHeartBeater
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.IntentActions
import io.reactivex.Observable

class SparkPointHeartBeater constructor(
    private val context: Context,
    private val schedulers: IThreadSchedulers
) : AppUpdaterHeartBeater {

    override fun scheduleEvery(
        interval: Long,
        sendImmediately: Boolean
    ): Observable<Unit> {
        // Delegate to the Service
        val initialDelay = if (sendImmediately) interval / 10 else 0
        HeartBeatService.scheduleUpdatesCheck(context, interval, sendImmediately, initialDelay)

        // Observe the result via the local broadcast
        val intentFilter = IntentFilter(IntentActions.ACTION_SEND_HEART_BEAT_NOW)
        return RxLocalBroadcastReceiver.bind(context, intentFilter)
            .map { intent -> validateApiResponse(intent) }
    }

    private fun validateApiResponse(
        intent: Intent
    ) {
        // TODO
    }
}