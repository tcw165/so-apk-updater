package co.sodalabs.apkupdater.feature.adminui

import android.content.Context
import android.provider.Settings
import android.util.AttributeSet
import androidx.core.content.res.TypedArrayUtils
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceDataStore
import androidx.preference.R
import timber.log.Timber

/**
 * The settings component which stores the data in [Settings.Secure] instead of
 * shared preference.
 */
class SecureStringEditTextPreference : EditTextPreference {

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, TypedArrayUtils.getAttr(context, R.attr.editTextPreferenceStyle, android.R.attr.editTextPreferenceStyle))
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        Timber.wtf("This is really used!")
    }

    override fun shouldPersist(): Boolean = true

    override fun getPreferenceDataStore(): PreferenceDataStore? {
        return object : PreferenceDataStore() {

            override fun getString(key: String, defValue: String?): String? {
                val resolver = context.applicationContext.contentResolver
                return Settings.Secure.getString(resolver, key)
            }

            override fun putString(key: String, value: String?) {
                val resolver = context.applicationContext.contentResolver
                value?.let { nonNullValue ->
                    Settings.Secure.putString(resolver, key, nonNullValue)
                }
            }

            // Note: Int, boolean, and the types other than string are not supported.
        }
    }
}