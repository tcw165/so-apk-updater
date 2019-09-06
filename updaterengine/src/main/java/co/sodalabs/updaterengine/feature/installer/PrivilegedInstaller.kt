package co.sodalabs.updaterengine.feature.installer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import co.sodalabs.privilegedinstaller.IPrivilegedCallback
import co.sodalabs.privilegedinstaller.IPrivilegedService
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.Packages
import co.sodalabs.updaterengine.data.AppliedUpdate
import co.sodalabs.updaterengine.data.DownloadedUpdate
import co.sodalabs.updaterengine.exception.CompositeException
import co.sodalabs.updaterengine.exception.InstallNoPrivilegedPermissionException
import co.sodalabs.updaterengine.feature.installer.PrivilegedInstallFlags.INSTALL_ALLOW_DOWNGRADE
import co.sodalabs.updaterengine.feature.installer.PrivilegedInstallFlags.INSTALL_REPLACE_EXISTING
import co.sodalabs.updaterengine.feature.installer.PrivilegedInstallStatusCode.DELETE_SUCCEEDED
import co.sodalabs.updaterengine.feature.installer.PrivilegedInstallStatusCode.INSTALL_SUCCEEDED
import timber.log.Timber
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Installer that only works if the "F-Droid Privileged
 * Extension" is installed as a privileged app.
 * <p/>
 * "F-Droid Privileged Extension" provides a service that exposes
 * internal Android APIs for install/uninstall which are protected
 * by INSTALL_PACKAGES, DELETE_PACKAGES permissions.
 * Both permissions are protected by systemOrSignature (in newer versions:
 * system|signature) and cannot be used directly by F-Droid.
 * <p/>
 * Instead, this installer binds to the service of
 * "F-Droid Privileged Extension" and then executes the appropriate methods
 * inside the privileged context of the privileged extension.
 * <p/>
 * This installer makes unattended installs/uninstalls possible.
 * Thus no PendingIntents are returned.
 *
 * @see <a href="https://groups.google.com/forum/#!msg/android-security-discuss/r7uL_OEMU5c/LijNHvxeV80J">
 * Sources for Android 4.4 change</a>
 * @see <a href="https://android.googlesource.com/platform/frameworks/base/+/ccbf84f44">
 * Commit that restricted "signatureOrSystem" permissions</a>
 */
