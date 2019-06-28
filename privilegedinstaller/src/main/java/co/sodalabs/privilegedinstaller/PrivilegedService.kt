package co.sodalabs.privilegedinstaller

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.IPackageDeleteObserver
import android.content.pm.IPackageInstallObserver
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.widget.Toast
import timber.log.Timber
import java.io.IOException
import java.lang.reflect.Method

/**
 * SOURCE ORIGINALLY FROM F-DROID
 *
 * This service provides an API via AIDL IPC for the main F-Droid app to install/delete packages.
 *
 * @see [https://gitlab.com/fdroid/privileged-extension/#f-droid-privileged-extension]
 */
class PrivilegedService : Service() {

    private lateinit var installMethod: Method
    private lateinit var deleteMethod: Method

    private var privilegedCallback: IPrivilegedCallback? = null

    private val accessProtectionHelper by lazy {
        AccessProtectionHelper(this)
    }

    companion object {
        private const val BROADCAST_ACTION_INSTALL = "org.fdroid.fdroid.PrivilegedExtension.ACTION_INSTALL_COMMIT"
        private const val BROADCAST_ACTION_UNINSTALL = "org.fdroid.fdroid.PrivilegedExtension.ACTION_UNINSTALL_COMMIT"
        private const val BROADCAST_SENDER_PERMISSION = "android.permission.INSTALL_PACKAGES"
        private const val EXTRA_LEGACY_STATUS = "android.content.pm.extra.LEGACY_STATUS"
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Timber.v("Privileged installer is online")

        // get internal methods via reflection
        try {
            val installTypes = arrayOf<Class<*>>(
                Uri::class.java,
                IPackageInstallObserver::class.java,
                Int::class.javaPrimitiveType!!,
                String::class.java
            )
            val deleteTypes = arrayOf<Class<*>>(
                String::class.java,
                IPackageDeleteObserver::class.java,
                Int::class.javaPrimitiveType!!
            )

            val pm = packageManager
            installMethod = pm.javaClass.getMethod("installPackage", *installTypes)
            deleteMethod = pm.javaClass.getMethod("deletePackage", *deleteTypes)
        } catch (e: NoSuchMethodException) {
            Timber.e(e, "Android not compatible!")
            stopSelf()
        }

        val installFilter = IntentFilter()
        installFilter.addAction(BROADCAST_ACTION_INSTALL)
        registerReceiver(broadcastReceiver, installFilter, BROADCAST_SENDER_PERMISSION, null/*scheduler*/)

        val uninstallFilter = IntentFilter()
        uninstallFilter.addAction(BROADCAST_ACTION_UNINSTALL)
        registerReceiver(broadcastReceiver, uninstallFilter, BROADCAST_SENDER_PERMISSION, null /*scheduler*/)
    }

