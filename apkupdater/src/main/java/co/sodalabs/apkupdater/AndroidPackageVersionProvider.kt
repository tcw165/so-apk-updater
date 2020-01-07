package co.sodalabs.apkupdater

import Packages
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import co.sodalabs.updaterengine.IAppPreference
import co.sodalabs.updaterengine.IPackageVersionProvider
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.PreferenceProps
import co.sodalabs.updaterengine.RxBroadcastReceiver
import co.sodalabs.updaterengine.extension.ensureBackgroundThread
import co.sodalabs.updaterengine.extension.ensureMainThread
import io.reactivex.Observable
import javax.inject.Inject

private const val EMPTY_STRING = ""

class AndroidPackageVersionProvider @Inject constructor(
    private val applicationContext: Context,
    private val packageManager: PackageManager,
    private val appPreference: IAppPreference,
    private val schedulers: IThreadSchedulers
) : IPackageVersionProvider {

    override fun getPackageVersion(
        packageName: String
    ): String {
        ensureBackgroundThread()

        val debugVersion: String? = when (packageName) {
            Packages.SPARKPOINT_PACKAGE_NAME -> {
                val debugVersion = appPreference.getString(PreferenceProps.MOCK_SPARKPOINT_VERSION, EMPTY_STRING)
                if (debugVersion.isNotBlank()) debugVersion else null
            }
            else -> null
        }

        return debugVersion ?: getActualPackageVersion(packageName)
    }

    override fun observePackageChanges(): Observable<Unit> {
        ensureMainThread()

        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
        }
        return RxBroadcastReceiver.bind(applicationContext, intentFilter, captureStickyIntent = false)
            .map { Unit }
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