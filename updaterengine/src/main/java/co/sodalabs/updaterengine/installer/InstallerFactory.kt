package co.sodalabs.updaterengine.installer

import android.content.Context
import android.text.TextUtils
import co.sodalabs.updaterengine.data.Apk
import timber.log.Timber

object InstallerFactory {

    /**
     * Returns an instance of an appropriate installer.
     * Either DefaultInstaller, PrivilegedInstaller, or in the special
     * case to install the "F-Droid Privileged Extension" ExtensionInstaller.
     *
     * @param context current [Context]
     * @param apk to be installed, always required.
     * @return instance of an Installer
     */
    fun create(context: Context, apk: Apk): Installer {
        if (TextUtils.isEmpty(apk.packageName)) {
            throw IllegalArgumentException("Apk.packageName must not be empty: $apk")
        }

        return if (PrivilegedInstaller.isDefault(context)) {
            Timber.d("privileged extension correctly installed -> PrivilegedInstaller")
            PrivilegedInstaller(context, apk)
        } else {
            DefaultInstaller(context, apk)
        }
    }
}