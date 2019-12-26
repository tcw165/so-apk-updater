package co.sodalabs.updaterengine.feature.installer

import androidx.annotation.Keep
import co.sodalabs.updaterengine.data.AppliedUpdate
import co.sodalabs.updaterengine.data.DownloadedAppUpdate

@Keep
data class InstallResult(
    val appliedUpdates: List<AppliedUpdate>,
    val failedUpdates: List<DownloadedAppUpdate>,
    val errorsToFailedUpdate: List<Throwable>
)