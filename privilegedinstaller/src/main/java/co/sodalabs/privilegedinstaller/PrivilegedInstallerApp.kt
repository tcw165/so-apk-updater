@file:Suppress("unused")

package co.sodalabs.privilegedinstaller

import androidx.multidex.MultiDexApplication
import timber.log.Timber

class PrivilegedInstallerApp : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}