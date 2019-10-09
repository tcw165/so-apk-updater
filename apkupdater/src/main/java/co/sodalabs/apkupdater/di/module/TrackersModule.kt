@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.AndroidTouchTracker
import co.sodalabs.apkupdater.ITouchTracker
import co.sodalabs.apkupdater.di.scopes.ApplicationScope
import dagger.Binds
import dagger.Module

@Module
abstract class TrackersModule {

    @Binds
    @ApplicationScope
    abstract fun provideTouchTracker(tracker: AndroidTouchTracker): ITouchTracker
}