package co.sodalabs.apkupdater.feature.settings

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.feature.rx.InitialValueObservable
import io.reactivex.Observable
import java.util.UUID
import javax.inject.Inject

class AndroidSharedSettings @Inject constructor(
    private val contentResolver: ContentResolver,
    private val schedulers: IThreadSchedulers
) : ISharedSettings {

    override fun isDeviceProvisioned(): Boolean {
        return try {
            getGlobalInt(SystemProps.DEVICE_PROVISIONED, 0) == 1
        } catch (error: Throwable) {
            false
        }
    }

    override fun isUserSetupComplete(): Boolean {
        return try {
            getSecureInt(SystemProps.USER_SETUP_COMPLETE, 0) == 1
        } catch (error: Throwable) {
            false
        }
    }

    override fun getGlobalInt(key: String, default: Int): Int {
        return try {
            Settings.Global.getInt(contentResolver, key, default)
        } catch (error: Throwable) {
            default
        }
    }

    override fun putGlobalInt(key: String, value: Int): Boolean {
        return try {
            Settings.Global.putInt(contentResolver, key, value)
        } catch (error: Throwable) {
            false
        }
    }

    override fun observeGlobalInt(
        key: String,
        default: Int
    ): InitialValueObservable<Int> {
        return observeWithNamespaceAndType(SettingsNamespace.Global, key, default)
    }

    override fun getSecureInt(
        key: String,
        default: Int
    ): Int {
        return try {
            Settings.Secure.getInt(contentResolver, key, default)
        } catch (error: Throwable) {
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
            false
        }
    }

    override fun observeSecureInt(key: String, default: Int): InitialValueObservable<Int> {
        return observeWithNamespaceAndType(SettingsNamespace.Secure, key, default)
    }

    override fun getSecureString(
        key: String
    ): String? {
        return try {
            Settings.Secure.getString(contentResolver, key)
        } catch (error: Throwable) {
            null
        }
    }

    override fun putSecureString(key: String, value: String): Boolean {
        return try {
            Settings.Secure.putString(contentResolver, key, value)
        } catch (error: Throwable) {
            false
        }
    }

    override fun observeSecureString(key: String, default: String): InitialValueObservable<String> {
        return observeWithNamespaceAndType(SettingsNamespace.Secure, key, default)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> observeWithNamespaceAndType(
        namespace: SettingsNamespace,
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
                            if (!selfChange) return

                            val value = when (default) {
                                is Int -> namespace.getIntFor(key, default)
                                else -> throw IllegalArgumentException()
                            }
                            emitter.onNext(value as T)
                        }
                    }
                    contentResolver.registerContentObserver(uri, false, contentObserver)

                    // Cancellation
                    emitter.setCancellable {
                        contentResolver.unregisterContentObserver(contentObserver)
                        thread.looper?.quitSafely()
                    }

                    // Initial value
                    val stickyValue: Any? = when (default) {
                        is Int -> namespace.getIntFor(key, default)
                        is String? -> namespace.getStringFor(key)
                        else -> throw IllegalArgumentException()
                    }
                    stickyValue?.let {
                        emitter.onNext(it as T)
                    }
                } catch (err: Throwable) {
                    emitter.onError(err)
                }
            }
            .subscribeOn(schedulers.io())
    }

    private fun SettingsNamespace.getUriFor(
        key: String
    ): Uri {
        return when (this) {
            SettingsNamespace.Global -> Settings.Global.getUriFor(key)
            SettingsNamespace.Secure -> Settings.Secure.getUriFor(key)
            SettingsNamespace.System -> Settings.System.getUriFor(key)
        }
    }

    private fun SettingsNamespace.getIntFor(
        key: String,
        default: Int
    ): Int {
        return when (this) {
            SettingsNamespace.Global -> Settings.Global.getInt(contentResolver, key, default)
            SettingsNamespace.Secure -> Settings.Secure.getInt(contentResolver, key, default)
            SettingsNamespace.System -> Settings.System.getInt(contentResolver, key, default)
        }
    }

    private fun SettingsNamespace.getStringFor(
        key: String
    ): String? {
        return when (this) {
            SettingsNamespace.Global -> Settings.Global.getString(contentResolver, key)
            SettingsNamespace.Secure -> Settings.Secure.getString(contentResolver, key)
            SettingsNamespace.System -> Settings.System.getString(contentResolver, key)
        }
    }
}