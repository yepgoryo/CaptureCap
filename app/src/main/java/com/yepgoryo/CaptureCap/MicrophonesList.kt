package com.yepgoryo.CaptureCap

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.AttributeSet
import androidx.preference.ListPreference

class MicrophonesList(context: Context, attributeSet: AttributeSet?): ListPreference(context, attributeSet) {
    private val microphonesList: ArrayList<String> = ArrayList()
    private val microphonesIDList: ArrayList<String> = ArrayList()

    private fun getAllMicrophones() {
        microphonesList.clear()
        microphonesIDList.clear()
        val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        for (microphoneInfo in audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            var micType = "UNKNOWN"
            when (microphoneInfo.type) {
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> {micType = "BUILTIN_EARPIECE"}
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {micType = "BUILTIN_SPEAKER"}
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> {micType = "WIRED_HEADSET"}
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {micType = "WIRED_HEADPHONES"}
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {micType = "BLUETOOTH_SCO"}
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {micType = "BLUETOOTH_A2DP"}
                AudioDeviceInfo.TYPE_HDMI -> {micType = "HDMI"}
                AudioDeviceInfo.TYPE_DOCK -> {micType = "DOCK"}
                AudioDeviceInfo.TYPE_USB_ACCESSORY -> {micType = "USB_ACCESSORY"}
                AudioDeviceInfo.TYPE_USB_DEVICE -> {micType = "USB_DEVICE"}
                AudioDeviceInfo.TYPE_USB_HEADSET -> {micType = "USB_HEADSET"}
                AudioDeviceInfo.TYPE_TELEPHONY -> {micType = "TELEPHONY"}
                AudioDeviceInfo.TYPE_LINE_ANALOG -> {micType = "LINE_ANALOG"}
                AudioDeviceInfo.TYPE_HDMI_ARC -> {micType = "HDMI_ARC"}
                AudioDeviceInfo.TYPE_HDMI_EARC -> {micType = "HDMI_EARC"}
                AudioDeviceInfo.TYPE_LINE_DIGITAL -> {micType = "LINE_DIGITAL"}
                AudioDeviceInfo.TYPE_FM -> {micType = "FM"}
                AudioDeviceInfo.TYPE_AUX_LINE -> {micType = "AUX_LINE"}
                AudioDeviceInfo.TYPE_IP -> {micType = "IP"}
                AudioDeviceInfo.TYPE_BUS -> {micType = "BUS"}
                AudioDeviceInfo.TYPE_HEARING_AID -> {micType = "HEARING_AID"}
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> {micType = "BUILTIN_MIC"}
                AudioDeviceInfo.TYPE_FM_TUNER -> {micType = "FM_TUNER"}
                AudioDeviceInfo.TYPE_TV_TUNER -> {micType = "TV_TUNER"}
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> {micType = "BUILTIN_SPEAKER_SAFE"}
                AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> {micType = "REMOTE_SUBMIX"}
                AudioDeviceInfo.TYPE_BLE_HEADSET -> {micType = "BLE_HEADSET"}
                AudioDeviceInfo.TYPE_BLE_SPEAKER -> {micType = "BLE_SPEAKER"}
                AudioDeviceInfo.TYPE_BLE_BROADCAST -> {micType = "BLE_BROADCAST"}
                AudioDeviceInfo.TYPE_DOCK_ANALOG -> {micType = "DOCK_ANALOG"}
                AudioDeviceInfo.TYPE_MULTICHANNEL_GROUP -> {micType = "MULTICHANNEL_GROUP"}
            }
            microphonesList.add("${microphoneInfo.productName} ($micType)")
            microphonesIDList.add(microphoneInfo.id.toString())
        }
    }

    init {
        getAllMicrophones()
        val entries: Array<String> = arrayOf(context.getResources().getString(R.string.microphone_option_auto)) + microphonesList
        val values: Array<String> = arrayOf(context.getResources().getString(R.string.microphone_option_auto_value)) + microphonesIDList
        setEntries(entries)
        entryValues = values
        setDefaultValue(context.getResources().getString(R.string.microphone_option_auto_value))
    }

    override fun onClick() {
        super.onClick()
        getAllMicrophones()
        val entries: Array<String> = arrayOf(context.getResources().getString(R.string.microphone_option_auto)) + microphonesList
        val values: Array<String> = arrayOf(context.getResources().getString(R.string.microphone_option_auto_value)) + microphonesIDList
        setEntries(entries)
        entryValues = values
        setDefaultValue(context.getResources().getString(R.string.microphone_option_auto_value))
    }
}
