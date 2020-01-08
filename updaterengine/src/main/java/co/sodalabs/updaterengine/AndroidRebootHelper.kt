package co.sodalabs.updaterengine

import android.content.Context
import android.os.PowerManager
import javax.inject.Inject

private const val REBOOT_RECOVERY = "recovery"

class AndroidRebootHelper @Inject constructor(
    private val context: Context
) : IRebootHelper {

    private val powerManager by lazy { context.getSystemService(Context.POWER_SERVICE) as PowerManager }

    override fun rebootNormally() {
        powerManager.reboot(null)
    }

    override fun rebootToRecovery() {
        powerManager.reboot(REBOOT_RECOVERY)
    }
}