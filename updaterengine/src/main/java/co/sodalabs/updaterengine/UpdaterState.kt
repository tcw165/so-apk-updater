package co.sodalabs.updaterengine

import androidx.annotation.Keep

@Keep
enum class UpdaterState(
    private val printedName: String
) {
    Idle("Idle"),
    Check("Check"),
    Download("Download"),
    Install("Install");

    override fun toString(): String {
        return "\"$printedName\""
    }
}