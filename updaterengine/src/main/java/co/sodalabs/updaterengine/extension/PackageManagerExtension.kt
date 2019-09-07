package co.sodalabs.updaterengine.extension

import android.content.pm.PackageManager

fun PackageManager.isPackageInstalled(packageName: String): Boolean {
    return try {
        this.getPackageInfo(packageName, 0)
        true
    } catch (error: PackageManager.NameNotFoundException) {
        false
    }
}