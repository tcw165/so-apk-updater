package co.sodalabs.updaterengine

import android.content.Context
import android.os.PowerManager
import javax.inject.Inject

private const val REBOOT_RECOVERY = "recovery"

class AndroidRebootHelper @Inject constructor(
    private val context: Context
) : IRebootHelper {

    override fun rebootToRecovery() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.reboot(REBOOT_RECOVERY)
    }
}