package co.sodalabs.updaterengine.feature.installer

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.app.JobIntentService
import co.sodalabs.updaterengine.IPackageVersionProvider
import co.sodalabs.updaterengine.IRebootHelper
import co.sodalabs.updaterengine.ISystemProperties
import co.sodalabs.updaterengine.ITimeUtil
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.Packages
import co.sodalabs.updaterengine.UpdaterConfig
import co.sodalabs.updaterengine.UpdaterService
import co.sodalabs.updaterengine.data.AppliedUpdate
import co.sodalabs.updaterengine.data.DownloadedAppUpdate
import co.sodalabs.updaterengine.data.DownloadedFirmwareUpdate
import co.sodalabs.updaterengine.extension.ensureBackgroundThread
import co.sodalabs.updaterengine.utils.BuildUtils
import dagger.android.AndroidInjection
import timber.log.Timber
import java.io.File
import javax.inject.Inject

private val CACHE_DIR = File("/cache/recovery")

private val COMMAND_FILE = File(CACHE_DIR, "command")
private val EXTENDED_COMMAND_FILE = File(CACHE_DIR, "extendedcommand")

private val MAX_ATTEMPTS_ALLOWED_FOR_INSTALLING_FIRMWARE = 1

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

    @Inject
    lateinit var updaterConfig: UpdaterConfig
    @Inject
    lateinit var rebootHelper: IRebootHelper
    @Inject
    lateinit var timeUtil: ITimeUtil
    @Inject
    lateinit var packageVersionProvider: IPackageVersionProvider
    @Inject
    lateinit var systemProperties: ISystemProperties

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
            // App Update
            IntentActions.ACTION_INSTALL_APP_UPDATE -> installAppUpdate(intent)
            IntentActions.ACTION_UNINSTALL_PACKAGES -> uninstallPackages(intent)
            // Firmware Update
            IntentActions.ACTION_INSTALL_FIRMWARE_UPDATE -> installFirmwareUpdate(intent)
            else -> throw IllegalArgumentException("${intent.action} is not supported")
        }
    }

    // App Update /////////////////////////////////////////////////////////////

    private fun installAppUpdate(
        intent: Intent
    ) {
        val downloadedUpdates = intent.getParcelableArrayListExtra<DownloadedAppUpdate>(IntentActions.PROP_DOWNLOADED_UPDATES)

        val installer = createInstaller()
        val appliedUpdates = mutableListOf<AppliedUpdate>()
        val errors = mutableListOf<Throwable>()

        val (successfulUpdates, failedUpdates, errorsToFailedUpdate) = installer.installPackages(downloadedUpdates)
        appliedUpdates.addAll(successfulUpdates)
        errors.addAll(errorsToFailedUpdate)

        UpdaterService.notifyAppUpdateInstalled(this, appliedUpdates, failedUpdates, errors)
    }

    private fun uninstallPackages(
        intent: Intent
    ) {
        // val packageNames = intent.getStringArrayListExtra(IntentActions.PROP_APP_PACKAGE_NAMES)
        // installer.uninstallPackage(packageNames)
        TODO()
    }

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

    // Firmware Update ////////////////////////////////////////////////////////

    private fun installFirmwareUpdate(
        intent: Intent
    ) {
        try {
            ensureInstallCommandFile()

            // TODO: Differentiate the full or incremental update
            // TODO: Get boolean for wipe-data & wipe-cache

            // Assume the downloaded update is the incremental update
            val downloadedUpdates = intent.getParcelableArrayListExtra<DownloadedFirmwareUpdate>(IntentActions.PROP_DOWNLOADED_UPDATES)
            require(downloadedUpdates.size == 1) { "There should be ONE downloaded firmware!" }
            val downloadedUpdate = downloadedUpdates.first()
            val fromUpdate = downloadedUpdate.fromUpdate

            if (fromUpdate.isIncremental) {
                writeUpdateCommand(downloadedUpdate, wipeData = false, wipeCache = false)
            } else {
                writeUpdateCommand(downloadedUpdate, wipeData = true, wipeCache = true)
            }

            // Notify the engine the install completes.
            UpdaterService.notifyFirmwareUpdateInstallComplete(this, fromUpdate)
        } catch (error: Throwable) {
            Timber.e(error)
        }
    }

    private fun ensureInstallCommandFile() {
        ensureBackgroundThread()

        // Ensure the directory.
        if (!CACHE_DIR.exists()) {
            CACHE_DIR.mkdirs()
        }

        // Clean up any previous commands.
        if (EXTENDED_COMMAND_FILE.exists()) {
            EXTENDED_COMMAND_FILE.delete()
        }
        if (COMMAND_FILE.exists()) {
            COMMAND_FILE.delete()
        }

        // Create a clean command file
        COMMAND_FILE.createNewFile()
    }

    private fun writeUpdateCommand(
        update: DownloadedFirmwareUpdate,
        wipeData: Boolean,
        wipeCache: Boolean
    ) {
        Timber.v("[Install] Write firmware install command...")
        ensureBackgroundThread()

        COMMAND_FILE.bufferedWriter()
            .use { out ->
                val cmdList = mutableListOf<String>()

                cmdList.add("boot-recovery")
                if (wipeData) {
                    cmdList.add("--wipe_data")
                }
                if (wipeCache) {
                    cmdList.add("--wipe_cache")
                }

                val originalFilePath = update.file.canonicalPath
                // Correct the file path due to some Android constraint. The
                // partition is mounted as 'legacy' in the normal boot, while it
                // is '0' in the recovery mode.
                val correctedFilePath = originalFilePath
                    .replaceFirst("/storage/emulated/legacy/", "/data/media/0/")
                    .replaceFirst("/storage/emulated/0/", "/data/media/0/")
                val correctedFile = File(correctedFilePath)
                cmdList.add("--update_package=$correctedFile")

                // Note: The command segment is delimited by '\n'.
                // e.g.
                // boot-recovery\n
                // --wipe_data\n
                // --wipe_cache\n
                // --update_package=file\n
                out.write(cmdList.concatWithLinebreak())
            }

        // Log the content of the command file
        if (BuildUtils.isDebug()) {
            val content = COMMAND_FILE.bufferedReader().use { it.readText() }
            Timber.v("[Install] Write firmware install command... successfully")
            Timber.v("[Install] The actual command content:\n$content\n")
        }
    }

    // Firmware

    private fun List<String>.concatWithLinebreak(): String {
        val builder = StringBuilder()
        for (cmd in this) {
            builder.append(cmd)
            // Always append '\n' at the end of command even for the last line.
            // The '\n' is recognized by the system.
            builder.append("\n")
        }
        return builder.toString()
    }
}