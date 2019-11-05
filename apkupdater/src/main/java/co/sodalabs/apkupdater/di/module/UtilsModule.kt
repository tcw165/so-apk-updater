@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.di.scopes.ApplicationScope
import co.sodalabs.updaterengine.ITimeUtil
import co.sodalabs.updaterengine.IZipUtil
import co.sodalabs.updaterengine.utils.AndroidTimeUtil
import co.sodalabs.updaterengine.utils.AndroidZipUtil
import dagger.Binds
import dagger.Module

@Module
abstract class UtilsModule {

    @Binds
    @ApplicationScope
    abstract fun provideTimeUtil(
        util: AndroidTimeUtil
    ): ITimeUtil

    @Binds
    @ApplicationScope
    abstract fun provideZipUtil(
        util: AndroidZipUtil
    ): IZipUtil
}