package co.sodalabs.updaterengine

import co.sodalabs.updaterengine.data.Apk
import co.sodalabs.updaterengine.data.AppUpdate
import io.reactivex.Single

interface AppUpdatesDownloader {

    fun download(
        updates: List<AppUpdate>
    ): Single<List<Apk>>
}