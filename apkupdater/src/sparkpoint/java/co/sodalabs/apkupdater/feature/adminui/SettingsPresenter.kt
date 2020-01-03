package co.sodalabs.apkupdater.feature.adminui

import Packages
import co.sodalabs.apkupdater.BuildConfig
import co.sodalabs.apkupdater.ISystemLauncherUtil
import co.sodalabs.updaterengine.IAppPreference
import co.sodalabs.updaterengine.IPackageVersionProvider
import co.sodalabs.updaterengine.ISharedSettings
import co.sodalabs.updaterengine.ISystemProperties
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.IWorkObserver
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.PreferenceProps
import co.sodalabs.updaterengine.UpdaterHeartBeater
import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.data.FirmwareUpdate
import co.sodalabs.updaterengine.exception.DeviceNotSetupException
import co.sodalabs.updaterengine.extension.ALWAYS_RETRY
import co.sodalabs.updaterengine.extension.smartRetryWhen
import co.sodalabs.updaterengine.feature.logPersistence.ILogsPersistenceLauncher
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val NOT_AVAILABLE_STRING = "n/a"

private const val PACKAGE_APK_UPDATER = BuildConfig.APPLICATION_ID
private const val PACKAGE_PRIVILEGED_INSTALLER = co.sodalabs.updaterengine.Packages.PRIVILEGED_EXTENSION_PACKAGE_NAME
private const val PACKAGE_SPARKPOINT = Packages.SPARKPOINT_PACKAGE_NAME

