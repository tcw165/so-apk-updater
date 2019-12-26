package co.sodalabs.updaterengine.extension

import android.content.Intent
import android.os.Parcelable
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.data.DownloadedAppUpdate
import co.sodalabs.updaterengine.exception.CompositeException

fun Intent.prepareError(
    intentAction: String,
    error: Throwable
) {
    this.apply {
        action = intentAction
        // Error
        putExtra(IntentActions.PROP_ERROR, error)
    }
}

fun <T : Parcelable> Intent.prepareUpdateFound(
    intentAction: String,
    updates: List<T>,
    errors: List<Throwable>
) {
    this.apply {
        action = intentAction
        // Result
        putParcelableArrayListExtra(IntentActions.PROP_FOUND_UPDATES, ArrayList(updates))
        // Error
        if (errors.isNotEmpty()) {
            putExtra(IntentActions.PROP_ERROR, CompositeException(errors))
        }
    }
}

fun <T : Parcelable> Intent.prepareUpdateDownloadProgress(
    intentAction: String,
    update: T,
    percentageComplete: Int,
    currentBytes: Long,
    totalBytes: Long
) {
    this.apply {
        action = intentAction
        // Result
        putExtra(IntentActions.PROP_FOUND_UPDATE, update as Parcelable)
        // Progress
        putExtra(IntentActions.PROP_PROGRESS_PERCENTAGE, percentageComplete)
        putExtra(IntentActions.PROP_DOWNLOAD_CURRENT_BYTES, currentBytes)
        putExtra(IntentActions.PROP_DOWNLOAD_TOTAL_BYTES, totalBytes)
    }
}

fun <T : Parcelable, R : Parcelable> Intent.prepareUpdateDownloaded(
    intentAction: String,
    foundUpdates: List<T>,
    downloadedUpdates: List<R>,
    errors: List<Throwable>
) {
    this.apply {
        action = intentAction
        // Result
        putParcelableArrayListExtra(IntentActions.PROP_FOUND_UPDATES, ArrayList(foundUpdates))
        putParcelableArrayListExtra(IntentActions.PROP_DOWNLOADED_UPDATES, ArrayList(downloadedUpdates))
        // Error
        if (errors.isNotEmpty()) {
            putExtra(IntentActions.PROP_ERROR, CompositeException(errors))
        }
    }
}

fun <T : Parcelable> Intent.prepareUpdateInstalled(
    intentAction: String,
    appliedUpdates: List<T>,
    failedUpdates: List<DownloadedAppUpdate>,
    errorsToFailedUpdate: List<Throwable>
) {
    this.apply {
        action = intentAction
        // Result
        putParcelableArrayListExtra(IntentActions.PROP_APPLIED_UPDATES, ArrayList(appliedUpdates))
        putParcelableArrayListExtra(IntentActions.PROP_NOT_APPLIED_UPDATES, ArrayList(failedUpdates))
        // Error
        if (errorsToFailedUpdate.isNotEmpty()) {
            putExtra(IntentActions.PROP_ERROR, CompositeException(errorsToFailedUpdate))
        }
    }
}

// Firmware OTA Specific //////////////////////////////////////////////////////

fun <T : Parcelable> Intent.prepareFirmwareUpdateCheckComplete(
    intentAction: String,
    updates: T
) {
    this.apply {
        action = intentAction
        // Result
        putExtra(IntentActions.PROP_FOUND_UPDATE, updates)
    }
}

fun <T : Parcelable, R : Parcelable> Intent.prepareFirmwareUpdateDownloadComplete(
    intentAction: String,
    foundUpdates: T,
    downloadedUpdates: R,
    error: Throwable? = null
) {
    this.apply {
        action = intentAction
        // Result
        putExtra(IntentActions.PROP_FOUND_UPDATE, foundUpdates)
        putExtra(IntentActions.PROP_DOWNLOADED_UPDATE, downloadedUpdates)
        // Error
        error?.let {
            putExtra(IntentActions.PROP_ERROR, it)
        }
    }
}

fun <T : Parcelable> Intent.prepareFirmwareUpdateDownloadError(
    intentAction: String,
    foundUpdates: T,
    error: Throwable
) {
    this.apply {
        action = intentAction
        // Original request
        putExtra(IntentActions.PROP_FOUND_UPDATE, foundUpdates)
        // Error
        putExtra(IntentActions.PROP_ERROR, error)
    }
}

fun <T : Parcelable> Intent.prepareFirmwareUpdateInstallComplete(
    intentAction: String,
    appliedUpdate: T
) {
    this.apply {
        action = intentAction
        // Result
        putExtra(IntentActions.PROP_APPLIED_UPDATE, appliedUpdate)
    }
}