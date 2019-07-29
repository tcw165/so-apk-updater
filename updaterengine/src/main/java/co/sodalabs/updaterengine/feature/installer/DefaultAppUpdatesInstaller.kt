package co.sodalabs.updaterengine.feature.installer

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import co.sodalabs.privilegedinstaller.RxLocalBroadcastReceiver
import co.sodalabs.updaterengine.AppUpdatesInstaller
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.data.Apk
import io.reactivex.Completable

class DefaultAppUpdatesInstaller constructor(
    private val context: Context,
    private val schedulers: IThreadSchedulers
) : AppUpdatesInstaller {

    override fun install(
        apk: Apk
    ): Completable {
        // Install action.
        val installCompletable = launchInstallerService(apk)
        // Install result.
        val intentFilter = IntentFilter()
        intentFilter.addAction(IntentActions.ACTION_INSTALL_SUCCESSFULLY)
        intentFilter.addAction(IntentActions.ACTION_INSTALL_CANCELLED)
        intentFilter.addAction(IntentActions.ACTION_INSTALL_FAILED)
        val installResultCompletable = RxLocalBroadcastReceiver.bind(context, intentFilter)
            .filter {
                val expectPackageName = apk.fromUpdate.packageName
                val givenPackageName = it.getStringExtra(IntentActions.PROP_APP_PACKAGE_NAME)
                givenPackageName == expectPackageName
            }
            .map { peekInstallFailure(it) }
            .ignoreElements()

        return Completable.mergeArray(
            installResultCompletable,
            installCompletable)
    }

    private fun launchInstallerService(
        apk: Apk
    ): Completable {
        return Completable
            .fromAction {
                val localApkUri = Uri.fromFile(apk.file)
                val packageName = apk.fromUpdate.packageName
                InstallerService.install(
                    context,
                    localApkUri,
                    packageName)
            }
            .subscribeOn(schedulers.main())
    }

    private fun peekInstallFailure(
        intent: Intent
    ) {
        // TODO: Define install exception
        when (intent.action) {
            IntentActions.ACTION_INSTALL_CANCELLED,
            IntentActions.ACTION_INSTALL_FAILED -> {
                throw RuntimeException("Failed to install!")
            }
        }
    }
}