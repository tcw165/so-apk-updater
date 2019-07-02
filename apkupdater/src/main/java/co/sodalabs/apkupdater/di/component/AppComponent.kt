@file:Suppress("unused")

package co.sodalabs.apkupdater.di.component

import android.content.Context
import co.sodalabs.apkupdater.UpdaterApp
import co.sodalabs.apkupdater.UpdatesCheckerService
import co.sodalabs.apkupdater.di.ApplicationScope
import co.sodalabs.apkupdater.di.module.ApplicationContextModule
import co.sodalabs.apkupdater.di.module.ThreadSchedulersModule
import co.sodalabs.apkupdater.di.module.UpdaterModule
import co.sodalabs.updaterengine.IThreadSchedulers
import dagger.Component

@ApplicationScope
@Component(
    modules = [
        ApplicationContextModule::class,
        ThreadSchedulersModule::class,
        UpdaterModule::class
    ]
)
interface AppComponent {

    fun inject(app: UpdaterApp)
    fun inject(service: UpdatesCheckerService)

    fun getApplicationContext(): Context

    fun getSchedulers(): IThreadSchedulers
}