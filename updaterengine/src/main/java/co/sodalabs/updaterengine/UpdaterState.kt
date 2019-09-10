package co.sodalabs.updaterengine

import androidx.annotation.Keep
import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.data.DownloadedUpdate

@Keep
sealed class UpdaterState {

    @Keep
    object Idle : UpdaterState() {
        override fun toString(): String = "Idle"
    }

    @Keep
    data class Check(
        val packages: List<String>,
        val resetSession: Boolean
    ) : UpdaterState() {
        override fun toString(): String = "Check"
    }

    @Keep
    data class Download(
        val updates: List<AppUpdate>
    ) : UpdaterState() {
        override fun toString(): String = "Download"
    }

    @Keep
    data class Install(
        val updates: List<DownloadedUpdate>
    ) : UpdaterState() {
        override fun toString(): String = "Install"
    }
}