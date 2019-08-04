package co.sodalabs.updaterengine.feature.installer

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.JobIntentService
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.Packages
import co.sodalabs.updaterengine.UpdaterJobs
import co.sodalabs.updaterengine.data.DownloadedUpdate
import timber.log.Timber
import java.util.Objects

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
         * @param context this app's [Context]
         * @param localApkUri [Uri] pointing to (downloaded) local apk file
         * @param downloadUri [Uri] where the apk has been downloaded from
         * @param apk apk object of app that should be installed
         * @see .uninstall
         */
        fun install(
            context: Context,
            downloadedUpdates: List<DownloadedUpdate>
        ) {
            val intent = Intent(context, InstallerJobIntentService::class.java)
            intent.action = IntentActions.ACTION_INSTALL_UPDATES
            intent.putParcelableArrayListExtra(IntentActions.PROP_DOWNLOADED_UPDATES, ArrayList(downloadedUpdates))
            enqueueWork(context, intent)
        }

        /**
         * Uninstall an app.  [Objects.requireNonNull] is used to
         * enforce the `@NonNull` requirement, since that annotation alone
         * is not enough to catch all possible nulls.
         *
         * If you quickly cycle between installing an app and uninstalling it, then
         * [App.installedApk] will still be null when
         * [AppDetails2.startUninstall] calls
         * this method.  It is better to crash earlier here, before the [Intent]
         * is sent install a null [DownloadedUpdate] instance since this service is set to
         * receive Sticky Intents.  That means they will automatically be resent
         * by the system until they successfully complete.  If an `Intent`
         * install a null `DownloadedUpdate` is sent, it'll crash.
         *
         * @param context this app's [Context]
         */
        fun uninstall(
            context: Context,
            packageNames: List<String>
        ) {
            val intent = Intent(context, InstallerJobIntentService::class.java)
            intent.action = IntentActions.ACTION_UNINSTALL_PACKAGES
            intent.putStringArrayListExtra(IntentActions.PROP_APP_PACKAGE_NAMES, ArrayList(packageNames))
            enqueueWork(context, intent)
        }

        // TODO: Schedule install?

        private fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(context, InstallerJobIntentService::class.java, UpdaterJobs.JOB_ID_INSTALL_UPDATES, intent)
        }
    }

    override fun onHandleWork(intent: Intent) {
        val installer = createInstaller()
        try {
            when (intent.action) {
                IntentActions.ACTION_INSTALL_UPDATES -> {
                    val downloadedUpdates = intent.getParcelableArrayListExtra<DownloadedUpdate>(IntentActions.PROP_DOWNLOADED_UPDATES)
                    installer.installPackages(downloadedUpdates)
                }
                IntentActions.ACTION_UNINSTALL_PACKAGES -> {
                    val packageNames = intent.getStringArrayListExtra(IntentActions.PROP_APP_PACKAGE_NAMES)
                    installer.uninstallPackage(packageNames)
                }
            }
        } catch (error: Throwable) {
            Timber.e(error)
            // TODO: Error handling
        }
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
            PrivilegedInstaller(this)
        } else {
            DefaultInstaller(this)
        }
    }
}