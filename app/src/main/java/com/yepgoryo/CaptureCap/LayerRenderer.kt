/*
 * This code has been co-authored by an AI.
 * Model name: Qwen 3 Coder Next
 */

package com.yepgoryo.CaptureCap

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer

class LayerRenderer(
    private val eglContext: EGLContext,
    private val backgroundSurfaceTexture: SurfaceTexture,
    private val backgroundSurfaceTextureId: Int,
    private val overlayBitmap1: Bitmap?,
    private val frontCameraSurfaceTexture: SurfaceTexture?,
    private val frontCameraSurfaceTextureId: Int?,
    private val overlayBitmap2: Bitmap?,
    private val outputSurface: Surface,
    private val width: Int,
    private val height: Int,
    private val displayRotation: Int,
    private val displayRatio: Float,
    private val cameraRotation: Int,
    private val camera: VideoOverlay.CameraItem?,
    private val useOverlay: Boolean,
    private val useCropArea: Boolean,
    private val smoothCrop: Boolean,
    private val cropAreaWidth: Int,
    private val cropAreaHeight: Int,
    private val cropAreaX: Int,
    private val cropAreaY: Int,
) {

    private var uCenterNDCLoc: Int = 0
    private var uHalfSizeNDCLoc: Int = 0
    private var uRotationRadLoc: Int = 0
    private var uScaleXLoc: Int = 0
    private var uScaleYLoc: Int = 0
    private var uRotDegLoc: Int = 0

    val CAMERA_VERTEX_SHADER = """
        #version 100
        #extension GL_OES_EGL_image_external : require

        attribute vec2 aPosition;
        varying vec2 vTextureCoord;
        uniform vec2 uCenterNDC;
        uniform float uScaleX;
        uniform float uScaleY;
        uniform float uRotationRad;

        void main() {
            vec2 offset = (aPosition - 0.5) * 2.0;
            float c = cos(uRotationRad), s = sin(uRotationRad);
            vec2 rotatedOffset = vec2(
                offset.x * c - offset.y * s,
                offset.x * s + offset.y * c
            );
            gl_Position = vec4(rotatedOffset * vec2(uScaleX, uScaleY) + uCenterNDC, 0.0, 1.0);
            vTextureCoord = aPosition;
        }
    """.trimIndent()

    val CAMERA_FRAGMENT_SHADER = """
        #extension GL_OES_EGL_image_external : require

        precision mediump float;
        uniform samplerExternalOES sTexture;
        varying vec2 vTextureCoord;
        uniform float uOpacity;

        void main() {
            vec4 color = texture2D(sTexture, vTextureCoord);
            gl_FragColor = vec4(color.rgb * uOpacity, uOpacity);
        }
    """.trimIndent()

    private val OES_VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec4 aTexCoord;
        varying vec2 vTexCoord;

        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord.xy;
        }
    """.trimIndent()

    private val OES_FRAGMENT_SHADER = """
        #extension GL_OES_EGL_image_external : require

        precision mediump float;
        uniform samplerExternalOES uTexture;
        varying vec2 vTexCoord;

        void main() {
            gl_FragColor = texture2D(uTexture, vTexCoord);
        }
    """.trimIndent()

    private val TEXTURE_VERTEX_SHADER = OES_VERTEX_SHADER

    private val TEXTURE_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        varying vec2 vTexCoord;

        void main() {
            gl_FragColor = texture2D(uTexture, vTexCoord);
        }
    """.trimIndent()

    private val CROPPED_VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec4 aTexCoord;
        uniform float scaleX;
        uniform float scaleY;
        uniform vec2 uCenterPos;
        varying vec2 vTexCoord;

        void main() {
            gl_Position = vec4(aPosition.xy, 0.0, 1.0);
            vTexCoord = vec2((aTexCoord.x * scaleX) + uCenterPos.x, ((1.0 - aTexCoord.y) * scaleY) + uCenterPos.y);
        }
    """.trimIndent()

    private val CROPPED_FRAGMENT_SHADER = """
        precision mediump float;
        uniform sampler2D uTexture;
        varying vec2 vTexCoord;

        void main() {
            gl_FragColor = texture2D(uTexture, vTexCoord);
        }
    """.trimIndent()

    private lateinit var eglCore: EglCore

    private lateinit var outputEglSurface: EGLSurface

    private var programOesBackground: Int
    private var programOverlay1: Int
    private var programOesFrontCamera: Int
    private var programOverlay2: Int
    private var programOverlayBuffer: Int

    private var oesTexBackground = -1
    private var texOverlay1 = -1
    private var oesTexFrontCamera = -1
    private var texOverlay2 = -1

    private var uLocsOesBg = 0
    private var uLocsOverlay1 = 0
    private var uLocsOverlay2 = 0
    private var uLocsOverlayBuffer = 0
    private var uPosOverlayBuffer = 0
    private var uScaleXOverlayBuffer = 0
    private var uScaleYOverlayBuffer = 0
    private var uCenterPosOverlayBuffer = 0
    private lateinit var fullFrameBuffer: IntBuffer
    private lateinit var renderedTexture: IntBuffer

    private var aPositionLoc: Int = 0
    private var opacityUniformLocation: Int = 0

    init {
        eglCore = EglCore.create(eglContext)

        initSurface()

        programOesBackground = GlUtil.createProgram(OES_VERTEX_SHADER, OES_FRAGMENT_SHADER)
        programOverlay1 = GlUtil.createProgram(TEXTURE_VERTEX_SHADER, TEXTURE_FRAGMENT_SHADER)
        programOesFrontCamera = GlUtil.createProgram(CAMERA_VERTEX_SHADER, CAMERA_FRAGMENT_SHADER)
        programOverlay2 = GlUtil.createProgram(TEXTURE_VERTEX_SHADER, TEXTURE_FRAGMENT_SHADER)
        programOverlayBuffer = GlUtil.createProgram(CROPPED_VERTEX_SHADER, CROPPED_FRAGMENT_SHADER)

        QuadBuffers.create()

        oesTexBackground = backgroundSurfaceTextureId
        if (frontCameraSurfaceTextureId != null) {
            oesTexFrontCamera = frontCameraSurfaceTextureId
        }

        if (overlayBitmap1 != null) {
            texOverlay1 = GlUtil.create2dTexture(overlayBitmap1)
        }

        if (overlayBitmap2 != null) {
            texOverlay2 = GlUtil.create2dTexture(overlayBitmap2)
        }

        uLocsOesBg = GLES20.glGetUniformLocation(programOesBackground, "uTexture")

        if (overlayBitmap1 != null) {
            uLocsOverlay1 = GLES20.glGetUniformLocation(programOverlay1, "uTexture")
        }

        if (frontCameraSurfaceTexture != null) {
            aPositionLoc = GLES20.glGetAttribLocation(programOesFrontCamera, "aPosition")

            uCenterNDCLoc = GLES20.glGetUniformLocation(programOesFrontCamera, "uCenterNDC")

            uHalfSizeNDCLoc = GLES20.glGetUniformLocation(programOesFrontCamera, "uHalfSizeNDC")
            uRotationRadLoc = GLES20.glGetUniformLocation(programOesFrontCamera, "uRotationRad")

            uScaleXLoc = GLES20.glGetUniformLocation(programOesFrontCamera, "uScaleX")
            uScaleYLoc = GLES20.glGetUniformLocation(programOesFrontCamera, "uScaleY")
            uRotDegLoc = GLES20.glGetUniformLocation(programOesFrontCamera, "uRotDeg")

            opacityUniformLocation = GLES20.glGetUniformLocation(programOesFrontCamera, "uOpacity")
        }

        if (overlayBitmap2 != null) {
            uLocsOverlay2 = GLES20.glGetUniformLocation(programOverlay2, "uTexture")
        }

        if (useCropArea) {
            uLocsOverlayBuffer = GLES20.glGetUniformLocation(programOverlayBuffer, "uTexture")
            uPosOverlayBuffer = GLES20.glGetUniformLocation(programOverlayBuffer, "aPosition")
            uScaleXOverlayBuffer = GLES20.glGetUniformLocation(programOverlayBuffer, "scaleX")
            uScaleYOverlayBuffer = GLES20.glGetUniformLocation(programOverlayBuffer, "scaleY")
            uCenterPosOverlayBuffer = GLES20.glGetUniformLocation(programOverlayBuffer, "uCenterPos")

            fullFrameBuffer = IntBuffer.allocate(1)

            GLES20.glGenFramebuffers(1, fullFrameBuffer)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fullFrameBuffer[0])

            if (!GLES20.glIsFramebuffer(fullFrameBuffer[0])) {
                throw RuntimeException("Not a frame buffer!")
            }

            renderedTexture = IntBuffer.allocate(1)

            GLES20.glGenTextures(1, renderedTexture)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderedTexture[0])
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, (width*displayRatio).toInt(), (height*displayRatio).toInt(), 0,GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)

            if (smoothCrop) {
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            } else {
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            }

            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderedTexture[0], 0)

            if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                throw RuntimeException("Error, framebuffer wasn't set up!")
            }

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        }
    }

    private fun initSurface() {
        outputEglSurface = EGL14.eglCreateWindowSurface(
            eglCore.display,
            eglCore.config,
            outputSurface,
            intArrayOf(EGL14.EGL_NONE),
            0
        )

        if (outputEglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("No EGL surface has been created.")
        }

        eglCore.makeCurrent(outputEglSurface!!)

        if (EGL14.eglGetCurrentSurface(EGL14.EGL_READ) != outputEglSurface) {
            throw RuntimeException("Could not get EGL surface.")
        }
    }

    fun setCoordsFullScreen() {
        val stride = 4 * 4
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, stride, 2 * 4)
    }

    fun draw() {
        if (useCropArea) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fullFrameBuffer[0])
        }
        GLES20.glViewport(0, 0, (width*displayRatio).toInt(), (height*displayRatio).toInt())
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(programOesBackground)

        setCoordsFullScreen()

        updateTransformMatrix(backgroundSurfaceTexture)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexBackground)
        GLES20.glUniform1i(uLocsOesBg, 0)
        QuadBuffers.drawIndexed()

        if (useOverlay) {
            if (overlayBitmap1 != null) {
                GLES20.glUseProgram(programOverlay1)

                setCoordsFullScreen()

                GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texOverlay1)
                GLES20.glUniform1i(uLocsOverlay1, 1)

                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

                QuadBuffers.drawIndexed()
            }

            if (frontCameraSurfaceTexture != null) {
                GLES20.glUseProgram(programOesFrontCamera)

                val displayDeg = when (displayRotation) {
                    Surface.ROTATION_0 -> 0
                    Surface.ROTATION_90 -> 90
                    Surface.ROTATION_180 -> 180
                    Surface.ROTATION_270 -> 270
                    else -> 0
                }

                val rotationDegrees = (cameraRotation - displayDeg + 360) % 360

                val rotationDeg = -camera!!.rotation - rotationDegrees
                val destWidthPx = (camera!!.width * camera.scale) * displayRatio
                val destHeightPx = (camera!!.height * camera.scale) * displayRatio
                val widthPx = (width * displayRatio).toInt()
                val heightPx = (height * displayRatio).toInt()
                val xPx = (camera.x) * displayRatio
                val yPx = (camera.y) * displayRatio

                GLES20.glUniform1f(opacityUniformLocation, camera!!.opacity)

                val unitQuadBuffer = ByteBuffer.allocateDirect(4 * 2 * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()

                unitQuadBuffer.put(
                    floatArrayOf(
                        0f, 0f,
                        1f, 0f,
                        0f, 1f,
                        1f, 1f
                    )
                )

                unitQuadBuffer.flip()

                val rotationRad = Math.toRadians(rotationDeg.toDouble())

                val centerXNdc = 2.0f * xPx / widthPx - 1.0f
                val centerYNdc = 1.0f - 2.0f * yPx / heightPx

                val scaleXNdc = 2.0f * destWidthPx / widthPx / 2.0f
                val scaleYNdc = 2.0f * destHeightPx / heightPx / 2.0f

                GLES20.glUniform2f(uCenterNDCLoc, centerXNdc, centerYNdc)
                GLES20.glUniform1f(uScaleXLoc, scaleXNdc)
                GLES20.glUniform1f(uScaleYLoc, scaleYNdc)
                GLES20.glUniform1f(uRotationRadLoc, rotationRad.toFloat())

                GLES20.glEnableVertexAttribArray(aPositionLoc)
                GLES20.glVertexAttribPointer(
                    aPositionLoc,
                    2,
                    GLES20.GL_FLOAT,
                    false,
                    0,
                    unitQuadBuffer
                )

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexFrontCamera)

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                GLES20.glDisableVertexAttribArray(aPositionLoc)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
            }

            if (overlayBitmap2 != null) {
                GLES20.glUseProgram(programOverlay2)

                setCoordsFullScreen()

                GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texOverlay2)
                GLES20.glUniform1i(uLocsOverlay2, 3)

                QuadBuffers.drawIndexed()
            }
        }

        if (useCropArea) {
            val widthPx = (width * displayRatio).toInt()
            val heightPx = (height * displayRatio).toInt()

            val xPx = (cropAreaX) * displayRatio
            val yPx = (heightPx-cropAreaHeight-cropAreaY) * displayRatio

            val centerXNdc = xPx / widthPx
            val centerYNdc = yPx / heightPx

            val destWidthPx = (cropAreaWidth * 1.0f) * displayRatio
            val destHeightPx = (cropAreaHeight * 1.0f) * displayRatio

            val scaleXNdc = 2.0f * destWidthPx / widthPx / 2.0f
            val scaleYNdc = 2.0f * destHeightPx / heightPx / 2.0f

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

            GLES20.glViewport(0, 0, cropAreaWidth, cropAreaHeight)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(programOverlayBuffer)

            setCoordsFullScreen()

            GLES20.glActiveTexture(GLES20.GL_TEXTURE4)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderedTexture[0])
            GLES20.glUniform1f(uScaleXOverlayBuffer, scaleXNdc)
            GLES20.glUniform1f(uScaleYOverlayBuffer, scaleYNdc)
            GLES20.glUniform2f(uCenterPosOverlayBuffer, centerXNdc, centerYNdc)
            GLES20.glUniform1i(uLocsOverlayBuffer, 4)

            QuadBuffers.drawIndexed()
        }

        eglCore.swapBuffers()
    }

    fun release() {
        eglCore.releaseSurface()
        eglCore.destroy()
    }

    private fun updateTransformMatrix(st: SurfaceTexture) {
        val matrix = FloatArray(16)
        st.getTransformMatrix(matrix)

        val texCoords = FloatArray(8)
        QuadBuffers.vertices.let { quad ->
            for (i in 0 until 4) {
                val s = quad[i * 4 + 2]
                val t = quad[i * 4 + 3]

                val s_ = matrix[0] * s + matrix[1] * t + matrix[3]
                val t_ = matrix[4] * s + matrix[5] * t + matrix[7]

                texCoords[i * 2] = s_
                texCoords[i * 2 + 1] = 1 - t_
            }
        }

        GLES20.glEnableVertexAttribArray(1)
        GLES20.glVertexAttribPointer(
            1,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            texCoords.asFloatBuffer()
        )
    }

    private fun FloatArray.asFloatBuffer(): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder())
        for (f in this) buffer.putFloat(f)
        buffer.flip()
        return buffer
    }
}