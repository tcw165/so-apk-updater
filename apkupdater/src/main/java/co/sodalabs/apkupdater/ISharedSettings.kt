package co.sodalabs.apkupdater

import co.sodalabs.updaterengine.feature.rx.InitialValueObservable

interface ISharedSettings {
    fun isDeviceProvisioned(): Boolean
    fun isUserSetupComplete(): Boolean

    fun getGlobalInt(key: String, default: Int): Int
    fun putGlobalInt(key: String, value: Int): Boolean
    fun observeGlobalInt(key: String, default: Int): InitialValueObservable<Int>

    fun getSecureInt(key: String, default: Int): Int
    fun putSecureInt(key: String, value: Int): Boolean
    fun observeSecureInt(key: String, default: Int): InitialValueObservable<Int>

    fun getSecureString(key: String): String?
    fun putSecureString(key: String, value: String): Boolean
    fun observeSecureString(key: String, default: String): InitialValueObservable<String>
}