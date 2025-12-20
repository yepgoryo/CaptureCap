package com.yepgoryo.CaptureCap

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.PreferenceDialogFragmentCompat

class QualityDialogFragment : PreferenceDialogFragmentCompat() {

    companion object {
        fun newInstance(name: String): QualityDialogFragment {
            val qualityDialogFragment = QualityDialogFragment()
            val bundle = Bundle(1)
            bundle.putString("key", name)
            qualityDialogFragment.setArguments(bundle)
            return qualityDialogFragment
        }
    }

    private var appSettings: GlobalProperties? = null
    private var keyName: String = ""
    private var qualityScale: Int = 9

    fun setKeyName(name: String) {
        this.keyName = name
    }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        this.appSettings = GlobalProperties(requireContext())
    }

    fun updateHint(quality: Int, textView: TextView) {
        if (quality > 6) {
            textView.setText(R.string.quality_normal)
        } else if (quality < 6 && quality > 3) {
            textView.setText(R.string.quality_low)
        } else if (quality < 3) {
            textView.setText(R.string.quality_lowest)
        }
    }

    public override fun onBindDialogView(view: View) {
        this.qualityScale = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.QUALITY_SCALE, 9)
        val textView: TextView = view.findViewById(R.id.quality_title)
        val seekBar: SeekBar = view.findViewById(R.id.quality_seek)
        updateHint(this.qualityScale, textView)
        seekBar.progress = this.qualityScale
        seekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                this@QualityDialogFragment.qualityScale = progress
                this@QualityDialogFragment.updateHint(progress, textView)
            }
        })
    }

    override fun onDialogClosed(resultPositive: Boolean) {
        if (resultPositive) {
            this.appSettings?.setIntProperty(GlobalProperties.PropertiesInt.QUALITY_SCALE, this.qualityScale)
        }
    }
}
