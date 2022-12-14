package co.sodalabs.updaterengine

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import co.sodalabs.updaterengine.extension.toBoolean
import co.sodalabs.updaterengine.extension.toInt
import co.sodalabs.updaterengine.rx.InitialValueObservable
import io.reactivex.Observable
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

private const val EMPTY_STRING = ""

class AndroidSharedSettings @Inject constructor(
    private val contentResolver: ContentResolver,
    private val appPreference: IAppPreference,
    private val schedulers: IThreadSchedulers
) : ISharedSettings {

    override fun isDeviceProvisioned(): Boolean {
        return try {
            getGlobalInt(SharedSettingsProps.DEVICE_PROVISIONED, 0) == 1
        } catch (error: Throwable) {
            Timber.w(error)
            false
        }
    }

    override fun isUserSetupComplete(): Boolean {
        val mockUserSetupNOTComplete = appPreference.getBoolean(PreferenceProps.MOCK_USER_SETUP_INCOMPLETE, false)
        val actualValue = try {
            getSecureInt(SharedSettingsProps.USER_SETUP_COMPLETE, 0) == 1
        } catch (error: Throwable) {
            Timber.w(error)
            false
        }
        Timber.v("[SharedSettings] user-setup-complete: mock=$mockUserSetupNOTComplete, actual=$actualValue")

        // If the mock is true, that means we are pretending like we are still in OOBE.
        if (mockUserSetupNOTComplete) {
            return false
        }

        // If the mock is false, just return the actual value.
        return actualValue
    }

    override fun observeUserSetupComplete(): InitialValueObservable<Boolean> {
        return observeSecureInt(SharedSettingsProps.USER_SETUP_COMPLETE, 0)
            .map {
                // Delegate to the getter function to allow the mock value.
                isUserSetupComplete()
            }
    }

    override fun isPasscodeAuthorized(code: String): Boolean {
        return getSecureString(SharedSettingsProps.ADMIN_PASSCODE)?.contentEquals(code) ?: false
    }

    @SuppressLint("HardwareIds")
    override fun getHardwareId(): String {
        val androidId = getSecureString(Settings.Secure.ANDROID_ID)
        return "$androidId"
    }

    override fun getDeviceId(): String {
        val debugID = appPreference.getString(PreferenceProps.MOCK_DEVICE_ID, EMPTY_STRING)
        return if (debugID.isNotBlank()) {
            debugID
        } else {
            // Actual value from system.
            getSecureString(SharedSettingsProps.DEVICE_ID) ?: EMPTY_STRING
        }
    }

    override fun observeDeviceId(): InitialValueObservable<String> {
        return observeWithNamespaceAndType(SharedSettingsNamespace.Secure, SharedSettingsProps.DEVICE_ID, EMPTY_STRING)
            // Delegate to the getter function to allow the mock value.
            .map { getDeviceId() }
    }

    override fun getGlobalInt(key: String, default: Int): Int {
        return try {
            Settings.Global.getInt(contentResolver, key, default)
        } catch (error: Throwable) {
            Timber.w(error)
            default
        }
    }

    override fun putGlobalInt(key: String, value: Int): Boolean {
        return try {
            Settings.Global.putInt(contentResolver, key, value)
        } catch (error: Throwable) {
            Timber.w(error)
            false
        }
    }

    override fun observeGlobalInt(
        key: String,
        default: Int
    ): InitialValueObservable<Int> {
        return observeWithNamespaceAndType(SharedSettingsNamespace.Global, key, default)
    }

    override fun getGlobalString(key: String, default: String): String {
        return try {
            Settings.Global.getString(contentResolver, key) ?: default
        } catch (error: Throwable) {
            Timber.w(error)
            default
        }
    }

    override fun putGlobalString(key: String, value: String): Boolean {
        return try {
            Settings.Global.putString(contentResolver, key, value)
        } catch (error: Throwable) {
            Timber.w(error)
            false
        }
    }

    override fun observeGlobalString(
        key: String,
        default: String
    ): InitialValueObservable<String> {
        return observeWithNamespaceAndType(SharedSettingsNamespace.Global, key, default)
    }

    override fun getSecureInt(
        key: String,
        default: Int
    ): Int {
        return try {
            Settings.Secure.getInt(contentResolver, key, default)
        } catch (error: Throwable) {
            Timber.w(error)
            default
        }
    }

    override fun putSecureInt(
        key: String,
        value: Int
    ): Boolean {
        return try {
            Settings.Secure.putInt(contentResolver, key, value)
        } catch (error: Throwable) {
            Timber.w(error)
            false
        }
    }

    override fun observeSecureInt(key: String, default: Int): InitialValueObservable<Int> {
        return observeWithNamespaceAndType(SharedSettingsNamespace.Secure, key, default)
    }

    override fun getSecureBoolean(key: String, default: Boolean): Boolean {
        return try {
            // Note: Settings doesn't have boolean type, so we use int.
            Settings.Secure.getInt(contentResolver, key, default.toInt()).toBoolean()
        } catch (error: Throwable) {
            Timber.w(error)
            default
        }
    }

    override fun putSecureBoolean(key: String, value: Boolean): Boolean {
        return try {
            // Note: Settings doesn't have boolean type, so we use int.
            Settings.Secure.putInt(contentResolver, key, value.toInt())
        } catch (error: Throwable) {
            Timber.w(error)
            false
        }
    }

    override fun observeSecureBoolean(key: String, default: Boolean): InitialValueObservable<Boolean> {
        return observeWithNamespaceAndType(SharedSettingsNamespace.Secure, key, default)
    }

    override fun getSecureString(
        key: String
    ): String? {
        return try {
            Settings.Secure.getString(contentResolver, key)
        } catch (error: Throwable) {
            Timber.w(error)
            null
        }
    }

    override fun putSecureString(key: String, value: String): Boolean {
        return try {
            Settings.Secure.putString(contentResolver, key, value)
        } catch (error: Throwable) {
            Timber.w(error)
            false
        }
    }

    override fun observeSecureString(key: String, default: String): InitialValueObservable<String> {
        return observeWithNamespaceAndType(SharedSettingsNamespace.Secure, key, default)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> observeWithNamespaceAndType(
        namespace: SharedSettingsNamespace,
        key: String,
        default: T
    ): InitialValueObservable<T> {
        return Observable
            .create<T> { emitter ->
                val uri = namespace.getUriFor(key)
                val thread = HandlerThread(UUID.randomUUID().toString())
                try {
                    thread.start()

                    val handler = Handler(thread.looper)
                    val contentObserver = object : ContentObserver(handler) {
                        override fun onChange(selfChange: Boolean) {
                            val value: Any = when (default) {
                                is Int -> namespace.getIntFor(key, default)
                                is Boolean -> namespace.getIntFor(key, default.toInt()).toBoolean()
                                is String -> namespace.getStringFor(key) ?: default
                                else -> throw IllegalArgumentException()
                            }
                            emitter.onNext(value as T)
                        }
                    }
                    contentResolver.registerContentObserver(uri, true, contentObserver)

                    // Cancellation
                    emitter.setCancellable {
                        contentResolver.unregisterContentObserver(contentObserver)
                        thread.looper?.quitSafely()
                    }

                    // Initial value
                    val stickyValue: Any? = when (default) {
                        is Int -> namespace.getIntFor(key, default)
                        is Boolean -> namespace.getIntFor(key, default.toInt()).toBoolean()
                        is String -> namespace.getStringFor(key) ?: default as T
                        else -> throw IllegalArgumentException("Not support value type, $default")
                    }
                    stickyValue?.let {
                        emitter.onNext(it as T)
                    }
                } catch (err: Throwable) {
                    if (emitter.isDisposed ||
                        // Some Android isn't signed with the same key as this
                        // system app, and that will throw exception.
                        err is SecurityException
                    ) return@create

                    emitter.onError(err)
                }
            }
            .subscribeOn(schedulers.io())
    }

    private fun SharedSettingsNamespace.getUriFor(
        key: String
    ): Uri {
        return when (this) {
            SharedSettingsNamespace.Global -> Settings.Global.getUriFor(key)
            SharedSettingsNamespace.Secure -> Settings.Secure.getUriFor(key)
            SharedSettingsNamespace.System -> Settings.System.getUriFor(key)
        }
    }

    private fun SharedSettingsNamespace.getIntFor(
        key: String,
        default: Int
    ): Int {
        return when (this) {
            SharedSettingsNamespace.Global -> Settings.Global.getInt(contentResolver, key, default)
            SharedSettingsNamespace.Secure -> Settings.Secure.getInt(contentResolver, key, default)
            SharedSettingsNamespace.System -> Settings.System.getInt(contentResolver, key, default)
        }
    }

    private fun SharedSettingsNamespace.getStringFor(
        key: String
    ): String? {
        return when (this) {
            SharedSettingsNamespace.Global -> Settings.Global.getString(contentResolver, key)
            SharedSettingsNamespace.Secure -> Settings.Secure.getString(contentResolver, key)
            SharedSettingsNamespace.System -> Settings.System.getString(contentResolver, key)
        }
    }
}