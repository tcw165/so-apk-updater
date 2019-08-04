package co.sodalabs.apkupdater.feature.heartbeat

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import co.sodalabs.privilegedinstaller.RxLocalBroadcastReceiver
import co.sodalabs.updaterengine.AppUpdaterHeartBeater
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.data.HTTPResponseCode
import io.reactivex.Observable
import io.reactivex.Single

class SparkPointHeartBeater constructor(
    private val context: Context,
    private val schedulers: IThreadSchedulers
) : AppUpdaterHeartBeater {

    override fun sendHeartBeatNow(): Single<Int> {
        HeartBeatService.sendHeartBeatNow(context)

        return observeRecurringHeartBeat().firstOrError()
    }

    override fun scheduleRecurringHeartBeat(
        intervalMs: Long,
        sendImmediately: Boolean
    ) {
        // Delegate to the Service
        val initialDelay = if (sendImmediately) intervalMs / 10 else 0
        HeartBeatService.scheduleRecurringHeartBeat(context, intervalMs, sendImmediately, initialDelay)
    }

    override fun observeRecurringHeartBeat(): Observable<Int> {
        // Observe the result via the local broadcast
        val intentFilter = IntentFilter(IntentActions.ACTION_SEND_HEART_BEAT_NOW)
        return RxLocalBroadcastReceiver.bind(context, intentFilter)
            .map { intent -> validateApiResponse(intent) }
    }

    private fun validateApiResponse(
        intent: Intent
    ): Int {
        val code = intent.getIntExtra(IntentActions.PROP_HTTP_RESPONSE_CODE, HTTPResponseCode.Unknown.code)
        if (code == HTTPResponseCode.Unknown.code) {
            val error = intent.getSerializableExtra(IntentActions.PROP_ERROR) as? Throwable
            error?.let {
                // Throw the error in client
                throw it
            }
        }

        return code
    }
}