package co.sodalabs.updaterengine

import androidx.annotation.Keep

@Keep
enum class UpdaterState {
    Init,
    Idle,
    Check,
    Download,
    Install
}