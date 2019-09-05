package co.sodalabs.updaterengine.data

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.android.parcel.Parcelize

@Keep
@Parcelize
data class AppliedUpdate(
    val packageName: String,
    val versionName: String
) : Parcelable