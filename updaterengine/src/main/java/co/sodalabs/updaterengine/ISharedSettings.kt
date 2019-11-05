package co.sodalabs.updaterengine

import co.sodalabs.updaterengine.rx.InitialValueObservable

interface ISharedSettings {
    fun isDeviceProvisioned(): Boolean
    fun isUserSetupComplete(): Boolean
    fun observeUserSetupComplete(): InitialValueObservable<Boolean>

    fun isPasscodeAuthorized(code: String): Boolean

    fun getHardwareId(): String
    fun getDeviceId(): String
    fun observeDeviceId(): InitialValueObservable<String>

    fun getGlobalInt(key: String, default: Int): Int
    fun putGlobalInt(key: String, value: Int): Boolean
    fun observeGlobalInt(key: String, default: Int): InitialValueObservable<Int>
    fun getGlobalString(key: String, default: String): String
    fun putGlobalString(key: String, value: String): Boolean
    fun observeGlobalString(key: String, default: String): InitialValueObservable<String>

    fun getSecureInt(key: String, default: Int): Int
    fun putSecureInt(key: String, value: Int): Boolean
    fun observeSecureInt(key: String, default: Int): InitialValueObservable<Int>

    fun getSecureBoolean(key: String, default: Boolean): Boolean
    fun putSecureBoolean(key: String, value: Boolean): Boolean
    fun observeSecureBoolean(key: String, default: Boolean): InitialValueObservable<Boolean>

    fun getSecureString(key: String): String?
    fun putSecureString(key: String, value: String): Boolean
    fun observeSecureString(key: String, default: String): InitialValueObservable<String>
}