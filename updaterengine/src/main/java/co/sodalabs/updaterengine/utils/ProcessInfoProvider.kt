package co.sodalabs.updaterengine.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import javax.inject.Inject

/**
 * This wrapper class provides process information such as PID, UID and Process Name
 */
class ProcessInfoProvider @Inject constructor(val context: Context) {

    fun getUidByPackage(packageName: String): Int {
        return Process.getUidForName(packageName)
    }

    fun getUidByPackage(packageNames: List<String>): List<Int> {
        return packageNames.map { getUidByPackage(it) }.toList()
    }

    fun getPidByPackage(packageName: String): Int? {
        return getRunningProcesses().firstOrNull { it.pkgList.contains(packageName) }?.pid
    }

    fun getPidByPackage(packageNames: List<String>): List<Int?> {
        return packageNames.map { getPidByPackage(it) }.toList()
    }

    fun getPackageNameByPid(pid: String) {
        TODO("To be implemented")
    }

    fun getProcessNameByPid() {
        TODO("To be implemented")
    }

    fun getRunningProcesses(): List<ActivityManager.RunningAppProcessInfo> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.runningAppProcesses
    }
}
