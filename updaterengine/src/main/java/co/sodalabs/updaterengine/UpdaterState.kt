package co.sodalabs.updaterengine

import androidx.annotation.Keep

@Keep
enum class UpdaterState {
    Idle,
    Check,
    Download,
    Install
}