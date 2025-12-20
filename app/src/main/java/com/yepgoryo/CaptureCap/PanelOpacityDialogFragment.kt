package com.yepgoryo.CaptureCap

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import androidx.preference.PreferenceDialogFragmentCompat

class PanelOpacityDialogFragment : PreferenceDialogFragmentCompat() {
    companion object {
        fun newInstance(str: String): PanelOpacityDialogFragment {
            val panelOpacityDialogFragment = PanelOpacityDialogFragment()
            val bundle = Bundle(1)
            bundle.putString("key", str)
            panelOpacityDialogFragment.setArguments(bundle)
            return panelOpacityDialogFragment
        }
    }

    private var appSettings: GlobalProperties? = null
    private var keyName: String = ""
    private var opacityScale: Int = 9

    fun setKeyName(str: String) {
        this.keyName = str
    }

    fun updateHint(opacityScale: Int, imageView: ImageView) {
        imageView.setAlpha((opacityScale + 1) * 0.1f)
    }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        this.appSettings = GlobalProperties(requireContext())
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    public override fun onBindDialogView(view: View) {
        this.opacityScale = this.appSettings?.getIntProperty(GlobalProperties.PropertiesInt.FLOATING_CONTROLS_OPACITY, 9) ?: 0
        val opacityHandle: ImageView = view.findViewById(R.id.opacity_handle)
        val darkTheme: GlobalProperties.DarkThemeProperty? = this.appSettings?.getDarkTheme(true)
        if (((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES && darkTheme == GlobalProperties.DarkThemeProperty.AUTOMATIC) || darkTheme == GlobalProperties.DarkThemeProperty.DARK) {
            opacityHandle.setImageDrawable(context?.resources?.getDrawable(R.drawable.floatingpanel_shape_dark, context?.theme))
        }
        val seekBar: SeekBar = view.findViewById(R.id.opacity_seek)
        updateHint(this.opacityScale, opacityHandle)
        seekBar.progress = this.opacityScale
        seekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                this@PanelOpacityDialogFragment.opacityScale = progress
                this@PanelOpacityDialogFragment.updateHint(progress, opacityHandle)
            }
        })
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            this.appSettings?.setIntProperty(GlobalProperties.PropertiesInt.FLOATING_CONTROLS_OPACITY, this.opacityScale)
        }
    }
}
