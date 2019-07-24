package co.sodalabs.updaterengine.feature.installer

import android.net.Uri

sealed class InstallerAction {

    data class InstallApp(
        val localApkUri: Uri,
        val packageName: String
    ) : InstallerAction()

    data class UninstallApp(
        val packageName: String
    ) : InstallerAction()
}