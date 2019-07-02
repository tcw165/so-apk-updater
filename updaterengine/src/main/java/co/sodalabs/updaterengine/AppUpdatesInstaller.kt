package co.sodalabs.updaterengine

import co.sodalabs.updaterengine.data.Apk
import io.reactivex.Completable

interface AppUpdatesInstaller {

    fun install(
        apk: Apk
    ): Completable
}