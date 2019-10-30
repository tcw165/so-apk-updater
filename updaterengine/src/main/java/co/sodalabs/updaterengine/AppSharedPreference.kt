package co.sodalabs.updaterengine

import android.annotation.SuppressLint
import android.content.SharedPreferences
import io.reactivex.Observable
import javax.inject.Inject

class AppSharedPreference @Inject constructor(
    private val preferences: SharedPreferences
) : IAppPreference {

    override fun getInt(prop: String, default: Int): Int {
        return preferences.getInt(prop, default)
    }

    override fun putInt(prop: String, value: Int) {
        preferences.edit()
            .putInt(prop, value)
            .apply()
    }

    override fun observeIntChange(
        prop: String,
        default: Int
    ): Observable<Int> {
        return observePropertyChange(prop) { p ->
            p.getInt(prop, default)
        }
    }

    @SuppressLint("ApplySharedPref")
    override fun getLong(prop: String, default: Long): Long {
        return preferences.getLong(prop, default)
    }

    override fun putLong(prop: String, value: Long) {
        preferences.edit()
            .putLong(prop, value)
            .apply()
    }

    override fun observeLongChange(prop: String, default: Long): Observable<Long> {
        return observePropertyChange(prop) { p ->
            p.getLong(prop, default)
        }
    }

    override fun getBoolean(prop: String, default: Boolean): Boolean {
        return preferences.getBoolean(prop, default)
    }

    override fun putBoolean(prop: String, value: Boolean) {
        preferences.edit()
            .putBoolean(prop, value)
            .apply()
    }

    override fun observeStringChange(
        prop: String,
        default: String
    ): Observable<String> {
        return observePropertyChange(prop) { p ->
            p.getString(prop, default) ?: default
        }
    }

    override fun getString(prop: String, default: String): String {
        return preferences.getString(prop, default) ?: default
    }

    override fun putString(prop: String, value: String) {
        preferences.edit()
            .putString(prop, value)
            .apply()
    }

    override fun observeBooleanChange(
        prop: String,
        default: Boolean
    ): Observable<Boolean> {
        return observePropertyChange(prop) { p ->
            p.getBoolean(prop, default)
        }
    }

    override fun containsKey(prop: String): Boolean {
        return preferences.contains(prop)
    }

    override fun observeAnyChange(): Observable<String> {
        return Observable.create { emitter ->
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
                if (this.preferences == preferences) {
                    emitter.onNext(key)
                }
            }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            emitter.setCancellable { preferences.unregisterOnSharedPreferenceChangeListener(listener) }

            // Don't emit the initial value
        }
    }

    private fun <T> observePropertyChange(
        propKey: String,
        valueExtractor: (preferences: SharedPreferences) -> T
    ): Observable<T> {
        return Observable.create { emitter ->
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
                if (this.preferences == preferences && key == propKey) {
                    val value = valueExtractor.invoke(preferences)
                    emitter.onNext(value)
                }
            }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            emitter.setCancellable { preferences.unregisterOnSharedPreferenceChangeListener(listener) }

            // Don't emit the initial value
        }
    }

    @SuppressLint("ApplySharedPref")
    override fun forceFlush() {
        preferences.edit().commit()
    }
}
