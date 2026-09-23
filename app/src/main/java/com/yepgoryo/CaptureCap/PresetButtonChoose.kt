package com.yepgoryo.CaptureCap

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.CompoundButton
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatToggleButton

class PresetButtonChoose(context: Context, attrSet: AttributeSet) : LinearLayout(context, attrSet) {

    var stateChanged: ((Int) -> Unit)? = null

    private var buttonPreset1: AppCompatToggleButton? = null
    private var buttonPreset2: AppCompatToggleButton? = null
    private var buttonPreset3: AppCompatToggleButton? = null
    private var buttonPreset4: AppCompatToggleButton? = null

    fun setButtonChecked(index: Int) {
        findButtons()
        when (index) {
            1 -> {
                buttonPreset1?.isChecked = true
                buttonPreset1?.isEnabled = false
                updateButtonBackground(buttonPreset1!!, true)
            }
            2 -> {
                buttonPreset2?.isChecked = true
                buttonPreset2?.isEnabled = false
                updateButtonBackground(buttonPreset2!!, true)
            }
            3 -> {
                buttonPreset3?.isChecked = true
                buttonPreset3?.isEnabled = false
                updateButtonBackground(buttonPreset3!!, true)
            }
            4 -> {
                buttonPreset4?.isChecked = true
                buttonPreset4?.isEnabled = false
                updateButtonBackground(buttonPreset4!!, true)
            }
            else -> {}
        }
    }

    private fun resetButtons(except: Int) {
        if (except != 1) buttonPreset1?.isChecked = false
        if (except != 2) buttonPreset2?.isChecked = false
        if (except != 3) buttonPreset3?.isChecked = false
        if (except != 4) buttonPreset4?.isChecked = false
    }

    private fun updateButtonBackground(button: AppCompatToggleButton, isChecked: Boolean) {
        val newTextColorLight = TypedValue()
        val newTextColorDark = TypedValue()
        val theme = context.getTheme()
        theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, newTextColorDark, true)
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, newTextColorLight, true)

        if (isChecked) {
            button.setBackgroundDrawable(context.getDrawable(R.drawable.bg_setting_rounded_button_disabled))
            button.setTextColor(newTextColorLight.data)
        } else {
            button.setBackgroundDrawable(context.getDrawable(R.drawable.bg_setting_rounded_button_checked))
            button.setTextColor(newTextColorDark.data)
        }
    }

    private val listener = CompoundButton.OnCheckedChangeListener { button, isChecked ->
        var buttonNumChecked = 1
        when (button.id) {
            R.id.audio_button_preset_1 -> {
                buttonNumChecked = 1
            }

            R.id.audio_button_preset_2 -> {
                buttonNumChecked = 2
            }

            R.id.audio_button_preset_3 -> {
                buttonNumChecked = 3
            }

            R.id.audio_button_preset_4 -> {
                buttonNumChecked = 4
            }
        }

        if (isChecked) {
            val newTextColorLight = TypedValue()
            val newTextColorDark = TypedValue()
            val theme = context.getTheme()

            resetButtons(buttonNumChecked)

            theme.resolveAttribute(
                androidx.appcompat.R.attr.colorPrimary,
                newTextColorDark,
                true
            )

            theme.resolveAttribute(
                com.google.android.material.R.attr.colorOnPrimary,
                newTextColorLight,
                true
            )

            button.setBackgroundDrawable(context.getDrawable(R.drawable.bg_setting_rounded_button_disabled))
            button.setTextColor(newTextColorLight.data)

            stateChanged?.invoke(buttonNumChecked)

            button.isEnabled = false

        } else {
            val newTextColorLight = TypedValue()
            val newTextColorDark = TypedValue()
            val theme = context.getTheme()
            theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, newTextColorDark, true)
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, newTextColorLight, true)

            button.setBackgroundDrawable(context.getDrawable(R.drawable.bg_setting_rounded_button_checked))
            button.setTextColor(newTextColorDark.data)
            button.isEnabled = true
        }
    }

    private fun findButtons() {
        buttonPreset1 = findViewById(R.id.audio_button_preset_1)
        buttonPreset2 = findViewById(R.id.audio_button_preset_2)
        buttonPreset3 = findViewById(R.id.audio_button_preset_3)
        buttonPreset4 = findViewById(R.id.audio_button_preset_4)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        findButtons()
        buttonPreset1?.setOnCheckedChangeListener(listener)
        buttonPreset2?.setOnCheckedChangeListener(listener)
        buttonPreset3?.setOnCheckedChangeListener(listener)
        buttonPreset4?.setOnCheckedChangeListener(listener)
    }
}