package co.sodalabs.updaterengine.installer

import android.content.Context
import android.net.Uri
import co.sodalabs.updaterengine.data.Apk
import timber.log.Timber
import java.io.IOException

/**
 * The URI where the APK was originally downloaded from. This is also used
 * as the unique ID representing this in the whole install process in
 * [InstallManagerService], there is is generally known as the
 * "download URL" since it is the URL used to download the APK.
 * @see Intent.EXTRA_ORIGINATING_URI
 */

/**
Handles the actual install process.  Subclasses implement the details.
 */
abstract class Installer(
    protected val context: Context,
    protected val apk: Apk
) {

    companion object {
        const val EXTRA_DOWNLOAD_URI = "co.sodalabs.apkupdater.installer.Installer.extra.DOWNLOAD_URI"
        const val EXTRA_APK = "co.sodalabs.apkupdater.installer.Installer.extra.APK"
    }

    /**
    Install apk given the URI that points to the local APK file, and the
    download URI to identify which session this belongs to.  This first
    moves the APK file to private directory for the installation process
    to read from.  Then the hash of the APK is checked against the
    {@link Apk} instance provided when this {@code Installer} object was
    instantiated.  The list of permissions in the APK file and the
    {@code Apk} instance are compared, if they do not match, then the user
    is prompted install the system installer dialog, which shows all the
    permissions that the APK is requesting.
    @param localApkUri points to the local copy of the APK to be installed
    @param downloadUri serves as the unique ID for all actions related to the
    installation of that specific APK
    @see InstallManagerService
    @see <a href="https://issuetracker.google.com/issues/37091886">ACTION_INSTALL_PACKAGE Fails For Any Possible Uri</a>
     */
    fun installPackage(localApkUri: Uri, downloadUri: Uri) {
        val sanitizedUri: Uri

        try {
            sanitizedUri = ApkFileProvider.getSafeUri(context, localApkUri, apk)
        } catch (e: IOException) {
            Timber.d(e)
            return
        }

        // TODO: Verify
        //        try {
        //            // verify that permissions of the apk file match the ones from the apk object
        //            val apkVerifier = ApkVerifier(context, localApkUri, apk)
        //            apkVerifier.verifyApk()
        //        } catch (e: ApkVerifier.ApkVerificationException) {
        //            Timber.d(e)
        //            sendBroadcastInstall(downloadUri, ACTION_INSTALL_INTERRUPTED, e.message)
        //            return
        //        } catch (e: ApkVerifier.ApkPermissionUnequalException) {
        //            // if permissions of apk are not the ones listed in the repo
        //            // and an unattended installer is used, a wrong permission screen
        //            // has been shown, thus fallback to AOSP DefaultInstaller!
        //            if (isUnattended()) {
        //                Timber.d(e)
        //                Timber.d("Falling back to AOSP DefaultInstaller!")
        //                val defaultInstaller = DefaultInstaller(context, apk)
        //                defaultInstaller.installPackageInternal(sanitizedUri, downloadUri)
        //                return
        //            }
        //        }

        Timber.d("Attempt Install Package: $sanitizedUri - $downloadUri")
        installPackageInternal(sanitizedUri, downloadUri)
    }

    abstract fun installPackageInternal(localApkUri: Uri, downloadUri: Uri)

    /**
     * Uninstall app as defined by [Installer.apk] in
     * [Installer.Installer]
     */
    abstract fun uninstallPackage()

    /**
     * This [Installer] instance is capable of "unattended" install and
     * uninstall activities, without the system enforcing a user prompt.
     */
    abstract fun isUnattended(): Boolean
}