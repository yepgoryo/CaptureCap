/*
 * This code has been co-authored by an AI.
 * Model name: Qwen 3 Coder Next
 */

package com.yepgoryo.CaptureCap

import android.Manifest
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import androidx.annotation.MainThread
import androidx.annotation.RequiresPermission

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class FrontCameraManager(
    private val context: Context
) {

    private val THREAD_TAG = "CameraBackground"
    private val logger = Logger(this, context)
    private lateinit var cameraDevice: CameraDevice
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var surfaceTexture: SurfaceTexture

    private val cameraOpenCloseLock = Semaphore(1)
    private var cameraHandler: Handler? = null

    private var cameraManager: CameraManager? = null
    private var frontCameraId: String? = null

    @RequiresPermission(Manifest.permission.CAMERA)
    @MainThread
    fun openCamera(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        this.surfaceTexture = surfaceTexture.apply {
            setDefaultBufferSize(width, height)
        }

        cameraManager = context.getSystemService(CameraManager::class.java)
            ?: throw IllegalStateException("CameraManager unavailable")

        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Could not open camera within timeout")
            }

            frontCameraId = getFrontCameraId(cameraManager!!)
                ?: throw IllegalStateException("No front-facing camera found")

            val cameraThread = HandlerThread(THREAD_TAG)
            cameraThread.start()
            cameraHandler = Handler(cameraThread.looper)

            cameraManager!!.openCamera(frontCameraId!!, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    this@FrontCameraManager.cameraDevice = camera
                    cameraOpenCloseLock.release()
                    startRecord()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    this@FrontCameraManager.cameraDevice = camera
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    this@FrontCameraManager.cameraDevice = camera

                    val msg = when (error) {
                        ERROR_CAMERA_IN_USE -> "Camera in use"
                        ERROR_MAX_CAMERAS_IN_USE -> "Max cameras in use"
                        ERROR_CAMERA_DISABLED -> "Camera disabled"
                        ERROR_CAMERA_DEVICE -> "Fatal device error"
                        else -> "Unknown camera error ($error)"
                    }
                    throw RuntimeException("Camera open failed: $msg")
                }
            }, cameraHandler)
        } catch (e: Exception) {
            logger.logError(e)
            release()
            throw e
        }
    }

    @MainThread
    fun startRecord() {
        try {
            val recordRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                .apply { addTarget(Surface(surfaceTexture)) }

            cameraDevice.createCaptureSession(
                listOf(Surface(surfaceTexture)),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        try {
                            recordRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                                CameraMetadata.CONTROL_MODE_AUTO)
                            val threadHandler = Handler(Looper.getMainLooper())
                            cameraCaptureSession.setRepeatingRequest(
                                recordRequestBuilder.build(),
                                null,
                                cameraHandler ?: threadHandler
                            )
                        } catch (e: CameraAccessException) {
                            logger.logError(e)
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        release()
                        throw RuntimeException("Failed to configure capture session")
                    }
                },
                cameraHandler
            )
        } catch (e: Exception) {
            logger.logError(e)
            e.printStackTrace()
            release()
        }
    }

    @MainThread
    fun release() {
        try {
            cameraCaptureSession.close()
            cameraDevice.close()
            cameraHandler?.looper?.quitSafely()
        } catch (e: Exception) {
            logger.logError(e)
            throw e
        }

        cameraOpenCloseLock.release()
    }

    private fun getFrontCameraId(manager: CameraManager): String? =
        manager.cameraIdList.firstOrNull { id ->
            try {
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } catch (e: Exception) {
                logger.logError(e)
                false
            }
        }

    fun getCameraRotation(): Int {
        val characteristics = cameraManager!!.getCameraCharacteristics(frontCameraId!!)

        return characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
    }
}