package co.sodalabs.updaterengine

interface IRebootHelper {
    fun rebootNormally()
    fun rebootToRecovery()
    fun isRebooting(): Boolean
    fun forceReboot()
}