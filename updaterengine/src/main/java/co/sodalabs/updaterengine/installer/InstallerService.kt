package co.sodalabs.updaterengine.installer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.support.v4.app.JobIntentService
import co.sodalabs.updaterengine.data.Apk
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
        private const val ACTION_INSTALL = "co.sodalabs.apkupdater.installer.InstallerService.action.INSTALL"
        private const val ACTION_UNINSTALL = "co.sodalabs.apkupdater.installer.InstallerService.action.UNINSTALL"

        /**
         * Install an apk from [Uri].
         *
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
        fun install(context: Context, localApkUri: Uri, downloadUri: Uri, apk: Apk) {
            val intent = Intent(context, InstallerService::class.java)
            intent.action = InstallerService.ACTION_INSTALL
            intent.data = localApkUri
            intent.putExtra(Installer.EXTRA_DOWNLOAD_URI, downloadUri)
            intent.putExtra(Installer.EXTRA_APK, apk)
            enqueueWork(context, intent)
        }

        /**
         * Uninstall an app.  [Objects.requireNonNull] is used to
         * enforce the `@NonNull` requirement, since that annotation alone
         * is not enough to catch all possible nulls.
         *
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
         * @param apk [Apk] instance of the app that will be uninstalled
         */
        fun uninstall(context: Context, apk: Apk) {
            if (Build.VERSION.SDK_INT >= 19) {
                Objects.requireNonNull(apk)
            }

            val intent = Intent(context, InstallerService::class.java)
            intent.action = InstallerService.ACTION_UNINSTALL
            intent.putExtra(Installer.EXTRA_APK, apk)
            enqueueWork(context, intent)
        }

        private fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(context, InstallerService::class.java, 0x872394, intent)
        }
    }

    override fun onHandleWork(intent: Intent) {
        val apk: Apk = intent.getParcelableExtra(Installer.EXTRA_APK) ?: return

        val installer = InstallerFactory.create(this, apk)

        if (InstallerService.ACTION_INSTALL == intent.action) {
            val uri = intent.data ?: return
            val downloadUri: Uri = intent.getParcelableExtra(Installer.EXTRA_DOWNLOAD_URI)
            installer.installPackage(uri, downloadUri)
        } else if (InstallerService.ACTION_UNINSTALL == intent.action) {
            installer.uninstallPackage()

            // TODO: Uninstall OBB files
            //            val thread = object : Thread() {
            //                override fun run() {
            //                    super.run()
            //                    priority = MIN_PRIORITY
            //
            //                    val mainObbFile = apk.getMainObbFile()
            //                    if (mainObbFile == null) {
            //                        return;
            //                    }
            //                    val obbDir = mainObbFile.getParentFile()
            //                    if (obbDir == null) {
            //                        return;
            //                    }
            //                    FileFilter filter = new WildcardFileFilter("*.obb");
            //                    File[] obbFiles = obbDir . listFiles (filter);
            //                    if (obbFiles == null) {
            //                        return;
            //                    }
            //                    for (File f : obbFiles) {
            //                        Timber.d("Uninstalling OBB " + f);
            //                        FileUtils.deleteQuietly(f);
            //                    }
            //                }
            //            }
            //            thread.start()
        }
    }
}