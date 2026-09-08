package com.yepgoryo.CaptureCap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface

import java.nio.ByteBuffer

class VideoEncoder(private val context: Context, customWidth: Int, customHeight: Int, scaleRatio: Float, private val rotation: Int, nativeFramerate: Int, recordQualityScale: Float, private val drawOverlay: Boolean, customBitrate: Boolean, recordCustomBitrate: Int, private val customKeyFrame: Boolean, private val recordCustomKeyframe: Int, codec: String, codecProfileLevel: MediaCodecInfo.CodecProfileLevel, val bitmapBeforeCamera: Bitmap?, val bitmapAfterCamera: Bitmap?, var camera: VideoOverlay.CameraItem?, val vDisplay: VirtualDisplay, val useCustomFormat: Boolean, val customFormat: String, val useCropArea: Boolean, val smoothCrop: Boolean, val cropAreaWidth: Int, val cropAreaHeight: Int, val cropAreaX: Int, val cropAreaY: Int) : Encoder {
    private val BPP: Float = 0.25f
    private var height: Int = 1920
    private var scaleRatio: Float = 1.0f
    private var width: Int = 1080
    private var codecName: String = ""
    private var codecProfileLevel: MediaCodecInfo.CodecProfileLevel? = null
    private var mCallback: Callback? = null
    private var isStopped = false

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

    private var mCodecCallbackEncoderVirtualDisplay: MediaCodec.Callback = object: MediaCodec.Callback() {
        override fun onInputBufferAvailable(mediaCodec: MediaCodec, index: Int) {}

        override fun onOutputBufferAvailable(mediaCodec: MediaCodec, index: Int, bufferInfo: MediaCodec.BufferInfo) {
            val buffer = mediaCodec.getOutputBuffer(index)
            if (buffer != null && bufferInfo.size > 0) {
                feedInputBuffer(surfaceDecoder!!, buffer, bufferInfo)
            }
            mediaCodec.releaseOutputBuffer(index, false)
        }

        override fun onError(mediaCodec: MediaCodec, codecException: MediaCodec.CodecException) {}

        override fun onOutputFormatChanged(mediaCodec: MediaCodec, mediaFormat: MediaFormat) {}
    }

    fun feedInputBuffer(codec: MediaCodec, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        try {
            val index = codec.dequeueInputBuffer(0)
            if (index >= 0) {
                val inputBuf = codec.getInputBuffer(index)!!
                inputBuf.put(buffer)
                codec.queueInputBuffer(index, 0, info.size, info.presentationTimeUs, info.flags)
            }
        } catch (e: Exception) {
            Log.e("Decoder", "feedInput failed", e)
        }
    }

    var mEncoder: MediaCodec? = null
    var surfaceDecoder: MediaCodec? = null
    var encoderVirtualDisplay: MediaCodec? = null
    var encoderVirtualDisplaySurface: Surface? = null
    private var mSurface: Surface? = null
    private var screenFramerate: Int = 0
    private var usedBitrate: Int = 0

    private val handlerThread = HandlerThread("RenderLoop")
    private var renderHandler: Handler? = null

    private val buffersHandlerThread = HandlerThread("BuffersLoop")
    private var buffersHandler: Handler? = null

    private var layerRenderer: LayerRenderer? = null
    private var cameraManager: FrontCameraManager? = null

    private var surfaceTexture: SurfaceTexture? = null
    private var cameraTexture: SurfaceTexture? = null

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
        handlerThread.start()
        renderHandler = Handler(handlerThread.looper)

        buffersHandlerThread.start()
        buffersHandler = Handler(buffersHandlerThread.looper)
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

