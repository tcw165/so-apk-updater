package co.sodalabs.apkupdater.feature.remoteConfig

import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class RemoteConfig(
    @Json(name = "timezone")
    val timezone: String? = null,
    @Json(name = "install_window_start")
    val installWindowStart: Int? = null,
    @Json(name = "install_window_end")
    val installWindowEnd: Int? = null,
    @Json(name = "download_using_cache")
    val downloadUsingDiskCache: Boolean? = null,
    @Json(name = "force_full_firmware_update")
    val forceFullFirmwareUpdate: Boolean? = null,
    @Json(name = "allow_downgrade_app")
    val allowDowngradeApp: Boolean? = null,
    @Json(name = "update_check_interval")
    val updateCheckInterval: Long? = null,
    // The following fields are one-shot actions from server
    @Json(name = "enable_device_reboot")
    val toReboot: Boolean? = null
)