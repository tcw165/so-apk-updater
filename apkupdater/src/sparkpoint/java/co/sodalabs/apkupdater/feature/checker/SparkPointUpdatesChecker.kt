package co.sodalabs.apkupdater.feature.checker

import android.content.Context
import co.sodalabs.updaterengine.AppUpdatesChecker
import co.sodalabs.updaterengine.IThreadSchedulers
import javax.inject.Inject

class SparkPointUpdatesChecker @Inject constructor(
    private val context: Context,
    private val schedulers: IThreadSchedulers
) : AppUpdatesChecker {

    override fun checkNow(
        packageNames: List<String>
    ) {
        CheckJobIntentService.checkUpdatesNow(
            context,
            packageNames.toTypedArray())
    }

    override fun scheduleCheckAfter(packageNames: List<String>, afterMs: Long) {
        // TODO("not implemented")
    }

    override fun scheduleRecurringCheck(
        packageNames: List<String>,
        intervalMs: Long
    ) {
        CheckJobIntentService.scheduleRecurringUpdateCheck(
            context,
            packageNames.toTypedArray(),
            intervalMs)
    }
}