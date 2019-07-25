package co.sodalabs.apkupdater.feature.adminui

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import androidx.core.content.res.TypedArrayUtils
import androidx.preference.EditTextPreference
import androidx.preference.R

class IntEditTextPreference : EditTextPreference {

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, TypedArrayUtils.getAttr(context, R.attr.editTextPreferenceStyle, android.R.attr.editTextPreferenceStyle))
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        specifyTheInputMethodType()
    }

    private fun specifyTheInputMethodType() {
        setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
            editText.selectAll()
        }
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        val value = getPersistedInt(0)
        text = "$value"
    }

    /**
     * Override this function cause [EditTextPreference] is not configurable in
     * XML to take Int preference.
     */
    override fun persistString(value: String?): Boolean {
        if (!shouldPersist()) {
            return false
        }

        // Shouldn't store null
        value?.let {
            val num = Integer.parseInt(it)
            val dataStore = preferenceDataStore
            if (dataStore != null) {
                dataStore.putInt(key, num)
            } else {
                sharedPreferences.edit()
                    .putInt(key, num)
                    .apply()
            }

            return true
        } ?: kotlin.run {
            // It's already there, so the same as persisting
            return true
        }
    }

    /**
     * Override this function cause [EditTextPreference] is not configurable in
     * XML to take Int preference.
     */
    override fun getPersistedString(defaultReturnValue: String): String {
        if (!shouldPersist()) {
            return "0"
        }

        val dataStore = preferenceDataStore
        val num = dataStore?.let {
            dataStore.getInt(key, 0)
        } ?: sharedPreferences.getInt(key, 0)
        return num.toString()
    }
}