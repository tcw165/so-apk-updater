package co.sodalabs.updaterengine.feature.statemachine

import android.content.Context
import co.sodalabs.updaterengine.IPackageVersionProvider
import co.sodalabs.updaterengine.ISharedSettings
import co.sodalabs.updaterengine.ISystemProperties
import co.sodalabs.updaterengine.UpdaterConfig
import co.sodalabs.updaterengine.UpdaterState
import javax.inject.Inject

// General
const val KEY_TRANSITION = "transition"
const val KEY_NEXT_CHECK_TIME = "next_check_time"
const val KEY_CHECK_INTERVAL = "check_interval"

const val PROP_DEVICE_ID = "device_id"
const val PROP_FIRMWARE_VERSION = "firmware_version"
const val PROP_UPDATER_VERSION = "updater_version"
const val PROP_INSTALL_WINDOW = "install_window"

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
const val KEY_DOWNLOAD_DELAY = "download_delay"

const val PROP_TYPE_APP = "app"
const val PROP_TYPE_FIRMWARE = "firmware"
const val PROP_CURRENT_TIME = "current_time"
// Install

const val KEY_INSTALL_TYPE = "install_type"
const val KEY_INSTALL_RUNNING = "install_running"
const val KEY_INSTALL_RESULT = "install_result"
const val KEY_INSTALL_ERROR = "install_error"

const val KEY_INSTALL_DELAY = "install_delay"
const val KEY_INSTALL_AT = "install_at"

class UpdaterStateMachine @Inject constructor(
    private val context: Context,
    private val config: UpdaterConfig,
    private val sharedSettings: ISharedSettings,
    private val systemProperties: ISystemProperties,
    private val packageVersionProvider: IPackageVersionProvider
) : IUpdaterStateMachine {

    override val state: UpdaterState
        get() = internalState

    override val metadata: Map<String, Any>
        get() = synchronized(lock) {
            mutableMetadata.toMap()
        }

    @Volatile
    private var internalState = UpdaterState.Idle

    private val mutableMetadata = HashMap<String, Any>()
    private val lock = Any()

    override fun putState(state: UpdaterState) {
        synchronized(lock) {
            internalState = state
            mutableMetadata.clear()
            addMetadata(
                mapOf(
                    KEY_TRANSITION to state.name,
                    PROP_INSTALL_WINDOW to config.installWindow.toString(),
                    PROP_DEVICE_ID to getDeviceID(),
                    PROP_FIRMWARE_VERSION to getFirmwareVersion(),
                    PROP_UPDATER_VERSION to getPackageVersion()
                )
            )
        }
    }

    override fun addMetadata(metadata: Map<String, Any>) {
        synchronized(lock) {
            mutableMetadata.putAll(metadata)
        }
    }

    private fun getDeviceID(): String {
        return sharedSettings.getDeviceId()
    }

    private fun getFirmwareVersion(): String {
        return systemProperties.getFirmwareVersion()
    }

    private fun getPackageVersion(): String {
        return packageVersionProvider.getPackageVersion(context.packageName)
    }
}