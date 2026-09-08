package com.yepgoryo.CaptureCap

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
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
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.util.SparseLongArray
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.yepgoryo.CaptureCap.AudioEncoder.Callback

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean

class AudioPlaybackRecorder(
    private val recordMicrophone: Boolean,
    private val recordAudio: Boolean,
    sampleRate: Int,
    channels: Int,
    mediaProjection: MediaProjection?,
    useCustomCodec: Boolean,
    codecName: String,
    useCustomFormat: Boolean,
    formatName: String,
    recordOnlyAudio: Boolean,
    private var shizukuRecordPhoneCall: Boolean,
    private var shizukuRecordService: IShizukuRecordService?,
    private var shizukuAudioSource: GlobalProperties.ShizukuPhoneCallAudioSource,
    private val context: Context,
    private var sourceMedia: Boolean,
    private var sourceGame: Boolean,
    private var sourceUnknown: Boolean,
) : Encoder {
    private val LAST_FRAME_ID: Int = -1
    private val TAG: String = "AudioPlaybackRecorder"
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
    private var audioMuted: Boolean = false
    private var shizukuPhoneCallMuted: Boolean = false
    private var micMuted: Boolean = false
    private var appSettings: GlobalProperties? = null
    private var isDeviceBluetoothSCO = false
    private var audioManager: AudioManager? = null
    private var scrcpyInputStream: DataInputStream? = null
    private var scrcpyInputPfd: ParcelFileDescriptor? = null

    companion object {
        const val AUDIO_BUFFER_SIZE = 4096
    }

    enum class RecordMessage {
        MSG_PREPARE,
        MSG_FEED_INPUT,
        MSG_DRAIN_OUTPUT,
        MSG_RELEASE_OUTPUT,
        MSG_STOP,
        MSG_RELEASE
    }

    init {
        this.mEncoder = AudioEncoder(sampleRate, channels, useCustomCodec, codecName, useCustomFormat, formatName, recordOnlyAudio)
        this.mSampleRate = sampleRate
        this.mChannelsSampleRate = sampleRate * 2
        this.mChannelConfig = 12
        if (channels == 1) {
            this.mChannelConfig = 16
        }
        this.mProjection = mediaProjection
        this.mRecordThread = HandlerThread(TAG)
        this.mainContext = context
        this.appSettings = GlobalProperties(context)
        this.audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
        if (shizukuRecordPhoneCall) {
            initScrcpyRecord()
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
        if (shizukuRecordPhoneCall) {
            releaseScrcpyRecord()
        }
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
                            if (isDeviceBluetoothSCO) {
                                audioManager!!.startBluetoothSco()
                            }
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
                        if (isDeviceBluetoothSCO) {
                            audioManager!!.stopBluetoothSco()
                        }
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
                        isDeviceBluetoothSCO = false
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

        val audioVolumeScale = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.AUDIO_VOLUME, 100) / 100.0f
        val micVolumeScale = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.MICROPHONE_VOLUME, 100) / 100.0f
        val shizukuPhoneCallVolumeScale = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.SHIZUKU_PHONE_CALL_VOLUME, 100) / 100.0f

        if (!eos) {
            if ((recordMicrophone || recordAudio || shizukuRecordPhoneCall) && ((!recordAudio || audioMuted) && (!recordMicrophone || micMuted) && (!shizukuRecordPhoneCall || shizukuPhoneCallMuted))) {
                val framePlayback = ByteArray(AUDIO_BUFFER_SIZE)
                var playbackRead: Int = 0

                if (recordAudio) {
                    playbackRead = mPlayback!!.read(framePlayback, 0, AUDIO_BUFFER_SIZE)
                } else if (recordMicrophone) {
                    playbackRead = mMic!!.read(framePlayback, 0, AUDIO_BUFFER_SIZE)
                }

                getScrcpyAudioBuffer()

                var i = 0
                while (i < playbackRead) {
                    framePlayback[i] = 0.toByte()
                    i += 1
                }

                mEncoder.getInputBuffer(index)?.put(framePlayback)

                if (playbackRead >= 0) {
                    read = playbackRead
                } else {
                    read = 0
                }
            } else if ((recordMicrophone && !micMuted) && (recordAudio && !audioMuted) && (!shizukuRecordPhoneCall || shizukuPhoneCallMuted)) {
                val framePlayback = ByteArray(AUDIO_BUFFER_SIZE)
                val playbackRead: Int = mPlayback!!.read(framePlayback, 0, AUDIO_BUFFER_SIZE)
                val frameMic = ByteArray(AUDIO_BUFFER_SIZE)
                var micRead: Int = mMic!!.read(frameMic, 0, AUDIO_BUFFER_SIZE)

                if (playbackRead < micRead) {
                    micRead = playbackRead
                }

                val outerFrame = ByteArray(micRead)

                var i = 0
                while (i < micRead) {
                    var framePlaybackPacketInt = 0
                    var frameMicPacketInt = 0
                    if (framePlayback.size > i) {
                        framePlaybackPacketInt = framePlayback[i].toInt()
                    }
                    if (frameMic.size > i) {
                        frameMicPacketInt = frameMic[i].toInt()
                    }
                    outerFrame[i] = ((framePlaybackPacketInt * audioVolumeScale).toInt() + (frameMicPacketInt * micVolumeScale).toInt()).toByte()
                    i += 1
                }

                mEncoder.getInputBuffer(index)?.put(outerFrame)

                if (micRead >= 0) {
                    read = micRead
                } else {
                    read = 0
                }
            } else if ((recordMicrophone && !micMuted) && (recordAudio && !audioMuted) && (shizukuRecordPhoneCall && !shizukuPhoneCallMuted)) {
                val framePlayback = ByteArray(AUDIO_BUFFER_SIZE)
                val playbackRead: Int = mPlayback!!.read(framePlayback, 0, AUDIO_BUFFER_SIZE)
                val frameMic = ByteArray(AUDIO_BUFFER_SIZE)
                var micRead: Int = mMic!!.read(frameMic, 0, AUDIO_BUFFER_SIZE)

                if (playbackRead < micRead) {
                    micRead = playbackRead
                }

                val shizukuPhoneCallBuffer = getScrcpyAudioBuffer()
                val packetRead = shizukuPhoneCallBuffer.size

                if (micRead < packetRead) {
                    micRead = packetRead
                }

                val outerFrame = ByteArray(micRead)

                var i = 0
                while (i < micRead) {
                    var shizukuPhoneCallBufferInt = 0
                    var framePlaybackPacketInt = 0
                    var frameMicPacketInt = 0
                    if (shizukuPhoneCallBuffer.size > i) {
                        shizukuPhoneCallBufferInt = shizukuPhoneCallBuffer[i].toInt()
                    }
                    if (framePlayback.size > i) {
                        framePlaybackPacketInt = framePlayback[i].toInt()
                    }
                    if (frameMic.size > i) {
                        frameMicPacketInt = frameMic[i].toInt()
                    }
                    outerFrame[i] = ((shizukuPhoneCallBufferInt * shizukuPhoneCallVolumeScale).toInt() + (framePlaybackPacketInt * audioVolumeScale).toInt() + (frameMicPacketInt * micVolumeScale).toInt()).toByte()
                    i += 1
                }

                mEncoder.getInputBuffer(index)?.put(outerFrame)

                if (micRead >= 0) {
                    read = micRead
                } else {
                    read = 0
                }
            } else if ((recordMicrophone && !micMuted) && (!recordAudio || audioMuted) && (!shizukuRecordPhoneCall || shizukuPhoneCallMuted)) {
                val frameMic = ByteArray(AUDIO_BUFFER_SIZE)
                val micRead = mMic!!.read(frameMic, 0, AUDIO_BUFFER_SIZE)

                var i = 0
                while (i < micRead) {
                    frameMic[i] = (frameMic[i] * micVolumeScale).toInt().toByte()
                    i += 1
                }

                mEncoder.getInputBuffer(index)?.put(frameMic)

                if (micRead >= 0) {
                    read = micRead
                } else {
                    read = 0
                }
            } else if ((!recordMicrophone || micMuted) && (recordAudio && !audioMuted) && (!shizukuRecordPhoneCall || shizukuPhoneCallMuted)) {
                val framePlayback = ByteArray(AUDIO_BUFFER_SIZE)
                val playbackRead = mPlayback!!.read(framePlayback, 0, AUDIO_BUFFER_SIZE)

                var i = 0
                while (i < playbackRead) {
                    framePlayback[i] = (framePlayback[i] * audioVolumeScale).toInt().toByte()
                    i += 1
                }

                mEncoder.getInputBuffer(index)?.put(framePlayback)

                if (playbackRead >= 0) {
                    read = playbackRead
                } else {
                    read = 0
                }
            } else if ((!recordMicrophone || micMuted) && (!recordAudio || audioMuted) && (shizukuRecordPhoneCall && !shizukuPhoneCallMuted)) {
                val shizukuPhoneCallBuffer = getScrcpyAudioBuffer()
                val packetRead = shizukuPhoneCallBuffer.size
                val framePacket = ByteArray(AUDIO_BUFFER_SIZE)

                var i = 0
                while (i < packetRead) {
                    framePacket[i] = (shizukuPhoneCallBuffer[i] * shizukuPhoneCallVolumeScale).toInt().toByte()
                    i += 1
                }

                mEncoder.getInputBuffer(index)?.put(framePacket)

                if (packetRead >= 0) {
                    read = packetRead
                } else {
                    read = 0
                }
            } else if ((!recordMicrophone || micMuted) && (recordAudio && !audioMuted) && (shizukuRecordPhoneCall && !shizukuPhoneCallMuted)) {
                val framePlayback = ByteArray(AUDIO_BUFFER_SIZE)
                val playbackRead = mPlayback!!.read(framePlayback, 0, AUDIO_BUFFER_SIZE)

                val shizukuPhoneCallBuffer = getScrcpyAudioBuffer()
                var packetRead = shizukuPhoneCallBuffer.size

                if (packetRead < playbackRead) {
                    packetRead = playbackRead
                }

                val outerFrame = ByteArray(packetRead)

                var i = 0
                while (i < packetRead) {
                    var shizukuPhoneCallBufferInt = 0
                    var framePlaybackPacketInt = 0
                    if (shizukuPhoneCallBuffer.size > i) {
                        shizukuPhoneCallBufferInt = shizukuPhoneCallBuffer[i].toInt()
                    }
                    if (framePlayback.size > i) {
                        framePlaybackPacketInt = framePlayback[i].toInt()
                    }
                    outerFrame[i] = ((shizukuPhoneCallBufferInt * shizukuPhoneCallVolumeScale).toInt() + (framePlaybackPacketInt * audioVolumeScale).toInt()).toByte()
                    i += 1
                }

                mEncoder.getInputBuffer(index)?.put(outerFrame)

                if (packetRead >= 0) {
                    read = packetRead
                } else {
                    read = 0
                }
            } else if ((recordMicrophone && !micMuted) && (!recordAudio || audioMuted) && (shizukuRecordPhoneCall && !shizukuPhoneCallMuted)) {
                val frameMic = ByteArray(AUDIO_BUFFER_SIZE)
                val micRead = mMic!!.read(frameMic, 0, AUDIO_BUFFER_SIZE)

                val shizukuPhoneCallBuffer = getScrcpyAudioBuffer()
                var packetRead = shizukuPhoneCallBuffer.size

                if (packetRead < micRead) {
                    packetRead = micRead
                }

                val outerFrame = ByteArray(packetRead)

                var i = 0
                while (i < packetRead) {
                    var shizukuPhoneCallBufferInt = 0
                    var frameMicPacketInt = 0
                    if (shizukuPhoneCallBuffer.size > i) {
                        shizukuPhoneCallBufferInt = shizukuPhoneCallBuffer[i].toInt()
                    }
                    if (frameMic.size > i) {
                        frameMicPacketInt = frameMic[i].toInt()
                    }
                    outerFrame[i] = ((shizukuPhoneCallBufferInt * shizukuPhoneCallVolumeScale).toInt() + (frameMicPacketInt * micVolumeScale).toInt()).toByte()
                    i += 1
                }

                mEncoder.getInputBuffer(index)?.put(outerFrame)

                if (packetRead >= 0) {
                    read = packetRead
                } else {
                    read = 0
                }
            } else {
                throw RuntimeException("Wrong audio configuration!")
            }

        }

        val pstTs: Long = this.calculateFrameTimestamp(read shl 3)
        var flags: Int = MediaCodec.BUFFER_FLAG_KEY_FRAME

        if (eos) {
            flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM
        }

        mEncoder.queueInputBuffer(index, offset, read, pstTs.toInt(), flags)
    }

    fun setMicrophoneMuted(muted: Boolean) {
        if (recordMicrophone) {
            micMuted = muted
        }
    }

    fun setAudioMuted(muted: Boolean) {
        if (recordAudio) {
            audioMuted = muted
        }
    }

    fun setShizukuPhoneCallMuted(muted: Boolean) {
        if (shizukuRecordPhoneCall) {
            shizukuPhoneCallMuted = muted
        }
    }

    fun microphoneMuted(): Boolean {
        return micMuted
    }

    fun audioMuted(): Boolean {
        return audioMuted
    }

    fun shizukuPhoneCallMuted(): Boolean {
        return shizukuPhoneCallMuted
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
            val audioRecordBuild: AudioRecord = AudioRecord.Builder().setAudioFormat(audioFormatBuild).setBufferSizeInBytes(AUDIO_BUFFER_SIZE).setAudioPlaybackCaptureConfig(audioPlaybackCaptureConfigurationBuild).build()
            if (audioRecordBuild.state == 0) {
                return null
            }
            return audioRecordBuild
        } catch (unused: Exception) {
            Toast.makeText(this.mainContext, R.string.error_playback_not_allowed, Toast.LENGTH_SHORT).show()
            return null
        }
    }

    fun findMicrophoneById(id: Int): AudioDeviceInfo? {
        for (microphoneInfo in audioManager!!.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            if (id == microphoneInfo.id) {
                return microphoneInfo
            }
        }
        return null
    }

    fun getMicrophone(): AudioDeviceInfo? {
        val micValue = appSettings!!.getStringProperty(GlobalProperties.PropertiesString.SELECTED_MICROPHONE, context.resources.getString(R.string.microphone_option_auto_value))
        if (micValue == context.resources.getString(R.string.microphone_option_auto_value)) {
            return null
        }
        return findMicrophoneById(micValue.toInt())
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun createMicRecord(sampleRate: Int, channelConfig: Int, format: Int): AudioRecord? {
        try {
            isDeviceBluetoothSCO = false
            val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, format, AUDIO_BUFFER_SIZE)
            val customMic: AudioDeviceInfo? = getMicrophone()
            if (customMic != null) {
                if (customMic!!.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    isDeviceBluetoothSCO = true
                }
                Log.d(TAG, "Found custom microphone ${customMic!!.id}")
                if (!audioRecord.setPreferredDevice(customMic!!)) {
                    Log.e(TAG, "Error: couldn't select custom microphone ${customMic!!.id}")
                }
            }
            if (audioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
                return null
            }
            return audioRecord
        } catch (_: Exception) {
            return null
        }
    }

    private fun getScrcpyAudioBuffer(): ByteArray {
        try {
            /* Skip buffer header */
            scrcpyInputStream!!.skipBytes(8)

            val bufferSize = scrcpyInputStream!!.readInt()

            if (bufferSize !in 1..AUDIO_BUFFER_SIZE) {
                throw java.io.IOException("Invalid audio buffer size $bufferSize")
            }

            val audioBytes = ByteArray(bufferSize)
            scrcpyInputStream!!.readFully(audioBytes)

            return audioBytes
        } catch (_: EOFException) {
            Log.d(TAG, "Stream ended: EOF")
        } catch (e: Exception) {
            Log.e(TAG, "Stream ended with error: ${e.message}", e)
        }

        return ByteArray(0)
    }

    private fun initScrcpyRecord() {
        Log.d(TAG, "Initializing scrcpy")

        val scrcpyPath = ScrcpyHelper.getScrcpyServerPath(context)

        ScrcpyHelper.checkScrcpyServerFile(context, scrcpyPath)

        Log.d(TAG, "Starting the record service")

        if (shizukuRecordService == null) {
            Log.e(TAG, "Shizuku record service is null!")
        }

        val audioSource = when (shizukuAudioSource) {
            GlobalProperties.ShizukuPhoneCallAudioSource.VOICE_CALL -> {
                "voice-call"
            }
            GlobalProperties.ShizukuPhoneCallAudioSource.VOICE_CALL_UPLINK -> {
                "voice-call-uplink"
            }
            GlobalProperties.ShizukuPhoneCallAudioSource.VOICE_CALL_DOWNLINK -> {
                "voice-call-downlink"
            }
        }

        try {
            scrcpyInputPfd = shizukuRecordService!!.startRecording(
                audioSource,
                scrcpyPath,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start the recording: ${e.message}")
            throw e
        }

        Log.d(TAG, "Successfully started the record service")

        scrcpyInputStream = DataInputStream(
            BufferedInputStream(FileInputStream(scrcpyInputPfd!!.fileDescriptor))
        )

        try {
            /* Skip codec magic header */
            scrcpyInputStream!!.skipBytes(4)

            Log.d(TAG, "Initialized scrcpy server")
        } catch (_: EOFException) {
            Log.d(TAG, "Stream ended: EOF")
        } catch (e: Exception) {
            Log.e(TAG, "Stream ended with error: ${e.message}", e)
            throw e
        }
    }

    private fun releaseScrcpyRecord() {
        shizukuRecordService?.stopRecording()
        scrcpyInputStream!!.close()
        scrcpyInputPfd?.close()

        Log.d(TAG, "Released scrcpy")
    }
}
