package co.sodalabs.apkupdater

import Packages
import android.content.pm.PackageManager
import co.sodalabs.updaterengine.IAppPreference
import co.sodalabs.updaterengine.IPackageVersionProvider
import co.sodalabs.updaterengine.PreferenceProps
import co.sodalabs.updaterengine.extension.ensureBackgroundThread
import javax.inject.Inject

private const val EMPTY_STRING = ""

class AndroidPackageVersionProvider @Inject constructor(
    private val packageManager: PackageManager,
    private val appPreference: IAppPreference
) : IPackageVersionProvider {

    override fun getPackageVersion(
        packageName: String
    ): String {
        ensureBackgroundThread()

        val debugVersion: String? = when (packageName) {
            Packages.SPARKPOINT_PACKAGE_NAME -> {
                val debugVersion = appPreference.getString(PreferenceProps.MOCK_SPARKPOINT_VERSION, EMPTY_STRING)
                if (debugVersion == EMPTY_STRING) {
                    debugVersion
                } else {
                    null
                }
            }
            else -> null
        }

        return debugVersion ?: getActualPackageVersion(packageName)
    }

    private fun getActualPackageVersion(
        packageName: String
    ): String {
        return try {
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            info.versionName
        } catch (error: PackageManager.NameNotFoundException) {
            // The package is not installed (or deleted)
            EMPTY_STRING
        }
    }
}