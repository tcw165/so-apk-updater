package co.sodalabs.apkupdater

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import co.sodalabs.apkupdater.view.TouchBridgeFrameLayout
import co.sodalabs.updaterengine.ISharedSettings
import co.sodalabs.updaterengine.IThreadSchedulers
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.SingleEmitter
import kotlinx.android.synthetic.main.dialog_passcode.btClose
import kotlinx.android.synthetic.main.dialog_passcode.btSubmit
import kotlinx.android.synthetic.main.dialog_passcode.etCode
import kotlinx.android.synthetic.main.layout_numpad.btDelete
import kotlinx.android.synthetic.main.layout_numpad.btEight
import kotlinx.android.synthetic.main.layout_numpad.btFive
import kotlinx.android.synthetic.main.layout_numpad.btFour
import kotlinx.android.synthetic.main.layout_numpad.btHash
import kotlinx.android.synthetic.main.layout_numpad.btNine
import kotlinx.android.synthetic.main.layout_numpad.btOne
import kotlinx.android.synthetic.main.layout_numpad.btSeven
import kotlinx.android.synthetic.main.layout_numpad.btSix
import kotlinx.android.synthetic.main.layout_numpad.btThree
import kotlinx.android.synthetic.main.layout_numpad.btTwo
import kotlinx.android.synthetic.main.layout_numpad.btZero
import timber.log.Timber
import javax.inject.Inject

class AndroidPasscodeDialogFactory @Inject constructor(
    private val activity: AppCompatActivity,
    private val leakUtil: ILeakUtil,
    private val sharedSettings: ISharedSettings,
    private val touchTracker: ITouchTracker,
    private val schedulers: IThreadSchedulers
) : IPasscodeDialogFactory {

    override fun showPasscodeDialog(): Single<Boolean> {
        return Single
            .create<Boolean> { emitter ->
                val dialog = createDialog(emitter)
                emitter.setCancellable {
                    if (dialog.isShowing) {
                        dialog.dismiss()
                    }

                    // The system TextLine has a memory leak issue under 23.
                    leakUtil.clearTextLineCache()
                }
                Timber.v("[Passcode] Show dialog")
                dialog.show()
            }
            .subscribeOn(schedulers.main())
            .retryWithErrorToast()
    }

    private fun createDialog(
        emitter: SingleEmitter<Boolean>
    ): Dialog {
        return Dialog(activity, R.style.PasscodeDialog)
            .apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setContentView(inflateContentViewWithTouchBridge(R.layout.dialog_passcode))

                window?.apply {
                    setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundDrawableResource(android.R.color.transparent)
                    setGravity(Gravity.CENTER)
                    setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
                    // Most importantly, not cancellable
                    setCancelable(false)
                }

                val padButtons = listOf(
                    btZero to DigitStrings.ZERO,
                    btOne to DigitStrings.ONE,
                    btTwo to DigitStrings.TWO,
                    btThree to DigitStrings.THREE,
                    btFour to DigitStrings.FOUR,
                    btFive to DigitStrings.FIVE,
                    btSix to DigitStrings.SIX,
                    btSeven to DigitStrings.SEVEN,
                    btEight to DigitStrings.EIGHT,
                    btNine to DigitStrings.NINE,
                    btHash to DigitStrings.HASH
                )
                padButtons.forEach { (button, digitString) ->
                    button.setOnClickListener {
                        etCode.addInput(digitString)
                    }
                }

                etCode.setOnTouchListener { v, event ->
                    v?.onTouchEvent(event)
                    val inputMethod = v?.context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    inputMethod?.hideSoftInputFromWindow(v.windowToken, 0)
                    true
                }
                etCode.onChange { code ->
                    if (sharedSettings.isPasscodeAuthorized(code)) {
                        etCode.setBackgroundResource(R.drawable.bg_rounded_input)
                        btSubmit.isEnabled = true
                    } else {
                        etCode.setBackgroundResource(R.drawable.bg_rounded_input_error)
                        btSubmit.isEnabled = false
                    }
                }
                etCode.requestFocus()

                btDelete.setOnClickListener {
                    etCode.deleteLast()
                }
                btClose.setOnClickListener {
                    Timber.v("[Passcode] The passcode is NOT authorized")
                    emitter.onSuccess(false)
                }
                btSubmit.isEnabled = false
                btSubmit.setOnClickListener {
                    val code = etCode.text.toString()
                    if (sharedSettings.isPasscodeAuthorized(code)) {
                        Timber.v("[Passcode] The passcode is authorized")
                        emitter.onSuccess(true)
                    }
                }
            }
    }

    private fun inflateContentViewWithTouchBridge(
        @LayoutRes resInt: Int
    ): View {
        return (LayoutInflater.from(activity)
            .inflate(resInt, null, false) as TouchBridgeFrameLayout)
            .apply {
                // Bypass the touch event to the touch tracker so that
                // we could analyze the user behavior or extend the auto-
                // dismiss timeout.
                setTouchBridger { event -> touchTracker.trackEvent(event) }
            }
    }

    private fun EditText.onChange(cb: (String) -> Unit) {
        this.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                cb(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    @SuppressLint("SetTextI18n")
    private fun EditText.addInput(value: Char) {
        setText("$text$value")
        setSelection(text.length)
    }

    private fun EditText.deleteLast() {
        // Since the formatter doesn't have method to delete single input entry.
        // Work around here is to preserve one-less digits upon button click and
        // then clear both formatter and EditText before re-entering the input
        val input = text
            .toString()
            .toCharArray()
            .dropLast(1)
            .joinToString("")
        text.clear()
        input.forEach { addInput(it) }
    }

    // Error Handling /////////////////////////////////////////////////////////

    private fun Single<Boolean>.retryWithErrorToast(): Single<Boolean> {
        return this.retryWhen { errors ->
            errors.flatMap { error ->
                activity.showErrorToastCompletable(error)
            }
        }
    }

    private fun Context.showErrorToastCompletable(
        error: Throwable
    ): Flowable<Unit> {
        val context = this
        return Flowable
            .fromCallable {
                Toast.makeText(context, error.toString(), Toast.LENGTH_SHORT).show()
            }
            .subscribeOn(schedulers.main())
    }
}