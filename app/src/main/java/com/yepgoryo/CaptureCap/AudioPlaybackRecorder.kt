package com.yepgoryo.CaptureCap

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.SparseLongArray
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.yepgoryo.CaptureCap.AudioEncoder.Callback

import java.io.IOException
import java.nio.ByteBuffer
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

class AudioPlaybackRecorder(private val recordMicrophone: Boolean,
                            private val recordAudio: Boolean,
                            sampleRate: Int,
                            channels: Int,
                            mediaProjection: MediaProjection?,
                            useCustomCodec: Boolean,
                            codecName: String,
                            context: Context,
                            private var sourceMedia: Boolean,
                            private var sourceGame: Boolean,
                            private var sourceUnknown: Boolean
) : Encoder {
    private val LAST_FRAME_ID: Int = -1
    private val TAG: String = "AudioPlaybackRecorder"
    private val audioBufLimit: Int = 2048
    private var mCallback: Callback? = null
    private var mCallbackDelegate: CallbackDelegate? = null
    private var mChannelConfig: Int
    private var mChannelsSampleRate: Int
    private var mEncoder: AudioEncoder
    private var mMic: AudioRecord? = null
    private var mPlayback: AudioRecord? = null
    private var mProjection: MediaProjection? = null
    private var mRecordHandler: RecordHandler? = null
    private var mRecordThread: HandlerThread
    private var mSampleRate: Int
    private var mainContext: Context
    private var mFormat: Int = 2
    private var mForceStop: AtomicBoolean = AtomicBoolean(false)
    private var mFramesUsCache: SparseLongArray = SparseLongArray(2)

    enum class RecordMessage {
        MSG_PREPARE,
        MSG_FEED_INPUT,
        MSG_DRAIN_OUTPUT,
        MSG_RELEASE_OUTPUT,
        MSG_STOP,
        MSG_RELEASE
    }

    init {
        this.mEncoder = AudioEncoder(sampleRate, channels, useCustomCodec, codecName)
        this.mSampleRate = sampleRate
        this.mChannelsSampleRate = sampleRate * 2
        this.mChannelConfig = 12
        if (channels == 1) {
            this.mChannelConfig = 16
        }
        this.mProjection = mediaProjection
        this.mRecordThread = HandlerThread(TAG)
        this.mainContext = context
    }

    fun setCallback(callback: Encoder.Callback) {
        this.mCallback = callback as Callback?
    }

    fun setCallback(callback: Callback) {
        this.mCallback = callback
    }

    fun prepare() {
        val looper = Looper.myLooper()
        if (looper != null && this.mCallback != null) {
            this.mCallbackDelegate = CallbackDelegate(looper, this.mCallback)
        }
        this.mRecordThread.start()
        this.mRecordHandler = RecordHandler(this.mRecordThread.getLooper())
        this.mRecordHandler?.sendEmptyMessage(RecordMessage.MSG_PREPARE.ordinal)
    }

    fun stop() {
        val callbackDelegate: CallbackDelegate? = this.mCallbackDelegate
        callbackDelegate?.removeCallbacksAndMessages(null)
        this.mForceStop.set(true)
        val recordHandler: RecordHandler? = this.mRecordHandler
        recordHandler?.sendEmptyMessage(RecordMessage.MSG_STOP.ordinal)
    }

    fun release() {
        val recordHandler: RecordHandler? = this.mRecordHandler
        recordHandler?.sendEmptyMessage(RecordMessage.MSG_RELEASE.ordinal)
        this.mRecordThread.quitSafely()
    }

    fun releaseOutputBuffer(index: Int) {
        Message.obtain(this.mRecordHandler, RecordMessage.MSG_RELEASE_OUTPUT.ordinal, index, 0).sendToTarget()
    }

    fun getOutputBuffer(index: Int): ByteBuffer? {
        return this.mEncoder.getOutputBuffer(index)
    }

    inner class CallbackDelegate(looper: Looper, callback: AudioEncoder.Callback?) : Handler(looper) {
        private val mCallback: AudioEncoder.Callback? = callback

        fun onError(encoder: Encoder, exc: Exception) {
            Message.obtain(this) {
                if (this@CallbackDelegate.mCallback != null) {
                    this@CallbackDelegate.mCallback.onError(encoder, exc)
                }
            }.sendToTarget()
        }

        fun onOutputFormatChanged(audioEncoder: AudioEncoder, mediaFormat: MediaFormat) {
            Message.obtain(this, object: Runnable {
                override fun run() {
                    if (this@CallbackDelegate.mCallback != null) {
                        this@CallbackDelegate.mCallback.onOutputFormatChanged(audioEncoder, mediaFormat)
                    }
                }
            }).sendToTarget()
        }

        fun onOutputBufferAvailable(audioEncoder: AudioEncoder, index: Int, bufferInfo: MediaCodec.BufferInfo) {
            Message.obtain(this, object: Runnable {
                override fun run() {
                    this@CallbackDelegate.mCallback?.onOutputBufferAvailable(audioEncoder, index, bufferInfo)
                }
            }).sendToTarget()
        }
    }

    inner class RecordHandler(looper: Looper) : Handler(looper) {
        private val mCachedInfos: LinkedList<MediaCodec.BufferInfo> = LinkedList<MediaCodec.BufferInfo>()
        private val mMuxingOutputBufferIndices: LinkedList<Integer> = LinkedList<Integer>()
        private val mPollRate: Int = 2048000 / this@AudioPlaybackRecorder.mSampleRate

        @RequiresApi(Build.VERSION_CODES.Q)
        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        override fun handleMessage(message: Message) {
            val recordMessage: RecordMessage = RecordMessage.entries[message.what]
            when (recordMessage) {
                RecordMessage.MSG_PREPARE -> {
                    if (this@AudioPlaybackRecorder.recordAudio) {
                        val audioPlaybackRecorder: AudioPlaybackRecorder = this@AudioPlaybackRecorder
                        val audioRecordCreateAudioRecord: AudioRecord? = audioPlaybackRecorder.createAudioRecord(audioPlaybackRecorder.mSampleRate, this@AudioPlaybackRecorder.mChannelConfig, 2, this@AudioPlaybackRecorder.mProjection!!)
                        if (audioRecordCreateAudioRecord == null) {
                            this@AudioPlaybackRecorder.mCallbackDelegate?.onError(this@AudioPlaybackRecorder, IllegalArgumentException())
                        } else {
                            audioRecordCreateAudioRecord.startRecording()
                            this@AudioPlaybackRecorder.mPlayback = audioRecordCreateAudioRecord
                        }
                    }
                    if (this@AudioPlaybackRecorder.recordMicrophone) {
                        val audioRecordCreateMicRecord: AudioRecord? = this@AudioPlaybackRecorder.createMicRecord(this@AudioPlaybackRecorder.mSampleRate, this@AudioPlaybackRecorder.mChannelConfig, this@AudioPlaybackRecorder.mFormat)
                        if (audioRecordCreateMicRecord == null) {
                            this@AudioPlaybackRecorder.mCallbackDelegate?.onError(this@AudioPlaybackRecorder, IllegalArgumentException())
                        } else {
                            audioRecordCreateMicRecord.startRecording()
                            this@AudioPlaybackRecorder.mMic = audioRecordCreateMicRecord
                        }
                    }
                    try {
                        this@AudioPlaybackRecorder.mEncoder.prepare()
                    } catch (e: Exception) {
                        this@AudioPlaybackRecorder.mCallbackDelegate?.onError(this@AudioPlaybackRecorder, e)
                    }
                }
                RecordMessage.MSG_DRAIN_OUTPUT -> {
                    offerOutput()
                    pollInputIfNeed()
                }
                RecordMessage.MSG_RELEASE_OUTPUT -> {
                    this@AudioPlaybackRecorder.mEncoder.releaseOutputBuffer(message.arg1)
                    this.mMuxingOutputBufferIndices.poll()
                    pollInputIfNeed()
                }
                RecordMessage.MSG_STOP -> {
                    if (this@AudioPlaybackRecorder.recordAudio && this@AudioPlaybackRecorder.mPlayback != null) {
                        this@AudioPlaybackRecorder.mPlayback?.stop()
                    }
                    if (this@AudioPlaybackRecorder.recordMicrophone && this@AudioPlaybackRecorder.mMic != null) {
                        this@AudioPlaybackRecorder.mMic?.stop()
                    }
                    this@AudioPlaybackRecorder.mEncoder.stop()
                }
                RecordMessage.MSG_RELEASE -> {
                    if (this@AudioPlaybackRecorder.mPlayback != null) {
                        this@AudioPlaybackRecorder.mPlayback?.release()
                        this@AudioPlaybackRecorder.mPlayback = null
                    }
                    if (this@AudioPlaybackRecorder.recordMicrophone) {
                        this@AudioPlaybackRecorder.mMic?.release()
                        this@AudioPlaybackRecorder.mMic = null
                    }
                    this@AudioPlaybackRecorder.mEncoder.release()
                }
                else -> {}
            }

            if ((recordMessage == RecordMessage.MSG_PREPARE || recordMessage == RecordMessage.MSG_FEED_INPUT) && !this@AudioPlaybackRecorder.mForceStop.get()) {
                val iPollInput: Int = pollInput()
                if (iPollInput >= 0) {
                    this@AudioPlaybackRecorder.feedAudioEncoder(iPollInput)
                    if (!this@AudioPlaybackRecorder.mForceStop.get()) {
                        sendEmptyMessage(RecordMessage.MSG_DRAIN_OUTPUT.ordinal)
                    }
                } else {
                    sendEmptyMessageDelayed(RecordMessage.MSG_FEED_INPUT.ordinal,this.mPollRate.toLong())
                }
            }
        }

        private fun offerOutput() {
            while (!this@AudioPlaybackRecorder.mForceStop.get()) {
                var bufferInfoPoll: MediaCodec.BufferInfo? = this.mCachedInfos.poll()
                if (bufferInfoPoll == null) {
                    bufferInfoPoll = MediaCodec.BufferInfo()
                }
                val iDequeueOutputBuffer: Int? = this@AudioPlaybackRecorder.mEncoder.mEncoder?.dequeueOutputBuffer(bufferInfoPoll, 1L)
                if (iDequeueOutputBuffer == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    this@AudioPlaybackRecorder.mCallbackDelegate!!.onOutputFormatChanged(this@AudioPlaybackRecorder.mEncoder, this@AudioPlaybackRecorder.mEncoder.mEncoder!!.getOutputFormat())
                }
                if (iDequeueOutputBuffer != null) {
                    if (iDequeueOutputBuffer < 0) {
                        bufferInfoPoll.set(0, 0, 0L, 0)
                        this.mCachedInfos.offer(bufferInfoPoll)
                        return
                    } else {
                        this.mMuxingOutputBufferIndices.offer(iDequeueOutputBuffer as Integer)
                        this@AudioPlaybackRecorder.mCallbackDelegate!!.onOutputBufferAvailable(
                            this@AudioPlaybackRecorder.mEncoder,
                            iDequeueOutputBuffer,
                            bufferInfoPoll
                        )
                    }
                }
            }
        }

        private fun pollInput(): Int {
            return this@AudioPlaybackRecorder.mEncoder.mEncoder!!.dequeueInputBuffer(0L)
        }

        private fun pollInputIfNeed() {
            if (this.mMuxingOutputBufferIndices.size <= 1 && !this@AudioPlaybackRecorder.mForceStop.get()) {
                removeMessages(RecordMessage.MSG_FEED_INPUT.ordinal)
                sendEmptyMessageDelayed(RecordMessage.MSG_FEED_INPUT.ordinal, 0L)
            }
        }
    }

    fun feedAudioEncoder(index: Int) {
        if (index < 0 || mForceStop.get()) return

        var eos: Boolean = false
        if (mPlayback != null) {
            eos = (mPlayback!!.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED)
        }
        if (mMic != null) {
            eos = (mMic!!.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED)
        }

        val offset: Int = mEncoder.getInputBuffer(index)!!.position()
        var read = 0

        if (!eos) {
            if (recordMicrophone && !recordAudio) {
                val frameMic = ByteArray(audioBufLimit)
                val micRead = mMic!!.read(frameMic, 0, audioBufLimit)
                mEncoder.getInputBuffer(index)?.put(frameMic)

                if (micRead >= 0) {
                    read = micRead
                } else {
                    read = 0
                }
            } else if (!recordMicrophone && recordAudio) {
                val framePlayback = ByteArray(audioBufLimit)
                val playbackRead = mPlayback!!.read(framePlayback, 0, audioBufLimit)
                mEncoder.getInputBuffer(index)?.put(framePlayback)

                if (playbackRead >= 0) {
                    read = playbackRead
                } else {
                    read = 0
                }
            } else if (recordMicrophone && recordAudio) {
                val framePlayback = ByteArray(audioBufLimit)
                val playbackRead: Int = mPlayback!!.read(framePlayback, 0, audioBufLimit)
                val frameMic = ByteArray(audioBufLimit)
                var micRead: Int = mMic!!.read(frameMic, 0, audioBufLimit)

                if (playbackRead < micRead) {
                    micRead = playbackRead
                }

                var i = 0
                while (i < micRead) {
                    framePlayback[i] = (framePlayback[i] + frameMic[i]).toByte()
                    i += 1
                }

                mEncoder.getInputBuffer(index)?.put(framePlayback)

                if (playbackRead >= 0) {
                    read = playbackRead
                } else {
                    read = 0
                }
            }

        }

        val pstTs: Long = this.calculateFrameTimestamp(read shl 3)
        var flags: Int = MediaCodec.BUFFER_FLAG_KEY_FRAME

        if (eos) {
            flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM
        }

        mEncoder.queueInputBuffer(index, offset, read, pstTs.toInt(), flags)
    }

    private fun calculateFrameTimestamp(totalBits: Int): Long {
        val totalSamples: Int = totalBits shr 4
        var frameUs: Long = this.mFramesUsCache.get(totalSamples, -1L)
        if (frameUs == -1L) {
            frameUs = (totalSamples.toLong() * 1000000) / this.mChannelsSampleRate
            this.mFramesUsCache.put(totalSamples, frameUs)
        }
        var timeUs: Long = (SystemClock.elapsedRealtimeNanos() / 1000) - frameUs
        var lastFrameUs: Long = this.mFramesUsCache.get(LAST_FRAME_ID, -1L)
        var currentUs: Long
        if (lastFrameUs == -1L) {
            currentUs = timeUs
        } else {
            currentUs = lastFrameUs
        }
        if (timeUs-currentUs >= (frameUs shl 1)) {
            currentUs = timeUs 
        }
        this.mFramesUsCache.put(LAST_FRAME_ID, currentUs+frameUs)
        return currentUs
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @RequiresApi(Build.VERSION_CODES.Q)
    fun createAudioRecord(sampleRate: Int, channelMask: Int, audioEncoding: Int, mediaProjection: MediaProjection): AudioRecord? {
        val audioFormatBuild: AudioFormat = AudioFormat.Builder().setEncoding(audioEncoding).setSampleRate(sampleRate).setChannelMask(channelMask).build()
        var builderM: AudioPlaybackCaptureConfiguration.Builder = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
        if (!this.sourceMedia && !this.sourceGame && !this.sourceUnknown) {
            builderM = builderM.addMatchingUsage(AudioAttributes.USAGE_MEDIA)
        }
        if (this.sourceMedia) {
            builderM = builderM.addMatchingUsage(AudioAttributes.USAGE_MEDIA)
        }
        if (this.sourceGame) {
            builderM = builderM.addMatchingUsage(AudioAttributes.USAGE_GAME)
        }
        if (this.sourceUnknown) {
            builderM = builderM.addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
        }
        val audioPlaybackCaptureConfigurationBuild: AudioPlaybackCaptureConfiguration = builderM.build()
        try {
            val audioRecordBuild: AudioRecord = AudioRecord.Builder().setAudioFormat(audioFormatBuild).setBufferSizeInBytes(audioBufLimit).setAudioPlaybackCaptureConfig(audioPlaybackCaptureConfigurationBuild).build()
            if (audioRecordBuild.state == 0) {
                return null
            }
            return audioRecordBuild
        } catch (unused: Exception) {
            Toast.makeText(this.mainContext, R.string.error_playback_not_allowed, Toast.LENGTH_SHORT).show()
            return null
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun createMicRecord(sampleRate: Int, channelConfig: Int, format: Int): AudioRecord? {
        try {
            val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, format, audioBufLimit)
            if (audioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
                return null
            }
            return audioRecord
        } catch (unused: Exception) {
            return null
        }
    }
}
