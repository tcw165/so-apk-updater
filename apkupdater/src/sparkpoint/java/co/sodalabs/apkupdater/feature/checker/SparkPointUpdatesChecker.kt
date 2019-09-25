package co.sodalabs.apkupdater.feature.checker

import android.content.Context
import co.sodalabs.updaterengine.UpdatesChecker
import javax.inject.Inject

class SparkPointUpdatesChecker @Inject constructor(
    private val context: Context
) : UpdatesChecker {

    override fun checkNow() {
        CheckJobIntentService.checkUpdatesNow(context)
    }

    override fun scheduleCheck(
        triggerAtMillis: Long
    ) {
        CheckJobIntentService.scheduleCheck(
            context,
            triggerAtMillis
        )
    }
}