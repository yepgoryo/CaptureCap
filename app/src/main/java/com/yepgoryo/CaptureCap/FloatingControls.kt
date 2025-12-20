package com.yepgoryo.CaptureCap

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.Surface
import android.widget.Chronometer
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.res.ResourcesCompat

class FloatingControls : Service() {

    companion object {
        const val ACTION_RECORD_PANEL = MainActivity.appName + ".PANEL_RECORD"
        const val ACTION_POSITION_PANEL = MainActivity.appName + ".PANEL_POSITION"
    }

    private var appSettings: GlobalProperties? = null
    private var densityNormal: Float = 0.0F
    private var display: Display? = null
    private var displayHeight: Int = 0
    private var displayWidth: Int = 0
    private var floatWindowLayoutParam: WindowManager.LayoutParams? = null
    private var floatingPanel: ViewGroup? = null
    private var heightNormal: Int = 0
    private var layoutType: Int = 0
    private var orientationOnStart: Int = 0
    private var panelHeight: Int = 0
    private var panelSize: GlobalProperties.FloatingControlsSizeProperty? = null
    private var panelWeightHidden: Int = 0
    private var panelWidth: Int = 0
    private var panelWidthNormal: Int = 0
    private var widthNormal: Int = 0
    private var pauseButton: ImageButton? = null
    private var recordingPanelBinder: ScreenRecorder.RecordingPanelBinder? = null
    private var recordingProgress: Chronometer? = null
    private var resumeButton: ImageButton? = null
    private var sensor: SensorManager? = null
    private var startAction: String? = null
    private var stopButton: ImageButton? = null
    private var timerStart: Long = 0
    private var viewHandle: ImageView? = null
    private var windowManager: WindowManager? = null
    private var isHorizontal = true
    private var recordingPaused = false
    private var panelHidden = false
    private var isStopped = true
    private var isRestarting = false

