/*
 * This code has been co-authored by an AI.
 * Model name: Qwen 3 Coder Next
 */

package com.yepgoryo.CaptureCap

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible

class RecordSettingButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr), View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    private var switchButton: SwitchCompat? = null
    private var iconLight: ImageView? = null
    private var iconDark: ImageView? = null

    private var buttonText: TextView? = null

    interface OnToggleListener {
        fun onToggle(isChecked: Boolean)
    }

    private var onToggleListener: OnToggleListener? = null

    init {
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        isLongClickable = true

        setupBackground()
        setOnClickListener(this)
    }

    override fun onViewAdded(child: View?) {
        super.onViewAdded(child)
        if (child is ImageView) {
            if (iconLight == null) {
                iconLight = child
            } else {
                iconDark = child
            }
        }
        if (child is TextView && buttonText == null) {
            buttonText = child
        }
        if (child is SwitchCompat) {
            switchButton = child
            switchButton?.setOnCheckedChangeListener(this)
        }
    }

    private fun setupBackground() {
        iconLight?.isVisible = true
        iconDark?.isVisible = false

        val typedValue = TypedValue()
        val theme = context.getTheme()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
        val color: Int = typedValue.data

        buttonText?.setTextColor(color)
        background = ContextCompat.getDrawable(context, R.drawable.bg_setting_rounded_button_disabled)!!
    }

    private fun setupCheckedBackground() {
        iconLight?.isVisible = false
        iconDark?.isVisible = true

        val typedValue = TypedValue()
        val theme = context.getTheme()
        theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
        val color: Int = typedValue.data

        buttonText?.setTextColor(color)
        background = ContextCompat.getDrawable(context, R.drawable.bg_setting_rounded_button_checked)!!
    }

    override fun onClick(v: View) {
        if (switchButton?.isEnabled == true) {
            switchButton?.toggle()
        }
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (isChecked) {
            setupCheckedBackground()
        } else {
            setupBackground()
        }
        onToggleListener?.onToggle(isChecked)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && switchButton?.isEnabled == true) {
            val switchRect = Rect()
            switchButton?.getHitRect(switchRect)
            if (switchRect.contains(event.x.toInt(), event.y.toInt())) {
                return super.dispatchTouchEvent(event)
            }
        }
        return super.dispatchTouchEvent(event)
    }

    fun setSwitchChecked(checked: Boolean) {
        switchButton?.setOnCheckedChangeListener(null)
        switchButton?.isChecked = checked
        switchButton?.setOnCheckedChangeListener(this)

        if (checked) {
            setupCheckedBackground()
        } else {
            setupBackground()
        }
    }

    fun setOnToggleListener(listener: OnToggleListener) {
        onToggleListener = listener
    }
}