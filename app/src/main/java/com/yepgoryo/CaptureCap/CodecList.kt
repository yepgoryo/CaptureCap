package com.yepgoryo.CaptureCap

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.AttributeSet
import androidx.preference.ListPreference

class CodecList(context: Context, attributeSet: AttributeSet?): ListPreference(context, attributeSet) {
    private val codecsList: ArrayList<String> = ArrayList()
    private val prefName: String = key

    private fun getAllCodecs() {
        val str: String = if (this.prefName == GlobalProperties(context).getStringPropertyName(GlobalProperties.PropertiesString.AUDIO_CODEC_VALUE)) {MediaFormat.MIMETYPE_AUDIO_AAC} else {MediaFormat.MIMETYPE_VIDEO_AVC}
        for (mediaCodecInfo in MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos) {
            if (mediaCodecInfo.isEncoder) {
                for (codecType in mediaCodecInfo.getSupportedTypes()) {
                    if (codecType.equals(str, ignoreCase=true)) {
                        this.codecsList.add(mediaCodecInfo.name)
                    }
                }
            }
        }
    }

    init {
        getAllCodecs()
        val entries: Array<String> = arrayOf(context.getResources().getString(R.string.codec_option_auto)) + codecsList
        val values: Array<String> = arrayOf(context.getResources().getString(R.string.codec_option_auto_value)) + codecsList
        setEntries(entries)
        entryValues = values
        setDefaultValue(context.getResources().getString(R.string.audio_codec_option_auto_value))
    }
}