    override fun onDestroy() {
        Timber.v("Privileged installer is offline")

        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

    /**
     * Checks if an app is the current device owner.
     *
     * @param packageName to check
     * @return true if it is the device owner app
     */
    private fun isDeviceOwner(packageName: String): Boolean {
        if (Build.VERSION.SDK_INT < 18) {
            return false
        }

        val manager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return manager.isDeviceOwnerApp(packageName)
    }

    private fun hasPrivilegedPermissionsImpl(): Boolean {
        val hasInstallPermission = packageManager.checkPermission(
            Manifest.permission.INSTALL_PACKAGES,
            packageName
        ) == PackageManager.PERMISSION_GRANTED
        val hasDeletePermission = packageManager.checkPermission(
            Manifest.permission.DELETE_PACKAGES,
            packageName
        ) == PackageManager.PERMISSION_GRANTED

        Timber.v("Permission for APK install: $hasInstallPermission")
        Timber.v("Permission for APK delete: $hasDeletePermission")

        return hasInstallPermission && hasDeletePermission
    }

    private fun installPackageImpl(packageURI: Uri, flags: Int, installerPackageName: String?, callback: IPrivilegedCallback?) {
        // Internal callback from the system
        val installObserver = object : IPackageInstallObserver.Stub() {
            @Throws(RemoteException::class)
            override fun packageInstalled(packageName: String?, returnCode: Int) {
                // forward this internal callback to our callback
                try {
                    callback?.handleResult(packageName, returnCode)
                } catch (e1: RemoteException) {
                    Timber.e(e1, "RemoteException")
                }
            }
        }

        // execute internal method
        try {
            installMethod.invoke(
                packageManager, packageURI, installObserver,
                flags, installerPackageName
            )
        } catch (e: Exception) {
            Timber.e(e, "Android not compatible!")
            try {
                callback?.handleResult(null, 0)
            } catch (e1: RemoteException) {
                Timber.e(e1, "RemoteException")
            }
        }
    }

    private fun deletePackageImpl(packageName: String, flags: Int, callback: IPrivilegedCallback?) {
        if (isDeviceOwner(packageName)) {
            Timber.e("Cannot delete $packageName. This app is the device owner.")
            return
        }

        // Internal callback from the system
        val deleteObserver = object : IPackageDeleteObserver.Stub() {
            @Throws(RemoteException::class)
            override fun packageDeleted(packageName: String, returnCode: Int) {
                // forward this internal callback to our callback
                try {
                    callback?.handleResult(packageName, returnCode)
                } catch (e1: RemoteException) {
                    Timber.e(e1, "RemoteException")
                }
            }
        }

        // execute internal method
        try {
            deleteMethod.invoke(packageManager, packageName, deleteObserver, flags)
        } catch (e: Exception) {
            Timber.e(e, "Android not compatible!")
            try {
                callback?.handleResult(null, 0)
            } catch (e1: RemoteException) {
                Timber.e(e1, "RemoteException")
            }
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("InlinedApi")
        override fun onReceive(context: Context, intent: Intent) {
            val returnCode = intent.getIntExtra(
                EXTRA_LEGACY_STATUS, PackageInstaller.STATUS_FAILURE
            )
            val packageName = intent.getStringExtra(
                PackageInstaller.EXTRA_PACKAGE_NAME
            )
            try {
                privilegedCallback?.handleResult(packageName, returnCode)
            } catch (e1: RemoteException) {
                Timber.e(e1, "RemoteException")
            }
        }
    }

    private val binder = object : IPrivilegedService.Stub() {
        override fun hasPrivilegedPermissions(): Boolean {
            val callerIsAllowed = accessProtectionHelper.isCallerAllowed()
            return callerIsAllowed && hasPrivilegedPermissionsImpl()
        }

        override fun installPackage(
            packageURI: Uri,
            flags: Int,
            installerPackageName: String?,
            callback: IPrivilegedCallback?
        ) {
            if (!accessProtectionHelper.isCallerAllowed()) {
                return
            }

            if (Build.VERSION.SDK_INT >= 24) {
                doPackageStage(packageURI)
                privilegedCallback = callback
            } else {
                installPackageImpl(packageURI, flags, installerPackageName, callback)
            }
        }

        override fun deletePackage(packageName: String, flags: Int, callback: IPrivilegedCallback?) {
            if (!accessProtectionHelper.isCallerAllowed()) {
                return
            }
            if (Build.VERSION.SDK_INT >= 24) {
                privilegedCallback = callback
                val pm = packageManager
                val packageInstaller = pm.packageInstaller

                /*
                 * The client app used to set this to F-Droid, but we need it to be set to
                 * this package's package name to be able to uninstall from here.
                 */
                pm.setInstallerPackageName(packageName, BuildConfig.APPLICATION_ID)
                // Create a PendingIntent and use it to generate the IntentSender
                val broadcastIntent = Intent(BROADCAST_ACTION_UNINSTALL)
                val pendingIntent = PendingIntent.getBroadcast(
                    this@PrivilegedService, // context
                    0, // arbitary
                    broadcastIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
                packageInstaller.uninstall(packageName, pendingIntent.intentSender)
            } else {
                deletePackageImpl(packageName, flags, callback)
            }
        }
    }

    /**
     * Below function is copied mostly as-is from
     * https://android.googlesource.com/platform/packages/apps/PackageInstaller/+/06163dec5a23bb3f17f7e6279f6d46e1851b7d16
     */
    @TargetApi(24)
    private fun doPackageStage(packageURI: Uri) {
        val pm = packageManager
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        val packageInstaller = pm.packageInstaller
        var session: PackageInstaller.Session? = null
        try {
            val sessionId = packageInstaller.createSession(params)
            session = packageInstaller.openSession(sessionId)
            val inStream = contentResolver.openInputStream(packageURI)
            val outStream = session.openWrite("PackageInstaller", 0, -1 /* sizeBytes, unknown */)

            try {
                inStream.copyTo(outStream, 65536)
                session.fsync(outStream)
            } finally {
                inStream.closeQuietly()
                outStream.closeQuietly()
            }

            // Create a PendingIntent and use it to generate the IntentSender
            val broadcastIntent = Intent(BROADCAST_ACTION_INSTALL)
            val pendingIntent = PendingIntent.getBroadcast(
                this /*context*/,
                sessionId,
                broadcastIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )

            session.commit(pendingIntent.intentSender)
        } catch (e: IOException) {
            Timber.e(e, "Failure")
            Toast.makeText(this, e.localizedMessage, Toast.LENGTH_LONG).show()
        } finally {
            session.closeQuietly()
        }
    }
}
