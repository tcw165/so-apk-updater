package co.sodalabs.updaterengine.feature.installer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.JobIntentService
import co.sodalabs.updaterengine.IntentActions.ACTION_INSTALL_APP
import co.sodalabs.updaterengine.IntentActions.ACTION_UNINSTALL_APP
import co.sodalabs.updaterengine.IntentActions.PROP_APP_FILE_URI
import co.sodalabs.updaterengine.IntentActions.PROP_APP_PACKAGE_NAME
import co.sodalabs.updaterengine.UpdaterJobs.JOB_ID_INSTALL_UPDATES
import co.sodalabs.updaterengine.data.Apk
import io.reactivex.disposables.CompositeDisposable
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
class InstallerService : JobIntentService() {

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
            localApkUri: Uri,
            packageName: String
        ) {
            val intent = Intent(context, InstallerService::class.java)
            intent.action = ACTION_INSTALL_APP
            intent.putExtra(PROP_APP_FILE_URI, localApkUri)
            intent.putExtra(PROP_APP_PACKAGE_NAME, packageName)
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
         * is sent install a null [Apk] instance since this service is set to
         * receive Sticky Intents.  That means they will automatically be resent
         * by the system until they successfully complete.  If an `Intent`
         * install a null `Apk` is sent, it'll crash.
         *
         * @param context this app's [Context]
         */
        fun uninstall(
            context: Context,
            packageName: String
        ) {
            val intent = Intent(context, InstallerService::class.java)
            intent.action = ACTION_UNINSTALL_APP
            intent.putExtra(PROP_APP_PACKAGE_NAME, packageName)
            enqueueWork(context, intent)
        }

        // TODO: Schedule install?

        private fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(context, InstallerService::class.java, JOB_ID_INSTALL_UPDATES, intent)
        }
    }

    private val disposables = CompositeDisposable()

    override fun onCreate() {
        super.onCreate()

        defaultInstaller.start()
        privilegedInstaller.start()
    }

    override fun onDestroy() {
        defaultInstaller.stop()
        privilegedInstaller.stop()

        disposables.clear()

        super.onDestroy()
    }

    override fun onHandleWork(intent: Intent) {
        // A polling for waiting for the privileged install binding established.
        for (i in 0 until 5) {
            if (getInstaller() !is PrivilegedInstaller) {
                Timber.v("[Install] Waiting for the privileged installer binding established (attempt #$i)...")
                Thread.sleep(1000L)
            }
        }

        val installer = getInstaller()
        val packageName = intent.getStringExtra(PROP_APP_PACKAGE_NAME)

        when (intent.action) {
            ACTION_INSTALL_APP -> {
                val localApkUri = intent.getParcelableExtra<Uri>(PROP_APP_FILE_URI)
                installer.installPackage(localApkUri, packageName)
            }
            ACTION_UNINSTALL_APP -> {
                installer.uninstallPackage(packageName)
            }
        }
    }

    // Two Types of Installers ////////////////////////////////////////////////

    private val privilegedInstaller by lazy { PrivilegedInstaller(this) }
    private val defaultInstaller by lazy { DefaultInstaller(this) }

    private fun getInstaller(): Installer {
        return if (privilegedInstaller.isReady()) {
            privilegedInstaller
        } else {
            defaultInstaller
        }
    }
}