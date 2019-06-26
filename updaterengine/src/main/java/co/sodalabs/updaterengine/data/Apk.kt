package co.sodalabs.updaterengine.data

import android.net.Uri
import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.android.parcel.Parcelize

@Keep
@Parcelize
data class Apk(
    @JvmField val downloadUri: Uri,
    @JvmField val packageName: String,
    @JvmField val versionName: String,
    @JvmField val versionCode: Int,
    @JvmField val hash: String?,
    @JvmField val hashType: String = "sha256",
    @JvmField val apkName: String = packageName + "_" + versionCode + ".apk"
) : Parcelable {

    /**
     * Default to assuming apk if apkName is null since that has always been
     * what we had.
     *
     * @return true if this is an apk instead of a non-apk/media file
     */
    fun isApk(): Boolean {
        return this.apkName.endsWith(".apk")
    }
}