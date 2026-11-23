/*
 * This code has been co-authored by an AI.
 * Model name: Qwen 3 Coder Next
 */

package com.yepgoryo.CaptureCap

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Surface

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

data class OrientationChangedEvent(
    val angle: Float,
    val delta: Float,
    val orientation: Int,
    val timestampMs: Long = SystemClock.elapsedRealtime()
)

class HardwareOrientationSensor(
    private val context: Context,
    private val debounceDelayMillis: Long = 500L,
    private val noiseThresholdDegrees: Float = 5f
) : AutoCloseable {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val logger = Logger(this, context)

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    companion object {
        private const val GRAVITY_THRESHOLD = 6.0f
    }

    val orientationFlow: StateFlow<OrientationChangedEvent?> = callbackFlow {
        var lastAngle: Float = 0f
        var sensorListener: SensorEventListener? = null

        try {
            sensorListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

                    val gx = event.values[0]
                    val gy = event.values[1]

                    val gNorm = sqrt(gx * gx + gy * gy)
                    if (gNorm < GRAVITY_THRESHOLD) {
                        return
                    }

                    val angleRad = atan2(gx, gy)
                    var angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()

                    val rotation = when {
                        angleDeg in 45f..135f -> Surface.ROTATION_90
                        ((angleDeg in 135f..225f) || (angleDeg in -225f..-135f)) -> Surface.ROTATION_180
                        angleDeg in -135f..-45f -> Surface.ROTATION_270
                        else -> Surface.ROTATION_0
                    }

                    val delta = angleDeg - lastAngle

                    if (abs(delta) >= noiseThresholdDegrees) {
                        lastAngle = angleDeg
                        trySend(
                            OrientationChangedEvent(
                                angleDeg,
                                delta,
                                rotation,
                                SystemClock.elapsedRealtime()
                            )
                        )
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            sensorManager.registerListener(
                sensorListener,
                gravitySensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )

        } catch (e: Exception) {
            logger.logError(e)
            close(e)
            return@callbackFlow
        }

        awaitClose {
            sensorManager.unregisterListener(sensorListener)
            scope.cancel()
        }
    }
    .debounce(debounceDelayMillis)
    .scan(null as OrientationChangedEvent?) { prev, curr ->
        val angle1 = prev?.angle ?: curr.angle
        val delta = ((curr.angle - angle1 + 180f) % 360f - 180f)

        curr.copy(delta = delta.coerceIn(-180f, 180f))
    }
    .stateIn(
        scope,
        SharingStarted.WhileSubscribed(),
        null
    )

    val lastStableOrientation: OrientationChangedEvent?
        get() = orientationFlow.value

    override fun close() {
        scope.cancel()
    }
}