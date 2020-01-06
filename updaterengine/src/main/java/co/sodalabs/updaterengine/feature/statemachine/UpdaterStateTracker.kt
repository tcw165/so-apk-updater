package co.sodalabs.updaterengine.feature.statemachine

import android.content.Context
import co.sodalabs.updaterengine.IPackageVersionProvider
import co.sodalabs.updaterengine.ISharedSettings
import co.sodalabs.updaterengine.ISystemProperties
import co.sodalabs.updaterengine.SharedSettingsProps
import co.sodalabs.updaterengine.UpdaterConfig
import co.sodalabs.updaterengine.UpdaterState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

// General
const val KEY_TRANSITION = "transition"
const val KEY_NEXT_CHECK_TIME = "next_check_time"
const val KEY_CHECK_INTERVAL = "check_interval"

const val PROP_DEVICE_ID = "device_id"
const val PROP_FIRMWARE_VERSION = "firmware_version"
const val PROP_UPDATER_VERSION = "updater_version"
const val PROP_INSTALL_WINDOW = "install_window"
const val PROP_BASE_URL = "base_url"

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

class UpdaterStateTracker @Inject constructor(
    private val context: Context,
    private val config: UpdaterConfig,
    private val sharedSettings: ISharedSettings,
    private val systemProperties: ISystemProperties,
    private val packageVersionProvider: IPackageVersionProvider
) : IUpdaterStateTracker {

    private val internalState = AtomicReference<UpdaterState>(UpdaterState.Init)
    private val internalStateMetadata: MutableMap<String, String> = ConcurrentHashMap()
    private val lock = Any()

    override fun snapshotState(): UpdaterState {
        return synchronized(lock) {
            internalState.get()
        }
    }

    override fun snapshotStateWithMetadata(): Pair<UpdaterState, Map<String, String>> {
        return synchronized(lock) {
            Pair(internalState.get(), internalStateMetadata.toMap())
        }
    }

    override fun putState(
        state: UpdaterState,
        metadata: Map<String, String>
    ) {
        synchronized(lock) {
            internalState.set(state)

            // Prepare the common metadata.
            internalStateMetadata.clear()
            internalStateMetadata.putAll(
                mapOf(
                    KEY_TRANSITION to state.name,
                    PROP_INSTALL_WINDOW to config.installWindow.toString(),
                    PROP_DEVICE_ID to getDeviceID(),
                    PROP_FIRMWARE_VERSION to getFirmwareVersion(),
                    PROP_UPDATER_VERSION to getPackageVersion(),
                    PROP_BASE_URL to getBaseUrl()
                )
            )
            // Append the given metadata.
            internalStateMetadata.putAll(metadata)
        }
    }

    override fun addStateMetadata(
        metadata: Map<String, String>
    ) {
        synchronized(lock) {
            internalStateMetadata.putAll(metadata)
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

    private fun getBaseUrl(): String {
        return sharedSettings.getGlobalString(SharedSettingsProps.SPARKPOINT_REST_API_BASE_URL, "No base url set!")
    }
}