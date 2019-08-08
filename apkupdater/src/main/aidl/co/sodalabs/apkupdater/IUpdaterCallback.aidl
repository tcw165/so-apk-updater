// IPrivilegedCallback.aidl
package co.sodalabs.apkupdater;

interface IUpdaterCallback {

    void onCheckBegin();
    void onCheckFinishes(in int resultCode);

    void onDownloadBegin();
    void onDownloadInProgress(in int progress); // TODO: Multiple progresses?
    void onDownloadFinishes(in int resultCode);

    void onInstallBegin();
    void onInstallFinishes(in int resultCode); // TODO: Multiple apps?

    void onError(in int errorCode, in String error);
}