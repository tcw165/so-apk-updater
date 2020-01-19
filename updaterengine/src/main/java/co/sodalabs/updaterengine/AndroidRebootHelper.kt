package co.sodalabs.updaterengine

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import co.sodalabs.updaterengine.utils.IPreRebootCleaner
import co.sodalabs.updaterengine.utils.LinuxCommandUtils
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val REBOOT_RECOVERY = "recovery"

class AndroidRebootHelper @Inject constructor(
    private val context: Context,
    private val cleaner: IPreRebootCleaner,
    private val linuxCommandUtils: LinuxCommandUtils
) : IRebootHelper {

    private val powerManager by lazy { context.getSystemService(Context.POWER_SERVICE) as PowerManager }

    @Volatile
    private var rebooting: Boolean = false

    override fun rebootNormally() {
        setupForceReboot()

        rebooting = true
        cleaner.stopSystemComponents()
        Timber.v("[RemoteConfig] Rebooting...")
        powerManager.reboot(null)
    }

    override fun rebootToRecovery() {
        setupForceReboot()

        rebooting = true
        cleaner.stopSystemComponents()
        Timber.v("[RemoteConfig] Rebooting to recovery...")
        powerManager.reboot(REBOOT_RECOVERY)
    }

    override fun forceReboot() {
        rebooting = true
        cleaner.stopSystemComponents()
        linuxCommandUtils.performForceReboot()
    }

    override fun isRebooting(): Boolean = rebooting

    @SuppressLint("CheckResult")
    private fun setupForceReboot() {
        // TODO: This is a hack. We should be able to get things working with just the `rebootHelper`
        // Assuming that normal reboot failed, trigger force reboot as a backup after 2 minutes
        Timber.v("[RemoteConfig] Setting up force reboot...")
        Single.just(1)
            .observeOn(Schedulers.io())
            .delay(2, TimeUnit.MINUTES)
            .subscribe({
                Timber.v("[RemoteConfig] Normal reboot failed force rebooting...")
                forceReboot()
            }, Timber::e)
    }
}