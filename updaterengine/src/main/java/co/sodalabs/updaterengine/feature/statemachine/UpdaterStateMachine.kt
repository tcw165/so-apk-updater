package co.sodalabs.updaterengine.feature.statemachine

import co.sodalabs.updaterengine.UpdaterState
import javax.inject.Inject

// General
const val KEY_TRANSITION = "transition"
const val KEY_NEXT_CHECK_TIME = "next_check_time"

// Check

const val KEY_CHECK_TYPE = "check_type"
const val KEY_CHECK_RUNNING = "check_running"
const val KEY_CHECK_RESULT = "check_result"
const val KEY_CHECK_ERROR = "check_error"

// Download

const val KEY_DOWNLOAD_RUNNING = "download_running"
const val KEY_DOWNLOAD_RESULT = "download_result"
const val KEY_DOWNLOAD_ERROR = "download_error"

const val KEY_DOWNLOAD_TYPE = "download_type"
const val KEY_PROGRESS_PERCENTAGE = "progress_percent"
const val KEY_PROGRESS_CURRENT_BYTES = "progress_current_bytes"
const val KEY_PROGRESS_TOTAL_BYTES = "progress_total_bytes"
const val KEY_DOWNLOAD_RETRY_ATTEMPT = "download_retry_attempt"
const val KEY_DOWNLOAD_RETRY_AT = "download_retry_at"

const val PROP_TYPE_APP = "app"
const val PROP_TYPE_FIRMWARE = "firmware"

// Install

const val KEY_INSTALL_TYPE = "install_type"
const val KEY_INSTALL_RUNNING = "install_running"
const val KEY_INSTALL_RESULT = "install_result"
const val KEY_INSTALL_ERROR = "install_error"

const val KEY_INSTALL_AT = "install_at"

class UpdaterStateMachine @Inject constructor() : IUpdaterStateMachine {

    override val state: UpdaterState
        get() = internalState

    override val metadata: Map<String, Any>
        get() = synchronized(metadataLock) {
            mutableMetadata.toMap()
        }

    @Volatile
    private var internalState = UpdaterState.Idle

    private val mutableMetadata = HashMap<String, Any>()
    private val stateLock = Any()
    private val metadataLock = Any()

    override fun putState(state: UpdaterState) {
        synchronized(stateLock) {
            internalState = state
            updateMetadata(KEY_TRANSITION to state.name)
        }
    }

    override fun updateMetadata(keyValue: Pair<String, Any>) {
        updateMetadata(mapOf(keyValue))
    }

    override fun updateMetadata(metadata: Map<String, Any>) {
        synchronized(metadataLock) {
            mutableMetadata.clear()
            mutableMetadata.putAll(metadata)
        }
    }
}