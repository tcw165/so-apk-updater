package co.sodalabs.apkupdater.feature.adminui

import android.content.Intent
import co.sodalabs.apkupdater.data.UiState
import io.reactivex.Observable

interface ISettingsScreen {
    val sendHeartBeatNowPrefClicks: Observable<Unit>
    val checkUpdateNowPrefClicks: Observable<Unit>
    val showAndroidSettingsPrefClicks: Observable<Unit>
    val homeIntentPrefClicks: Observable<Unit>
    val internetSpeedTestPrefClicks: Observable<Unit>
    val sendLogsPrefClicks: Observable<Unit>

    fun updateVersionPrefSummary(versionsString: String?)
    fun setupBaseURLPreference(urls: Array<String>)
    fun setupUpdateChannelPreference(channels: Array<String>)
    fun updateHeartBeatPrefTitle(verbalResult: String?)
    fun sendHeartBeatNowBroadcast(): Observable<UiState<Int>>
    fun updateHeartBeatNowPrefState(state: UiState<Int>)
    fun markHeartBeatWIP()
    fun markHeartBeatDone()
    fun sendHeartBeatCompleteBroadcast(): Observable<UiState<Boolean>>
    fun markCheckUpdateWIP()
    fun markCheckUpdateDone()
    fun updateHeartBeatCheckPrefState(state: UiState<Boolean>)
    fun observeUpdaterBroadcasts(): Observable<Intent>
    fun updateUpdateStatusPrefSummary(message: String)
    fun showErrorMessage(error: Throwable)
    fun goToAndroidSettings()
    fun goToSpeedTest()
    fun showLogSendSuccessMessage(success: Boolean)
}