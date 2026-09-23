package com.yepgoryo.CaptureCap

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.PreferenceDialogFragmentCompat

class AudioVolumeDialogFragment : PreferenceDialogFragmentCompat() {
    companion object {
        fun newInstance(str: String): AudioVolumeDialogFragment {
            val audioVolumeDialogFragment = AudioVolumeDialogFragment()
            val bundle = Bundle(1)
            bundle.putString("key", str)
            audioVolumeDialogFragment.setArguments(bundle)
            return audioVolumeDialogFragment
        }
    }

    private var appSettings: GlobalProperties? = null
    private var keyName: String = ""
    private var audioVolumeScale: Int = 100
    private var micVolumeScale: Int = 100
    private var shizukuPhoneCallVolumeScale: Int = 100
    private var volumeSlotChosen: Int = 1

    private var shizukuPhoneCallVolume: TextView? = null
    private var shizukuPhoneCallSeekBar: SeekBar? = null
    private var micVolume: TextView? = null
    private var micSeekBar: SeekBar? = null
    private var audioVolume: TextView? = null
    private var audioSeekBar: SeekBar? = null
    private var chooseVolumeSlotButton: PresetButtonChoose? = null

    fun setKeyName(str: String) {
        this.keyName = str
    }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        this.appSettings = GlobalProperties(requireContext())
    }

    private fun saveVolumeToSlot() {
        when (volumeSlotChosen) {
            1 -> {
                this.appSettings!!.setIntProperty(GlobalProperties.PropertiesInt.AUDIO_VOLUME, this.audioVolumeScale)
                this.appSettings!!.setIntProperty(GlobalProperties.PropertiesInt.MICROPHONE_VOLUME, this.micVolumeScale)
                this.appSettings!!.setIntProperty(GlobalProperties.PropertiesInt.SHIZUKU_PHONE_CALL_VOLUME, this.shizukuPhoneCallVolumeScale)
            }
            2 -> {
                this.appSettings!!.setIntProperty(GlobalProperties.PropertiesInt.AUDIO_VOLUME_SLOT2, this.audioVolumeScale)
                this.appSettings!!.setIntProperty(GlobalProperties.PropertiesInt.MICROPHONE_VOLUME_SLOT2, this.micVolumeScale)
                this.appSettings!!.setIntProperty(GlobalProperties.PropertiesInt.SHIZUKU_PHONE_CALL_VOLUME_SLOT2, this.shizukuPhoneCallVolumeScale)
            }
            3 -> {
                this.appSettings!!.setIntProperty(GlobalProperties.PropertiesInt.AUDIO_VOLUME_SLOT3, this.audioVolumeScale)
                this.appSettings!!.setIntProperty(GlobalProperties.PropertiesInt.MICROPHONE_VOLUME_SLOT3, this.micVolumeScale)
                this.appSettings!!.setIntProperty(GlobalProperties.PropertiesInt.SHIZUKU_PHONE_CALL_VOLUME_SLOT3, this.shizukuPhoneCallVolumeScale)
            }
            4 -> {
                this.appSettings!!.setIntProperty(GlobalProperties.PropertiesInt.AUDIO_VOLUME_SLOT4, this.audioVolumeScale)
                this.appSettings!!.setIntProperty(GlobalProperties.PropertiesInt.MICROPHONE_VOLUME_SLOT4, this.micVolumeScale)
                this.appSettings!!.setIntProperty(GlobalProperties.PropertiesInt.SHIZUKU_PHONE_CALL_VOLUME_SLOT4, this.shizukuPhoneCallVolumeScale)
            }
        }
    }

    private fun loadVolumeFromSlot() {
        volumeSlotChosen = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.VOLUME_SLOT_CHOSEN, 1)

        when (volumeSlotChosen) {
            1 -> {
                this.audioVolumeScale = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.AUDIO_VOLUME, 100)
                this.micVolumeScale = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.MICROPHONE_VOLUME, 100)
                this.shizukuPhoneCallVolumeScale = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.SHIZUKU_PHONE_CALL_VOLUME, 100)
            }
            2 -> {
                this.audioVolumeScale = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.AUDIO_VOLUME_SLOT2, 100)
                this.micVolumeScale = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.MICROPHONE_VOLUME_SLOT2, 100)
                this.shizukuPhoneCallVolumeScale = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.SHIZUKU_PHONE_CALL_VOLUME_SLOT2, 100)
            }
            3 -> {
                this.audioVolumeScale = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.AUDIO_VOLUME_SLOT3, 100)
                this.micVolumeScale = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.MICROPHONE_VOLUME_SLOT3, 100)
                this.shizukuPhoneCallVolumeScale = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.SHIZUKU_PHONE_CALL_VOLUME_SLOT3, 100)
            }
            4 -> {
                this.audioVolumeScale = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.AUDIO_VOLUME_SLOT4, 100)
                this.micVolumeScale = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.MICROPHONE_VOLUME_SLOT4, 100)
                this.shizukuPhoneCallVolumeScale = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.SHIZUKU_PHONE_CALL_VOLUME_SLOT4, 100)
            }
        }

        audioSeekBar?.progress = this.audioVolumeScale
        audioVolume?.text = this.audioVolumeScale.toString()

        micSeekBar?.progress = this.micVolumeScale
        micVolume?.text = this.micVolumeScale.toString()

        shizukuPhoneCallSeekBar?.progress = this.shizukuPhoneCallVolumeScale
        shizukuPhoneCallVolume?.text = this.shizukuPhoneCallVolumeScale.toString()
    }

    public override fun onBindDialogView(view: View) {
        val shizukuPhoneCallPanel: LinearLayout = view.findViewById(R.id.phonecall_volume_panel)

        if (!this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.SHIZUKU_ENABLE, false)) {
            shizukuPhoneCallPanel.visibility = View.GONE
        } else {
            shizukuPhoneCallPanel.visibility = View.VISIBLE
        }

        loadVolumeFromSlot()

        chooseVolumeSlotButton = view.findViewById(R.id.audio_volume_preset)

        chooseVolumeSlotButton!!.setButtonChecked(volumeSlotChosen)

        chooseVolumeSlotButton!!.stateChanged = { newState ->
            saveVolumeToSlot()
            appSettings?.setIntProperty(GlobalProperties.PropertiesInt.VOLUME_SLOT_CHOSEN, newState)
            loadVolumeFromSlot()
        }

        audioVolume = view.findViewById(R.id.audio_volume_value)
        audioSeekBar = view.findViewById(R.id.audio_volume_seek)
        audioSeekBar!!.progress = this.audioVolumeScale
        audioVolume!!.text = this.audioVolumeScale.toString()
        audioSeekBar!!.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                this@AudioVolumeDialogFragment.audioVolumeScale = progress
                audioVolume!!.text = this@AudioVolumeDialogFragment.audioVolumeScale.toString()
            }
        })

        micVolume = view.findViewById(R.id.microphone_volume_value)
        micSeekBar = view.findViewById(R.id.microphone_volume_seek)
        micSeekBar!!.progress = this.micVolumeScale
        micVolume!!.text = this.micVolumeScale.toString()
        micSeekBar!!.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                this@AudioVolumeDialogFragment.micVolumeScale = progress
                micVolume!!.text = this@AudioVolumeDialogFragment.micVolumeScale.toString()
            }
        })

        shizukuPhoneCallVolume = view.findViewById(R.id.phonecall_volume_value)
        shizukuPhoneCallSeekBar = view.findViewById(R.id.phonecall_volume_seek)
        shizukuPhoneCallSeekBar!!.progress = this.shizukuPhoneCallVolumeScale
        shizukuPhoneCallVolume!!.text = this.shizukuPhoneCallVolumeScale.toString()
        shizukuPhoneCallSeekBar!!.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                this@AudioVolumeDialogFragment.shizukuPhoneCallVolumeScale = progress
                shizukuPhoneCallVolume!!.text = this@AudioVolumeDialogFragment.shizukuPhoneCallVolumeScale.toString()
            }
        })
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            when (this.appSettings?.getIntProperty(GlobalProperties.PropertiesInt.VOLUME_SLOT_CHOSEN, 1)) {
                1 -> {
                    this.appSettings?.setIntProperty(
                        GlobalProperties.PropertiesInt.AUDIO_VOLUME,
                        this.audioVolumeScale
                    )
                    this.appSettings?.setIntProperty(
                        GlobalProperties.PropertiesInt.MICROPHONE_VOLUME,
                        this.micVolumeScale
                    )
                    this.appSettings?.setIntProperty(
                        GlobalProperties.PropertiesInt.SHIZUKU_PHONE_CALL_VOLUME,
                        this.shizukuPhoneCallVolumeScale
                    )
                }
                2 -> {
                    this.appSettings?.setIntProperty(
                        GlobalProperties.PropertiesInt.AUDIO_VOLUME_SLOT2,
                        this.audioVolumeScale
                    )
                    this.appSettings?.setIntProperty(
                        GlobalProperties.PropertiesInt.MICROPHONE_VOLUME_SLOT2,
                        this.micVolumeScale
                    )
                    this.appSettings?.setIntProperty(
                        GlobalProperties.PropertiesInt.SHIZUKU_PHONE_CALL_VOLUME_SLOT2,
                        this.shizukuPhoneCallVolumeScale
                    )
                }
                3 -> {
                    this.appSettings?.setIntProperty(
                        GlobalProperties.PropertiesInt.AUDIO_VOLUME_SLOT3,
                        this.audioVolumeScale
                    )
                    this.appSettings?.setIntProperty(
                        GlobalProperties.PropertiesInt.MICROPHONE_VOLUME_SLOT3,
                        this.micVolumeScale
                    )
                    this.appSettings?.setIntProperty(
                        GlobalProperties.PropertiesInt.SHIZUKU_PHONE_CALL_VOLUME_SLOT3,
                        this.shizukuPhoneCallVolumeScale
                    )
                }
                4 -> {
                    this.appSettings?.setIntProperty(
                        GlobalProperties.PropertiesInt.AUDIO_VOLUME_SLOT4,
                        this.audioVolumeScale
                    )
                    this.appSettings?.setIntProperty(
                        GlobalProperties.PropertiesInt.MICROPHONE_VOLUME_SLOT4,
                        this.micVolumeScale
                    )
                    this.appSettings?.setIntProperty(
                        GlobalProperties.PropertiesInt.SHIZUKU_PHONE_CALL_VOLUME_SLOT4,
                        this.shizukuPhoneCallVolumeScale
                    )
                }
            }
        }
    }
}
