// IPrivilegedCallback.aidl
package co.sodalabs.privilegedinstaller;

interface IPrivilegedCallback {

    void handleResult(in String packageName, in int returnCode);
}