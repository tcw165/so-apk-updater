package co.sodalabs.apkupdater.feature.remoteConfig

interface IRemoteConfigSyncLauncher {
    fun applyRemoteConfigNow(config: RemoteConfig)
}