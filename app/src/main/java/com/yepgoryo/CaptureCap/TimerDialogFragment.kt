package com.yepgoryo.CaptureCap

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.preference.PreferenceDialogFragmentCompat

class TimerDialogFragment : PreferenceDialogFragmentCompat() {

    companion object {
        fun newInstance(name: String): TimerDialogFragment {
            val timerDialogFragment = TimerDialogFragment()
            val bundle = Bundle(1)
            bundle.putString("key", name)
            timerDialogFragment.setArguments(bundle)
            return timerDialogFragment
        }
    }

    private var appSettings: GlobalProperties? = null
    private var keyName: String = ""
    private var textMinutes: EditText? = null
    private var textMinutesInputData: String = "0"
    private var textSeconds: EditText? = null
    private var textSecondsInputData: String = "0"

    fun setKeyName(name: String) {
        this.keyName = name
    }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        this.appSettings = GlobalProperties(requireContext())
    }

    public override fun onBindDialogView(view: View) {
        this.textMinutes = view.findViewById(R.id.time_minutes)
        this.textSeconds = view.findViewById(R.id.time_seconds)
        textMinutes!!.addTextChangedListener(InputMinutesValidator())
        textSeconds!!.addTextChangedListener(InputSecondsValidator())
        val totalSeconds: Int = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.TIMER_SECONDS, 10)
        this.textMinutes!!.setText((totalSeconds / 60).toString())
        this.textSeconds!!.setText((totalSeconds % 60).toString())
    }

    override fun onDialogClosed(resultPositive: Boolean) {
        if (resultPositive) {
            val parsedMinutes: Int = Integer.parseInt(this@TimerDialogFragment.textMinutesInputData)
            val parsedSeconds: Int = Integer.parseInt(this@TimerDialogFragment.textSecondsInputData)
            this.appSettings?.setIntProperty(GlobalProperties.PropertiesInt.TIMER_SECONDS, ((parsedMinutes * 60) + parsedSeconds))
        }
    }

    private inner class InputSecondsValidator : TextWatcher {
        override fun afterTextChanged(editable: Editable) {}

        override fun beforeTextChanged(charSequence: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(charSequence: CharSequence, start: Int, count: Int, after: Int)  {
            val string: String = charSequence.toString()
            if (string.length > 2) {
                this@TimerDialogFragment.textSeconds?.setText(this@TimerDialogFragment.textSecondsInputData)
            } else {
                if (!string.contentEquals("")) {
                    val parsed: Int = Integer.parseInt(string)
                    if (parsed !in 0..59) {
                        this@TimerDialogFragment.textSeconds?.setText(this@TimerDialogFragment.textSecondsInputData)
                    } else {
                        this@TimerDialogFragment.textSecondsInputData = string
                    }
                }
            }
        }
    }

    private inner class InputMinutesValidator : TextWatcher {
        override fun afterTextChanged(editable: Editable) {}

        override fun beforeTextChanged(charSequence: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(charSequence: CharSequence, start: Int, count: Int, after: Int)  {
            val string: String = charSequence.toString()
            if (string.length > 2) {
                this@TimerDialogFragment.textMinutes?.setText(this@TimerDialogFragment.textMinutesInputData)
            } else {
                if (!string.contentEquals("")) {
                    val parsed: Int = Integer.parseInt(string)
                    if (parsed > 59 || parsed < 0) {
                        this@TimerDialogFragment.textMinutes?.setText(this@TimerDialogFragment.textMinutesInputData)
                    } else {
                        this@TimerDialogFragment.textMinutesInputData = string
                    }
                }
            }
        }
    }
}
