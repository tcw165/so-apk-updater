package co.sodalabs.updaterengine.data

import android.os.Parcelable
import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.android.parcel.Parcelize

@Keep
@Parcelize
@JsonClass(generateAdapter = true)
data class FirmwareUpdate(
    @Json(name = "version")
    val version: String,
    @Json(name = "is_incremental")
    val isIncremental: Boolean,
    @Json(name = "file_url")
    val fileURL: String,
    @Json(name = "file_hash")
    val fileHash: String,
    @Json(name = "update_options")
    val updateOptions: String
) : Parcelable