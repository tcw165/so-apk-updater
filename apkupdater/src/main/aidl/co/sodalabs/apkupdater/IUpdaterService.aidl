// IPrivilegedService.aidl
package co.sodalabs.apkupdater;

import co.sodalabs.apkupdater.IUpdaterCallback;

interface IUpdaterService {

    long getCheckIntervalSecs();
    oneway void setCheckIntervalSecs(in long intervalSecs);

    long getInstallStartHourOfDay();
    oneway void setInstallStartHourOfDay(in int startHourOfDay);
    long getInstallEndHourOfDay();
    oneway void setInstallEndHourOfDay(in int endHourOfDay);

    void addUpdaterListener(in IUpdaterCallback listener);
    void removeUpdaterListener(in IUpdaterCallback listener);
}