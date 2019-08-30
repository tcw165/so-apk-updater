@file:Suppress("unused")

package co.sodalabs.apkupdater.feature.adminui

import co.sodalabs.updaterengine.data.AppUpdate

object FakeUpdates {

    val file170MB = listOf(
        AppUpdate(
            packageName = "co.sodalabs.test.1",
            downloadUrl = "https://sparkdatav0.blob.core.windows.net/apks/lru-test-1.apk",
            hash = "doesn't really matter",
            versionName = "0.0.0")
    )

    val filesTotal830MB = listOf(
        AppUpdate(
            packageName = "co.sodalabs.test.1",
            downloadUrl = "https://sparkdatav0.blob.core.windows.net/apks/lru-test-1.apk",
            hash = "doesn't really matter",
            versionName = "0.0.0"),
        AppUpdate(
            packageName = "co.sodalabs.test.2",
            downloadUrl = "https://sparkdatav0.blob.core.windows.net/apks/lru-test-2.apk",
            hash = "doesn't really matter",
            versionName = "0.0.0"),
        AppUpdate(
            packageName = "co.sodalabs.test.3",
            downloadUrl = "https://sparkdatav0.blob.core.windows.net/apks/lru-test-3.apk",
            hash = "doesn't really matter",
            versionName = "0.0.0"),
        AppUpdate(
            packageName = "co.sodalabs.test.4",
            downloadUrl = "https://sparkdatav0.blob.core.windows.net/apks/lru-test-4.apk",
            hash = "doesn't really matter",
            versionName = "0.0.0"),
        AppUpdate(
            packageName = "co.sodalabs.test.5",
            downloadUrl = "https://sparkdatav0.blob.core.windows.net/apks/lru-test-5.apk",
            hash = "doesn't really matter",
            versionName = "0.0.0")
    )
}