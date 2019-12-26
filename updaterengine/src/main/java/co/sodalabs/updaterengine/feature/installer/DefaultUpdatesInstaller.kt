package co.sodalabs.updaterengine.feature.installer

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Parcelable
import android.os.PersistableBundle
import androidx.core.app.JobIntentService
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.UpdaterJobs
import co.sodalabs.updaterengine.UpdaterJobs.JOB_ID_INSTALL_UPDATES
import co.sodalabs.updaterengine.UpdatesInstaller
import co.sodalabs.updaterengine.data.DownloadedAppUpdate
import co.sodalabs.updaterengine.data.DownloadedFirmwareUpdate
import timber.log.Timber
import javax.inject.Inject

class DefaultUpdatesInstaller @Inject constructor(
    private val context: Context
) : UpdatesInstaller {

    private val alarmManager by lazy { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    private val jobScheduler by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        } else {
            TODO("VERSION.SDK_INT < LOLLIPOP")
        }
    }

    // Install ////////////////////////////////////////////////////////////////

    override fun installAppUpdateNow(
        downloadedUpdates: List<DownloadedAppUpdate>
    ) {
        installNow(downloadedUpdates, IntentActions.ACTION_INSTALL_APP_UPDATE)
    }

    override fun installFirmwareUpdateNow(
        downloadedUpdate: DownloadedFirmwareUpdate
    ) {
        // Note: We turn the singular update to a list to be compatible with the batch install.
        installNow(listOf(downloadedUpdate), IntentActions.ACTION_INSTALL_FIRMWARE_UPDATE)
    }

    override fun scheduleInstallAppUpdate(
        downloadedUpdates: List<DownloadedAppUpdate>,
        triggerAtMillis: Long
    ) {
        scheduleInstall(downloadedUpdates, triggerAtMillis, IntentActions.ACTION_INSTALL_APP_UPDATE)
    }

    override fun scheduleInstallFirmwareUpdate(
        downloadedUpdate: DownloadedFirmwareUpdate,
        triggerAtMillis: Long
    ) {
        // Note: We turn the singular update to a list to be compatible with the batch install.
        scheduleInstall(listOf(downloadedUpdate), triggerAtMillis, IntentActions.ACTION_INSTALL_FIRMWARE_UPDATE)
    }

    override fun cancelPendingInstalls() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Timber.v("[Install] (< 21) Cancel any pending install, using AlarmManager")

            val intent = Intent(context, InstallerJobIntentService::class.java).apply {
                action = IntentActions.ACTION_INSTALL_APP_UPDATE
            }
            val pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

            alarmManager.cancel(pendingIntent)
        } else {
            Timber.v("[Install] (>= 21) Cancel any pending install, using android-21 JobScheduler")

            // Note: The job would be consumed by InstallerJobService and translated
            // to an Intent. Then the Intent is handled here in onHandleWork()!
            jobScheduler.cancel(JOB_ID_INSTALL_UPDATES)
        }
    }

    private fun <T : Parcelable> installNow(
        downloadedUpdates: List<T>,
        intentAction: String
    ) {
        val intent = Intent(context, InstallerJobIntentService::class.java)
        intent.action = intentAction
        intent.putParcelableArrayListExtra(IntentActions.PROP_DOWNLOADED_UPDATES, ArrayList(downloadedUpdates))
        JobIntentService.enqueueWork(context, InstallerJobIntentService::class.java, JOB_ID_INSTALL_UPDATES, intent)
    }

    private fun <T : Parcelable> scheduleInstall(
        downloadedUpdates: List<T>,
        triggerAtMillis: Long,
        intentAction: String
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Timber.v("[Install] (< 21) Schedule an install, using AlarmManager, at $triggerAtMillis milliseconds")

            val intent = Intent(context, InstallerJobIntentService::class.java).apply {
                action = intentAction
                putParcelableArrayListExtra(IntentActions.PROP_DOWNLOADED_UPDATES, ArrayList(downloadedUpdates))
            }
            val pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

            // TODO: Do we need to recover the scheduling on boot?
            alarmManager.cancel(pendingIntent)
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            Timber.v("[Install] (>= 21) Schedule an install, using android-21 JobScheduler")

            val componentName = ComponentName(context, InstallerJobService::class.java)
            val persistentBundle = PersistableBundle()
            persistentBundle.putString(UpdaterJobs.JOB_ACTION, intentAction)
            // persistentBundle.put(IntentActions.PROP_DOWNLOADED_UPDATES, ArrayList(downloadedUpdates))

            val builder = JobInfo.Builder(JOB_ID_INSTALL_UPDATES, componentName)
                .setRequiresDeviceIdle(false)
                .setExtras(persistentBundle)

            if (Build.VERSION.SDK_INT >= 26) {
                builder.setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
            }

            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)

            // Note: The job would be consumed by CheckJobService and translated
            // to an Intent. Then the Intent is handled here in onHandleWork()!
            jobScheduler.cancel(JOB_ID_INSTALL_UPDATES)
            jobScheduler.schedule(builder.build())
        }
    }

    // Uninstall //////////////////////////////////////////////////////////////

    override fun uninstallAppNow(
        packageNames: List<String>
    ) {
        val intent = Intent(context, InstallerJobIntentService::class.java)
        intent.action = IntentActions.ACTION_UNINSTALL_PACKAGES
        intent.putStringArrayListExtra(IntentActions.PROP_APP_PACKAGE_NAMES, ArrayList(packageNames))
        JobIntentService.enqueueWork(context, InstallerJobIntentService::class.java, JOB_ID_INSTALL_UPDATES, intent)
    }

    override fun scheduleUninstallApp(
        packageNames: List<String>,
        triggerAtMillis: Long
    ) {
        TODO("not implemented")
    }
}