package com.yepgoryo.CaptureCap

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.widget.EditText
import androidx.preference.EditTextPreference

class NonNullText(context: Context, attributeSet: AttributeSet) : EditTextPreference(context, attributeSet) {
    private var defaultString: String = ""
    private var editListener: EditTextPreference.OnBindEditTextListener
    private var inputData: String = ""
    private var persistedString: String = ""
    private var prefName: String = ""
    private var textEdit: EditText? = null

    init {
        this.editListener = OnBindEditTextListener { editText ->
            editText.setInputType(InputType.TYPE_CLASS_NUMBER)
            this@NonNullText.textEdit = editText
            var nonNullText: NonNullText = this@NonNullText
            nonNullText.persistedString = nonNullText.getPersistedString(nonNullText.defaultString)
            editText.addTextChangedListener(InputValidator())
            if (this@NonNullText.persistedString.startsWith("0") || this@NonNullText.persistedString.contentEquals("")) {
                var nonNullText2: NonNullText = this@NonNullText
                nonNullText2.persistString(nonNullText2.defaultString)
                var nonNullText3: NonNullText = this@NonNullText
                nonNullText3.persistedString = nonNullText3.defaultString
            }
            var nonNullText4: NonNullText = this@NonNullText
            nonNullText4.inputData = nonNullText4.persistedString
        }
        this.prefName = key
        setOnBindEditTextListener(this.editListener)
    }

    private inner class InputValidator : TextWatcher {
        override fun afterTextChanged(editable: Editable) {}

        override fun beforeTextChanged(charSequence: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(charSequence: CharSequence, start: Int, count: Int, after: Int)  {
            val string: String = charSequence.toString()
            if (string.length >= 10) {
                this@NonNullText.textEdit?.setText(this@NonNullText.inputData)
            } else {
                if (!string.contentEquals("")) {
                    val parsed: Int = Integer.parseInt(string)
                    if (!this@NonNullText.prefName.contentEquals("bitratevalue") || parsed <= 250000000) {
                        if (!this@NonNullText.prefName.contentEquals("fpsvalue") || parsed <= 300) {
                            if (!this@NonNullText.prefName.contentEquals("sampleratevalue") || parsed <= 352800) {
                                this@NonNullText.inputData = string
                            } else {
                                this@NonNullText.textEdit?.setText(this@NonNullText.inputData)
                            }
                        } else {
                            this@NonNullText.textEdit?.setText(this@NonNullText.inputData)
                        }
                    } else {
                        this@NonNullText.textEdit?.setText(this@NonNullText.inputData)
                    }
                }
            }
        }
    }

    public override fun onSetInitialValue(value: Any?) {
        super.onSetInitialValue(value)
        if (value != null) {
            this.defaultString = value as String
        }
    }

    override fun callChangeListener(value: Any): Boolean {
        if (!(value as String).startsWith("0") && !this.inputData.contentEquals("") && this.inputData.length < 10 && ((!this.prefName.contentEquals("bitratevalue") || Integer.parseInt(this.inputData) >= 128000) && (!this.prefName.contentEquals("sampleratevalue") || Integer.parseInt(this.inputData) >= 8000))) {
            return true
        }
        persistString(this.persistedString)
        return false
    }
}