    fun closePanel() {
        if (this.floatingPanel != null) {
            this.windowManager?.removeView(this.floatingPanel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSelf()
    }

    private val sensorListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor, i: Int) {}

        override fun onSensorChanged(sensorEvent: SensorEvent) {
            val rotation: Int = this@FloatingControls.display!!.rotation
            if (sensorEvent.sensor.type == Sensor.TYPE_ACCELEROMETER && this@FloatingControls.orientationOnStart != rotation) {
                this@FloatingControls.orientationOnStart = rotation
                if (this@FloatingControls.startAction == ACTION_POSITION_PANEL) {
                    this@FloatingControls.closePanel()
                } else if (this@FloatingControls.startAction == ACTION_RECORD_PANEL) {
                    this@FloatingControls.timerStart = this@FloatingControls.recordingProgress!!.base
                    this@FloatingControls.closePanel()
                    this@FloatingControls.startRecord()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        this.display = (this.baseContext.getSystemService("display") as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        this.windowManager = (this.getSystemService("window") as WindowManager)
        val sensorManager: SensorManager = this.applicationContext.getSystemService("sensor") as SensorManager
        this.sensor = sensorManager
        sensorManager.registerListener(this.sensorListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 2)
    }

    override fun onBind(intent: Intent?): IBinder {
        if (intent!!.action == ACTION_POSITION_PANEL) {
            return this.panelPositionBinder
        }
        return this.panelBinder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            this.isRestarting = false
            if (intent.action == ACTION_RECORD_PANEL) {
                this.isStopped = false
                this.startAction = ACTION_RECORD_PANEL
            } else {
                if (intent.action == ACTION_POSITION_PANEL) {
                    this.isStopped = true
                    this.startAction = ACTION_POSITION_PANEL
                }
            }
            val point = Point()
            this.display!!.getSize(point)
            val displayMetrics = DisplayMetrics()
            this.display!!.getRealMetrics(displayMetrics)
            this.densityNormal = displayMetrics.density
            val rotation: Int = this.display!!.rotation
            this.orientationOnStart = rotation
            if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                this.widthNormal = point.y
                this.heightNormal = point.x
            } else {
                this.widthNormal = point.x
                this.heightNormal = point.y
            }
            if (this.recordingPanelBinder != null) {
                this.timerStart = this.recordingPanelBinder!!.getTimeStart()
            } else {
                this.timerStart = 0L
            }
            startRecord()
        }
        return START_STICKY
    }

    fun actionConnectPanel(recordingPanelBinder: ScreenRecorder.RecordingPanelBinder) {
        this@FloatingControls.recordingPanelBinder = recordingPanelBinder
    }

    fun actionDisconnectPanel() {
        this@FloatingControls.recordingPanelBinder = null
    }

    fun setControlState(paused: Boolean) {
        this@FloatingControls.recordingPaused = paused
        if (!this@FloatingControls.panelHidden) {
            if (paused) {
                this@FloatingControls.pauseButton?.visibility = View.GONE
                this@FloatingControls.resumeButton?.visibility = View.VISIBLE
            } else {
                this@FloatingControls.pauseButton?.visibility = View.VISIBLE
                this@FloatingControls.resumeButton?.visibility = View.GONE
            }
        }
    }

    private val panelBinder: IBinder = PanelBinder()
    private val panelPositionBinder: IBinder = PanelPositionBinder()

    inner class PanelBinder : Binder() {
        fun setConnectPanel(recordingPanelBinder: ScreenRecorder.RecordingPanelBinder) {
            this@FloatingControls.actionConnectPanel(recordingPanelBinder)
        }

        fun setDisconnectPanel() {
            this@FloatingControls.actionDisconnectPanel()
        }

        fun setPause(timeRecorded: Long) {
            this@FloatingControls.recordingProgress?.setBase(SystemClock.elapsedRealtime() - timeRecorded)
            this@FloatingControls.recordingProgress?.stop()
            when (this@FloatingControls.panelSize) {
                GlobalProperties.FloatingControlsSizeProperty.LARGE -> { this@FloatingControls.stopButton!!.setImageDrawable(ResourcesCompat.getDrawable(this@FloatingControls.resources, R.drawable.icon_stop_continue_color_action_big, this@FloatingControls.theme)) }
                GlobalProperties.FloatingControlsSizeProperty.NORMAL -> { this@FloatingControls.stopButton!!.setImageDrawable(ResourcesCompat.getDrawable(this@FloatingControls.resources, R.drawable.icon_stop_continue_color_action_normal, this@FloatingControls.theme)) }
                GlobalProperties.FloatingControlsSizeProperty.SMALL -> { this@FloatingControls.stopButton!!.setImageDrawable(ResourcesCompat.getDrawable(this@FloatingControls.resources, R.drawable.icon_stop_continue_color_action_small, this@FloatingControls.theme)) }
                GlobalProperties.FloatingControlsSizeProperty.TINY -> { this@FloatingControls.stopButton!!.setImageDrawable(ResourcesCompat.getDrawable(this@FloatingControls.resources, R.drawable.icon_stop_continue_color_action_tiny, this@FloatingControls.theme)) }
                else -> {}
            }
            this@FloatingControls.setControlState(true)
        }

        fun setResume(timeStarted: Long) {
            this@FloatingControls.recordingProgress?.setBase(timeStarted)
            this@FloatingControls.recordingProgress?.start()
            when (this@FloatingControls.panelSize) {
                GlobalProperties.FloatingControlsSizeProperty.LARGE -> { this@FloatingControls.stopButton!!.setImageDrawable(ResourcesCompat.getDrawable(this@FloatingControls.resources, R.drawable.icon_stop_color_action_big, this@FloatingControls.theme)) }
                GlobalProperties.FloatingControlsSizeProperty.NORMAL -> { this@FloatingControls.stopButton!!.setImageDrawable(ResourcesCompat.getDrawable(this@FloatingControls.resources, R.drawable.icon_stop_color_action_normal, this@FloatingControls.theme)) }
                GlobalProperties.FloatingControlsSizeProperty.SMALL -> { this@FloatingControls.stopButton!!.setImageDrawable(ResourcesCompat.getDrawable(this@FloatingControls.resources, R.drawable.icon_stop_color_action_small, this@FloatingControls.theme)) }
                GlobalProperties.FloatingControlsSizeProperty.TINY -> { this@FloatingControls.stopButton!!.setImageDrawable(ResourcesCompat.getDrawable(this@FloatingControls.resources, R.drawable.icon_stop_color_action_tiny, this@FloatingControls.theme)) }
                else -> {}
            }
            this@FloatingControls.setControlState(false)
        }

        fun setRestart(orientation: Int) {
            this@FloatingControls.isRestarting = true
            this@FloatingControls.orientationOnStart = orientation
            this@FloatingControls.timerStart = SystemClock.elapsedRealtime()
            this@FloatingControls.closePanel()
            this@FloatingControls.startRecord()
        }

        fun setStop() {
            this@FloatingControls.isStopped = true
            this@FloatingControls.setControlState(false)
            this@FloatingControls.closePanel()
            this@FloatingControls.startAction = null
        }
    }

    inner class PanelPositionBinder : Binder() {

        fun setStop() {
            if (this@FloatingControls.isHorizontal) {

                when (this@FloatingControls.panelSize) {
                    GlobalProperties.FloatingControlsSizeProperty.LARGE -> {
                        this@FloatingControls.appSettings?.setIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_HORIZONTAL_X_BIG, this@FloatingControls.floatWindowLayoutParam!!.x)
                        this@FloatingControls.appSettings?.setIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_HORIZONTAL_Y_BIG, this@FloatingControls.floatWindowLayoutParam!!.y)
                        this@FloatingControls.appSettings?.setBooleanProperty(GlobalProperties.PropertiesBoolean.PANEL_POSITION_HORIZONTAL_HIDDEN_BIG, this@FloatingControls.panelHidden)
                    }
                    GlobalProperties.FloatingControlsSizeProperty.NORMAL -> {
                        this@FloatingControls.appSettings?.setIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_HORIZONTAL_X_NORMAL, this@FloatingControls.floatWindowLayoutParam!!.x)
                        this@FloatingControls.appSettings?.setIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_HORIZONTAL_Y_NORMAL, this@FloatingControls.floatWindowLayoutParam!!.y)
                        this@FloatingControls.appSettings?.setBooleanProperty(GlobalProperties.PropertiesBoolean.PANEL_POSITION_HORIZONTAL_HIDDEN_NORMAL, this@FloatingControls.panelHidden)
                    }
                    GlobalProperties.FloatingControlsSizeProperty.SMALL -> {
                        this@FloatingControls.appSettings?.setIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_HORIZONTAL_X_SMALL, this@FloatingControls.floatWindowLayoutParam!!.x)
                        this@FloatingControls.appSettings?.setIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_HORIZONTAL_Y_SMALL, this@FloatingControls.floatWindowLayoutParam!!.y)
                        this@FloatingControls.appSettings?.setBooleanProperty(GlobalProperties.PropertiesBoolean.PANEL_POSITION_HORIZONTAL_HIDDEN_SMALL, this@FloatingControls.panelHidden)
                    }
                    GlobalProperties.FloatingControlsSizeProperty.TINY -> {
                        this@FloatingControls.appSettings?.setIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_HORIZONTAL_X_TINY, this@FloatingControls.floatWindowLayoutParam!!.x)
                        this@FloatingControls.appSettings?.setIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_HORIZONTAL_Y_TINY, this@FloatingControls.floatWindowLayoutParam!!.y)
                        this@FloatingControls.appSettings?.setBooleanProperty(GlobalProperties.PropertiesBoolean.PANEL_POSITION_HORIZONTAL_HIDDEN_TINY, this@FloatingControls.panelHidden)
                    }
                    else -> {}
                }
            } else {
                when (this@FloatingControls.panelSize) {
                    GlobalProperties.FloatingControlsSizeProperty.LARGE -> {
                        this@FloatingControls.appSettings?.setIntProperty(
                            GlobalProperties.PropertiesInt.PANEL_POSITION_VERTICAL_X_BIG,
                            this@FloatingControls.floatWindowLayoutParam!!.x
                        )
                        this@FloatingControls.appSettings?.setIntProperty(
                            GlobalProperties.PropertiesInt.PANEL_POSITION_VERTICAL_Y_BIG,
                            this@FloatingControls.floatWindowLayoutParam!!.y
                        )
                        this@FloatingControls.appSettings?.setBooleanProperty(
                            GlobalProperties.PropertiesBoolean.PANEL_POSITION_VERTICAL_HIDDEN_BIG,
                            this@FloatingControls.panelHidden
                        )
                    }

                    GlobalProperties.FloatingControlsSizeProperty.NORMAL -> {
                        this@FloatingControls.appSettings?.setIntProperty(
                            GlobalProperties.PropertiesInt.PANEL_POSITION_VERTICAL_X_NORMAL,
                            this@FloatingControls.floatWindowLayoutParam!!.x
                        )
                        this@FloatingControls.appSettings?.setIntProperty(
                            GlobalProperties.PropertiesInt.PANEL_POSITION_VERTICAL_Y_NORMAL,
                            this@FloatingControls.floatWindowLayoutParam!!.y
                        )
                        this@FloatingControls.appSettings?.setBooleanProperty(
                            GlobalProperties.PropertiesBoolean.PANEL_POSITION_VERTICAL_HIDDEN_NORMAL,
                            this@FloatingControls.panelHidden
                        )
                    }

                    GlobalProperties.FloatingControlsSizeProperty.SMALL -> {
                        this@FloatingControls.appSettings!!.setIntProperty(
                            GlobalProperties.PropertiesInt.PANEL_POSITION_VERTICAL_X_SMALL,
                            this@FloatingControls.floatWindowLayoutParam!!.x
                        )
                        this@FloatingControls.appSettings!!.setIntProperty(
                            GlobalProperties.PropertiesInt.PANEL_POSITION_VERTICAL_Y_SMALL,
                            this@FloatingControls.floatWindowLayoutParam!!.y
                        )
                        this@FloatingControls.appSettings!!.setBooleanProperty(
                            GlobalProperties.PropertiesBoolean.PANEL_POSITION_VERTICAL_HIDDEN_SMALL,
                            this@FloatingControls.panelHidden
                        )
                    }

                    GlobalProperties.FloatingControlsSizeProperty.TINY -> {
                        this@FloatingControls.appSettings!!.setIntProperty(
                            GlobalProperties.PropertiesInt.PANEL_POSITION_VERTICAL_X_TINY,
                            this@FloatingControls.floatWindowLayoutParam!!.x
                        )
                        this@FloatingControls.appSettings!!.setIntProperty(
                            GlobalProperties.PropertiesInt.PANEL_POSITION_VERTICAL_Y_TINY,
                            this@FloatingControls.floatWindowLayoutParam!!.y
                        )
                        this@FloatingControls.appSettings!!.setBooleanProperty(
                            GlobalProperties.PropertiesBoolean.PANEL_POSITION_VERTICAL_HIDDEN_TINY,
                            this@FloatingControls.panelHidden
                        )
                    }

                    else -> {}
                }

                this@FloatingControls.closePanel()
            }
        }
    }

    fun updateMetrics() {
        val orientation = this@FloatingControls.orientationOnStart
        if (orientation == Surface.ROTATION_270 || orientation == Surface.ROTATION_90) {
            this@FloatingControls.displayWidth = this@FloatingControls.heightNormal
            this@FloatingControls.displayHeight = this@FloatingControls.widthNormal
        } else {
            this@FloatingControls.displayWidth = this@FloatingControls.widthNormal
            this@FloatingControls.displayHeight = this@FloatingControls.heightNormal
        }
    }

    fun checkBoundaries() {
        var newX: Int = this@FloatingControls.floatWindowLayoutParam!!.x
        var newY: Int = this@FloatingControls.floatWindowLayoutParam!!.y

        if (!this@FloatingControls.panelHidden) {
            if (this@FloatingControls.isHorizontal) {
                if ((newX - (this@FloatingControls.panelWidth / 2)) < -(this@FloatingControls.displayWidth / 2)) {
                    newX = -(this@FloatingControls.displayWidth / 2) + (this@FloatingControls.panelWidth / 2)
                } else if (((this@FloatingControls.panelWidth / 2) + newX) > (this@FloatingControls.displayWidth / 2)) {
                    newX = (this@FloatingControls.displayWidth / 2) - (this@FloatingControls.panelWidth / 2)
                }
                if ((newY - (this@FloatingControls.panelHeight / 2)) < -(this@FloatingControls.displayHeight / 2)) {
                    newY = -(this@FloatingControls.displayHeight / 2)
                } else if (((this@FloatingControls.panelHeight / 2) + newY) > (this@FloatingControls.displayHeight / 2)) {
                    newY = (this@FloatingControls.displayHeight / 2) - (this@FloatingControls.panelHeight / 2)
                }
            } else {
                if ((newX - (this@FloatingControls.panelWidth / 2)) < -(this@FloatingControls.displayWidth / 2)) {
                    newX = -(this@FloatingControls.displayWidth / 2) + (this@FloatingControls.panelWidth / 2)
                } else if (((this@FloatingControls.panelWidth / 2) + newX) > (this@FloatingControls.displayWidth / 2)) {
                    newX = (this@FloatingControls.displayWidth / 2) - (this@FloatingControls.panelWidth / 2)
                }
                if ((newY - (this@FloatingControls.panelHeight / 2)) < -(this@FloatingControls.displayHeight / 2)) {
                    newY = -(this@FloatingControls.displayHeight / 2) + (this@FloatingControls.panelHeight / 2)
                } else if (((this@FloatingControls.panelHeight / 2) + newY) > (this@FloatingControls.displayHeight / 2)) {
                    newY = (this@FloatingControls.displayHeight / 2) - (this@FloatingControls.panelHeight / 2)
                }
            }
        } else {
            if (this@FloatingControls.isHorizontal) {
                if ((newX - (this@FloatingControls.panelWeightHidden / 2)) < -(this@FloatingControls.displayWidth / 2)) {
                    newX = -(this@FloatingControls.displayWidth / 2) + (this@FloatingControls.panelWeightHidden / 2)
                } else if (((this@FloatingControls.panelWeightHidden / 2) + newX) > (this@FloatingControls.displayWidth / 2)) {
                    newX = ((this@FloatingControls.displayWidth / 2) - (this@FloatingControls.panelWeightHidden / 2))
                }
                if ((newY - (this@FloatingControls.panelHeight / 2)) < -(this@FloatingControls.displayHeight / 2)) {
                    newY = -(this@FloatingControls.displayHeight / 2) + (this@FloatingControls.panelHeight / 2)
                } else if (((this@FloatingControls.panelHeight / 2) + newY) > (this@FloatingControls.displayHeight / 2)) {
                    newY = (this@FloatingControls.displayHeight / 2) - (this@FloatingControls.panelHeight / 2)
                }
            } else {
                if ((newX - (this@FloatingControls.panelWidth / 2)) < -(this@FloatingControls.displayWidth / 2)) {
                    newX = -(this@FloatingControls.displayWidth / 2) + (this@FloatingControls.panelWidth / 2)
                } else if (((this@FloatingControls.panelWidth / 2) + newX) > (this@FloatingControls.displayWidth / 2)) {
                    newX = ((this@FloatingControls.displayWidth / 2) - (this@FloatingControls.panelWidth / 2))
                }
                if ((newY - (this@FloatingControls.panelWeightHidden / 2)) < -(this@FloatingControls.displayHeight / 2)) {
                    newY = (-(this@FloatingControls.displayHeight / 2) + (this@FloatingControls.panelWeightHidden / 2))
                } else if (((this@FloatingControls.panelWeightHidden / 2) + newY) > (this@FloatingControls.displayHeight / 2)) {
                    newY = ((this@FloatingControls.displayHeight / 2) - (this@FloatingControls.panelWeightHidden / 2))
                }
            }
        }

        this@FloatingControls.floatWindowLayoutParam!!.x = newX
        this@FloatingControls.floatWindowLayoutParam!!.y = newY
    }

    fun togglePanel() {
        var newX = this@FloatingControls.floatWindowLayoutParam!!.x
        var newY = this@FloatingControls.floatWindowLayoutParam!!.y

        if (!this@FloatingControls.panelHidden) {
            this@FloatingControls.panelHidden = true
            this@FloatingControls.stopButton?.visibility = View.GONE
            this@FloatingControls.pauseButton?.visibility = View.GONE
            this@FloatingControls.resumeButton?.visibility = View.GONE
            this@FloatingControls.viewHandle?.visibility = View.GONE
            this@FloatingControls.recordingProgress?.visibility = View.GONE
            this@FloatingControls.panelWidth = this@FloatingControls.panelWeightHidden
            if (this@FloatingControls.isHorizontal) {
                newX += (this@FloatingControls.panelWidthNormal / 2) - (this@FloatingControls.panelWeightHidden / 2)
                this@FloatingControls.floatWindowLayoutParam!!.width = this@FloatingControls.panelWeightHidden
            } else {
                newY += (this@FloatingControls.panelHeight / 2) - (this@FloatingControls.panelWeightHidden / 2)
                this@FloatingControls.floatWindowLayoutParam!!.height = this@FloatingControls.panelWeightHidden
            }
        } else {
            this@FloatingControls.panelHidden = false
            this@FloatingControls.stopButton?.visibility = View.VISIBLE
            setControlState(this@FloatingControls.recordingPaused)
            this@FloatingControls.recordingProgress?.visibility = View.VISIBLE
            this@FloatingControls.viewHandle?.visibility = View.VISIBLE
            this@FloatingControls.panelWidth = this@FloatingControls.panelWidthNormal
            if (this@FloatingControls.isHorizontal) {
                newX -= (this@FloatingControls.panelWidthNormal / 2) - (this@FloatingControls.panelWeightHidden / 2)
                this@FloatingControls.floatWindowLayoutParam!!.width = this@FloatingControls.panelWidthNormal
            } else {
                newY -= (this@FloatingControls.panelHeight / 2) - (this@FloatingControls.panelWeightHidden / 2)
                this@FloatingControls.floatWindowLayoutParam!!.height = this@FloatingControls.panelHeight
            }
        }

        this@FloatingControls.floatWindowLayoutParam!!.x = newX
        this@FloatingControls.floatWindowLayoutParam!!.y = newY
    }

    fun startRecord() {
        updateMetrics()

        if (this@FloatingControls.orientationOnStart == Surface.ROTATION_270 || this@FloatingControls.orientationOnStart == Surface.ROTATION_90) {
            this@FloatingControls.isHorizontal = true
        } else {
            this@FloatingControls.isHorizontal = false
        }

        val globalProperties = GlobalProperties(baseContext)

        this@FloatingControls.appSettings = globalProperties
        this@FloatingControls.panelSize = globalProperties.getFloatingControlsSize()

        if (this@FloatingControls.isHorizontal) {
            when (this@FloatingControls.panelSize) {
                GlobalProperties.FloatingControlsSizeProperty.LARGE -> { this@FloatingControls.panelHidden = this@FloatingControls.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.PANEL_POSITION_HORIZONTAL_HIDDEN_BIG, false) }
                GlobalProperties.FloatingControlsSizeProperty.NORMAL -> { this@FloatingControls.panelHidden = this@FloatingControls.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.PANEL_POSITION_HORIZONTAL_HIDDEN_NORMAL, false) }
                GlobalProperties.FloatingControlsSizeProperty.SMALL -> { this@FloatingControls.panelHidden = this@FloatingControls.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.PANEL_POSITION_HORIZONTAL_HIDDEN_SMALL, false) }
                GlobalProperties.FloatingControlsSizeProperty.TINY -> { this@FloatingControls.panelHidden = this@FloatingControls.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.PANEL_POSITION_HORIZONTAL_HIDDEN_TINY, false) }
                GlobalProperties.FloatingControlsSizeProperty.LITTLE -> {}
                else -> {}
            }
        } else {
            when (this@FloatingControls.panelSize) {
                GlobalProperties.FloatingControlsSizeProperty.LARGE -> { this@FloatingControls.panelHidden = this@FloatingControls.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.PANEL_POSITION_VERTICAL_HIDDEN_BIG, false) }
                GlobalProperties.FloatingControlsSizeProperty.NORMAL -> { this@FloatingControls.panelHidden = this@FloatingControls.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.PANEL_POSITION_VERTICAL_HIDDEN_NORMAL, false) }
                GlobalProperties.FloatingControlsSizeProperty.SMALL -> { this@FloatingControls.panelHidden = this@FloatingControls.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.PANEL_POSITION_VERTICAL_HIDDEN_SMALL, false) }
                GlobalProperties.FloatingControlsSizeProperty.TINY -> { this@FloatingControls.panelHidden = this@FloatingControls.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.PANEL_POSITION_VERTICAL_HIDDEN_TINY, false) }
                GlobalProperties.FloatingControlsSizeProperty.LITTLE -> {}
                else -> {}
            }
        }

        val darkTheme: GlobalProperties.DarkThemeProperty = this@FloatingControls.appSettings!!.getDarkTheme(true)

        if (((this.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES && darkTheme == GlobalProperties.DarkThemeProperty.AUTOMATIC) || darkTheme == GlobalProperties.DarkThemeProperty.DARK) {
            this@FloatingControls.baseContext.setTheme(androidx.appcompat.R.style.Theme_AppCompat_DayNight)
        } else {
            this@FloatingControls.baseContext.setTheme(androidx.appcompat.R.style.Theme_AppCompat_Light)
        }

        if (this@FloatingControls.isHorizontal) {

            if (((this.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES && darkTheme == GlobalProperties.DarkThemeProperty.AUTOMATIC) || darkTheme == GlobalProperties.DarkThemeProperty.DARK) {
                when (this@FloatingControls.panelSize) {
                    GlobalProperties.FloatingControlsSizeProperty.LARGE -> { this@FloatingControls.floatingPanel = (View.inflate(baseContext, R.layout.panel_float_big_dark, null) as ViewGroup) }
                    GlobalProperties.FloatingControlsSizeProperty.NORMAL -> { this@FloatingControls.floatingPanel = (View.inflate(baseContext, R.layout.panel_float_normal_dark, null) as ViewGroup) }
                    GlobalProperties.FloatingControlsSizeProperty.SMALL -> { this@FloatingControls.floatingPanel = (View.inflate(baseContext, R.layout.panel_float_small_dark, null) as ViewGroup) }
                    GlobalProperties.FloatingControlsSizeProperty.TINY -> { this@FloatingControls.floatingPanel = (View.inflate(baseContext, R.layout.panel_float_tiny_dark, null) as ViewGroup) }
                    GlobalProperties.FloatingControlsSizeProperty.LITTLE -> {}
                    else -> {}
                }
            } else {
                when (this@FloatingControls.panelSize) {
                    GlobalProperties.FloatingControlsSizeProperty.LARGE -> { this@FloatingControls.floatingPanel = (View.inflate(baseContext, R.layout.panel_float_big, null) as ViewGroup) }
                    GlobalProperties.FloatingControlsSizeProperty.NORMAL -> { this@FloatingControls.floatingPanel = (View.inflate(baseContext, R.layout.panel_float_normal, null) as ViewGroup) }
                    GlobalProperties.FloatingControlsSizeProperty.SMALL -> { this@FloatingControls.floatingPanel = (View.inflate(baseContext, R.layout.panel_float_small, null) as ViewGroup) }
                    GlobalProperties.FloatingControlsSizeProperty.TINY -> { this@FloatingControls.floatingPanel = (View.inflate(baseContext, R.layout.panel_float_tiny, null) as ViewGroup) }
                    GlobalProperties.FloatingControlsSizeProperty.LITTLE -> {}
                    else -> {}
                }
            }
        } else {
            if (((this.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES && darkTheme == GlobalProperties.DarkThemeProperty.AUTOMATIC) || darkTheme == GlobalProperties.DarkThemeProperty.DARK) {
                when (this@FloatingControls.panelSize) {
                    GlobalProperties.FloatingControlsSizeProperty.LARGE -> { this@FloatingControls.floatingPanel = View.inflate(baseContext, R.layout.panel_float_vertical_big_dark, null) as ViewGroup }
                    GlobalProperties.FloatingControlsSizeProperty.NORMAL -> { this@FloatingControls.floatingPanel = View.inflate(baseContext, R.layout.panel_float_vertical_normal_dark, null) as ViewGroup }
                    GlobalProperties.FloatingControlsSizeProperty.SMALL -> { this@FloatingControls.floatingPanel = View.inflate(baseContext, R.layout.panel_float_vertical_small_dark, null) as ViewGroup }
                    GlobalProperties.FloatingControlsSizeProperty.TINY -> { this@FloatingControls.floatingPanel = View.inflate(baseContext, R.layout.panel_float_vertical_tiny_dark, null) as ViewGroup }
                    GlobalProperties.FloatingControlsSizeProperty.LITTLE -> {}
                    else -> {}
                }
            } else {
                when (this@FloatingControls.panelSize) {
                    GlobalProperties.FloatingControlsSizeProperty.LARGE -> { this@FloatingControls.floatingPanel = View.inflate(baseContext, R.layout.panel_float_vertical_big, null) as ViewGroup }
                    GlobalProperties.FloatingControlsSizeProperty.NORMAL -> { this@FloatingControls.floatingPanel = View.inflate(baseContext, R.layout.panel_float_vertical_normal, null) as ViewGroup }
                    GlobalProperties.FloatingControlsSizeProperty.SMALL -> { this@FloatingControls.floatingPanel = View.inflate(baseContext, R.layout.panel_float_vertical_small, null) as ViewGroup }
                    GlobalProperties.FloatingControlsSizeProperty.TINY -> { this@FloatingControls.floatingPanel = View.inflate(baseContext, R.layout.panel_float_vertical_tiny, null) as ViewGroup }
                    GlobalProperties.FloatingControlsSizeProperty.LITTLE -> {}
                    else -> {}
                }
            }
        }

        this@FloatingControls.layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        this@FloatingControls.viewHandle = this@FloatingControls.floatingPanel!!.findViewById(R.id.floatingpanelhandle)
        val linearLayout: LinearLayout = this@FloatingControls.floatingPanel!!.findViewById(R.id.panelwithbackground)

        if (((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES && darkTheme == GlobalProperties.DarkThemeProperty.AUTOMATIC) || darkTheme == GlobalProperties.DarkThemeProperty.DARK) {
            linearLayout.background = ResourcesCompat.getDrawable(resources, R.drawable.floatingpanel_shape_dark, theme)
            this@FloatingControls.viewHandle?.setImageResource(R.drawable.floatingpanel_shape_dark)
        }

        linearLayout.setAlpha((this@FloatingControls.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.FLOATING_CONTROLS_OPACITY, 9) + 1) * 0.1f)
        linearLayout.measure(0, 0)
        this@FloatingControls.panelWidthNormal = linearLayout.measuredWidth
        this@FloatingControls.panelHeight = linearLayout.measuredHeight

        when (this@FloatingControls.panelSize) {
           GlobalProperties.FloatingControlsSizeProperty.LARGE -> { this@FloatingControls.panelWeightHidden = ((this@FloatingControls.densityNormal * 50.0f) + 0.5f).toInt() }
           GlobalProperties.FloatingControlsSizeProperty.NORMAL -> { this@FloatingControls.panelWeightHidden = ((this@FloatingControls.densityNormal * 40.0f) + 0.5f).toInt() }
           GlobalProperties.FloatingControlsSizeProperty.SMALL -> { this@FloatingControls.panelWeightHidden = ((this@FloatingControls.densityNormal * 30.0f) + 0.5f).toInt() }
           GlobalProperties.FloatingControlsSizeProperty.TINY -> { this@FloatingControls.panelWeightHidden = ((this@FloatingControls.densityNormal * 20.0f) + 0.5f).toInt() }
           GlobalProperties.FloatingControlsSizeProperty.LITTLE -> {}
            else -> {}
        }

        if (this@FloatingControls.panelHidden) {
            this@FloatingControls.panelWidth = this@FloatingControls.panelWeightHidden
            if (this@FloatingControls.isHorizontal) {
                this@FloatingControls.floatWindowLayoutParam = WindowManager.LayoutParams(this@FloatingControls.panelWeightHidden, this@FloatingControls.panelHeight, this@FloatingControls.layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
            } else {
                this@FloatingControls.floatWindowLayoutParam = WindowManager.LayoutParams(this@FloatingControls.panelWidthNormal, this@FloatingControls.panelWeightHidden, this@FloatingControls.layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
            }
        } else {
            this@FloatingControls.panelWidth = this@FloatingControls.panelWidthNormal
            if (this@FloatingControls.isHorizontal) {
                this@FloatingControls.floatWindowLayoutParam = WindowManager.LayoutParams(this@FloatingControls.panelWidthNormal, this@FloatingControls.panelHeight, this@FloatingControls.layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
            } else {
                this@FloatingControls.floatWindowLayoutParam = WindowManager.LayoutParams(this@FloatingControls.panelWidthNormal, this@FloatingControls.panelHeight, this@FloatingControls.layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
            }
        }

        this@FloatingControls.floatWindowLayoutParam!!.gravity = Gravity.CENTER

        if (this@FloatingControls.isHorizontal) {

            when (this@FloatingControls.panelSize) {
                GlobalProperties.FloatingControlsSizeProperty.LARGE -> {
                    this@FloatingControls.floatWindowLayoutParam!!.x = this@FloatingControls.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_HORIZONTAL_X_BIG, (this@FloatingControls.displayWidth / 2) - (this@FloatingControls.panelWidth / 2))
                    this@FloatingControls.floatWindowLayoutParam!!.y = this@FloatingControls.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_HORIZONTAL_Y_BIG, 0)
                }
                GlobalProperties.FloatingControlsSizeProperty.NORMAL -> {
                    this@FloatingControls.floatWindowLayoutParam!!.x = this@FloatingControls.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_HORIZONTAL_X_NORMAL, (this@FloatingControls.displayWidth / 2) - (this@FloatingControls.panelWidth / 2))
                    this@FloatingControls.floatWindowLayoutParam!!.y = this@FloatingControls.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_HORIZONTAL_Y_NORMAL, 0)
                }
                GlobalProperties.FloatingControlsSizeProperty.SMALL -> {
                    this@FloatingControls.floatWindowLayoutParam!!.x = this@FloatingControls.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_HORIZONTAL_X_SMALL, (this@FloatingControls.displayWidth / 2) - (this@FloatingControls.panelWidth / 2))
                    this@FloatingControls.floatWindowLayoutParam!!.y = this@FloatingControls.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_HORIZONTAL_Y_SMALL, 0)
                }
                GlobalProperties.FloatingControlsSizeProperty.TINY -> {
                    this@FloatingControls.floatWindowLayoutParam!!.x = this@FloatingControls.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_HORIZONTAL_X_TINY, (this@FloatingControls.displayWidth / 2) - (this@FloatingControls.panelWidth / 2))
                    this@FloatingControls.floatWindowLayoutParam!!.y = this@FloatingControls.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_HORIZONTAL_Y_TINY, 0)
                }
                GlobalProperties.FloatingControlsSizeProperty.LITTLE -> {}
                else -> {}
            }
        } else {
            when (this@FloatingControls.panelSize) {
                GlobalProperties.FloatingControlsSizeProperty.LARGE -> {
                    this@FloatingControls.floatWindowLayoutParam!!.x = this@FloatingControls.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_VERTICAL_X_BIG, (this@FloatingControls.displayWidth / 2) - (this@FloatingControls.panelWidth / 2))
                    this@FloatingControls.floatWindowLayoutParam!!.y = this@FloatingControls.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_VERTICAL_Y_BIG, 0)
                }
                GlobalProperties.FloatingControlsSizeProperty.NORMAL -> {
                    this@FloatingControls.floatWindowLayoutParam!!.x = this@FloatingControls.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_VERTICAL_X_NORMAL, (this@FloatingControls.displayWidth / 2) - (this@FloatingControls.panelWidth / 2))
                    this@FloatingControls.floatWindowLayoutParam!!.y = this@FloatingControls.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_VERTICAL_Y_NORMAL, 0)
                }
                GlobalProperties.FloatingControlsSizeProperty.SMALL -> {
                    this@FloatingControls.floatWindowLayoutParam!!.x = this@FloatingControls.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_VERTICAL_X_SMALL, (this@FloatingControls.displayWidth / 2) - (this@FloatingControls.panelWidth / 2))
                    this@FloatingControls.floatWindowLayoutParam!!.y = this@FloatingControls.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_VERTICAL_Y_SMALL, 0)
                }
                GlobalProperties.FloatingControlsSizeProperty.TINY -> {
                    this@FloatingControls.floatWindowLayoutParam!!.x = this@FloatingControls.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_VERTICAL_X_TINY, (this@FloatingControls.displayWidth / 2) - (this@FloatingControls.panelWidth / 2))
                    this@FloatingControls.floatWindowLayoutParam!!.y = this@FloatingControls.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_VERTICAL_Y_TINY, 0)
                }
                GlobalProperties.FloatingControlsSizeProperty.LITTLE -> {}
                else -> {}
            }
        }
        checkBoundaries()
        this@FloatingControls.windowManager?.addView(this@FloatingControls.floatingPanel, this@FloatingControls.floatWindowLayoutParam)
        this@FloatingControls.pauseButton = this@FloatingControls.floatingPanel!!.findViewById(R.id.recordpausebuttonfloating)
        this@FloatingControls.stopButton = this@FloatingControls.floatingPanel!!.findViewById(R.id.recordstopbuttonfloating)
        this@FloatingControls.resumeButton = this@FloatingControls.floatingPanel!!.findViewById(R.id.recordresumebuttonfloating)
        this@FloatingControls.recordingProgress = this@FloatingControls.floatingPanel!!.findViewById(R.id.timerrecordfloating)
        this@FloatingControls.resumeButton?.visibility = View.GONE
        if (this@FloatingControls.panelHidden) {
            this@FloatingControls.stopButton?.visibility = View.GONE
            this@FloatingControls.pauseButton?.visibility = View.GONE
            this@FloatingControls.resumeButton?.visibility = View.GONE
            this@FloatingControls.viewHandle?.visibility = View.GONE
            this@FloatingControls.recordingProgress?.visibility = View.GONE
        } else {
            this@FloatingControls.stopButton?.visibility = View.VISIBLE
            setControlState(this@FloatingControls.recordingPaused)
            this@FloatingControls.recordingProgress?.visibility = View.VISIBLE
            this@FloatingControls.viewHandle?.visibility = View.VISIBLE
        }
        if (this@FloatingControls.startAction == ACTION_RECORD_PANEL) {
            this@FloatingControls.stopButton?.setOnClickListener {
                if (this@FloatingControls.recordingPanelBinder != null) {
                    this@FloatingControls.recordingPanelBinder?.stopService()
                }
                this@FloatingControls.closePanel()
            }
            this@FloatingControls.pauseButton?.setOnClickListener {
                if (this@FloatingControls.recordingPanelBinder != null) {
                    this@FloatingControls.recordingPanelBinder?.recordingPause()
                }
                this@FloatingControls.setControlState(true)
            }
            this@FloatingControls.resumeButton?.setOnClickListener {
                if (this@FloatingControls.recordingPanelBinder != null) {
                    this@FloatingControls.recordingPanelBinder?.recordingResume()
                }
                this@FloatingControls.setControlState(false)
            }
            if (!this@FloatingControls.isRestarting) {
                this@FloatingControls.recordingProgress?.setBase(this@FloatingControls.timerStart)
            }
            this@FloatingControls.recordingProgress?.start()
        }

        this.floatingPanel?.setOnTouchListener(object : View.OnTouchListener {
            var px: Double = 0.0
            var py: Double = 0.0
            var touchX: Double = 0.0
            var touchY: Double = 0.0
            var x: Double = 0.0
            var y: Double = 0.0
            var motionPrevX: Int = 0
            var motionPrevY: Int = 0
            var touchmotionX: Int = 0
            var touchmotionY: Int = 0
            var threshold: Int = 10

            override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
                val action = motionEvent.action
                if (action == MotionEvent.ACTION_DOWN) {
                    this@FloatingControls.updateMetrics()
                    this@FloatingControls.checkBoundaries()
                    this@FloatingControls.windowManager?.updateViewLayout(this@FloatingControls.floatingPanel, this@FloatingControls.floatWindowLayoutParam)
                    this.x = this@FloatingControls.floatWindowLayoutParam!!.x.toDouble()
                    this.y = this@FloatingControls.floatWindowLayoutParam!!.y.toDouble()
                    this.px = motionEvent.rawX.toDouble()
                    this.py = motionEvent.rawY.toDouble()
                    this.touchX = this.x
                    this.touchY = this.y
                    this.motionPrevX = this.x.toInt()
                    this.motionPrevY = this.y.toInt()
                    this.touchmotionX = 0
                    this.touchmotionY = 0
                } else if (action == MotionEvent.ACTION_UP) {
                    if (this.touchmotionX < this.threshold && this.touchmotionY < this.threshold) {
                        this@FloatingControls.floatWindowLayoutParam!!.x = this.touchX.toInt()
                        this@FloatingControls.floatWindowLayoutParam!!.y = this.touchY.toInt()
                        this@FloatingControls.togglePanel()
                    }
                    this@FloatingControls.checkBoundaries()
                    this@FloatingControls.windowManager?.updateViewLayout(this@FloatingControls.floatingPanel, this@FloatingControls.floatWindowLayoutParam)
                } else if (action == MotionEvent.ACTION_MOVE) {
                    this@FloatingControls.floatWindowLayoutParam!!.x = ((this.x + motionEvent.getRawX()) - this.px).toInt()
                    this@FloatingControls.floatWindowLayoutParam!!.y = ((this.y + motionEvent.getRawY()) - this.py).toInt()
                    var motionNewX = this@FloatingControls.floatWindowLayoutParam!!.x - this.motionPrevX
                    var motionNewY = this@FloatingControls.floatWindowLayoutParam!!.y - this.motionPrevY
                    if (motionNewX < 0) {
                        motionNewX = -motionNewX
                    }
                    if (motionNewY < 0) {
                        motionNewY = -motionNewY
                    }
                    if (this.touchmotionX < this.threshold) {
                        this.touchmotionX = this.touchmotionX + motionNewX
                    }
                    if (this.touchmotionY < this.threshold) {
                        this.touchmotionY = this.touchmotionY + motionNewY
                    }
                    this.motionPrevX = this@FloatingControls.floatWindowLayoutParam!!.x
                    this.motionPrevY = this@FloatingControls.floatWindowLayoutParam!!.y
                    this@FloatingControls.windowManager?.updateViewLayout(this@FloatingControls.floatingPanel, this@FloatingControls.floatWindowLayoutParam)
                }
                view.performClick()
                return false
            }
        })
    }

}
