package co.sodalabs.apkupdater.feature.remoteConfig

import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class RemoteConfig(
    @Json(name = "timezone")
    val timezone: String,
    @Json(name = "install_window_start")
    val installWindowStart: Int,
    @Json(name = "install_window_end")
    val installWindowEnd: Int,
    @Json(name = "download_using_cache")
    val downloadUsingDiskCache: Boolean,
    @Json(name = "force_full_firmware_update")
    val forceFullFirmwareUpdate: Boolean,
    @Json(name = "allow_downgrade_app")
    val allowDowngradeApp: Boolean
)