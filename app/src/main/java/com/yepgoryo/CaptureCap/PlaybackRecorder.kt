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
import android.view.Surface
import android.widget.Toast
import androidx.annotation.RequiresApi
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean

class PlaybackRecorder(
    private var context: Context,
    private var recordOnlyAudio: Boolean,
    private var virtualDisplay: VirtualDisplay?,
    private var fileDescriptor: FileDescriptor?,
    private var mediaProjection: MediaProjection?,
    private var enableStream: Boolean,
    private var streamUrl: String?,
    private var streamKey: String?,
    private var saveStreamToFile: Boolean,
    private var customWidth: Int,
    private var customHeight: Int,
    private var forceOrientation: GlobalProperties.ScreenOrientationProperty,
    private var forceRotation: GlobalProperties.ScreenRotationProperty,
    private var scaleRatio: Float,
    private val rotation: Int,
    refreshRate: Int,
    private var recordMicrophone: Boolean,
    private var recordPlayback: Boolean,
    private var shizukuRecordPhoneCall: Boolean,
    var shizukuRecordService: IShizukuRecordService?,
    private var shizukuAudioSource: GlobalProperties.ShizukuPhoneCallAudioSource,
    private val drawOverlay: Boolean,
    customQuality: Boolean,
    qualityScale: Float,
    customFps: Boolean,
    fpsValue: Int,
    private var customBitrate: Boolean,
    bitrateValue: Int,
    private val customKeyFrame: Boolean,
    private val customKeyFrameValue: Int,
    chooseCustomFormat: Boolean,
    formatName: String,
    chooseCustomCodec: Boolean,
    codecName: String,
    chooseCustomAudioFormat: Boolean,
    audioFormatName: String,
    chooseCustomAudioCodec: Boolean,
    audioCodecName: String,
    private var customSampleRate: Int,
    private var customChannelsCount: Int,
    private val useCropArea: Boolean,
    private val smoothCrop: Boolean,
    private val cropAreaWidth: Int,
    private val cropAreaHeight: Int,
    private val cropAreaX: Int,
    private val cropAreaY: Int,
    private var mediaAudioSource: Boolean,
    private var gameAudioSource: Boolean,
    private var unknownAudioSource: Boolean
) {
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
    private var sMuxer: RtmpMuxer? = null
    private var mVideoEncoder: VideoEncoder? = null
    private var mVideoPtsOffset: Long = 0L
    private var mWorker: HandlerThread? = null
    private var nativeFramerate: Int = refreshRate
    private var recordCustomBitrate: Int = 0
    private var recordQualityScale: Float = 1.0f
    private var try60FPS: Boolean = true
    private var useCustomAudioCodec: Boolean = false
    private var useCustomCodec: Boolean = false
    private var useCustomFormat: Boolean = false
    private var useCustomAudioFormat: Boolean = false
    private var customFormat: String = ""
    private var customAudioFormat: String = ""
    private var mMuxerStarted: Boolean = false
    private var tryNormalFPS: Boolean = true
    private var tryNativeFPS: Boolean = true
    private var doRestart: Boolean = false
    private var mVideoTrackIndex: Int = INVALID_INDEX
    private var mAudioTrackIndex: Int = INVALID_INDEX
    private var sVideoTrackIndex: Int = INVALID_INDEX
    private var sAudioTrackIndex: Int = INVALID_INDEX
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

    private var fullStreamPath: String = ""

    private var sps: ByteArray? = null
    private var pps: ByteArray? = null
    private var csd: ByteArray? = null
    private var asc: ByteArray? = null

    private var pollHandler: Handler? = null
    private var pollRunnable: Runnable? = null
    private var lastQueuedVideoBufferIndex: Int = MediaCodec.INFO_TRY_AGAIN_LATER
    private var lastQueuedVideoPtsUs: Long = 0L
    private var lastVideoOutputTimeNs: Long = 0L
    private var videoEncoderStallTimer: Handler? = null
    private var lastQueuedVideoData: ByteArray? = null

    var recordingCallback: ScreenRecorder.RecordingFinishedCallback? = null

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
        if (chooseCustomFormat) {
            this.useCustomFormat = chooseCustomFormat
            this.customFormat = formatName
        }
        if (chooseCustomAudioFormat) {
            this.useCustomAudioFormat = chooseCustomAudioFormat
            this.customAudioFormat = audioFormatName
        }
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
        if (enableStream) {
            this.fullStreamPath = this.streamUrl + "/" + this.streamKey
        }
        when (forceOrientation) {
            GlobalProperties.ScreenOrientationProperty.FORCE_LANDSCAPE -> {
                if (customWidth < customHeight) {
                    val hold = customHeight
                    customHeight = customWidth
                    customWidth = hold
                }
            }
            GlobalProperties.ScreenOrientationProperty.FORCE_PORTRAIT -> {
                if (customWidth > customHeight) {
                    val hold = customWidth
                    customWidth = customHeight
                    customHeight = hold
                }
            }
            else -> {}
        }
        if (!recordOnlyAudio) {
            virtualDisplay!!.resize(
                customWidth,
                customHeight,
                context.resources.displayMetrics.densityDpi
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                when (forceRotation) {
                    GlobalProperties.ScreenRotationProperty._0_DEGREES_ -> {
                        virtualDisplay!!.setRotation(Surface.ROTATION_0)
                    }

                    GlobalProperties.ScreenRotationProperty._90_DEGREES_ -> {
                        virtualDisplay!!.setRotation(Surface.ROTATION_90)
                    }

                    GlobalProperties.ScreenRotationProperty._180_DEGREES_ -> {
                        virtualDisplay!!.setRotation(Surface.ROTATION_180)
                    }

                    GlobalProperties.ScreenRotationProperty._270_DEGREES_ -> {
                        virtualDisplay!!.setRotation(Surface.ROTATION_270)
                    }

                    else -> {}
                }
            }
        }
    }

    private fun getAllAudioCodecs() {
        val useFormat = if (useCustomAudioFormat && recordOnlyAudio && !(!recordOnlyAudio && (customFormat == MediaFormat.MIMETYPE_VIDEO_AVC || customFormat == MediaFormat.MIMETYPE_VIDEO_HEVC))) {
            customAudioFormat
        } else {
            MediaFormat.MIMETYPE_AUDIO_AAC
        }
        for (mediaCodecInfo in MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos) {
            if (mediaCodecInfo.isEncoder) {
                for (type in mediaCodecInfo.getSupportedTypes()) {
                    if (type.equals(useFormat, ignoreCase = true)) {
                        this.codecsAudioList.add(mediaCodecInfo.name)
                    }
                }
            }
        }
    }

    private fun getAllCodecs() {
        val useFormat = if (useCustomFormat) {
            customFormat
        } else {
            MediaFormat.MIMETYPE_VIDEO_AVC
        }
        for (mediaCodecInfo in MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos) {
            if (mediaCodecInfo.isEncoder) {
                for (type in mediaCodecInfo.getSupportedTypes()) {
                    if (type.equals(useFormat, ignoreCase = true)) {
                        this.codecsList.add(mediaCodecInfo.name)
                        this.codecProfileLevels.add(mediaCodecInfo.getCapabilitiesForType(useFormat).profileLevels[0])
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
            var bitmapBeforeCameraName = VideoOverlay.BITMAP_BEFORE_CAMERA_VERTICAL
            if (customWidth > customHeight) {
                bitmapBeforeCameraName = VideoOverlay.BITMAP_BEFORE_CAMERA_HORIZONTAL
            }

            var bitmapAfterCameraName = VideoOverlay.BITMAP_AFTER_CAMERA_VERTICAL
            if (customWidth > customHeight) {
                bitmapAfterCameraName = VideoOverlay.BITMAP_AFTER_CAMERA_HORIZONTAL
            }

            val cameraItem = VideoOverlay.getCameraItem(context, (customWidth > customHeight))

            val bitmapBeforeCamera = VideoOverlay.BitmapSerializer.loadBitmapFromFile(context, bitmapBeforeCameraName)
            val bitmapAfterCamera = VideoOverlay.BitmapSerializer.loadBitmapFromFile(context, bitmapAfterCameraName)

            this.mVideoEncoder = VideoEncoder(context, customWidth, customHeight, scaleRatio, rotation, this.nativeFramerate, this.recordQualityScale, drawOverlay, customBitrate, this.recordCustomBitrate, customKeyFrame, customKeyFrameValue, codec, this.currentProfileLevel!!, bitmapBeforeCamera, bitmapAfterCamera, cameraItem, virtualDisplay!!, useCustomFormat, customFormat, useCropArea, smoothCrop, cropAreaWidth, cropAreaHeight, cropAreaX, cropAreaY)
        } else {
            this.mVideoEncoder = null
        }
        if (!recordMicrophone && !recordPlayback && !shizukuRecordPhoneCall) {
            this.mAudioEncoder = null
        } else {
            this.mAudioEncoder = AudioPlaybackRecorder(recordMicrophone, recordPlayback, customSampleRate, customChannelsCount, mediaProjection, this.useCustomAudioCodec, this.customAudioCodec, this.useCustomAudioFormat, this.customAudioFormat, recordOnlyAudio, shizukuRecordPhoneCall, shizukuRecordService, shizukuAudioSource, context, mediaAudioSource, gameAudioSource, unknownAudioSource)
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

    fun setMicrophoneMuted(mic: Boolean) {
        this.mAudioEncoder?.setMicrophoneMuted(mic)
    }

    fun setAudioMuted(audio: Boolean) {
        this.mAudioEncoder?.setAudioMuted(audio)
    }

    fun setShizukuPhoneCallMuted(phonecall: Boolean) {
        this.mAudioEncoder?.setShizukuPhoneCallMuted(phonecall)
    }

    fun microphoneMuted(): Boolean {
        if (this.mAudioEncoder == null) {
            return false
        }
        return this.mAudioEncoder!!.microphoneMuted()
    }

    fun audioMuted(): Boolean {
        if (this.mAudioEncoder == null) {
            return false
        }
        return this.mAudioEncoder!!.audioMuted()
    }

    fun shizukuPhoneCallMuted(): Boolean {
        if (this.mAudioEncoder == null) {
            return false
        }
        return this.mAudioEncoder!!.shizukuPhoneCallMuted()
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
        Log.v(TAG, "Signal end of stream")
        val bufferInfo: MediaCodec.BufferInfo = MediaCodec.BufferInfo()
        bufferInfo.set(0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        val byteBufferAllocate: ByteBuffer = ByteBuffer.allocate(0)
        if (!this.recordOnlyAudio) {
            if ((enableStream && this.sVideoTrackIndex != INVALID_INDEX) || this.mVideoTrackIndex != INVALID_INDEX) {
                writeSampleData(true, bufferInfo, byteBufferAllocate)
            }
        }

        if ((enableStream && this.sAudioTrackIndex != INVALID_INDEX) || this.mAudioTrackIndex != INVALID_INDEX) {
            writeSampleData(false, bufferInfo, byteBufferAllocate)
        }

        this.sVideoTrackIndex = INVALID_INDEX
        this.sAudioTrackIndex = INVALID_INDEX
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
            if (enableStream) {
                var streamFormat = MediaFormat.MIMETYPE_VIDEO_AVC
                if (useCustomFormat) {
                    streamFormat = customFormat
                }
                this.sMuxer = RtmpMuxer(context, fullStreamPath, customSampleRate, customChannelsCount, streamFormat)
                if (saveStreamToFile) {
                    this.mMuxer = MediaMuxer(fileDescriptor!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                }
            } else {
                this.mMuxer = MediaMuxer(fileDescriptor!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }
            if (!this.recordOnlyAudio) {
                prepareVideoEncoder()
            }
            prepareAudioEncoder()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun muxVideo(index: Int, bufferInfo: MediaCodec.BufferInfo) {
        if (this.mIsRunning.get()) {
            if (!this.mMuxerStarted || (!enableStream && this.mVideoTrackIndex == INVALID_INDEX) || (enableStream && this.sVideoTrackIndex == INVALID_INDEX)) {
                this.mPendingVideoEncoderBufferIndices.add(index as Integer)
                this.mPendingVideoEncoderBufferInfos.add(bufferInfo)
            } else {
                if (!this.mIsPaused.get()) {
                    val outputBuffer: ByteBuffer = this.mVideoEncoder!!.getOutputBuffer(index)
                    bufferInfo.presentationTimeUs -= this.lastTimeout
                    writeSampleData(true, bufferInfo, outputBuffer)
                }
                this.mVideoEncoder!!.releaseOutputBuffer(index)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    this.mVideoTrackIndex = INVALID_INDEX
                    this.sVideoTrackIndex = INVALID_INDEX
                    signalStop(true)
                }
            }
        }
    }

    fun muxAudio(index: Int, bufferInfo: MediaCodec.BufferInfo) {
        if (this.mIsRunning.get()) {
            if (!this.mMuxerStarted || (!enableStream && this.mAudioTrackIndex == INVALID_INDEX) || (enableStream && this.sAudioTrackIndex == INVALID_INDEX)) {
                this.mPendingAudioEncoderBufferIndices.add(index as Integer)
                this.mPendingAudioEncoderBufferInfos.add(bufferInfo)
            } else {
                if (!this.mIsPaused.get()) {
                    val outputBuffer: ByteBuffer = this.mAudioEncoder!!.getOutputBuffer(index)!!
                    bufferInfo.presentationTimeUs -= this.lastTimeout
                    writeSampleData(false, bufferInfo, outputBuffer)
                }
                this.mAudioEncoder!!.releaseOutputBuffer(index)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    this.mAudioTrackIndex = INVALID_INDEX
                    this.sAudioTrackIndex = INVALID_INDEX
                    signalStop(true)
                }
            }
        }
    }

    private fun writeSampleData(isVideo: Boolean, bufferInfo: MediaCodec.BufferInfo, byteBuffer: ByteBuffer) {
        var byteBufferWrite: ByteBuffer? = byteBuffer
        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            bufferInfo.size = 0
        }
        if (bufferInfo.size != 0 || (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            if (bufferInfo.presentationTimeUs != 0L) {
                if (enableStream && !saveStreamToFile) {
                    if (isVideo) {
                        resetVideoPts(bufferInfo)
                    } else {
                        resetAudioPts(bufferInfo)
                    }
                } else {
                    if (isVideo) {
                        resetVideoPts(bufferInfo)
                    } else {
                        resetAudioPts(bufferInfo)
                    }
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
            try {
                if (enableStream) {
                    var trackIndex = if (isVideo) {
                        sVideoTrackIndex
                    } else {
                        sAudioTrackIndex
                    }
                    this.sMuxer!!.writeSampleData(trackIndex, byteBufferWrite, bufferInfo)
                }
                if ((enableStream && saveStreamToFile) || !enableStream) {
                    var trackIndex = if (isVideo) {
                        mVideoTrackIndex
                    } else {
                        mAudioTrackIndex
                    }
                    this.mMuxer!!.writeSampleData(trackIndex, byteBufferWrite, bufferInfo)
                }
            } catch (exc: Exception) {
                recordingCallback?.onError(exc)
            }
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
            if (enableStream) {
                this.sVideoTrackIndex = this.sMuxer!!.addTrack(this.mVideoOutputFormat!!)
                if (saveStreamToFile) {
                    this.mVideoTrackIndex = this.mMuxer!!.addTrack(this.mVideoOutputFormat!!)
                }
            } else {
                this.mVideoTrackIndex = this.mMuxer!!.addTrack(this.mVideoOutputFormat!!)
            }
        }
        this.mAudioTrackIndex = if (this.mAudioEncoder == null) {
            INVALID_INDEX
        } else {
            if (!enableStream || (enableStream && saveStreamToFile)) {
                this.mMuxer!!.addTrack(this.mAudioOutputFormat!!)
            } else {
                INVALID_INDEX
            }
        }
        this.sAudioTrackIndex = if (this.mAudioEncoder == null) {
            INVALID_INDEX
        } else {
            if (enableStream) {
                this.sMuxer!!.addTrack(this.mAudioOutputFormat!!)
            } else {
                INVALID_INDEX
            }
        }
        if (enableStream) {
            try {
                this.sMuxer!!.start()
                if (saveStreamToFile) {
                    this.mMuxer!!.start()
                }
            } catch (exc: Exception) {
                recordingCallback?.onError(exc)
            }
        } else {
            this.mMuxer!!.start()
        }
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

    fun extractByteArray(csdByteBuffer: ByteBuffer): ByteArray {
        val buf = csdByteBuffer.duplicate()
        val payload = ByteArray(buf.remaining())
        buf.get(payload)
        return payload.copyOf()
    }

    fun ByteArray.hex() = joinToString(" ") { "%02X".format(it) }

    /*
     * This function has been co-authored by an AI.
     * Model name: Qwen 3 Coder Next
     */
    private fun startVideoStallTimer() {
        pollHandler?.removeCallbacks(pollRunnable!!)
        pollHandler = Handler(mWorker!!.looper)
        checkStallAndRefeed()
    }

    /*
     * This function has been co-authored by an AI.
     * Model name: Qwen 3 Coder Next
     */
    private fun checkStallAndRefeed() {
        pollRunnable = object : Runnable {
            override fun run() {
                if (!mIsRunning.get() || mMuxerStarted || mVideoEncoder == null) {
                    return
                }

                val nowNs = System.nanoTime()
                val timeoutNs = 1_000_000_000L

                if (lastVideoOutputTimeNs != 0L && nowNs - lastVideoOutputTimeNs > timeoutNs) {
                    if (lastQueuedVideoBufferIndex != MediaCodec.INFO_TRY_AGAIN_LATER && mVideoEncoder != null) {
                        try {
                            val inputIndex = mVideoEncoder!!.mEncoder?.dequeueInputBuffer(0)
                            if (inputIndex!! >= 0) {
                                val inputBuffer = mVideoEncoder!!.mEncoder?.getInputBuffer(inputIndex)
                                val data = lastQueuedVideoData ?: return
                                inputBuffer?.clear()
                                inputBuffer?.put(data)
                                inputBuffer?.flip()
                                mVideoEncoder!!.queueInputBuffer(inputIndex,
                                    0,
                                    data.size,
                                    lastQueuedVideoPtsUs,
                                    0
                                )
                            }
                        } catch (exc: Exception) {
                            recordingCallback?.onError(exc)
                        }
                    }
                }
                pollHandler?.postDelayed({
                    checkStallAndRefeed()
                }, 1000)
            }
        }
        pollHandler?.postDelayed({
            checkStallAndRefeed()
        }, 1000)
    }

    private fun prepareVideoEncoder() {
        this.mVideoEncoder!!.setCallback(object: VideoEncoder.Callback() {
            override fun onInputBufferAvailable(videoEncoder: VideoEncoder, index: Int) {
                val inputBuffer = videoEncoder.getInputBuffer(index)
                lastQueuedVideoData = ByteArray(inputBuffer.remaining())
                inputBuffer.get(lastQueuedVideoData!!)
                super.onInputBufferAvailable(videoEncoder, index)
            }

            override fun onOutputBufferAvailable(videoEncoder: VideoEncoder, index: Int, bufferInfo: MediaCodec.BufferInfo) {
                try {
                    this@PlaybackRecorder.muxVideo(index, bufferInfo)
                } catch (e: Exception) {
                    Message.obtain(this@PlaybackRecorder.mHandler, MessageCommand.MSG_ERROR.ordinal, e).sendToTarget()
                }
            }

            override fun onError(encoder: Encoder, exc: Exception) {
                Log.d("PlaybackRecorder", "Video codec error detected")
                Message.obtain(this@PlaybackRecorder.mHandler, MessageCommand.MSG_ERROR.ordinal, exc).sendToTarget()
            }

            override fun onOutputFormatChanged(videoEncoder: VideoEncoder, mediaFormat: MediaFormat) {
                if (mediaFormat.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_VIDEO_AVC) {
                    val spsBuffer = mediaFormat.getByteBuffer("csd-0")
                    val ppsBuffer = mediaFormat.getByteBuffer("csd-1")

                    if (spsBuffer != null) {
                        sps = extractByteArray(spsBuffer)
                        Log.d(TAG, "SPS output format init: ${sps!!.hex()}")
                    } else {
                        Log.e(TAG, "spsBuffer is null!")
                    }

                    if (ppsBuffer != null) {
                        pps = extractByteArray(ppsBuffer)
                        Log.d(TAG, "PPS output format init: ${pps!!.hex()}")
                    } else {
                        Log.e(TAG, "ppsBuffer is null!")
                    }

                    if (enableStream && sps != null && pps != null) {
                        this@PlaybackRecorder.sMuxer!!.setAVCConfig(sps!!, pps!!)
                    }
                } else if (mediaFormat.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_VIDEO_HEVC) {
                    val csdBuffer = mediaFormat.getByteBuffer("csd-0")

                    if (csdBuffer != null) {
                        csd = extractByteArray(csdBuffer)
                        Log.d(TAG, "CSD output format init: ${csd!!.hex()}")
                    } else {
                        Log.e(TAG, "csdBuffer is null!")
                    }

                    var tier = 0
                    var level = 93

                    when (mediaFormat.getInteger(MediaFormat.KEY_LEVEL)) {
                        MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel1 -> {
                            tier = 1
                            level = 30
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel2 -> {
                            tier = 1
                            level = 60
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel21 -> {
                            tier = 1
                            level = 63
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel3 -> {
                            tier = 1
                            level = 90
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel31 -> {
                            tier = 1
                            level = 93
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel4 -> {
                            tier = 1
                            level = 120
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel41 -> {
                            tier = 1
                            level = 123
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel5 -> {
                            tier = 1
                            level = 150
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel51 -> {
                            tier = 1
                            level = 153
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel52 -> {
                            tier = 1
                            level = 156
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel6 -> {
                            tier = 1
                            level = 180
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel61 -> {
                            tier = 1
                            level = 183
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel62 -> {
                            tier = 1
                            level = 186
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel1 -> {
                            tier = 0
                            level = 30
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel2 -> {
                            tier = 0
                            level = 60
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel21 -> {
                            tier = 0
                            level = 63
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel3 -> {
                            tier = 0
                            level = 90
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel31 -> {
                            tier = 0
                            level = 93
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel4 -> {
                            tier = 0
                            level = 120
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel41 -> {
                            tier = 0
                            level = 123
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel5 -> {
                            tier = 0
                            level = 150
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel51 -> {
                            tier = 0
                            level = 153
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel52 -> {
                            tier = 0
                            level = 156
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel6 -> {
                            tier = 0
                            level = 180
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel61 -> {
                            tier = 0
                            level = 183
                        }

                        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel62 -> {
                            tier = 0
                            level = 186
                        }
                    }

                    if (enableStream && csd != null) {
                        this@PlaybackRecorder.sMuxer!!.setHEVCConfig(csd!!, mediaFormat.getInteger(MediaFormat.KEY_PROFILE), tier, level)
                    }
                }

                this@PlaybackRecorder.resetVideoOutputFormat(mediaFormat)
                this@PlaybackRecorder.startMuxerIfReady()
            }
        })
        this.mVideoEncoder!!.prepare()
        startVideoStallTimer()
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
                val ascBuffer = mediaFormat.getByteBuffer("csd-0")

                if (ascBuffer != null) {
                    asc = extractByteArray(ascBuffer)
                    Log.d(TAG, "ASC output format init: ${asc!!.hex()}")
                } else {
                    Log.e(TAG, "ascBuffer is null!")
                }

                if (enableStream && asc != null) {
                    this@PlaybackRecorder.sMuxer!!.setAACConfig(asc!!)
                }

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
        videoEncoderStallTimer?.removeCallbacksAndMessages(null)
        pollHandler?.removeCallbacks(pollRunnable!!)
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
        this.mVideoOutputFormat = null
        this.mAudioOutputFormat = null
        this.mVideoTrackIndex = INVALID_INDEX
        this.mAudioTrackIndex = INVALID_INDEX

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
        if (enableStream) {
            if (this.sMuxer != null) {
                try {
                    this.sMuxer!!.stop()
                    this.sMuxer!!.release()
                } catch (exc: Exception) {
                    throw exc
                }
                this.sMuxer = null
            }
            if (this.mMuxer != null && saveStreamToFile) {
                try {
                    if (this.mMuxerStarted) {
                        this.mMuxer?.stop()
                        this.mMuxer?.release()
                    }
                } catch (exc: Exception) {
                    throw exc
                }
                this.mMuxer = null
            }
        } else {
            if (this.mMuxer != null) {
                try {
                    if (this.mMuxerStarted) {
                        this.mMuxer?.stop()
                        this.mMuxer?.release()
                    }
                } catch (exc: Exception) {
                    throw exc
                }
                this.mMuxer = null
            }
        }
        this.mMuxerStarted = false
        this.mHandler = null
    }

    protected fun finalize() {
        if (this.virtualDisplay != null) {
            release()
        }
    }
}
