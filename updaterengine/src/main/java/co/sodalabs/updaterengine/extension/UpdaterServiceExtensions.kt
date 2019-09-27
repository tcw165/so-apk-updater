package co.sodalabs.updaterengine.extension

import android.content.Intent
import android.os.Parcelable
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.exception.CompositeException

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

fun <T : Parcelable> Intent.prepareFirmwareUpdateFound(
    intentAction: String,
    updates: T,
    error: Throwable? = null
) {
    this.apply {
        action = intentAction
        // Result
        putExtra(IntentActions.PROP_FOUND_UPDATE, updates)
        // Error
        error?.let {
            putExtra(IntentActions.PROP_ERROR, it)
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

fun <T : Parcelable, R : Parcelable> Intent.prepareFirmwareUpdateDownloaded(
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

fun <T : Parcelable> Intent.prepareUpdateInstalled(
    intentAction: String,
    appliedUpdates: List<T>,
    errors: List<Throwable>
) {
    this.apply {
        action = intentAction
        // Result
        putParcelableArrayListExtra(IntentActions.PROP_APPLIED_UPDATES, ArrayList(appliedUpdates))
        // Error
        if (errors.isNotEmpty()) {
            putExtra(IntentActions.PROP_ERROR, CompositeException(errors))
        }
    }
}

fun <T : Parcelable> Intent.prepareFirmwareUpdateInstalled(
    intentAction: String,
    appliedUpdate: T,
    error: Throwable? = null
) {
    this.apply {
        action = intentAction
        // Result
        putExtra(IntentActions.PROP_APPLIED_UPDATE, appliedUpdate)
        // Error
        error?.let {
            putExtra(IntentActions.PROP_ERROR, it)
        }
    }
}