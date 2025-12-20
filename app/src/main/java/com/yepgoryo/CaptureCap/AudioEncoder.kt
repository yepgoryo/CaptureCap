package com.yepgoryo.CaptureCap

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Looper
import java.io.IOException
import java.nio.ByteBuffer

open class AudioEncoder(
    private var sampleRate: Int,
    channels: Int,
    private var useCustomCodec: Boolean,
    private var codecName: String
) : Encoder {
    private var channelsCount: Int = channels
    private var mCallback: Callback? = null
    private var mCodecCallback: MediaCodec.Callback = object: MediaCodec.Callback() {
        override fun onInputBufferAvailable(mediaCodec: MediaCodec, index: Int) {
            this@AudioEncoder.mCallback!!.onInputBufferAvailable(this@AudioEncoder, index)
        }

        override fun onOutputBufferAvailable(mediaCodec: MediaCodec, index: Int, bufferInfo: MediaCodec.BufferInfo) {
            this@AudioEncoder.mCallback!!.onOutputBufferAvailable(this@AudioEncoder, index, bufferInfo)
        }

        override fun onError(mediaCodec: MediaCodec, codecException: MediaCodec.CodecException) {
            this@AudioEncoder.mCallback!!.onError(this@AudioEncoder, codecException)
        }

        override fun onOutputFormatChanged(mediaCodec: MediaCodec, mediaFormat: MediaFormat) {
            this@AudioEncoder.mCallback!!.onOutputFormatChanged(this@AudioEncoder, mediaFormat)
        }
    }
    var mEncoder: MediaCodec? = null

    private fun selectCodec(codecNameSelect: String): MediaCodecInfo? {
        val codecInfos: Array<MediaCodecInfo> = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        for (mediaCodecInfo in codecInfos) {
            if (mediaCodecInfo.isEncoder) {
                for (mediaCodecSupportedType in mediaCodecInfo.getSupportedTypes()) {
                    if (mediaCodecSupportedType.equals(codecNameSelect, ignoreCase = true)) {
                        val audioCapabilities: MediaCodecInfo.AudioCapabilities? = mediaCodecInfo.getCapabilitiesForType(codecNameSelect).audioCapabilities
                        if (audioCapabilities!!.isSampleRateSupported(44100) && audioCapabilities!!.isSampleRateSupported(48000) && audioCapabilities!!.getMaxInputChannelCount() >= 2) {
                            return mediaCodecInfo
                        }
                    }
                }
            }
        }
        for (mediaCodecInfo in codecInfos) {
            if (mediaCodecInfo.isEncoder) {
                for (mediaCodecSupportedType in mediaCodecInfo.getSupportedTypes()) {
                    if (mediaCodecSupportedType.equals(codecNameSelect, ignoreCase = true)) {
                        return mediaCodecInfo
                    }
                }
            }
        }
        return null
    }

    protected fun createMediaFormat(): MediaFormat {
        val mediaFormat = MediaFormat()
        mediaFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC)
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, this.sampleRate * 16 * this.channelsCount)
        mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, this.channelsCount)
        mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, this.sampleRate)
        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        return mediaFormat
    }

    abstract class Callback : Encoder.Callback {
        open fun onInputBufferAvailable(audioEncoder: AudioEncoder, index: Int) {
        }

        open fun onOutputBufferAvailable(audioEncoder: AudioEncoder, index: Int, bufferInfo: MediaCodec.BufferInfo) {
        }

        open fun onOutputFormatChanged(audioEncoder: AudioEncoder, mediaFormat: MediaFormat) {
        }
    }

    fun setCallback(callback: Encoder.Callback) {
        if (callback !is Callback) {
            throw IllegalArgumentException()
        }
        setCallback(callback)
    }

    fun setCallback(callback: Callback) {
        if (this.mEncoder != null) {
            throw IllegalStateException()
        }
        this.mCallback = callback
    }

    @Throws(IOException::class)
    fun prepare() {
        var mediaCodecCreateByCodecName: MediaCodec 
        if (Looper.myLooper() == null || Looper.myLooper() == Looper.getMainLooper() || this.mEncoder != null) {
            throw IllegalStateException()
        }
        val mediaFormatCreateMediaFormat: MediaFormat = createMediaFormat()
        val codecMime: String = mediaFormatCreateMediaFormat.getString("mime") ?: "error"
        mediaCodecCreateByCodecName = if (!this.useCustomCodec) {
            MediaCodec.createEncoderByType(codecMime)
        } else {
            MediaCodec.createByCodecName(this.codecName)
        }
        if (this.mCallback != null) {
            mediaCodecCreateByCodecName.setCallback(this.mCodecCallback)
        }
        mediaCodecCreateByCodecName.configure(mediaFormatCreateMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mediaCodecCreateByCodecName.start()
        this.mEncoder = mediaCodecCreateByCodecName
    }

    @Throws(IOException::class)
    private fun createEncoder(encoderType: String): MediaCodec {
        return MediaCodec.createEncoderByType(encoderType)
    }

    fun getOutputBuffer(index: Int): ByteBuffer? {
        return this.mEncoder!!.getOutputBuffer(index)
    }

    fun getInputBuffer(index: Int): ByteBuffer? {
        return this.mEncoder!!.getInputBuffer(index)
    }

    @Throws(MediaCodec.CryptoException::class)
    fun queueInputBuffer(index: Int, offset: Int, size: Int, presentationTimeUs: Int, flags: Int) {
        this.mEncoder!!.queueInputBuffer(index, offset, size, presentationTimeUs.toLong(), flags)
    }

    fun releaseOutputBuffer(num: Int) {
        this.mEncoder!!.releaseOutputBuffer(num, false)
    }

    fun stop() {
        val mediaCodec: MediaCodec? = this.mEncoder
        if (mediaCodec != null) {
            mediaCodec.stop()
        }
    }

    fun release() {
        val mediaCodec: MediaCodec? = this.mEncoder
        if (mediaCodec != null) {
            mediaCodec.release()
            this.mEncoder = null
        }
    }
}
