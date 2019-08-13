// IPrivilegedCallback.aidl
package co.sodalabs.updaterengine;

interface IFirmwareCheckCallback {

    /**
     * Callback as the engine is retrieving for the updates from the server.
     */
    void onCheckingUpdate();
    /**
     * Callback as the engine found an update.
     */
    void onFoundUpdate(in int updateID, in String version);
    /**
     * Callback as the engine finishes the check and no available update.
     */
    void onCheckCompleteWithoutUpdate();
    /**
     * Callback as the engine throws error. e.g., Cannot hit the server.
     */
    void onError(in int errorCode, in String error);
}