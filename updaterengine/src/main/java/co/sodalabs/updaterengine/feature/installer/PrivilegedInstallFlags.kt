package co.sodalabs.updaterengine.feature.installer

// Copy from AOSP source code, android.content.pm.PackageManager
object PrivilegedInstallFlags {

    const val INSTALL_REPLACE_EXISTING = 0x00000002
    const val INSTALL_ALLOW_DOWNGRADE = 0x00000080
}