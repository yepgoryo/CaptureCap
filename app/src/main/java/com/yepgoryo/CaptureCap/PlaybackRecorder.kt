package com.yepgoryo.CaptureCap

import android.content.Context
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean

class PlaybackRecorder(private var context: Context,
        private var recordOnlyAudio: Boolean,
        private var virtualDisplay: VirtualDisplay?,
        private var fileDescriptor: FileDescriptor,
        private var mediaProjection: MediaProjection?,
        private var customWidth: Int,
        private var customHeight: Int,
        private var scaleRatio: Float,
        refreshRate: Int,
        private var recordMicrophone: Boolean,
        private var recordPlayback: Boolean,
        customQuality: Boolean,
        qualityScale: Float,
        customFps: Boolean,
        fpsValue: Int,
        private var customBitrate: Boolean,
        bitrateValue: Int,
        chooseCustomCodec: Boolean,
        codecName: String,
        chooseCustomAudioCodec: Boolean,
        audioCodecName: String,
        private var customSampleRate: Int,
        private var customChannelsCount: Int,
        private var mediaAudioSource: Boolean,
        private var gameAudioSource: Boolean,
        private var unknownAudioSource: Boolean) {
    private var INVALID_INDEX: Int = -1
    private var STOP_WITH_EOS: Int = 1
    private var TAG: String = "PlaybackRecorder"
    private var currentProfileLevel: MediaCodecInfo.CodecProfileLevel? = null
    private var customAudioCodec: String = ""
    private var customCodec: String = ""
    private var mAudioEncoder: AudioPlaybackRecorder? = null
    private var mAudioPtsOffset: Long = 0L
    private var mCallback: Callback? = null
    private var mHandler: CallbackHandler? = null
    private var mMuxer: MediaMuxer? = null
    private var mVideoEncoder: VideoEncoder? = null
    private var mVideoPtsOffset: Long = 0L
    private var mWorker: HandlerThread? = null
    private var nativeFramerate: Int = refreshRate
    private var recordCustomBitrate: Int = 0
    private var recordQualityScale: Float = 1.0f
    private var try60FPS: Boolean = true
    private var useCustomAudioCodec: Boolean = false
    private var useCustomCodec: Boolean = false
    private var mMuxerStarted: Boolean = false
    private var tryNormalFPS: Boolean = true
    private var tryNativeFPS: Boolean = true
    private var doRestart: Boolean = false
    private var mVideoTrackIndex: Int = INVALID_INDEX
    private var mAudioTrackIndex: Int = INVALID_INDEX
    private var codecsTryIndex: Int = 0
    private var codecsTryFramerate: Int = 30
    private var lastPaused: Long = 0
    private var lastTimeout: Long = 0
    private var mForceQuit: AtomicBoolean = AtomicBoolean(false)
    private var mIsRunning: AtomicBoolean = AtomicBoolean(false)
    private var mIsPaused: AtomicBoolean = AtomicBoolean(false)
    private var mVideoOutputFormat: MediaFormat? = null
    private var mAudioOutputFormat: MediaFormat? = null
    private var mPendingVideoEncoderBufferIndices: LinkedList<Integer> = LinkedList()
    private var mPendingAudioEncoderBufferIndices: LinkedList<Integer>  = LinkedList()
    private var mPendingAudioEncoderBufferInfos: LinkedList<MediaCodec.BufferInfo> = LinkedList()
    private var mPendingVideoEncoderBufferInfos: LinkedList<MediaCodec.BufferInfo> = LinkedList()
    private var codecsList: ArrayList<String> = ArrayList()
    private var codecsAudioList: ArrayList<String> = ArrayList()
    private var codecProfileLevels: ArrayList<MediaCodecInfo.CodecProfileLevel> = ArrayList()

    interface Callback {
        fun onRecording(presentationTimeUs: Long)

        fun onStart()

        fun onStop(th: Throwable)
    }

    private enum class MessageCommand {
        MSG_START,
        MSG_STOP,
        MSG_ERROR
    }

    init {
        getAllCodecs()
        getAllAudioCodecs()
        if (customQuality) {
            this.recordQualityScale = qualityScale
        }
        if (customFps) {
            this.nativeFramerate = fpsValue
        }
        if (customBitrate) {
            this.recordCustomBitrate = bitrateValue
        }
        if (chooseCustomCodec && this.codecsList.contains(codecName)) {
            this.useCustomCodec = chooseCustomCodec
            this.customCodec = codecName
        }
        if (chooseCustomAudioCodec && this.codecsAudioList.contains(audioCodecName)) {
            this.useCustomAudioCodec = chooseCustomAudioCodec
            this.customAudioCodec = audioCodecName
        }
        if (this.nativeFramerate <= 60) {
            this.try60FPS = false
        }
    }

    private fun getAllAudioCodecs() {
        for (mediaCodecInfo in MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos) {
            if (mediaCodecInfo.isEncoder) {
                for (type in mediaCodecInfo.getSupportedTypes()) {
                    if (type.equals(MediaFormat.MIMETYPE_AUDIO_AAC, ignoreCase = true)) {
                        this.codecsAudioList.add(mediaCodecInfo.name)
                    }
                }
            }
        }
    }

    private fun getAllCodecs() {
        for (mediaCodecInfo in MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos) {
            if (mediaCodecInfo.isEncoder) {
                for (type in mediaCodecInfo.getSupportedTypes()) {
                    if (type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)) {
                        this.codecsList.add(mediaCodecInfo.name)
                        this.codecProfileLevels.add(mediaCodecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).profileLevels[0])
                    }
                }
            }
        }
    }

    private fun getCodec(): String {
        if (this.codecsTryIndex < this.codecsList.size) {
            if (this.tryNativeFPS) {
                this.codecsTryFramerate = this.nativeFramerate
            } else if (this.try60FPS) {
                this.codecsTryFramerate = 60
            } else if (this.tryNormalFPS) {
                this.codecsTryFramerate = 30
            }
        } else {
            if (this.tryNativeFPS) {
                this.tryNativeFPS = false
            } else if (this.try60FPS) {
                this.try60FPS = false
            }
            this.codecsTryIndex = 0
        }
        val codec: String = this.codecsList.get(this.codecsTryIndex)
        this.currentProfileLevel = this.codecProfileLevels.get(this.codecsTryIndex)
        this.codecsTryIndex++
        return codec
    }

    fun pause() {
        this.mIsPaused.set(true)
        this.lastPaused = System.currentTimeMillis() * 1000
        this.mVideoEncoder?.suspendCodec(1)
    }

    fun resume() {
        this.lastTimeout += (System.currentTimeMillis() * 1000) - this.lastPaused
        this.mVideoEncoder?.suspendCodec(0)
        this.mIsPaused.set(false)
    }

    fun quit() {
        this.mForceQuit.set(true)
        if (!this.mIsRunning.get()) {
            release()
        } else {
            signalStop(false)
        }
    }

    fun restart() {
        if (this.codecsTryIndex >= this.codecsList.size && this.tryNormalFPS) {
            Toast.makeText(context, R.string.error_recorder_codec_error, Toast.LENGTH_SHORT).show()
            quit()
        } else {
            this.doRestart = true
            release()
            start()
            this.doRestart = false
        }
    }

    fun start() {
        this.lastPaused = 0L
        this.lastTimeout = 0L
        if (!this.recordOnlyAudio) {
            var codec: String = getCodec()
            if (this.useCustomCodec) {
                codec = this.customCodec
                this.currentProfileLevel = this.codecProfileLevels.get(this.codecsList.lastIndexOf(codec))
            }
            this.mVideoEncoder = VideoEncoder(customWidth, customHeight, scaleRatio, this.nativeFramerate, this.recordQualityScale, customBitrate, this.recordCustomBitrate, codec, this.currentProfileLevel!!)
        } else {
            this.mVideoEncoder = null
        }
        if (!recordMicrophone && !recordPlayback) {
            this.mAudioEncoder = null
        } else {
            this.mAudioEncoder = AudioPlaybackRecorder(recordMicrophone, recordPlayback, customSampleRate, customChannelsCount, mediaProjection, this.useCustomAudioCodec, this.customAudioCodec, context, mediaAudioSource, gameAudioSource, unknownAudioSource)
        }
        if (this.mWorker != null && !this.doRestart) {
            throw IllegalStateException()
        }
        val handlerThread = HandlerThread(TAG)
        this.mWorker = handlerThread
        handlerThread.start()
        val callbackHandler = CallbackHandler(this.mWorker!!.getLooper())
        this.mHandler = callbackHandler
        callbackHandler.sendEmptyMessage(MessageCommand.MSG_START.ordinal)
    }

    fun setCallback(callback: Callback) {
        this.mCallback = callback
    }

    private inner class CallbackHandler(looper: Looper) : Handler(looper) {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun handleMessage(message: Message) {
            val messageCommand: MessageCommand = MessageCommand.entries[message.what]
            if (messageCommand == MessageCommand.MSG_START) {
                try {
                    this@PlaybackRecorder.record()
                    if (this@PlaybackRecorder.mCallback != null) {
                        this@PlaybackRecorder.mCallback!!.onStart()
                    }
                } catch (error: Exception) {
                    message.obj = error
                }
            } else if (messageCommand == MessageCommand.MSG_STOP || messageCommand == MessageCommand.MSG_ERROR) {
                this@PlaybackRecorder.stopEncoders()
                if (message.arg1 != STOP_WITH_EOS) {
                    this@PlaybackRecorder.signalEndOfStream()
                }
                if (this@PlaybackRecorder.mCallback != null) {
                    this@PlaybackRecorder.mCallback!!.onStop((message.obj as Throwable))
                }
                if (this@PlaybackRecorder.mForceQuit.get() || this@PlaybackRecorder.useCustomCodec) {
                    this@PlaybackRecorder.release()
                } else {
                    this@PlaybackRecorder.restart()
                }
            }
        }
    }

    fun signalEndOfStream() {
        Log.v("PlaybackRecorder", "Signal end of stream")
        val bufferInfo: MediaCodec.BufferInfo = MediaCodec.BufferInfo()
        bufferInfo.set(0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        val byteBufferAllocate: ByteBuffer = ByteBuffer.allocate(0)
        if (!this.recordOnlyAudio && this.mVideoTrackIndex != INVALID_INDEX) {
            writeSampleData(this.mVideoTrackIndex, bufferInfo, byteBufferAllocate)
        }
        if (this.mAudioTrackIndex != INVALID_INDEX) {
            writeSampleData(this.mAudioTrackIndex, bufferInfo, byteBufferAllocate)
        }
        this.mVideoTrackIndex = INVALID_INDEX
        this.mAudioTrackIndex = INVALID_INDEX
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun record() {
        if (this.mIsRunning.get() || this.mForceQuit.get() || (virtualDisplay == null && !this.recordOnlyAudio)) {
            throw IllegalStateException()
        }
        this.mIsRunning.set(true)
        try {
            this.mMuxer = MediaMuxer(fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (!this.recordOnlyAudio) {
                prepareVideoEncoder()
            }
            prepareAudioEncoder()
            if (!this.recordOnlyAudio) {
                virtualDisplay?.surface = this.mVideoEncoder!!.getInputSurface()
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun muxVideo(index: Int, bufferInfo: MediaCodec.BufferInfo) {
        if (this.mIsRunning.get()) {
            if (!this.mMuxerStarted || this.mVideoTrackIndex == INVALID_INDEX) {
                this.mPendingVideoEncoderBufferIndices.add(index as Integer)
                this.mPendingVideoEncoderBufferInfos.add(bufferInfo)
            } else {
                if (!this.mIsPaused.get()) {
                    val outputBuffer: ByteBuffer = this.mVideoEncoder!!.getOutputBuffer(index)
                    bufferInfo.presentationTimeUs -= this.lastTimeout
                    writeSampleData(this.mVideoTrackIndex, bufferInfo, outputBuffer)
                }
                this.mVideoEncoder!!.releaseOutputBuffer(index)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    this.mVideoTrackIndex = INVALID_INDEX
                    signalStop(true)
                }
            }
        }
    }

    fun muxAudio(index: Int, bufferInfo: MediaCodec.BufferInfo) {
        if (this.mIsRunning.get()) {
            if (!this.mMuxerStarted || this.mAudioTrackIndex == INVALID_INDEX) {
                this.mPendingAudioEncoderBufferIndices.add(index as Integer)
                this.mPendingAudioEncoderBufferInfos.add(bufferInfo)
            } else {
                if (!this.mIsPaused.get()) {
                    val outputBuffer: ByteBuffer = this.mAudioEncoder!!.getOutputBuffer(index)!!
                    bufferInfo.presentationTimeUs -= this.lastTimeout
                    writeSampleData(this.mAudioTrackIndex, bufferInfo, outputBuffer)
                }
                this.mAudioEncoder!!.releaseOutputBuffer(index)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    this.mAudioTrackIndex = INVALID_INDEX
                    signalStop(true)
                }
            }
        }
    }

    private fun writeSampleData(index: Int, bufferInfo: MediaCodec.BufferInfo, byteBuffer: ByteBuffer) {
        var byteBufferWrite: ByteBuffer? = byteBuffer
        var callback: Callback
        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            bufferInfo.size = 0
        }
        if (bufferInfo.size != 0 || (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            if (bufferInfo.presentationTimeUs != 0L) {
                if (index == this.mVideoTrackIndex) {
                    resetVideoPts(bufferInfo)
                } else if (index == this.mAudioTrackIndex) {
                    resetAudioPts(bufferInfo)
                }
            }
            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0 && this.mCallback != null) {
                this.mCallback?.onRecording(bufferInfo.presentationTimeUs)
            }
        } else {
            byteBufferWrite = null
        }
        if (byteBufferWrite != null) {
            byteBufferWrite.position(bufferInfo.offset)
            byteBufferWrite.limit(bufferInfo.offset + bufferInfo.size)
            this.mMuxer!!.writeSampleData(index, byteBufferWrite, bufferInfo)
        }
    }

    private fun resetAudioPts(bufferInfo: MediaCodec.BufferInfo) {
        if (this.mAudioPtsOffset == 0L) {
            this.mAudioPtsOffset = bufferInfo.presentationTimeUs
            bufferInfo.presentationTimeUs = 0L
        } else {
            bufferInfo.presentationTimeUs -= this.mAudioPtsOffset
        }
    }

    private fun resetVideoPts(bufferInfo: MediaCodec.BufferInfo) {
        if (this.mVideoPtsOffset == 0L) {
            this.mVideoPtsOffset = bufferInfo.presentationTimeUs
            bufferInfo.presentationTimeUs = 0L
        } else {
            bufferInfo.presentationTimeUs -= this.mVideoPtsOffset
        }
    }

    fun resetVideoOutputFormat(mediaFormat: MediaFormat) {
        if (this.mVideoTrackIndex >= 0 || this.mMuxerStarted) {
            throw IllegalStateException()
        }
        this.mVideoOutputFormat = mediaFormat
    }

    fun resetAudioOutputFormat(mediaFormat: MediaFormat) {
        if (this.mAudioTrackIndex >= 0 || this.mMuxerStarted) {
            throw IllegalStateException()
        }
        this.mAudioOutputFormat = mediaFormat
    }

    fun startMuxerIfReady() {
        if (this.mMuxerStarted) {
            return
        }
        if (this.mVideoOutputFormat == null && !this.recordOnlyAudio) {
            return
        }
        if (this.mAudioEncoder != null && this.mAudioOutputFormat == null) {
            return
        }
        if (!this.recordOnlyAudio) {
            this.mVideoTrackIndex = this.mMuxer!!.addTrack(this.mVideoOutputFormat!!)
        }
        this.mAudioTrackIndex = if (this.mAudioEncoder == null) {
            INVALID_INDEX
        } else {
            this.mMuxer!!.addTrack(this.mAudioOutputFormat!!)
        }
        this.mMuxer!!.start()
        this.mMuxerStarted = true
        if (this.mPendingVideoEncoderBufferIndices.isEmpty() && this.mPendingAudioEncoderBufferIndices.isEmpty()) {
            return
        }
        if (!this.recordOnlyAudio) {
            while (true) {
                val bufferInfoPoll: MediaCodec.BufferInfo? = this.mPendingVideoEncoderBufferInfos.poll()
                if (bufferInfoPoll == null) {
                    break
                } else {
                    muxVideo(this.mPendingVideoEncoderBufferIndices.poll().toInt(), bufferInfoPoll)
                }
            }
        }
        if (this.mAudioEncoder == null) {
            return
        }
        while (true) {
            val bufferInfoPoll: MediaCodec.BufferInfo? = this.mPendingAudioEncoderBufferInfos.poll()
            if (bufferInfoPoll == null) {
                return
            } else {
                muxAudio(this.mPendingAudioEncoderBufferIndices.poll().toInt(), bufferInfoPoll)
            }
        }
    }

    private fun prepareVideoEncoder() {
        this.mVideoEncoder!!.setCallback(object: VideoEncoder.Callback() {
            override fun onOutputBufferAvailable(videoEncoder: VideoEncoder, index: Int, bufferInfo: MediaCodec.BufferInfo) {
                try {
                    this@PlaybackRecorder.muxVideo(index, bufferInfo)
                } catch (e: Exception) {
                    Message.obtain(this@PlaybackRecorder.mHandler, MessageCommand.MSG_ERROR.ordinal, e).sendToTarget()
                }
            }

            override fun onError(encoder: Encoder, exc: Exception) {
                Message.obtain(this@PlaybackRecorder.mHandler, MessageCommand.MSG_ERROR.ordinal, exc).sendToTarget()
            }

            override fun onOutputFormatChanged(videoEncoder: VideoEncoder, mediaFormat: MediaFormat) {
                this@PlaybackRecorder.resetVideoOutputFormat(mediaFormat)
                this@PlaybackRecorder.startMuxerIfReady()
            }
        })
        this.mVideoEncoder!!.prepare()
    }

    private fun prepareAudioEncoder() {
        if (this.mAudioEncoder == null) {
            return
        }
        this.mAudioEncoder!!.setCallback(object: AudioEncoder.Callback() {
            override fun onOutputBufferAvailable(audioEncoder: AudioEncoder, index: Int, bufferInfo: MediaCodec.BufferInfo) {
                try {
                    this@PlaybackRecorder.muxAudio(index, bufferInfo)
                } catch (e: Exception) {
                    Message.obtain(this@PlaybackRecorder.mHandler, MessageCommand.MSG_ERROR.ordinal, e).sendToTarget()
                }
            }

            override fun onOutputFormatChanged(audioEncoder: AudioEncoder, mediaFormat: MediaFormat) {
                this@PlaybackRecorder.resetAudioOutputFormat(mediaFormat)
                this@PlaybackRecorder.startMuxerIfReady()
            }

            override fun onError(encoder: Encoder, exc: Exception) {
                Message.obtain(this@PlaybackRecorder.mHandler, MessageCommand.MSG_ERROR.ordinal, exc).sendToTarget()
            }
        })
        this.mAudioEncoder!!.prepare()
    }

    private fun signalStop(z: Boolean) {
        this.mHandler!!.sendMessageAtFrontOfQueue(Message.obtain(this.mHandler, MessageCommand.MSG_STOP.ordinal, if (z) STOP_WITH_EOS else 0, 0))
    }

    fun stopEncoders() {
        this.mIsRunning.set(false)
        this.mPendingAudioEncoderBufferInfos.clear()
        this.mPendingAudioEncoderBufferIndices.clear()
        this.mPendingVideoEncoderBufferInfos.clear()
        this.mPendingVideoEncoderBufferIndices.clear()
        try {
            if (this.mVideoEncoder != null) {
                this.mVideoEncoder?.stop()
            }
        } catch (exc: Exception) {
            throw exc
        }
        try {
            if (this.mAudioEncoder != null) {
                this.mAudioEncoder?.stop()
            }
        } catch (exc: Exception) {
            throw exc
        }
    }

    fun release() {
        if (virtualDisplay != null) {
            virtualDisplay?.surface = null
            if (!this.doRestart) {
                virtualDisplay?.release()
            }
        }
        this.mVideoOutputFormat = null
        this.mAudioOutputFormat = null
        this.mVideoTrackIndex = INVALID_INDEX
        this.mAudioTrackIndex = INVALID_INDEX
        this.mMuxerStarted = false
        if (this.mWorker != null) {
            this.mWorker?.quitSafely()
            this.mWorker = null
        }
        if (this.mVideoEncoder != null) {
            this.mVideoEncoder?.release()
            this.mVideoEncoder = null
        }
        if (this.mAudioEncoder != null) {
            this.mAudioEncoder?.release()
            this.mAudioEncoder = null
        }
        if (this.mMuxer != null) {
            try {
                this.mMuxer?.stop()
                this.mMuxer?.release()
            } catch (exc: Exception) {
                throw exc
            }
            this.mMuxer = null
        }
        this.mHandler = null
    }

    protected fun finalize() {
        if (this.virtualDisplay != null) {
            release()
        }
    }
}
