package co.sodalabs.apkupdater.feature.checker

import android.content.Context
import co.sodalabs.updaterengine.UpdatesChecker
import javax.inject.Inject

class SparkPointUpdatesChecker @Inject constructor(
    private val context: Context
) : UpdatesChecker {

    override fun checkNow(
        packageNames: List<String>
    ) {
        CheckJobIntentService.checkUpdatesNow(
            context,
            packageNames.toTypedArray()
        )
    }

    override fun scheduleDelayedCheck(
        packageNames: List<String>,
        delayMillis: Long
    ) {
        CheckJobIntentService.scheduleNextCheck(
            context,
            packageNames.toTypedArray(),
            delayMillis
        )
    }
}