// IPrivilegedService.aidl
package co.sodalabs.privilegedinstaller;

import co.sodalabs.privilegedinstaller.IPrivilegedCallback;

interface IPrivilegedService {

    boolean hasPrivilegedPermissions();

    /**
    * - Docs based on PackageManager.installPackage()
    * - Asynchronous (oneway) IPC calls!
    *
    * Install a package. Since this may take a little while, the result will
    * be posted back to the given callback. An installation will fail if the
    * package named in the package file's manifest is already installed, or if there's no space
    * available on the device.
    *
    * @param packageURI The location of the package file to install.  This can be a 'file:' or a
    * 'content:' URI.
    * @param flags - possible values: {@link #INSTALL_FORWARD_LOCK},
    * {@link #INSTALL_REPLACE_EXISTING}, {@link #INSTALL_ALLOW_TEST}.
    * @param installerPackageName Optional package name of the application that is performing the
    * installation. This identifies which market the package came from.
    * @param callback An callback to get notified when the package installation is
    * complete.
    */
    oneway void installPackage(in Uri packageURI, in int flags, in String installerPackageName,
                    in IPrivilegedCallback callback);


    /**
    * - Docs based on PackageManager.deletePackage()
    * - Asynchronous (oneway) IPC calls!
    *
    * Attempts to delete a package.  Since this may take a little while, the result will
    * be posted back to the given observer.  A deletion will fail if the
    * named package cannot be found, or if the named package is a "system package".
    *
    * @param packageName The name of the package to delete
    * @param flags - possible values: {@link #DELETE_KEEP_DATA},
    * {@link #DELETE_ALL_USERS}.
    * @param callback An callback to get notified when the package deletion is
    * complete.
    */
    oneway void deletePackage(in String packageName, in int flags, in IPrivilegedCallback callback);
}
