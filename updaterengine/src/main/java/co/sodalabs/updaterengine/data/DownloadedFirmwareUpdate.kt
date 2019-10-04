package co.sodalabs.updaterengine.data

import android.os.Parcelable
import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.android.parcel.Parcelize
import java.io.File

@Keep
@Parcelize
@JsonClass(generateAdapter = true)
data class DownloadedFirmwareUpdate(
    @Json(name = "file")
    val file: File,
    @Json(name = "from_update")
    val fromUpdate: FirmwareUpdate
) : Parcelable