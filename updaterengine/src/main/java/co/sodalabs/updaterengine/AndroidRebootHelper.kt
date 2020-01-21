package co.sodalabs.updaterengine

import android.content.Context
import co.sodalabs.updaterengine.utils.IPreRebootCleaner
import co.sodalabs.updaterengine.utils.LinuxCommandUtils
import timber.log.Timber
import javax.inject.Inject

private const val REBOOT_RECOVERY = "recovery"

class AndroidRebootHelper @Inject constructor(
    private val context: Context,
    private val cleaner: IPreRebootCleaner,
    private val linuxCommandUtils: LinuxCommandUtils
) : IRebootHelper {

    // FIXME: In Big-Tab API 19, reboot has an issue that ShutdownThread freezes
    //  unexpectedly, and the caller process is likely killed. No reboot actually
    //  happens, which is a nightmare.
    //  See https://android.googlesource.com/platform/system/core/+/refs/heads/kitkat-release/adb/
    //  to understand why adb-reboot always works but not PowerManager.
    // private val powerManager by lazy { context.getSystemService(Context.POWER_SERVICE) as PowerManager }

    @Volatile
    private var rebooting: Boolean = false

    override fun rebootNormally() {
        Timber.v("[RemoteConfig] Rebooting...")

        rebooting = true

        // FIXME: Temporarily disabled for the reboot issue described above.
        // cleaner.stopSystemComponents()
        // powerManager.reboot(null)

        linuxCommandUtils.performAdbReboot()
    }

    override fun rebootToRecovery() {
        Timber.v("[RemoteConfig] Rebooting to recovery...")

        rebooting = true

        // FIXME: Temporarily disabled for the reboot issue described above.
        // cleaner.stopSystemComponents()
        // powerManager.reboot(REBOOT_RECOVERY)

        linuxCommandUtils.performAdbRebootToRecovery()
    }

    override fun isRebooting(): Boolean = rebooting
}