package co.sodalabs.updaterengine.feature.installer

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.os.PersistableBundle
import androidx.core.app.JobIntentService
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.Packages
import co.sodalabs.updaterengine.UpdaterConfig
import co.sodalabs.updaterengine.UpdaterJobs
import co.sodalabs.updaterengine.UpdaterService
import co.sodalabs.updaterengine.data.AppliedUpdate
import co.sodalabs.updaterengine.data.DownloadedAppUpdate
import co.sodalabs.updaterengine.data.DownloadedFirmwareUpdate
import dagger.android.AndroidInjection
import timber.log.Timber
import javax.inject.Inject

/**
 * MODIFIED FROM F-DROID OPEN SOURCE.
 *
 * This service handles the install process of apk files and
 * uninstall process of apps.
 * <p>
 * This service is based on an JobIntentService because:
 * <ul>
 * <li>no parallel installs/uninstalls should be allowed,
 * i.e., runs sequentially</li>
 * <li>no cancel operation is needed. Cancelling an installation
 * would be the same as starting uninstall afterwards</li>
 * </ul>
 * <p>
 * The download URL is only used as the unique ID that represents this
 * particular apk throughout the whole install process in
 * {@link InstallManagerService}.
 * <p>
 * This also handles deleting any associated OBB files when an app is
 * uninstalled, as per the
 * <a href="https://developer.android.com/google/play/expansion-files.html">
 * APK Expansion Files</a> spec.
 */
class InstallerJobIntentService : JobIntentService() {

