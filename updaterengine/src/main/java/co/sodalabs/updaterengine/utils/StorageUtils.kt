package co.sodalabs.updaterengine.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Environment.MEDIA_MOUNTED
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Taken from Universal Image Loader, because that is what F-Droid did.
 *
 * https://github.com/nostra13/Android-Universal-Image-Loader/blob/master/library/src/main/java/com/nostra13/universalimageloader/utils/StorageUtils.java
 */
object StorageUtils {

    private const val EXTERNAL_STORAGE_PERMISSION = "android.permission.WRITE_EXTERNAL_STORAGE"

    /**
     * Returns application cache directory. Cache directory will be created on SD card
     * *("/Android/data/[app_package_name]/cache")* if card is mounted and app has appropriate permission. Else -
     * Android defines cache directory on device's file system.
     *
     * @param context Application context
     * @return Cache [directory][File].<br></br>
     * **NOTE:** Can be null in some unpredictable cases (if SD card is unmounted and
     * [Context.getCacheDir()][android.content.Context.getCacheDir] returns null).
     */
    fun getCacheDirectory(context: Context): File {
        return getCacheDirectory(context, true)
    }

    /**
     * Returns application cache directory. Cache directory will be created on SD card
     * *("/Android/data/[app_package_name]/cache")* (if card is mounted and app has appropriate permission) or
     * on device's file system depending incoming parameters.
     *
     * @param context Application context
     * @param preferExternal Whether prefer external location for cache
     * @return Cache [directory][File].<br></br>
     * **NOTE:** Can be null in some unpredictable cases (if SD card is unmounted and
     * [Context.getCacheDir()][android.content.Context.getCacheDir] returns null).
     */
    @SuppressLint("SdCardPath")
    fun getCacheDirectory(context: Context, preferExternal: Boolean): File {
        var appCacheDir: File? = null
        val externalStorageState = try {
            Environment.getExternalStorageState()
        } catch (e: NullPointerException) {
            // (sh)it happens (Issue #660)
            ""
        } catch (e: IncompatibleClassChangeError) {
            // (sh)it happens too (Issue #989)
            ""
        }

        if (preferExternal && MEDIA_MOUNTED == externalStorageState && hasExternalStoragePermission(context)) {
            appCacheDir = getExternalCacheDir(context)
        }

        if (appCacheDir == null) {
            appCacheDir = context.cacheDir
        }

        if (appCacheDir == null) {
            val cacheDirPath = "/data/data/" + context.packageName + "/cache/"
            Timber.w("Can't define system cache directory! '%s' will be used.", cacheDirPath)
            appCacheDir = File(cacheDirPath)
        }

        return appCacheDir
    }

    private fun getExternalCacheDir(context: Context): File? {
        val dataDir = File(File(Environment.getExternalStorageDirectory(), "Android"), "data")
        val appCacheDir = File(File(dataDir, context.packageName), "cache")
        if (!appCacheDir.exists()) {
            if (!appCacheDir.mkdirs()) {
                Timber.w("Unable to create external cache directory")
                return null
            }
            try {
                File(appCacheDir, ".nomedia").createNewFile()
            } catch (e: IOException) {
                Timber.e(e, "Can't create \".nomedia\" file in application external cache directory")
            }
        }
        return appCacheDir
    }

    private fun hasExternalStoragePermission(context: Context): Boolean {
        val perm = context.checkCallingOrSelfPermission(EXTERNAL_STORAGE_PERMISSION)
        return perm == PackageManager.PERMISSION_GRANTED
    }
}