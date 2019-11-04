package co.sodalabs.updaterengine.extension

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper

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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            packageInfo.versionCode
        } else {
            (packageInfo.longVersionCode and 0x0FFFFFFFF).toInt()
        }
    } catch (ignored: PackageManager.NameNotFoundException) {
        0
    }
}

fun Context.runOnUiThread(f: Context.() -> Unit) {
    if (Looper.getMainLooper().thread == Thread.currentThread()) {
        f()
    } else {
        Handler(Looper.getMainLooper()).post { f() }
    }
}