    companion object {

        /**
         * Install an apk from [Uri].
         *
         * This does not include the same level of input validation as
         * [.uninstall] since this is called in one place where
         * the input has already been validated.
         *
         * @see .uninstall
         */
        fun installAppUpdateNow(
            context: Context,
            downloadedUpdates: List<DownloadedAppUpdate>
        ) {
            installNow(context, downloadedUpdates, IntentActions.ACTION_INSTALL_APP_UPDATE)
        }

        fun installFirmwareUpdateNow(
            context: Context,
            downloadedUpdates: List<DownloadedFirmwareUpdate>
        ) {
            installNow(context, downloadedUpdates, IntentActions.ACTION_INSTALL_APP_UPDATE)
        }

        private fun <T : Parcelable> installNow(
            context: Context,
            downloadedUpdates: List<T>,
            intentAction: String
        ) {
            val intent = Intent(context, InstallerJobIntentService::class.java)
            intent.action = intentAction
            intent.putParcelableArrayListExtra(IntentActions.PROP_DOWNLOADED_UPDATES, ArrayList(downloadedUpdates))
            enqueueWork(context, intent)
        }

        fun scheduleInstallAppUpdate(
            context: Context,
            downloadedUpdates: List<DownloadedAppUpdate>,
            triggerAtMillis: Long
        ) {
            scheduleInstall(context, downloadedUpdates, triggerAtMillis, IntentActions.ACTION_INSTALL_APP_UPDATE)
        }

        fun scheduleInstallFirmwareUpdate(
            context: Context,
            downloadedUpdates: List<DownloadedFirmwareUpdate>,
            triggerAtMillis: Long
        ) {
            scheduleInstall(context, downloadedUpdates, triggerAtMillis, IntentActions.ACTION_INSTALL_FIRMWARE_UPDATE)
        }

        private fun <T : Parcelable> scheduleInstall(
            context: Context,
            downloadedUpdates: List<T>,
            triggerAtMillis: Long,
            intentAction: String
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                Timber.v("[Install] (< 21) Schedule an install, using AlarmManager, at $triggerAtMillis milliseconds")

                val intent = Intent(context, InstallerJobIntentService::class.java)
                intent.action = intentAction
                intent.putParcelableArrayListExtra(IntentActions.PROP_DOWNLOADED_UPDATES, ArrayList(downloadedUpdates))

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
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

                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                val componentName = ComponentName(context, InstallerJobService::class.java)
                val persistentBundle = PersistableBundle()
                persistentBundle.putString(UpdaterJobs.JOB_ACTION, intentAction)
                // persistentBundle.put(IntentActions.PROP_DOWNLOADED_UPDATES, ArrayList(downloadedUpdates))

                val builder = JobInfo.Builder(UpdaterJobs.JOB_ID_INSTALL_UPDATES, componentName)
                    .setRequiresDeviceIdle(false)
                    .setExtras(persistentBundle)

                if (Build.VERSION.SDK_INT >= 26) {
                    builder.setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                }

                builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)

                // Note: The job would be consumed by CheckJobService and translated
                // to an Intent. Then the Intent is handled here in onHandleWork()!
                jobScheduler.cancel(UpdaterJobs.JOB_ID_INSTALL_UPDATES)
                jobScheduler.schedule(builder.build())
            }
        }

        fun cancelPendingInstalls(
            context: Context
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                Timber.v("[Install] (< 21) Cancel any pending install, using AlarmManager")

                val intent = Intent(context, InstallerJobIntentService::class.java)
                intent.action = IntentActions.ACTION_INSTALL_APP_UPDATE

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

                alarmManager.cancel(pendingIntent)
            } else {
                Timber.v("[Install] (>= 21) Cancel any pending install, using android-21 JobScheduler")

                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

                // Note: The job would be consumed by InstallerJobService and translated
                // to an Intent. Then the Intent is handled here in onHandleWork()!
                jobScheduler.cancel(UpdaterJobs.JOB_ID_INSTALL_UPDATES)
            }
        }

        /**
         * Uninstall apps.
         */
        fun uninstallNow(
            context: Context,
            packageNames: List<String>
        ) {
            val intent = Intent(context, InstallerJobIntentService::class.java)
            intent.action = IntentActions.ACTION_UNINSTALL_PACKAGES
            intent.putStringArrayListExtra(IntentActions.PROP_APP_PACKAGE_NAMES, ArrayList(packageNames))
            enqueueWork(context, intent)
        }

        private fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(context, InstallerJobIntentService::class.java, UpdaterJobs.JOB_ID_INSTALL_UPDATES, intent)
        }
    }

    @Inject
    lateinit var updaterConfig: UpdaterConfig

    override fun onCreate() {
        Timber.v("[Install] Installer Service is online")
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun onDestroy() {
        Timber.v("[Install] Installer Service is offline")
        super.onDestroy()
    }

    override fun onHandleWork(intent: Intent) {
        when (intent.action) {
            IntentActions.ACTION_INSTALL_APP_UPDATE -> installAppUpdate(intent)
            IntentActions.ACTION_INSTALL_FIRMWARE_UPDATE -> installFirmwareUpdate(intent)
            IntentActions.ACTION_UNINSTALL_PACKAGES -> uninstallPackages(intent)
            else -> throw IllegalArgumentException("${intent.action} is not supported")
        }
    }

    private fun installAppUpdate(
        intent: Intent
    ) {
        val installer = createInstaller()
        val appliedUpdates = mutableListOf<AppliedUpdate>()
        val errors = mutableListOf<Throwable>()

        val downloadedUpdates = intent.getParcelableArrayListExtra<DownloadedAppUpdate>(IntentActions.PROP_DOWNLOADED_UPDATES)
        val (updates, errs) = installer.installPackages(downloadedUpdates)
        appliedUpdates.addAll(updates)
        errors.addAll(errs)

        UpdaterService.notifyAppUpdateInstalled(this, appliedUpdates, errors)
    }

    private fun installFirmwareUpdate(
        intent: Intent
    ) {
        // val downloadedUpdates = intent.getParcelableArrayListExtra<DownloadedFirmwareUpdate>(IntentActions.PROP_DOWNLOADED_UPDATES)
        // UpdaterService.notifyFirmwareUpdateInstalled(this, downloadedUpdates, errors)
        TODO()
    }

    private fun uninstallPackages(
        intent: Intent
    ) {
        // val packageNames = intent.getStringArrayListExtra(IntentActions.PROP_APP_PACKAGE_NAMES)
        // installer.uninstallPackage(packageNames)
        TODO()
    }

    // Installer Factory //////////////////////////////////////////////////////

    private fun createInstaller(): Installer {
        val installedPackages: List<PackageInfo> = packageManager.getInstalledPackages(PackageManager.GET_SERVICES)
        var isPrivilegedInstallerInstalled = false
        for (i in 0 until installedPackages.size) {
            val packageInfo = installedPackages[i]
            if (packageInfo.packageName == Packages.PRIVILEGED_EXTENSION_PACKAGE_NAME) {
                isPrivilegedInstallerInstalled = true
                break
            }
        }

        return if (isPrivilegedInstallerInstalled) {
            PrivilegedInstaller(updaterConfig.installAllowDowngrade, this)
        } else {
            DefaultInstaller(this)
        }
    }
}