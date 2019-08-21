// IPrivilegedService.aidl
package co.sodalabs.updaterengine;

import co.sodalabs.updaterengine.IFirmwareCheckCallback;
import co.sodalabs.updaterengine.IFirmwareInstallCallback;

interface IUpdaterService {

    long getCheckIntervalSecs();
    oneway void setCheckIntervalSecs(in long intervalSecs);

    long getInstallStartHourOfDay();
    oneway void setInstallStartHourOfDay(in int startHourOfDay);
    long getInstallEndHourOfDay();
    oneway void setInstallEndHourOfDay(in int endHourOfDay);

    oneway void checkFirmwareUpdateNow(in IFirmwareCheckCallback callback);
    oneway void installFirmwareUpdate(in IFirmwareInstallCallback callback);
}