class PrivilegedInstaller(
    private val allowDowngrade: Boolean,
    context: Context
) : Installer(context) {

    private val installFlags by lazy {
        var flags = INSTALL_REPLACE_EXISTING
        if (allowDowngrade) {
            flags = flags or INSTALL_ALLOW_DOWNGRADE
        }
        flags
    }

    override fun installPackages(
        localUpdates: List<DownloadedUpdate>
    ): Pair<List<AppliedUpdate>, List<Throwable>> {
        if (localUpdates.isEmpty()) {
            Timber.v("[Install] Install skips due to no updates")
            return Pair(emptyList(), emptyList())
        }

        Timber.v("[Install] Install starts...")
        Timber.v("[Install] $installFlagsPrettyString")

        val appliedUpdates = CopyOnWriteArrayList<AppliedUpdate>()
        val errors = CopyOnWriteArrayList<Throwable>()

        val (connection, privService) = bindPrivilegedService()
        privService?.apply {
            val remainingInstalls: Queue<DownloadedUpdate> = LinkedList(localUpdates)
            // Apply the updates one by one synchronously.
            while (remainingInstalls.isNotEmpty()) {
                val downloadedUpdate = remainingInstalls.poll()
                val filePath = downloadedUpdate.file.canonicalPath
                val fileURI = ApkFileProvider.fromFile(context, downloadedUpdate.file)
                val countDownLatch = CountDownLatch(1)
                val installCallback = object : IPrivilegedCallback.Stub() {
                    override fun handleResult(
                        packageName: String,
                        returnCode: Int
                    ) {
                        if (returnCode == INSTALL_SUCCEEDED) {
                            Timber.v("[Install] Install completes for \"$fileURI\"")

                            val appliedUpdate = AppliedUpdate(
                                packageName = downloadedUpdate.fromUpdate.packageName,
                                versionName = downloadedUpdate.fromUpdate.versionName
                            )
                            appliedUpdates.add(appliedUpdate)
                        } else {
                            Timber.e("[Install] Install fails for \"$fileURI\", return code is $returnCode")

                            val error = returnCode.toError(filePath)
                            errors.add(error)
                        }
                        countDownLatch.countDown()
                    }
                }

                if (hasPrivilegedPermissions()) {
                    try {
                        installPackage(fileURI, installFlags, context.packageName, installCallback)
                    } catch (error: Throwable) {
                        Timber.e(error)
                        errors.add(error)
                    }
                } else {
                    Timber.e("[Install] The privileged installer doesn't have the privileged permissions...")

                    errors.add(InstallNoPrivilegedPermissionException())
                    countDownLatch.countDown()
                }

                // Wait for the async install
                countDownLatch.await(Intervals.TIMEOUT_INSTALL_MIN, TimeUnit.MINUTES)
            }
        }

        // Once all installs finishes, unbind the privileged service.
        connection.unbind()

        return Pair(appliedUpdates, errors)
    }

    override fun uninstallPackage(
        packageNames: List<String>
    ) {
        Timber.v("[Uninstall] Uninstall starts...")
        val errors = mutableListOf<Throwable>()
        val (connection, privService) = bindPrivilegedService()
        privService?.apply {
            val remainingUninstalls: Queue<String> = LinkedList(packageNames)
            while (remainingUninstalls.isNotEmpty()) {
                val toDeletePackageName = remainingUninstalls.poll()
                val countDownLatch = CountDownLatch(1)
                val uninstallCallback = object : IPrivilegedCallback.Stub() {
                    override fun handleResult(
                        packageName: String,
                        returnCode: Int
                    ) {
                        if (returnCode == DELETE_SUCCEEDED) {
                            Timber.v("[Uninstall] Uninstall completes for \"$packageName\"")
                        } else {
                            Timber.e("[Uninstall] Uninstall fails for \"$packageName\"")
                        }
                        countDownLatch.countDown()
                    }
                }

                if (hasPrivilegedPermissions()) {
                    deletePackage(toDeletePackageName, INSTALL_REPLACE_EXISTING, uninstallCallback)
                } else {
                    errors.add(InstallNoPrivilegedPermissionException())
                    Timber.e("[Uninstall] The privileged install doesn't have the privileged permissions...")

                    countDownLatch.countDown()
                }

                // Wait for the async install
                countDownLatch.await(Intervals.TIMEOUT_INSTALL_MIN, TimeUnit.MINUTES)
            }
        }

        // Once all uninstalls finishes, unbind the privileged service.
        connection.unbind()

        // Throw error after the service connection is recycled
        if (errors.isNotEmpty()) {
            throw CompositeException(errors)
        }
    }

    // Privileged Service Binding /////////////////////////////////////////////

    private fun bindPrivilegedService(): Pair<ServiceConnection, IPrivilegedService?> {
        var privService: IPrivilegedService? = null
        val countDownLatch = CountDownLatch(1)
        val serviceIntent = Intent()
        serviceIntent.setClassName(Packages.PRIVILEGED_EXTENSION_PACKAGE_NAME, Packages.PRIVILEGED_EXTENSION_SERVICE_INTENT)
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
                Timber.v("[Install] Bind the privileged installer service... connected")
                privService = IPrivilegedService.Stub.asInterface(binder)
                countDownLatch.countDown()
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                Timber.v("[Install] Bind the privileged installer service... disconnected")
                countDownLatch.countDown()
            }
        }

        Timber.v("[Install] Bind the privileged installer service...")
        // Bind the privileged service
        val bound = context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        Timber.v("[Install] Bind the privileged installer service... bound: $bound")
        // Wait for the async binding
        countDownLatch.await(Intervals.TIMEOUT_SERVICE_BINDING, TimeUnit.MILLISECONDS)

        return Pair(serviceConnection, privService)
    }

    private fun ServiceConnection.unbind() {
        Timber.v("[Install] Unbind the privileged installer service")
        context.unbindService(this)
    }

    // DEBUG //////////////////////////////////////////////////////////////////

    private val installFlagsPrettyString by lazy {
        val sb = StringBuilder()

        sb.appendln("Install flags is: [")
        if (installFlags and INSTALL_REPLACE_EXISTING == INSTALL_REPLACE_EXISTING) {
            sb.appendln("    INSTALL_REPLACE_EXISTING,")
        }
        if (installFlags and INSTALL_ALLOW_DOWNGRADE == INSTALL_ALLOW_DOWNGRADE) {
            sb.appendln("    INSTALL_ALLOW_DOWNGRADE,")
        }
        sb.appendln("]")

        sb.toString()
    }
}