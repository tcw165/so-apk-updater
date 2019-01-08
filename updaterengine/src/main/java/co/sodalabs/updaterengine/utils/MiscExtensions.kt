package co.sodalabs.updaterengine.utils

import android.content.Context
import android.content.pm.PackageManager

val Context.versionCode: Int
    get() {
        try {
            return packageManager.getPackageInfo(packageName, 0).versionCode
        } catch (ignored: PackageManager.NameNotFoundException) {
        }
        return 0
    }

fun PackageManager.versionCodeForPackage(packageName: String): Int {
    return try {
        val packageInfo = this.getPackageInfo(packageName, 0)
        packageInfo.versionCode
    } catch (ignored: PackageManager.NameNotFoundException) {
        0
    }
}