    private fun createMediaFormat(displayWidth: Int = width, displayHeight: Int = height): MediaFormat {
        val useFormat = if (useCustomFormat) {
            customFormat
        } else {
            MediaFormat.MIMETYPE_VIDEO_AVC
        }
        val mediaFormatCreateVideoFormat: MediaFormat = MediaFormat.createVideoFormat(useFormat, (displayWidth.toFloat() * this@VideoEncoder.scaleRatio).toInt(), (displayHeight * this@VideoEncoder.scaleRatio).toInt())
        mediaFormatCreateVideoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        mediaFormatCreateVideoFormat.setInteger(MediaFormat.KEY_BIT_RATE, this.usedBitrate)
        mediaFormatCreateVideoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, this.screenFramerate)
        if (customKeyFrame) {
            mediaFormatCreateVideoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, recordCustomKeyframe)
        } else {
            mediaFormatCreateVideoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        mediaFormatCreateVideoFormat.setInteger("i-frame-interval", 1)
        if (this.codecProfileLevel != null) {
            if (this.codecProfileLevel!!.profile != 0 && this.codecProfileLevel!!.level != 0) {
                mediaFormatCreateVideoFormat.setInteger(MediaFormat.KEY_PROFILE, this.codecProfileLevel!!.profile)
                mediaFormatCreateVideoFormat.setInteger(MediaFormat.KEY_LEVEL, this.codecProfileLevel!!.level)
            }
        }
        return mediaFormatCreateVideoFormat
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

        encoderVirtualDisplay?.release()
        surfaceDecoder?.release()

        coreGl?.releaseSurface()
        coreGl?.destroy()
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

    private var coreGl: EglCore? = null

    private var finalInputSurface: Surface? = null

    private var surfaceTextureId: Int = 0

    private var cameraTextureId: Int? = null

    fun prepare() {
        try {
            if (Looper.myLooper() == null || Looper.myLooper() == Looper.getMainLooper()) {
                throw IllegalStateException()
            }
            if (this.mEncoder != null) {
                throw IllegalStateException()
            }
            val mediaFormatCreateMediaFormat: MediaFormat = createMediaFormat()
            val mediaCodecMain: MediaCodec =
                MediaCodec.createByCodecName(this.codecName)
            var mediaCodecSurface: MediaCodec? = null
            var mediaCodecDecoder: MediaCodec? = null

            if (drawOverlay || useCropArea) {
                mediaCodecSurface = MediaCodec.createByCodecName(this.codecName)
                mediaCodecDecoder =
                    MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            }

            if (this.mCallback != null) {
                mediaCodecMain.setCallback(this.mCodecCallback)
                if (drawOverlay || useCropArea) {
                    mediaCodecSurface!!.setCallback(mCodecCallbackEncoderVirtualDisplay)
                }
            }

            if (drawOverlay || useCropArea) {
                coreGl = EglCore.create()
                coreGl!!.makeCurrent()

                surfaceTextureId = GlUtil.createOesTexture()

                if (camera != null) {
                    cameraTextureId = GlUtil.createOesTexture()
                    cameraTexture = SurfaceTexture(cameraTextureId!!)
                }

                surfaceTexture = SurfaceTexture(surfaceTextureId)

                val frameAvailable: SurfaceTexture.OnFrameAvailableListener =
                    SurfaceTexture.OnFrameAvailableListener {
                        if (!isStopped) {
                            renderHandler?.post {
                                if (camera != null && drawOverlay) {
                                    if (cameraManager == null) {
                                        cameraManager = FrontCameraManager(context)
                                        cameraManager?.openCamera(
                                            cameraTexture!!,
                                            camera!!.width,
                                            camera!!.height
                                        )
                                    }
                                }
                                if (layerRenderer == null) {
                                    var cameraRotation = 0
                                    if (cameraManager != null) {
                                        cameraRotation = cameraManager!!.getCameraRotation()
                                    }
                                    layerRenderer = LayerRenderer(
                                        coreGl!!.context,
                                        surfaceTexture!!,
                                        surfaceTextureId,
                                        bitmapBeforeCamera,
                                        cameraTexture,
                                        cameraTextureId,
                                        bitmapAfterCamera,
                                        finalInputSurface!!,
                                        width,
                                        height,
                                        rotation,
                                        scaleRatio,
                                        cameraRotation,
                                        camera,
                                        drawOverlay,
                                        useCropArea,
                                        smoothCrop,
                                        cropAreaWidth,
                                        cropAreaHeight,
                                        cropAreaX,
                                        cropAreaY,
                                    )
                                }

                                layerRenderer!!.draw()
                            }
                            surfaceTexture!!.updateTexImage()
                            if (camera != null) {
                                cameraTexture?.updateTexImage()
                            }
                        }
                    }
                surfaceTexture!!.setOnFrameAvailableListener(frameAvailable)
                if (camera != null) {
                    cameraTexture?.setOnFrameAvailableListener(frameAvailable)
                    cameraTexture?.updateTexImage()
                }
                surfaceTexture!!.updateTexImage()
            }

            if (drawOverlay || useCropArea) {
                val surf = Surface(surfaceTexture!!)
                mediaCodecSurface!!.configure(
                    mediaFormatCreateMediaFormat,
                    null,
                    null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE
                )
                mediaCodecDecoder!!.configure(
                    mediaFormatCreateMediaFormat,
                    surf,
                    null,
                    0
                )
            }
            val mediaFormatCreateOutputMediaFormatCustom: MediaFormat =
                if (useCropArea) {
                    createMediaFormat(cropAreaWidth, cropAreaHeight)
                } else {
                    createMediaFormat()
                }

            mediaCodecMain.configure(
                mediaFormatCreateOutputMediaFormatCustom,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
            )
            finalInputSurface = mediaCodecMain.createInputSurface()

            if (drawOverlay || useCropArea) {
                encoderVirtualDisplay = mediaCodecSurface
                encoderVirtualDisplaySurface = mediaCodecSurface!!.createInputSurface()

                mediaCodecSurface!!.start()
                mediaCodecDecoder!!.start()
            }
            mediaCodecMain.start()
            this.mEncoder = mediaCodecMain

            if (drawOverlay || useCropArea) {
                vDisplay.surface = encoderVirtualDisplaySurface
                surfaceDecoder = mediaCodecDecoder
                buffersHandler?.post { drainBuffersDecoder() }
            } else {
                vDisplay.surface = finalInputSurface
            }
        } catch (e: Exception) {
            Log.e("VideoEncoder", e.message!!)
            throw e
        }
    }

    fun drainBuffersDecoder() {
        val decoder = surfaceDecoder
        var decIndex: Int
        val decBufferInfo = MediaCodec.BufferInfo()
        decIndex = decoder!!.dequeueOutputBuffer(decBufferInfo, 0)
        while (decIndex >= 0) {
            decoder?.releaseOutputBuffer(decIndex, true)

            if (decBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                break
            }
            decIndex = decoder!!.dequeueOutputBuffer(decBufferInfo, 0)
        }

        buffersHandler?.post { drainBuffersDecoder() }
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
        isStopped = true
        cameraManager?.release()

        surfaceTexture?.setOnFrameAvailableListener(null)
        cameraTexture?.setOnFrameAvailableListener(null)

        buffersHandler?.removeCallbacksAndMessages(null)
        buffersHandlerThread.quitSafely()

        encoderVirtualDisplay?.stop()
        surfaceDecoder?.stop()
        this.mEncoder?.stop()

        renderHandler?.post {
            layerRenderer?.release()
        }

        renderHandler?.removeCallbacksAndMessages(null)
        handlerThread.quitSafely()

        surfaceTexture?.release()
        cameraTexture?.release()
    }
}
