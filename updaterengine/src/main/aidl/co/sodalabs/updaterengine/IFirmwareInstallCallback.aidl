// IPrivilegedCallback.aidl
package co.sodalabs.updaterengine;

interface IFirmwareInstallCallback {

    /**
     * Callback as the engine is installing the update.
     */
    void onInstallingUpdate(in int updateID);
    /**
     * Callback as the engine installs the update successfully.
     */
    void onInstallComplete(in int updateID);
    /**
     * Callback as the engine throws error for installing the update.
     */
    void onError(in int updateID, in int errorCode, in String error);
}