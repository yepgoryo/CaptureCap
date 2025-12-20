package com.yepgoryo.CaptureCap

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.Surface
import java.io.IOException
import java.nio.ByteBuffer

class VideoEncoder(customWidth: Int, customHeight: Int, scaleRatio: Float, nativeFramerate: Int, recordQualityScale: Float, customBitrate: Boolean, recordCustomBitrate: Int, codec: String, codecProfileLevel: MediaCodecInfo.CodecProfileLevel) : Encoder {
    private val BPP: Float = 0.25f
    private var height: Int = 1920
    private var scaleRatio: Float = 1.0f
    private var width: Int = 1080
    private var codecName: String = ""
    private var codecProfileLevel: MediaCodecInfo.CodecProfileLevel? = null
    private var mCallback: Callback? = null

    private var mCodecCallback: MediaCodec.Callback = object: MediaCodec.Callback() {
        override fun onInputBufferAvailable(mediaCodec: MediaCodec, index: Int) {
            this@VideoEncoder.mCallback!!.onInputBufferAvailable(this@VideoEncoder, index)
        }

        override fun onOutputBufferAvailable(mediaCodec: MediaCodec, index: Int, bufferInfo: MediaCodec.BufferInfo) {
            this@VideoEncoder.mCallback!!.onOutputBufferAvailable(this@VideoEncoder, index, bufferInfo)
        }

        override fun onError(mediaCodec: MediaCodec, codecException: MediaCodec.CodecException) {
            Log.e("VideoEncoder", "Codec error $codecException")
            this@VideoEncoder.mCallback!!.onError(this@VideoEncoder, codecException)
        }

        override fun onOutputFormatChanged(mediaCodec: MediaCodec, mediaFormat: MediaFormat) {
            this@VideoEncoder.mCallback!!.onOutputFormatChanged(this@VideoEncoder, mediaFormat)
        }
    }

    private var mEncoder: MediaCodec? = null
    private var mSurface: Surface? = null
    private var screenFramerate: Int = 0
    private var usedBitrate: Int = 0

    init {
        width = customWidth
        height = customHeight
        this@VideoEncoder.scaleRatio = scaleRatio
        this.screenFramerate = nativeFramerate
        this.codecName = codec
        this.codecProfileLevel = codecProfileLevel
        this.usedBitrate = (nativeFramerate * BPP * ((customWidth * scaleRatio).toInt()) * ((customHeight * scaleRatio).toInt()) * recordQualityScale).toInt()
        if (customBitrate) {
            this.usedBitrate = recordCustomBitrate
        }
    }

    fun suspendCodec(drop: Int) {
        val bundle = Bundle()
        bundle.putInt("drop-input-frames", drop)
        if (this.mEncoder != null) {
            this.mEncoder?.setParameters(bundle)
        }
    }

    private fun onEncoderConfigured(mediaCodec: MediaCodec) {
        this.mSurface = mediaCodec.createInputSurface()
    }

    private fun createMediaFormat(): MediaFormat {
        val mediaFormatCreateVideoFormat: MediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, (width.toFloat() * this@VideoEncoder.scaleRatio).toInt(), (height * this@VideoEncoder.scaleRatio).toInt())
        mediaFormatCreateVideoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        mediaFormatCreateVideoFormat.setInteger(MediaFormat.KEY_BIT_RATE, this.usedBitrate)
        mediaFormatCreateVideoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, this.screenFramerate)
        mediaFormatCreateVideoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5)
        if (this.codecProfileLevel != null) {
            if (this.codecProfileLevel!!.profile != 0 && this.codecProfileLevel!!.level != 0) {
                mediaFormatCreateVideoFormat.setInteger(MediaFormat.KEY_PROFILE, this.codecProfileLevel!!.profile)
                mediaFormatCreateVideoFormat.setInteger(MediaFormat.KEY_LEVEL, this.codecProfileLevel!!.level)
            }
        }
        return mediaFormatCreateVideoFormat
    }

    fun getInputSurface(): Surface {
        return this.mSurface!!
    }

    fun release() {
        if (this.mSurface != null) {
            this.mSurface?.release()
            this.mSurface = null
        }
        if (this.mEncoder != null) {
            this.mEncoder?.release()
            this.mEncoder = null
        }
    }

    abstract class Callback : Encoder.Callback {
        open fun onInputBufferAvailable(videoEncoder: VideoEncoder, index: Int) {}

        open fun onOutputBufferAvailable(videoEncoder: VideoEncoder, index: Int, bufferInfo: MediaCodec.BufferInfo) {}

        open fun onOutputFormatChanged(videoEncoder: VideoEncoder, mediaFormat: MediaFormat) {}
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

    fun prepare()  {
        if (Looper.myLooper() == null || Looper.myLooper() == Looper.getMainLooper()) {
            throw IllegalStateException()
        }
        if (this.mEncoder != null) {
            throw IllegalStateException()
        }
        val mediaFormatCreateMediaFormat: MediaFormat = createMediaFormat()
        val mediaCodecCreateByCodecName: MediaCodec = MediaCodec.createByCodecName(this.codecName)
        if (this.mCallback != null) {
            mediaCodecCreateByCodecName.setCallback(this.mCodecCallback)
        }
        mediaCodecCreateByCodecName.configure(mediaFormatCreateMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        onEncoderConfigured(mediaCodecCreateByCodecName)
        mediaCodecCreateByCodecName.start()
        this.mEncoder = mediaCodecCreateByCodecName
    }

    fun getOutputBuffer(index: Int): ByteBuffer {
        return this.mEncoder!!.getOutputBuffer(index)!!
    }

    fun getInputBuffer(index: Int): ByteBuffer {
        return this.mEncoder!!.getInputBuffer(index)!!
    }

    fun queueInputBuffer(index: Int, offset: Int, size: Int, pstTs: Long, flags: Int) {
        this.mEncoder!!.queueInputBuffer(index, offset, size, pstTs, flags)
    }

    fun releaseOutputBuffer(index: Int) {
        this.mEncoder!!.releaseOutputBuffer(index, false)
    }

    fun stop() {
        if (this.mEncoder != null) {
            this.mEncoder?.stop()
        }
    }
}