class SettingsPresenter @Inject constructor(
    private val screen: ISettingsScreen,
    private val heartbeater: UpdaterHeartBeater,
    private val pkgVersionProvider: IPackageVersionProvider,
    private val appPreference: IAppPreference,
    private val sharedSettings: ISharedSettings,
    private val systemProperties: ISystemProperties,
    private val logsPersistenceLauncher: ILogsPersistenceLauncher,
    private val workObserver: IWorkObserver,
    private val systemLauncherUtil: ISystemLauncherUtil,
    private val schedulers: IThreadSchedulers
) {

    private val disposables = CompositeDisposable()

    private val caughtErrorRelay = PublishRelay.create<Throwable>().toSerialized()

    fun resume() {
        screen.setupBaseURLPreference(BuildConfig.BASE_URLS)
        screen.setupUpdateChannelPreference(BuildConfig.UPDATE_CHANNELS)
        observeVersions()
        observeHeartBeatNowClicks()
        observeRecurringHeartBeat()
        observeCheckUpdateNowClicks()
        observeOtherButtonClicks()
        observeCaughtErrors()
    }

    fun pause() {
        disposables.clear()
    }

    // Versions ///////////////////////////////////////////////////////////////

    private fun observeVersions() {
        observeVersionsRelatedInformation()
            .startWith(Unit)
            .switchMapSingle { getVersionsSingle() }
            .observeOn(schedulers.main())
            .subscribe({ versionsString ->
                screen.updateVersionPrefSummary(versionsString)
            }, Timber::e)
            .addTo(disposables)
    }

    private fun observeVersionsRelatedInformation(): Observable<Unit> {
        // Upstream is the combination of initial trigger and the later change
        // trigger.
        return Observable
            .merge<Unit>(
                sharedSettings.observeDeviceId().map { Unit },
                appPreference.observeAnyChange().map { Unit }
            )
    }

    private fun getVersionsSingle(): Single<String> {
        return Single
            .fromCallable {
                val sb = StringBuilder()

                val deviceID = try {
                    sharedSettings.getDeviceId()
                } catch (securityError: SecurityException) {
                    "don't have permission"
                }
                sb.appendln("Device ID: '$deviceID'")
                sb.appendln("Hardware ID: '${sharedSettings.getHardwareId()}'")
                sb.appendln("Firmware Version: '${systemProperties.getFirmwareVersion()}'")

                sb.appendln("Updater Version: '${pkgVersionProvider.getPackageVersion(PACKAGE_APK_UPDATER)}'")
                sb.appendln("Updater Build Hash: '${BuildConfig.GIT_SHA}'")
                sb.appendln("Updater Build Type: '${BuildConfig.BUILD_TYPE}'")

                sb.appendln("Privileged Installer Version: '${pkgVersionProvider.getPackageVersion(PACKAGE_PRIVILEGED_INSTALLER)}'")
                sb.appendln("Sparkpoint Player Version: '${pkgVersionProvider.getPackageVersion(PACKAGE_SPARKPOINT)}'")
                sb.toString()
            }
            .subscribeOn(schedulers.io())
    }

    // Heart Beat /////////////////////////////////////////////////////////////

    private fun observeHeartBeatNowClicks() {
        screen.sendHeartBeatNowPrefClicks
            .observeOn(schedulers.main())
            .flatMap {
                heartbeater.sendHeartBeatNow()
                screen.sendHeartBeatNowBroadcast()
                    .subscribeOn(schedulers.main())
            }
            .smartRetryWhen(ALWAYS_RETRY, Intervals.RETRY_AFTER_1S, schedulers.computation()) { error ->
                if (error is DeviceNotSetupException) {
                    false
                } else {
                    caughtErrorRelay.accept(error)
                    true
                }
            }
            .observeOn(schedulers.main())
            .subscribe({ uiState ->
                screen.updateHeartBeatNowPrefState(uiState)
            }, Timber::e)
            .addTo(disposables)
    }

    private fun observeRecurringHeartBeat() {
        appPreference.observeStringChange(PreferenceProps.HEARTBEAT_VERBAL_RESULT, NOT_AVAILABLE_STRING)
            .startWith(getFirstHeartbeatVerbalResult().toObservable())
            .smartRetryWhen(ALWAYS_RETRY, Intervals.RETRY_AFTER_1S, schedulers.computation()) { error ->
                if (error is DeviceNotSetupException) {
                    false
                } else {
                    screen.markHeartBeatDone()
                    caughtErrorRelay.accept(error)
                    true
                }
            }
            .observeOn(schedulers.main())
            .subscribe({ verbalResult ->
                screen.updateHeartBeatPrefTitle(verbalResult)
            }, Timber::e)
            .addTo(disposables)
    }

    private fun getFirstHeartbeatVerbalResult(): Single<String> {
        return Single
            .fromCallable {
                appPreference.getString(PreferenceProps.HEARTBEAT_VERBAL_RESULT, NOT_AVAILABLE_STRING)
            }
            .subscribeOn(schedulers.io())
    }

    // Check Update ///////////////////////////////////////////////////////////

    @Suppress("USELESS_CAST")
    private fun observeCheckUpdateNowClicks() {
        screen.checkUpdateNowPrefClicks
            .observeOn(schedulers.main())
            .flatMap {
                screen.sendHeartBeatCompleteBroadcast()
                    .subscribeOn(schedulers.main())
            }
            .smartRetryWhen(ALWAYS_RETRY, Intervals.RETRY_AFTER_1S, schedulers.computation()) { error ->
                screen.markCheckUpdateDone()
                caughtErrorRelay.accept(error)
                true
            }
            .observeOn(schedulers.main())
            .subscribe({ uiState ->
                screen.updateHeartBeatCheckPrefState(uiState)
            }, Timber::e)
            .addTo(disposables)

        screen.observeUpdaterBroadcasts()
            .observeOn(schedulers.main())
            .subscribe({ intent ->
                when (intent.action) {
                    IntentActions.ACTION_CHECK_UPDATE -> screen.updateUpdateStatusPrefSummary("Check")
                    IntentActions.ACTION_CHECK_APP_UPDATE_COMPLETE -> {
                        val updates = intent.getParcelableArrayListExtra<AppUpdate>(IntentActions.PROP_FOUND_UPDATES)
                        val message = if (updates.isEmpty()) {
                            "Check... No updates found"
                        } else {
                            "Check... found available app updates"
                        }
                        screen.updateUpdateStatusPrefSummary(message)
                    }
                    IntentActions.ACTION_CHECK_FIRMWARE_UPDATE_COMPLETE -> {
                        val foundUpdate = intent.getParcelableExtra<FirmwareUpdate>(IntentActions.PROP_FOUND_UPDATE)
                        screen.updateUpdateStatusPrefSummary("Check... found available firmware update, '${foundUpdate.fileURL}'")
                    }
                    IntentActions.ACTION_CHECK_FIRMWARE_UPDATE_ERROR -> {
                        val error = intent.getSerializableExtra(IntentActions.PROP_ERROR) as Throwable
                        screen.updateUpdateStatusPrefSummary("Check... failed for firmware, $error")
                    }

                    // Download section
                    IntentActions.ACTION_DOWNLOAD_APP_UPDATE -> {
                        screen.updateUpdateStatusPrefSummary("Download... apps")
                    }
                    IntentActions.ACTION_DOWNLOAD_APP_UPDATE_PROGRESS -> {
                        val update = intent.getParcelableExtra<AppUpdate>(IntentActions.PROP_FOUND_UPDATE)
                        val progressPercentage = intent.getIntExtra(IntentActions.PROP_PROGRESS_PERCENTAGE, 0)
                        screen.updateUpdateStatusPrefSummary("Download... '${update.packageName}' at $progressPercentage%")
                    }
                    IntentActions.ACTION_DOWNLOAD_APP_UPDATE_COMPLETE -> {
                        val update = intent.getParcelableExtra<AppUpdate?>(IntentActions.PROP_FOUND_UPDATE)
                        update?.let {
                            screen.updateUpdateStatusPrefSummary("Download... '${it.packageName}' finished")
                        }
                    }
                    IntentActions.ACTION_DOWNLOAD_FIRMWARE_UPDATE -> {
                        screen.updateUpdateStatusPrefSummary("Download... firmware")
                    }
                    IntentActions.ACTION_DOWNLOAD_FIRMWARE_UPDATE_PROGRESS -> {
                        val update = intent.getParcelableExtra<FirmwareUpdate>(IntentActions.PROP_FOUND_UPDATE)
                        val progressPercentage = intent.getIntExtra(IntentActions.PROP_PROGRESS_PERCENTAGE, 0)
                        screen.updateUpdateStatusPrefSummary("Download... '${update.fileURL}' at $progressPercentage%")
                    }
                    IntentActions.ACTION_DOWNLOAD_FIRMWARE_UPDATE_COMPLETE -> {
                        val update = intent.getParcelableExtra<FirmwareUpdate?>(IntentActions.PROP_FOUND_UPDATE)
                        update?.let {
                            screen.updateUpdateStatusPrefSummary("Download... '${it.fileURL}' finished")
                        }
                    }

                    // Install section
                    IntentActions.ACTION_INSTALL_APP_UPDATE -> {
                        screen.updateUpdateStatusPrefSummary("Install... downloaded apps")
                    }
                    IntentActions.ACTION_INSTALL_APP_UPDATE_COMPLETE -> {
                        screen.updateUpdateStatusPrefSummary("Idle")
                    }
                    IntentActions.ACTION_INSTALL_FIRMWARE_UPDATE -> {
                        screen.updateUpdateStatusPrefSummary("Install... downloaded firmware")
                    }
                    IntentActions.ACTION_INSTALL_FIRMWARE_UPDATE_COMPLETE -> {
                        screen.updateUpdateStatusPrefSummary("Idle")
                    }
                    IntentActions.ACTION_INSTALL_FIRMWARE_UPDATE_ERROR -> {
                        screen.updateUpdateStatusPrefSummary("Install... failed for firmware")
                    }
                }
            }, Timber::e)
            .addTo(disposables)
    }

    // Error //////////////////////////////////////////////////////////////////

    private fun observeCaughtErrors() {
        caughtErrorRelay
            .observeOn(schedulers.main())
            .subscribe({ error ->
                screen.showErrorMessage(error)
            }, Timber::e)
            .addTo(disposables)
    }

    // Other buttons //////////////////////////////////////////////////////////

    private fun observeOtherButtonClicks() {
        screen.showAndroidSettingsPrefClicks
            .observeOn(schedulers.main())
            .subscribe({
                screen.goToAndroidSettings()
            }, Timber::e)
            .addTo(disposables)

        screen.homeIntentPrefClicks
            .observeOn(schedulers.io())
            .subscribe({
                systemLauncherUtil.startSystemLauncherWithSelector()
            }, { error ->
                screen.showErrorMessage(error)
                Timber.e(error)
            })
            .addTo(disposables)

        screen.internetSpeedTestPrefClicks
            .observeOn(schedulers.main())
            .subscribe({
                screen.goToSpeedTest()
            }, Timber::e)
            .addTo(disposables)

        screen.sendLogsPrefClicks
            .debounce(Intervals.DEBOUNCE_CLICKS, TimeUnit.MILLISECONDS, schedulers.computation())
            .observeOn(schedulers.main()) // For the following 'switchMap'
            .switchMapSingle { backupLogToCloudNow() }
            .observeOn(schedulers.main())
            .subscribe({ success ->
                screen.showLogSendSuccessMessage(success)
            }, Timber::e)
            .addTo(disposables)
    }

    private fun backupLogToCloudNow(): Single<Boolean> {
        val requestID = logsPersistenceLauncher.backupLogToCloudNow()
        return workObserver.observeWorkSuccessByID(requestID)
    }
}