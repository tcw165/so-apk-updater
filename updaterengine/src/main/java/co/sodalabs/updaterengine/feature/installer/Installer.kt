package co.sodalabs.updaterengine.feature.installer

import android.content.Context
import co.sodalabs.updaterengine.data.AppliedUpdate
import co.sodalabs.updaterengine.data.DownloadedAppUpdate

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
    protected val context: Context
) {
    /**
    Install apk given the URI that points to the local APK file, and the
    download URI to identify which session this belongs to.  This first
    moves the APK file to private directory for the installation process
    to read from.  Then the hash of the APK is checked against the
    {@link DownloadedAppUpdate} instance provided when this {@code Installer} object was
    instantiated.  The list of permissions in the APK file and the
    {@code DownloadedAppUpdate} instance are compared, if they do not match, then the user
    is prompted install the system installer dialog, which shows all the
    permissions that the APK is requesting.
    @param fileURI points to the local copy of the APK to be installed
    @param downloadUri serves as the unique ID for all actions related to the
    installation of that specific APK
    @see InstallManagerService
    @see <a href="https://issuetracker.google.com/issues/37091886">ACTION_INSTALL_PACKAGE Fails For Any Possible Uri</a>
     */
    abstract fun installPackages(localUpdates: List<DownloadedAppUpdate>): Pair<List<AppliedUpdate>, List<Throwable>>

    /**
     * Uninstall app as defined by [Installer.apk] in
     * [Installer.Installer]
     */
    // TODO: Return Pair<List<AppliedUpdate>, List<Throwable>>
    abstract fun uninstallPackage(packageNames: List<String>)
}