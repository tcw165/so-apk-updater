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
    @Json(name = "sequential_update_download_url")
    val sequentialUpdateDownloadURL: String,
    @Json(name = "full_update_download_url")
    val fullUpdateDownloadURL: String
    // TODO: Finish the definition
) : Parcelable