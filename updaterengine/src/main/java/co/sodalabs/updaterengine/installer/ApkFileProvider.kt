package co.sodalabs.updaterengine.installer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import co.sodalabs.updaterengine.data.Apk
import co.sodalabs.updaterengine.data.SanitizedFile
import co.sodalabs.updaterengine.net.ApkCache
import java.io.File
import java.io.IOException

/**
 * Helper methods for preparing APKs and arbitrary files for installation,
 * either locally or for sending via bluetooth.
 * <p/>
 * APK handling for installations:
 * <ol>
 * <li>APKs are downloaded into a cache directory that is either created on SD card
 * <i>"/Android/data/[app_package_name]/cache/apks"</i> (if card is mounted and app has
 * appropriate permission) or on device's file system depending incoming parameters</li>
 * <li>Before installation, the APK is copied into the private data directory of the F-Droid,
 * <i>"/data/data/[app_package_name]/files/install-$random.apk"</i></li>
 * <li>The hash of the file is checked against the expected hash from the repository</li>
 * <li>For {@link Build.VERSION_CODES#M < android-23}, a {@code file://} {@link Uri}
 * pointing to the {@link File} is returned, for {@link Build.VERSION_CODES#M >= android-23},
 * a {@code content://} {@code Uri} is returned using support lib's
 * {@link FileProvider}</li>
 * </ol>
 */
class ApkFileProvider : FileProvider() {

    companion object {

        private const val AUTHORITY_SUFFIX = ".installer.ApkFileProvider"

        /**
         * Copies the APK into private data directory of F-Droid and returns a
         * {@code file://} or {@code content://} URI to be used for the
         * actual installation process.  Only APKs will ever use a {@code content://}
         * URI, any other file will always use a {@code file://} URI since F-Droid
         * itself handles their whole installation process.
         */
        @Throws(IOException::class)
        fun getSafeUri(context: Context, localApkUri: Uri, expectedApk: Apk): Uri {
            val apkFile = File(localApkUri.path)
            val tempApkFile = ApkCache.copyApkFromCacheToFiles(context, apkFile, expectedApk)
            return getSafeUri(
                context, tempApkFile,
                Build.VERSION.SDK_INT >= 24 && expectedApk.isApk()
            )
        }

        /**
         * Return a {@link Uri} for all install processes to install this package
         * from.  This supports APKs and all other supported files.  It also
         * supports all installation methods, e.g. default, privileged, etc.
         * It can return either a {@code content://} or {@code file://} URI.
         * <p>
         * APKs need to be world readable, so that the Android system installer
         * is able to read it.  Saving it into external storage to send it to the
         * installer have access is insecure, because apps with permission to write
         * to the external storage can overwrite the app between F-Droid asking for
         * it to be installed and the installer actually installing it.
         */
        @SuppressLint("SetWorldReadable")
        private fun getSafeUri(context: Context, tempFile: SanitizedFile, useContentUri: Boolean): Uri {
            return if (useContentUri) {
                // TODO: Research if we need to inject
                val authority = context.packageName + AUTHORITY_SUFFIX
                val apkUri = getUriForFile(context, authority, tempFile)
                context.grantUriPermission(
                    PrivilegedInstaller.PRIVILEGED_EXTENSION_PACKAGE_NAME,
                    apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                apkUri
            } else {
                tempFile.setReadable(true, false)
                Uri.fromFile(tempFile)
            }
        }
    }
}