package co.sodalabs.updaterengine.utils

import android.content.Context
import androidx.work.WorkManager
import co.sodalabs.updaterengine.UpdaterHeartBeater
import javax.inject.Inject

class AndroidPreRebootCleaner @Inject constructor(
    private val context: Context,
    private val heartbeat: UpdaterHeartBeater
) : IPreRebootCleaner {

    override fun stopSystemComponents() {
        heartbeat.cancelRecurringHeartBeat()
        WorkManager.getInstance(context).cancelAllWork()
    }
}