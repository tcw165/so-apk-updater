package co.sodalabs.privilegedinstaller

import android.support.multidex.MultiDexApplication
import timber.log.Timber

class PrivilegedInstallerApp : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}
