package co.sodalabs.updaterengine.extension

import android.content.pm.PackageManager
import co.sodalabs.updaterengine.data.AppUpdate
import timber.log.Timber

fun List<AppUpdate>.getIndicesToRemove(
    packageManager: PackageManager,
    allowDowngrade: Boolean
): List<Int> {
    val originalList = this
    val toTrimIndices = mutableListOf<Int>()
    for (i in originalList.indices) {
        val update = originalList[i]
        val packageName = update.packageName
        val remoteVersionName = update.versionName

        if (packageManager.isPackageInstalled(packageName)) {
            val localPackageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            val localVersionName = localPackageInfo.versionName

            if (allowDowngrade ||
                remoteVersionName.isGreaterThan(localVersionName, orEqualTo = allowDowngrade)) {
                Timber.v("[Check] \"$packageName\" from $localVersionName to $remoteVersionName ... allowed!")
            } else {
                // Sorry, we will discard this update cause the version isn't
                // greater than the current local version.
                toTrimIndices.add(i)
                Timber.v("[Check] \"$packageName\" from $localVersionName to $remoteVersionName ... skipped!")
            }
        } else {
            Timber.v("[Check] \"$packageName\" from null to $remoteVersionName ... allowed!")
        }
    }

    Timber.v("[Check] Filter invalid updates... drop ${toTrimIndices.size} updates")

    return toTrimIndices
}