package com.yepgoryo.CaptureCap

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioManager
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.IconCompat
import rikka.shizuku.Shizuku

import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

import kotlin.math.sqrt

import kotlinx.coroutines.*

class ScreenRecorder : Service() {

    companion object {
        const val TAG: String = "ScreenRecorder"

        const val ELEVATED_PROCESS_SUFFIX: String = "ElevatedRecordService"
        const val ACTION_START: String = MainActivity.appName + ".START_RECORDING"
        const val ACTION_START_NOVIDEO: String = MainActivity.appName + ".START_RECORDING_NOVIDEO"
        const val ACTION_PAUSE: String = MainActivity.appName + ".PAUSE_RECORDING"
        const val ACTION_ENABLE_MIC: String = MainActivity.appName + ".ENABLE_MIC"
        const val ACTION_DISABLE_MIC: String = MainActivity.appName + ".DISABLE_MIC"
        const val ACTION_ENABLE_AUDIO: String = MainActivity.appName + ".ENABLE_AUDIO"
        const val ACTION_DISABLE_AUDIO: String = MainActivity.appName + ".DISABLE_AUDIO"
        const val ACTION_ENABLE_SHIZUKU_PHONE_CALL: String = MainActivity.appName + ".ENABLE_SHIZUKU_PHONE_CAL"
        const val ACTION_DISABLE_SHIZUKU_PHONE_CALL: String = MainActivity.appName + ".DISABLE_SHIZUKU_PHONE_CALL"

        const val ACTION_CONTINUE: String = MainActivity.appName + ".CONTINUE_RECORDING"
        const val ACTION_STOP: String = MainActivity.appName + ".STOP_RECORDING"
        const val ACTION_ACTIVITY_DELETE_FINISHED_FILE: String =
            MainActivity.appName + ".ACTIVITY_DELETE_FINISHED_FILE"

        const val ACTION_START_SHIZUKU: String = MainActivity.appName + ".START_SHIZUKU"
        const val ACTION_STOP_SHIZUKU: String = MainActivity.appName + ".STOP_SHIZUKU"
        const val ACTION_CONNECT_SHIZUKU: String = MainActivity.appName + ".CONNECT_SHIZUKU"
        const val ACTION_DISCONNECT_SHIZUKU: String = MainActivity.appName + ".DISCONNECT_SHIZUKU"
    }

    private val BPP: Float = 0.25f
    private var screenDensity: Float = 0.0f
    private var appSettings: GlobalProperties? = null
    private var customChannelsCount: Int = 0
    private var customSampleRate: Int = 0
    private var intentData: Intent? = null
    private var intentResult: Int = 0
    private var display: Display? = null
    private var finishedDocumentMime: String = ""
    private var finishedFile: File? = null
    private var finishedFileDocument: Uri? = null
    private var finishedFullFileDocument: Uri? = null
    private var intentFlag: Int = 0
    private var orientationOnStart: Int = 0
    private var recordFile: File? = null
    private var recordFileFullPath: Uri? = null
    private var recordFileMime: String = ""
    private var recordFilePath: Uri? = null
    private var recordFilePathParent: Uri? = null
    private var recorderPlayback: PlaybackRecorder? = null
    private var recordingFileDescriptor: FileDescriptor? = null
    private var recordingMediaProjection: MediaProjection? = null
    private var recordingMediaRecorder: MediaRecorder? = null
    private var recordingNotificationManager: NotificationManagerCompat? = null
    private var recordingOpenFileDescriptor: ParcelFileDescriptor? = null
    private var recordingVirtualDisplay: VirtualDisplay? = null
    private var screenHeightNormal: Int = 0
    private var screenWidthNormal: Int = 0
    private var sensor: SensorManager? = null
    private val NOTIFICATIONS_RECORDING_CHANNEL: String = "notifications"
    var runningService: Boolean = false
    private var recordingBinder: IBinder = RecordingBinder()
    private var recordingTileBinder: IBinder = RecordingTileBinder()
    private var cropperBinder: IBinder = VideoCropperBinder()
    private var settingsPanelBinder: IBinder = SettingsPanelBinder()
    private var shakeAcceleration: Float = 10.0f
    private var currentShakeAcceleration: Float = 9.80665f
    private var lastShakeAcceleration: Float = 9.80665f
    private var timeStart: Long = 0
    private var timeRecorded: Long = 0
    private var timerValue: Long = 0
    private var timerEndsAt: Long = 0
    private var timerEndsAtRealtime: Long = 0
    private var timerRunning: Boolean = false
    private var startFromPanel: Boolean = false
    private var startedFromPanel: Boolean = false
    private var useTimer: Boolean = false
    private var timerStartRecording: Timer = Timer()
    private var recordMicrophone: Boolean = false
    private var recordPlayback: Boolean = false
    private var enableSoundControlsNotification: Boolean = false
    private var forceOrientation: GlobalProperties.ScreenOrientationProperty = GlobalProperties.ScreenOrientationProperty.DEFAULT
    private var forceRotation: GlobalProperties.ScreenRotationProperty = GlobalProperties.ScreenRotationProperty.DEFAULT
    private var isPaused: Boolean = false
    private var isStopped: Boolean = false
    private var showFloatingControls: Boolean = false
    private var recordOnlyAudio: Boolean = false
    private var isActive: Boolean = false
    private var isRestarting: Boolean = false
    private var ignoreRotate: Boolean = false
    private var useRotateHardwareSensor: Boolean = false
    private var lastRotation: Int = Surface.ROTATION_0
    private var forcingOrientationAllowed: Boolean = false
    private var dontNotifyOnFinish: Boolean = false
    private var dontNotifyOnRotate: Boolean = false
    private var mediaAudioSource: Boolean = true
    private var gameAudioSource: Boolean = false
    private var unknownAudioSource: Boolean = false
    private var minimizeOnStart: Boolean = false
    private var enableStream: Boolean = false
    private var streamURL: String = ""
    private var streamKey: String = ""
    private var streamSave: Boolean = false

    private var errorDir: Boolean = false
    private var drawOverlay: Boolean = false
    private var hasCamera: Boolean = false
    private var finishedFileIntent: Intent? = null
    private var shareFinishedFileIntent: Intent? = null
    private var activityBinder: MainActivity.ActivityBinder? = null
    private var tileBinder: QuickTile.TileBinder? = null
    private var panelBinder: FloatingControls.PanelBinder? = null
    private var shizukuRecordService: IShizukuRecordService? = null
    private var useShizuku: Boolean = false
    private var useShizukuPhoneCallRecording: Boolean = false
    private var shizukuPhoneCallAudioSource: GlobalProperties.ShizukuPhoneCallAudioSource? = null
    private var shizukuAutoManage: Boolean = false
    private var shizukuServerAuthKey: String = ""
    private var shizukuConnectionHelper: ShizukuConnectionHelper? = null

    private var sensorListener: SensorEventListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

        @Throws(
            IllegalStateException::class,
            Resources.NotFoundException::class,
            IOException::class,
            NumberFormatException::class
        )

        override fun onSensorChanged(sensorEvent: SensorEvent) {
            var onShake: GlobalProperties.OnShakeProperty =
                GlobalProperties(this@ScreenRecorder.baseContext).getOnShake()
            if (sensorEvent.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                if (!useRotateHardwareSensor && isActive && !recordOnlyAudio && !ignoreRotate && forcingOrientationAllowed) {
                    if (orientationOnStart != display!!.rotation && display!!.rotation != Surface.ROTATION_180) {
                        orientationOnStart = display!!.rotation
                        isActive = false
                        isRestarting = true

                        screenRecordingStop()

                        when (display!!.rotation) {
                            Surface.ROTATION_0 -> {
                                forceOrientation =
                                    GlobalProperties.ScreenOrientationProperty.FORCE_PORTRAIT
                            }

                            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                                forceOrientation =
                                    GlobalProperties.ScreenOrientationProperty.FORCE_LANDSCAPE
                            }

                            Surface.ROTATION_180 -> {}
                        }

                        screenRecordingStart()
                    }
                }
                if (this@ScreenRecorder.appSettings != null && this@ScreenRecorder.isActive && onShake != GlobalProperties.OnShakeProperty.DO_NOTHING) {
                    var sensorGx: Float = sensorEvent.values[0]
                    var sensorGy: Float = sensorEvent.values[1]
                    var sensorGz: Float = sensorEvent.values[2]
                    this@ScreenRecorder.lastShakeAcceleration =
                        this@ScreenRecorder.currentShakeAcceleration
                    this@ScreenRecorder.currentShakeAcceleration =
                        sqrt(((sensorGx * sensorGx) + (sensorGy * sensorGy) + (sensorGz * sensorGz)).toDouble()).toFloat()
                    this@ScreenRecorder.shakeAcceleration =
                        (this@ScreenRecorder.shakeAcceleration * 0.9f) + (this@ScreenRecorder.currentShakeAcceleration - this@ScreenRecorder.lastShakeAcceleration)
                    if (this@ScreenRecorder.shakeAcceleration > 12.0f && this@ScreenRecorder.runningService) {
                        when (onShake) {
                            GlobalProperties.OnShakeProperty.PAUSE -> this@ScreenRecorder.screenRecordingPause()
                            GlobalProperties.OnShakeProperty.STOP -> this@ScreenRecorder.screenRecordingStop()
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private var mPanelConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            this@ScreenRecorder.panelBinder = iBinder as FloatingControls.PanelBinder
            this@ScreenRecorder.panelBinder!!.setConnectPanel(RecordingPanelBinder())
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            this@ScreenRecorder.panelBinder!!.setDisconnectPanel()
            this@ScreenRecorder.panelBinder = null
        }
    }

    private fun shizukuServiceArgs(): Shizuku.UserServiceArgs {
        return Shizuku.UserServiceArgs(ComponentName(packageName, ShizukuRecordService::class.java.name))
            .daemon(false)
            .processNameSuffix(ELEVATED_PROCESS_SUFFIX)
            .debuggable(BuildConfig.DEBUG)
            .version(
                packageManager.getPackageInfo(
                    packageName,
                    0
                ).longVersionCode.toInt()
            )
    }

    private var mShizukuRecordServiceConnection: ServiceConnection? = null

    @RequiresApi(Build.VERSION_CODES.R)
    fun shizukuManageStart() {
        shizukuServerAuthKey = this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.SHIZUKU_AUTH_KEY, "")

        if (!shizukuServerAuthKey.isBlank() && !ShizukuConnectionHelper.shizukuAvailable()) {
            ShizukuConnectionHelper.startShizuku(this@ScreenRecorder, shizukuServerAuthKey)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun shizukuManageStop() {
        shizukuServerAuthKey = this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.SHIZUKU_AUTH_KEY, "")

        if (!shizukuServerAuthKey.isBlank() && !ShizukuConnectionHelper.shizukuAvailable()) {
            ShizukuConnectionHelper.stopShizuku(this@ScreenRecorder, shizukuServerAuthKey)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun shizukuConnect() {
        useShizuku = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.SHIZUKU_ENABLE, false)

        if (!useShizuku) {
            return
        }

        if (mShizukuRecordServiceConnection != null && shizukuRecordService != null) {
            Log.e(TAG, "Shizuku connection already established")
            return
        }

        mShizukuRecordServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
                if (binder != null) {
                    Log.d(TAG, "RecordService connected successfully")
                    shizukuRecordService = IShizukuRecordService.Stub.asInterface(binder)
                } else {
                    Log.e(TAG, "RecordService connected with a null binder")
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.d(TAG, "RecordService disconnected")
                try {
                    if (ShizukuConnectionHelper.shizukuAvailable()) {
                        Shizuku.unbindUserService(shizukuServiceArgs(), this, false)
                        Log.d(TAG, "RecordService was unbound")
                    } else {
                        Log.d(TAG, "RecordService was already killed by the Shizuku server")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error occurred while trying to unbind RecordService", e)
                }

                shizukuRecordService = null
                throw IllegalStateException("Shizuku service disconnected")
            }
        }

        if (ShizukuConnectionHelper.hasShizukuPermission(this)) {
            try {
                Log.d(TAG, "Binding RecordService")

                if (mShizukuRecordServiceConnection != null) {
                    Shizuku.bindUserService(shizukuServiceArgs(), mShizukuRecordServiceConnection!!)
                }
            } catch (_: Exception) {
                Log.e(TAG, "Failed to bind RecorderService")
            }
        } else {
            Toast.makeText(this, R.string.error_shizuku_required, Toast.LENGTH_LONG).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun shizukuDisconnect() {
        if (mShizukuRecordServiceConnection != null) {
            try {
                if (ShizukuConnectionHelper.shizukuAvailable()) {
                    Shizuku.unbindUserService(shizukuServiceArgs(), mShizukuRecordServiceConnection, false)
                    Log.d(TAG, "RecordService was unbound")
                } else {
                    Log.d(TAG, "RecordService was already killed by the Shizuku Server")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error occurred while trying to unbind RecordService", e)
            }

            mShizukuRecordServiceConnection = null
        }
    }

    private enum class NotificationID {
        __,
        NOTIFICATION_RECORDING_ID,
        NOTIFICATION_RECORDING_FINISHED_ID,
        NOTIFICATION_RECORDING_AUDIO_CONTROLS_ID,
        NOTIFICATION_RECORDING_ROTATED_ID,
    }

    inner class RecordingBinder : Binder() {
        fun isStarted(): Boolean {
            return this@ScreenRecorder.runningService
        }

        fun isTimerRunning(): Boolean {
            return this@ScreenRecorder.timerRunning
        }

        fun startFromPreRecord() {
            actionStart()
        }

        fun abortPreRecord() {
            this@ScreenRecorder.abortPreRecord()
        }

        fun abortTimer() {
            this@ScreenRecorder.abortTimer()
        }

        fun recordingPause() {
            this@ScreenRecorder.screenRecordingPause()
        }

        fun stopService() {
            this@ScreenRecorder.screenRecordingStop()
        }

        fun recordingResume() {
            this@ScreenRecorder.screenRecordingResume()
        }

        fun recordingShare() {
            this@ScreenRecorder.screenRecordingShare()
        }

        fun recordingDelete() {
            this@ScreenRecorder.screenRecordingDelete()
        }

        fun recordingOpen() {
            this@ScreenRecorder.screenRecordingOpen()
        }

        fun recordingReset() {
            this@ScreenRecorder.screenRecordingReset()
        }

        fun muteMic() {
            this@ScreenRecorder.muteMic()
        }

        fun unmuteMic() {
            this@ScreenRecorder.unmuteMic()
        }

        fun muteAudio() {
            this@ScreenRecorder.muteAudio()
        }

        fun unmuteAudio() {
            this@ScreenRecorder.unmuteAudio()
        }

        fun muteShizukuPhoneCall() {
            this@ScreenRecorder.muteShizukuPhoneCall()
        }

        fun unmuteShizukuPhoneCall() {
            this@ScreenRecorder.unmuteShizukuPhoneCall()
        }

        fun micMuted(): Boolean {
            return this@ScreenRecorder.micMuted()
        }

        fun audioMuted(): Boolean {
            return this@ScreenRecorder.audioMuted()
        }

        fun shizukuPhoneCallMuted(): Boolean {
            return this@ScreenRecorder.shizukuPhoneCallMuted()
        }

        fun recordMic(): Boolean {
            return this@ScreenRecorder.recordMicrophone
        }

        fun recordAudio(): Boolean {
            return this@ScreenRecorder.recordPlayback
        }

        fun recordShizukuPhoneCall(): Boolean {
            return this@ScreenRecorder.useShizukuPhoneCallRecording
        }

        fun getTimeStart(): Long {
            return this@ScreenRecorder.timeStart
        }

        fun getTimeRecorded(): Long {
            return this@ScreenRecorder.timeRecorded
        }

        fun setConnect(activityBinder: MainActivity.ActivityBinder) {
            this@ScreenRecorder.actionConnect(activityBinder)
        }

        fun setDisconnect() {
            this@ScreenRecorder.actionDisconnect()
        }

        fun setPreStart(resultCode: Int, intent: Intent) {
            this@ScreenRecorder.intentResult = resultCode
            this@ScreenRecorder.intentData = intent
        }

        fun setAudioVolume(vol: Int) {
            if (this@ScreenRecorder.panelBinder != null) {
                this@ScreenRecorder.panelBinder!!.setAudioVolume(vol)
            }
        }

        fun setMicVolume(vol: Int) {
            if (this@ScreenRecorder.panelBinder != null) {
                this@ScreenRecorder.panelBinder!!.setMicVolume(vol)
            }
        }

        fun setShizukuPhoneCallVolume(vol: Int) {
            if (this@ScreenRecorder.panelBinder != null) {
                this@ScreenRecorder.panelBinder!!.setShizukuPhoneCallVolume(vol)
            }
        }
    }

    inner class RecordingTileBinder : Binder() {
        fun setConnectTile(tileBinder: QuickTile.TileBinder) {
            this@ScreenRecorder.actionConnectTile(tileBinder)
        }

        fun setDisconnectTile() {
            this@ScreenRecorder.actionDisconnectTile()
        }

        fun isStarted(): Boolean {
            return this@ScreenRecorder.runningService
        }

        fun stopService() {
            this@ScreenRecorder.screenRecordingStop()
        }
    }

    inner class RecordingPanelBinder : Binder() {
        fun getTimeStart(): Long {
            return this@ScreenRecorder.timeStart
        }

        fun isStarted(): Boolean {
            return this@ScreenRecorder.runningService
        }

        fun registerListener() {
            this@ScreenRecorder.sensor!!.registerListener(
                this@ScreenRecorder.sensorListener,
                this@ScreenRecorder.sensor!!.getDefaultSensor(1),
                2
            )
        }

        fun recordingStart() {
            this@ScreenRecorder.screenRecordingStart()
        }

        fun recordingPause() {
            this@ScreenRecorder.screenRecordingPause()
        }

        fun recordingResume() {
            this@ScreenRecorder.screenRecordingResume()
        }

        fun stopService() {
            this@ScreenRecorder.screenRecordingStop()
        }

        fun abortPreRecord() {
            this@ScreenRecorder.abortPreRecord()
        }

        fun recordMic(): Boolean {
            if (this@ScreenRecorder.recorderPlayback == null) {
                return false
            }
            return this@ScreenRecorder.recordMicrophone
        }

        fun recordAudio(): Boolean {
            if (this@ScreenRecorder.recorderPlayback == null) {
                return false
            }
            return this@ScreenRecorder.recordPlayback
        }

        fun recordShizukuPhoneCall(): Boolean {
            if (this@ScreenRecorder.recorderPlayback == null) {
                return false
            }
            return this@ScreenRecorder.useShizukuPhoneCallRecording
        }

        fun muteMic() {
            this@ScreenRecorder.muteMic()
        }

        fun unmuteMic() {
            this@ScreenRecorder.unmuteMic()
        }

        fun muteAudio() {
            this@ScreenRecorder.muteAudio()
        }

        fun unmuteAudio() {
            this@ScreenRecorder.unmuteAudio()
        }

        fun muteShizukuPhoneCall() {
            this@ScreenRecorder.muteShizukuPhoneCall()
        }

        fun unmuteShizukuPhoneCall() {
            this@ScreenRecorder.unmuteShizukuPhoneCall()
        }

        fun micMuted(): Boolean {
            return this@ScreenRecorder.micMuted()
        }

        fun audioMuted(): Boolean {
            return this@ScreenRecorder.audioMuted()
        }

        fun shizukuPhoneCallMuted(): Boolean {
            return this@ScreenRecorder.shizukuPhoneCallMuted()
        }

        fun mainActivityUpdateSoundSwitchButtons() {
            this@ScreenRecorder.activityBinder?.updateSoundSwitchButtons()
        }
    }

    inner class VideoCropperBinder : Binder() {
        fun getFilePath(): Uri {
            return this@ScreenRecorder.finishedFileDocument!!
        }
    }

    inner class SettingsPanelBinder : Binder() {}

    private var stoppedOnError = false

    inner class RecordingFinishedCallback {
        fun onError(error: Exception) {
            if (!stoppedOnError) {
                Toast.makeText(baseContext, error.message, Toast.LENGTH_SHORT).show()
                stoppedOnError = true

                screenRecordingStop()
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        orientationSensor.close()
        scope.cancel()
        this.sensor?.unregisterListener(this.sensorListener)
        shizukuAutoManage = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.SHIZUKU_AUTO_MANAGE, false)
        shizukuServerAuthKey = this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.SHIZUKU_AUTH_KEY, "")
        useShizuku = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.SHIZUKU_ENABLE, false)
        if (useShizuku && shizukuAutoManage && !shizukuServerAuthKey.isEmpty()) {
            shizukuManageStop()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && intent.action == QuickTile.ACTION_CONNECT_TILE) {
            return this.recordingTileBinder
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && intent.action == RecordingCropScreen.ACTION_CONNECT_CROP) {
            return this.cropperBinder
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && intent.action == SettingsPanel.ACTION_SETTINGS_PANEL_CONNECT) {
            return this.settingsPanelBinder
        }
        return this.recordingBinder
    }

    private lateinit var orientationSensor: HardwareOrientationSensor
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        this.display = (baseContext.getSystemService("display") as DisplayManager).getDisplay(0)
        val sensorManager: SensorManager = applicationContext.getSystemService("sensor") as SensorManager
        this.sensor = sensorManager
        sensorManager.registerListener(this.sensorListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 2)

        orientationSensor = HardwareOrientationSensor(
            this,
            500L,
            10f
        )

        scope.launch {
            orientationSensor.orientationFlow.collect { event ->
                if (event != null) {
                    onHardwareOrientationChanged(event)
                }
            }
        }

        if (this.panelBinder == null) {
            val intent = Intent(this, FloatingControls::class.java)
            intent.setAction(FloatingControls.ACTION_RECORD_PANEL)
            bindService(intent, this.mPanelConnection, Context.BIND_AUTO_CREATE)
        }

        initShizukuConnection()
    }

    private fun initShizukuConnection() {
        var globalProperties = GlobalProperties(baseContext)
        this.appSettings = globalProperties

        useShizuku = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.SHIZUKU_ENABLE, false)
        useShizukuPhoneCallRecording = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.SHIZUKU_RECORD_PHONECALL, false)
        shizukuAutoManage = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.SHIZUKU_AUTO_MANAGE, false)
        shizukuServerAuthKey = this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.SHIZUKU_AUTH_KEY, "")

        if (useShizuku) {
            if (shizukuAutoManage) {
                shizukuManageStart()
                shizukuConnect()
            } else {
                shizukuConnect()
            }
        }
    }

    private fun onHardwareOrientationChanged(event: OrientationChangedEvent) {
        val orientation = event.orientation

        val timeSinceChangeMs = SystemClock.elapsedRealtime() - event.timestampMs
        if (isActive && useRotateHardwareSensor && !recordOnlyAudio && !ignoreRotate && forcingOrientationAllowed) {
            if (orientation != lastRotation && orientation != Surface.ROTATION_180 && timeSinceChangeMs <= 2000) {
                isActive = false
                isRestarting = true

                screenRecordingStop()

                when (orientation) {
                    Surface.ROTATION_0 -> {
                        forceOrientation =
                            GlobalProperties.ScreenOrientationProperty.FORCE_PORTRAIT
                    }

                    Surface.ROTATION_90, Surface.ROTATION_270 -> {
                        forceOrientation =
                            GlobalProperties.ScreenOrientationProperty.FORCE_LANDSCAPE
                    }

                    Surface.ROTATION_180 -> {}
                }

                screenRecordingStart()

                lastRotation = orientation
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            useShizuku = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.SHIZUKU_ENABLE, false)
            useShizukuPhoneCallRecording = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.SHIZUKU_RECORD_PHONECALL, false)
            if (intent.action == ACTION_START) {
                if (useShizuku && useShizukuPhoneCallRecording && !ShizukuConnectionHelper.shizukuAvailable()) {
                    initShizukuConnection()
                    Toast.makeText(this, R.string.shizuku_waiting, Toast.LENGTH_SHORT).show()
                } else {
                    if (!this@ScreenRecorder.timerRunning) {
                        this.recordOnlyAudio = false
                        actionStart()
                    }
                }
            } else if (intent.action == ACTION_START_NOVIDEO) {
                if (useShizuku && useShizukuPhoneCallRecording && !ShizukuConnectionHelper.shizukuAvailable()) {
                    initShizukuConnection()
                    Toast.makeText(this, R.string.shizuku_waiting, Toast.LENGTH_SHORT).show()
                } else {
                    if (!this@ScreenRecorder.timerRunning) {
                        this.recordOnlyAudio = true
                        actionStart()
                    }
                }
            } else if (intent.action == ACTION_STOP) {
                if (!this@ScreenRecorder.timerRunning && !this@ScreenRecorder.startFromPanel) {
                    screenRecordingStop()
                } else if (this@ScreenRecorder.startFromPanel) {
                    abortPreRecord()
                } else if (this@ScreenRecorder.timerRunning) {
                    abortTimer()
                }
            } else if (intent.action == ACTION_PAUSE) {
                screenRecordingPause()
            } else if (intent.action == ACTION_ENABLE_MIC) {
                unmuteMic()
                this.activityBinder?.updateSoundSwitchButtons()
            } else if (intent.action == ACTION_DISABLE_MIC) {
                muteMic()
                this.activityBinder?.updateSoundSwitchButtons()
            } else if (intent.action == ACTION_ENABLE_AUDIO) {
                unmuteAudio()
                this.activityBinder?.updateSoundSwitchButtons()
            } else if (intent.action == ACTION_DISABLE_AUDIO) {
                muteAudio()
                this.activityBinder?.updateSoundSwitchButtons()
            } else if (intent.action == ACTION_ENABLE_SHIZUKU_PHONE_CALL) {
                unmuteShizukuPhoneCall()
                this.activityBinder?.updateSoundSwitchButtons()
            } else if (intent.action == ACTION_DISABLE_SHIZUKU_PHONE_CALL) {
                muteShizukuPhoneCall()
                this.activityBinder?.updateSoundSwitchButtons()
            } else if (intent.action == ACTION_CONTINUE) {
                screenRecordingResume()
            } else if (intent.action == ACTION_ACTIVITY_DELETE_FINISHED_FILE) {
                screenRecordingDelete()
            } else if (intent.action == ACTION_CONNECT_SHIZUKU) {
                shizukuConnect()
            } else if (intent.action == ACTION_DISCONNECT_SHIZUKU) {
                shizukuDisconnect()
            } else if (intent.action == ACTION_START_SHIZUKU) {
                shizukuManageStart()
                shizukuConnect()
            } else if (intent.action == ACTION_STOP_SHIZUKU) {
                shizukuDisconnect()
                shizukuManageStop()
            }
        }
        return START_STICKY
    }

    fun refreshSoundControlsNotification() {
        val soundNotification = getSoundSwitchNotification()
        this.recordingNotificationManager!!.notify(NotificationID.NOTIFICATION_RECORDING_AUDIO_CONTROLS_ID.ordinal, soundNotification.build())
    }

    fun removeSoundControlsNotification() {
        this.recordingNotificationManager!!.cancel(NotificationID.NOTIFICATION_RECORDING_AUDIO_CONTROLS_ID.ordinal)
    }

    fun muteMic() {
        if (this.recorderPlayback != null) {
            if (this.panelBinder != null) {
                this.panelBinder!!.setMicMuted(true)
            }
            this.recorderPlayback?.setMicrophoneMuted(true)
            if (enableSoundControlsNotification && isActive) {
                refreshSoundControlsNotification()
            }
        }
    }

    fun unmuteMic() {
        if (this.recorderPlayback != null) {
            if (this.panelBinder != null) {
                this.panelBinder!!.setMicMuted(false)
            }
            this.recorderPlayback?.setMicrophoneMuted(false)
            if (enableSoundControlsNotification && isActive) {
                refreshSoundControlsNotification()
            }
        }
    }

    fun muteAudio() {
        if (this.recorderPlayback != null) {
            if (this.panelBinder != null) {
                this.panelBinder!!.setAudioMuted(true)
            }
            this.recorderPlayback?.setAudioMuted(true)
            if (enableSoundControlsNotification && isActive) {
                refreshSoundControlsNotification()
            }
        }
    }

    fun unmuteAudio() {
        if (this.recorderPlayback != null) {
            if (this.panelBinder != null) {
                this.panelBinder!!.setAudioMuted(false)
            }
            this.recorderPlayback?.setAudioMuted(false)
            if (enableSoundControlsNotification && isActive) {
                refreshSoundControlsNotification()
            }
        }
    }

    fun muteShizukuPhoneCall() {
        if (this.recorderPlayback != null) {
            if (this.panelBinder != null) {
                this.panelBinder!!.setShizukuPhoneCallMuted(true)
            }
            this.recorderPlayback?.setShizukuPhoneCallMuted(true)
            if (enableSoundControlsNotification && isActive) {
                refreshSoundControlsNotification()
            }
        }
    }

    fun unmuteShizukuPhoneCall() {
        if (this.recorderPlayback != null) {
            if (this.panelBinder != null) {
                this.panelBinder!!.setShizukuPhoneCallMuted(false)
            }
            this.recorderPlayback?.setShizukuPhoneCallMuted(false)
            if (enableSoundControlsNotification && isActive) {
                refreshSoundControlsNotification()
            }
        }
    }

    fun audioMuted(): Boolean {
        if (this.recorderPlayback == null) {
            return false
        }
        return this.recorderPlayback!!.audioMuted()
    }

    fun micMuted(): Boolean {
        if (this.recorderPlayback == null) {
            return false
        }
        return this.recorderPlayback!!.microphoneMuted()
    }

    fun shizukuPhoneCallMuted(): Boolean {
        if (this.recorderPlayback == null) {
            return false
        }
        return this.recorderPlayback!!.shizukuPhoneCallMuted()
    }

    fun actionStart() {
        val displayMetrics = DisplayMetrics()
        this.display!!.getRealMetrics(displayMetrics)
        this.orientationOnStart = this.display!!.rotation
        lastRotation = this.display!!.rotation
        screenDensity = displayMetrics.densityDpi.toFloat()
        if (this.orientationOnStart == Surface.ROTATION_270 || this.orientationOnStart == Surface.ROTATION_90) {
            this.screenWidthNormal = displayMetrics.heightPixels
            this.screenHeightNormal = displayMetrics.widthPixels
        } else {
            this.screenWidthNormal = displayMetrics.widthPixels
            this.screenHeightNormal = displayMetrics.heightPixels
        }
        var globalProperties = GlobalProperties(baseContext)
        this.appSettings = globalProperties
        this.dontNotifyOnFinish = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.DONT_NOTIFY_ON_FINISH, false)
        this.mediaAudioSource = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.AUD_SOURCE_MEDIA, false)
        this.gameAudioSource = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.AUD_SOURCE_GAME, false)
        this.unknownAudioSource = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.AUD_SOURCE_UNKNOWN, false)
        this.minimizeOnStart = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.MINIMIZE_ON_START, false)
        this.enableStream = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.CHECK_STREAM, false)
        this.streamURL = this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.STREAM_URL, "")
        this.streamKey = this.appSettings!!.getPrivateStringProperty(GlobalProperties.PropertiesString.STREAM_KEY, "")
        this.streamSave = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.STREAM_SAVE_TO_FILE, false)
        this.intentFlag = Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        this.startFromPanel = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.PRE_RECORDING, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.intentFlag = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.CUSTOM_SAMPLE_RATE, false)) {
            val stringProperty: String = this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.SAMPLE_RATE_VALUE, "44100")
            if (stringProperty.length < 10) {
                try {
                    this.customSampleRate = Integer.parseInt(stringProperty)
                } catch (exc: NumberFormatException) {
                    this.customSampleRate = 44100
                }
            }
        } else {
            this.customSampleRate = 44100
            val property: String = (getSystemService("audio") as AudioManager).getProperty("android.media.property.OUTPUT_SAMPLE_RATE")
            if (property != "" && property.length < 10) {
                try {
                    if (Integer.parseInt(property) > 44100 && Integer.parseInt(property) >= 48000) {
                        this.customSampleRate = 48000
                    }
                } catch (_: NumberFormatException) {
                    this.customSampleRate = 44100
                }
            }
        }
        if (this.appSettings!!.getAudioChannels() == GlobalProperties.AudioChannelsProperty.MONO) {
            this.customChannelsCount = 1
        } else {
            this.customChannelsCount = 2
        }
        this.recordingNotificationManager = NotificationManagerCompat.from(this)
        if (this.recordingNotificationManager!!.getNotificationChannel(NOTIFICATIONS_RECORDING_CHANNEL) == null) {
            val vibrate = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.ENABLE_VIBRATION, false)
            var importance = NotificationManager.IMPORTANCE_HIGH
            if (!vibrate) {
                importance = NotificationManager.IMPORTANCE_MIN
            }
            this.recordingNotificationManager!!.createNotificationChannel(NotificationChannelCompat.Builder(NOTIFICATIONS_RECORDING_CHANNEL, importance).setName(getString(R.string.notifications_channel)).setLightsEnabled(true).setLightColor(android.graphics.Color.RED).setShowBadge(true).setVibrationEnabled(vibrate).build())
        }
        this.runningService = true
        if (this.tileBinder != null) {
            this.tileBinder!!.recordingState(true)
        }
        this.useTimer = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.ENABLE_TIMER, false)
        if (this.useTimer && !this.startFromPanel) {
            timerStart()
        } else if (this.startFromPanel && !startedFromPanel) {
            startOnPanel()
            startedFromPanel = true
        } else {
            if (this.startFromPanel && startedFromPanel) {
                panelBinder?.setStop()
            }
            screenRecordingStart()
        }
    }

    fun actionConnect(activityBinder: MainActivity.ActivityBinder) {
        this.activityBinder = activityBinder
        if (this.runningService) {
            if (!this.isPaused) {
                if (!timerRunning && !startedFromPanel) {
                    activityBinder.recordingStart(false)
                } else if (startedFromPanel) {
                    activityBinder.preRecordingStart()
                } else {
                    activityBinder.timerStart(timerEndsAtRealtime)
                }
            } else {
                activityBinder.recordingPause(this.timeRecorded, false)
            }
        } else {
            if (this.isStopped) {
                activityBinder.recordingStop(false)
            }
        }
    }

    fun actionConnectTile(tileBinder: QuickTile.TileBinder) {
        this.tileBinder = tileBinder
    }

    fun actionDisconnect() {
        this.activityBinder = null
    }

    fun actionDisconnectTile() {
        this.tileBinder = null
    }

    fun recordingError() {
        if (!this.errorDir) {
            Toast.makeText(this, R.string.error_recorder_failed, Toast.LENGTH_SHORT).show()
        }
        screenRecordingStop()
    }

    fun getBitmapDescriptor(id: Int): Bitmap {
        var vectorDrawable = getDrawable(id)

        val h = vectorDrawable!!.getIntrinsicHeight()
        val w = vectorDrawable!!.getIntrinsicWidth()

        vectorDrawable!!.setBounds(0, 0, w, h)

        val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bm)
        vectorDrawable!!.draw(canvas)

        return bm
    }

    fun getModeNight(): Boolean {
        val uiModeManager: UiModeManager = baseContext.getSystemService(UI_MODE_SERVICE) as UiModeManager
        val mode = uiModeManager.getNightMode()
        if (mode == UiModeManager.MODE_NIGHT_YES) {
            return true
        }
        return false
    }

    private fun getScreenResolution(): Array<Int> {
        val dimensions: Array<Int> = arrayOf(0,0)
        if (((this.orientationOnStart == Surface.ROTATION_270 || this.orientationOnStart == Surface.ROTATION_90) && this.screenWidthNormal < this.screenHeightNormal) || (!(this.orientationOnStart == Surface.ROTATION_270 || this.orientationOnStart == Surface.ROTATION_90) && this.screenWidthNormal > this.screenHeightNormal)) {
            dimensions[0] = 1920
            dimensions[1] = 1080
            if (this.screenHeightNormal == 3840) {
                dimensions[0] = 3840
                dimensions[1] = 2160
            } else if (this.screenHeightNormal in 1920..<3840) {
                dimensions[0] = 1920
                dimensions[1] = 1080
            } else if (this.screenHeightNormal in 1280..<1920) {
                dimensions[0] = 1280
                dimensions[1] = 720
            } else if (this.screenHeightNormal in 720..<1280) {
                dimensions[0] = 720
                dimensions[1] = 480
            } else if (this.screenHeightNormal in 480..<720) {
                dimensions[0] = 480
                dimensions[1] = 360
            } else if (this.screenHeightNormal in 320..<480) {
                dimensions[0] = 360
                dimensions[1] = 240
            }
        } else if ((!(this.orientationOnStart == Surface.ROTATION_270 || this.orientationOnStart == Surface.ROTATION_90) && this.screenWidthNormal < this.screenHeightNormal) || ((this.orientationOnStart == Surface.ROTATION_270 || this.orientationOnStart == Surface.ROTATION_90) && this.screenWidthNormal > this.screenHeightNormal)) {
            dimensions[0] = 1080
            dimensions[1] = 1920
            if (this.screenWidthNormal == 3840) {
                dimensions[0] = 2160
                dimensions[1] = 3840
            } else if (this.screenWidthNormal in 1920..<3840) {
                dimensions[0] = 1080
                dimensions[1] = 1920
            } else if (this.screenWidthNormal in 1280..<1920) {
                dimensions[0] = 720
                dimensions[1] = 1280
            } else if (this.screenWidthNormal in 720..<1280) {
                dimensions[0] = 480
                dimensions[1] = 720
            } else if (this.screenWidthNormal in 480..<720) {
                dimensions[0] = 360
                dimensions[1] = 480
            } else if (this.screenWidthNormal in 320..<480) {
                dimensions[0] = 240
                dimensions[1] = 360
            }
        }
        return dimensions
    }

    private fun startFloatingControls() {
        if (this.showFloatingControls && !isRestarting) {
            val floatingControlsIntent = Intent(this, FloatingControls::class.java)
            floatingControlsIntent.setAction(FloatingControls.ACTION_RECORD_PANEL)
            startService(floatingControlsIntent)
        }
    }

    fun screenRecordingStart() {
        timerRunning = false
        startedFromPanel = false
        stoppedOnError = false

        drawOverlay = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.DRAW_OVERLAY, false)
        enableSoundControlsNotification = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.SOUND_CONTROL_NOTIFICATION, false)
        ignoreRotate = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.NO_ROTATE, false)
        useRotateHardwareSensor = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.ROTATE_HARDWARE_SENSOR, false)
        dontNotifyOnRotate = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.DONT_NOTIFY_ON_ROTATE, false)
        useShizuku = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.SHIZUKU_ENABLE, false)
        useShizukuPhoneCallRecording = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.SHIZUKU_RECORD_PHONECALL, false)
        shizukuPhoneCallAudioSource = this.appSettings!!.getShizukuPhoneCallSource()
        shizukuAutoManage = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.SHIZUKU_AUTO_MANAGE, false)
        shizukuServerAuthKey = this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.SHIZUKU_AUTH_KEY, "")

        if (useShizuku && useShizukuPhoneCallRecording) {
            if (!ShizukuConnectionHelper.shizukuAvailable()) {
                Toast.makeText(baseContext, R.string.shizuku_waiting, Toast.LENGTH_LONG).show()
                if (shizukuAutoManage && !shizukuServerAuthKey.isBlank()) {
                    shizukuManageStart()
                }
                shizukuConnect()
                screenRecordingStop()
                stopSelf()
                return
            }
        }
        val format: String = this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.FORMAT_VALUE, resources.getString(R.string.format_option_auto_value))
        val audioFormat: String = this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.AUDIO_FORMAT_VALUE, resources.getString(R.string.audio_format_option_auto_value))

        val useCustomFormat = (format != resources.getString(R.string.format_option_auto_value))
        val useCustomAudioFormat = (audioFormat != resources.getString(R.string.format_option_auto_value))

        var codec: String = resources.getString(R.string.codec_option_auto_value)
        var audioCodec: String = resources.getString(R.string.audio_codec_option_auto_value)

        if (useCustomFormat) {
            when (format) {
                MediaFormat.MIMETYPE_VIDEO_AVC -> {
                    codec = this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.AVC_CODEC, resources.getString(R.string.codec_option_auto_value))
                }
                MediaFormat.MIMETYPE_VIDEO_HEVC -> {
                    codec = this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.HEVC_CODEC, resources.getString(R.string.codec_option_auto_value))
                }
            }
        }

        if (useCustomAudioFormat) {
            when (audioFormat) {
                MediaFormat.MIMETYPE_AUDIO_AAC -> {
                    audioCodec = this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.AAC_CODEC, resources.getString(R.string.audio_codec_option_auto_value))
                }
                else -> {}
            }
        }

        val useCustomCodec = (codec != resources.getString(R.string.codec_option_auto_value))
        val useCustomAudioCodec = (audioCodec != resources.getString(R.string.audio_codec_option_auto_value))

        if (!isRestarting) {
            forceOrientation = this.appSettings!!.getScreenOrientation()
            forcingOrientationAllowed = (forceOrientation == GlobalProperties.ScreenOrientationProperty.DEFAULT)
        }
        forceRotation = this.appSettings!!.getScreenRotation()

        var horizontal = false

        if (this.display!!.rotation == Surface.ROTATION_270 || this.display!!.rotation == Surface.ROTATION_90) {
            horizontal = true
        }

        hasCamera = (VideoOverlay.getCameraItem(baseContext, horizontal) != null)

        this.isStopped = false
        if (this.minimizeOnStart) {
            val homeIntent = Intent(Intent.ACTION_MAIN)
            homeIntent.addCategory(Intent.CATEGORY_HOME)
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(homeIntent)
        }
        this.showFloatingControls = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.FLOATING_CONTROLS, false) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Settings.canDrawOverlays(this)
        this.recordMicrophone = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.CHECK_SOUND_MIC, false)
        this.recordPlayback = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.CHECK_SOUND_PLAYBACK, false)

        val recordingDate: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Calendar.getInstance().getTime())
        var recordingFileName: String = "ScreenRecording_$recordingDate"

        var folderPath: String = this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.FOLDER_PATH, "")
        if (this.recordOnlyAudio) {
            folderPath = this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.FOLDER_AUDIO_PATH, "")
        }

        var fileExtension: String = ".mp4"
        var fileMimeType: String = "video/mp4"
        if (this.recordOnlyAudio) {
            recordingFileName = "AudioRecording_$recordingDate"
            if (!audioFormat.contentEquals(resources.getString(R.string.audio_format_option_auto_value))) {
                when (audioFormat) {
                    MediaFormat.MIMETYPE_AUDIO_AAC -> {
                        fileMimeType = "audio/mp4"
                        fileExtension = ".m4a"
                    }
                    else -> {}
                }
            } else {
                fileMimeType = "audio/mp4"
                fileExtension = ".m4a"
            }
        } else {
            fileMimeType = "video/mp4"
            fileExtension = ".mp4"
        }

        var fullFilePath: Uri?
        val documentPath: String = Regex("^content://[^/]*/tree/").replaceFirst(folderPath, "")
        val documentParentPath: Uri = Uri.parse("$folderPath/document/$documentPath")
        if (!folderPath.matches(Regex("^content://com\\.android\\.externalstorage\\.documents/tree/.*"))) {
            fullFilePath = null
        } else if (documentPath.startsWith("primary%3A")) {
            fullFilePath = Uri.parse("/storage/emulated/0/" + Uri.decode(documentPath.replaceFirst("primary%3A", "")) + "/" + recordingFileName + fileExtension)
        } else {
            val storageFilePath: Uri = Uri.parse("/storage/" + Uri.decode(documentPath.replaceFirst("%3A", "/")) + "/" + recordingFileName + fileExtension)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || File(storageFilePath.toString()).isDirectory()) {
                fullFilePath = storageFilePath
            } else {
                fullFilePath = Uri.parse("/storage/sdcard" + Uri.decode(Regex(".*\\%3A").replaceFirst(documentPath, "/")) + "/" + recordingFileName + fileExtension)
            }
        }

        var file: File? = null
        var fullFilePathCreateDocument: Uri? = null
        var fullFilePathRenameDocument: Uri? = null
        if (!enableStream || (enableStream && streamSave)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    fullFilePathCreateDocument = DocumentsContract.createDocument(
                        contentResolver,
                        documentParentPath,
                        fileMimeType,
                        recordingFileName
                    )
                    if (!fullFilePathCreateDocument.toString()
                            .endsWith(fileExtension) && this.recordOnlyAudio) {
                        try {
                            fullFilePathRenameDocument = DocumentsContract.renameDocument(
                                contentResolver,
                                fullFilePathCreateDocument!!,
                                "$recordingFileName$fileExtension"
                            )
                        } catch (exc: Exception) {
                            fullFilePathRenameDocument = null
                        }
                        if (fullFilePathRenameDocument == null) {
                            fullFilePathRenameDocument =
                                Uri.parse("$fullFilePathCreateDocument$fileExtension")
                        }
                        fullFilePathCreateDocument = fullFilePathRenameDocument
                    }
                    file = null
                } catch (_: Exception) {
                    Log.e(TAG, "Invalid recording path: $documentParentPath")
                    if (activityBinder != null) {
                        this.errorDir = true
                        this.activityBinder?.resetDir(this.recordOnlyAudio)
                    }
                    recordingError()
                    stopSelf()
                    return
                }
            } else {
                try {
                    Log.v(TAG, "File path: " + fullFilePath.toString())
                    file = File(fullFilePath.toString())
                    file.createNewFile()
                    fullFilePathCreateDocument = null
                } catch (_: Exception) {
                    file = null
                    fullFilePathCreateDocument = null
                }
            }
        }
        if (((file == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) || (fullFilePathCreateDocument == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)) && (!enableStream || (enableStream && streamSave))) {
            recordingError()
            this.activityBinder?.resetDir(this.recordOnlyAudio)
            stopSelf()
        } else {
            this.recordFile = file
            this.recordFilePath = fullFilePathCreateDocument
            this.recordFileMime = fileMimeType
            this.recordFilePathParent = documentParentPath
            this.recordFileFullPath = fullFilePath
            this.timeStart = SystemClock.elapsedRealtime()

            val recordingStartedBuilder = getRecordingNotification()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var serviceStartFlag = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                if ((recordMicrophone || recordPlayback) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    serviceStartFlag =
                        serviceStartFlag or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                if (drawOverlay && hasCamera && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    serviceStartFlag =
                        serviceStartFlag or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                }
                startForeground(
                    NotificationID.NOTIFICATION_RECORDING_ID.ordinal,
                    recordingStartedBuilder.build(),
                    serviceStartFlag
                )
            } else {
                startForeground(
                    NotificationID.NOTIFICATION_RECORDING_ID.ordinal,
                    recordingStartedBuilder.build()
                )
            }

            if (enableSoundControlsNotification && (recordMicrophone || recordPlayback || (useShizukuPhoneCallRecording && useShizuku))) {
                refreshSoundControlsNotification()
            }

            if (this.activityBinder != null) {
                if (!isRestarting) {
                    this.activityBinder?.recordingStart(true)
                } else {
                    this.activityBinder?.recordingResume(this.timeStart)
                }
            }

            if (this.showFloatingControls && isRestarting) {
                this.panelBinder?.setResume(this.timeStart)
            }

            var width: Int
            var height: Int

            if (useRotateHardwareSensor) {
                if (this.lastRotation == Surface.ROTATION_270 || this.lastRotation == Surface.ROTATION_90) {
                    width = this.screenHeightNormal
                    height = this.screenWidthNormal
                } else {
                    width = this.screenWidthNormal
                    height = this.screenHeightNormal
                }
            } else {
                if (this.orientationOnStart == Surface.ROTATION_270 || this.orientationOnStart == Surface.ROTATION_90) {
                    width = this.screenHeightNormal
                    height = this.screenWidthNormal
                } else {
                    width = this.screenWidthNormal
                    height = this.screenHeightNormal
                }
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
                val screenResolution: Array<Int> = getScreenResolution()
                width = screenResolution[0]
                height = screenResolution[1]
            }

            val resolution: GlobalProperties.ResolutionProperty = this.appSettings!!.getResolution()
            var scaleRatio: Float

            if (resolution == GlobalProperties.ResolutionProperty.NATIVE) {
                scaleRatio = 1.0f
            } else {
                val screenHeight: Int = if (height > width) {
                    width
                } else {
                    height
                }
                var screenScale = 0.0f
                if (resolution == GlobalProperties.ResolutionProperty._2160P_ && screenHeight >= 2160) {
                    screenScale = 2160.0f
                } else if (resolution == GlobalProperties.ResolutionProperty._1080P_ && screenHeight >= 1080) {
                    screenScale = 1080.0f
                } else if (resolution == GlobalProperties.ResolutionProperty._720P_ && screenHeight >= 720) {
                    screenScale = 720.0f
                } else if (resolution == GlobalProperties.ResolutionProperty._480P_ && screenHeight >= 480) {
                    screenScale = 480.0f
                } else if (resolution == GlobalProperties.ResolutionProperty._360P_ && screenHeight >= 360) {
                    screenScale = 360.0f
                }
                scaleRatio = screenScale / screenHeight
            }

            val mediaProjectionManager: MediaProjectionManager =
                getSystemService(MediaProjectionManager::class.java)
            val callback: MediaProjection.Callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    if (this@ScreenRecorder.isActive && !this@ScreenRecorder.isRestarting) {
                        this@ScreenRecorder.recordingError()
                    }
                }
            }

            if (this.recordOnlyAudio && (!this.recordPlayback || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)) {
                this.recordingMediaProjection = null
            } else {
                if (!isRestarting) {
                    val mediaProjection: MediaProjection? =
                        mediaProjectionManager.getMediaProjection(
                            this.intentResult,
                            this.intentData!!
                        )
                    this.recordingMediaProjection = mediaProjection
                    mediaProjection!!.registerCallback(callback, null)
                }
            }

            if (!this.recordOnlyAudio && !isRestarting) {
                this.recordingVirtualDisplay = this.recordingMediaProjection!!.createVirtualDisplay(
                    "CaptureCap",
                    width,
                    height,
                    screenDensity.toInt(),
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    null,
                    null,
                    null
                )
            }

            isRestarting = false

            var refreshRate: Int = this.display!!.refreshRate.toInt()
            val customQuality: Boolean = this.appSettings!!.getBooleanProperty(
                GlobalProperties.PropertiesBoolean.CUSTOM_QUALITY,
                false
            )
            val qualityScale: Float = (this.appSettings!!.getIntProperty(
                GlobalProperties.PropertiesInt.QUALITY_SCALE,
                9
            ) + 1) * 0.1f
            val customFps: Boolean = this.appSettings!!.getBooleanProperty(
                GlobalProperties.PropertiesBoolean.CUSTOM_FPS,
                false
            )
            val fpsValue: Int = Integer.parseInt(
                this.appSettings!!.getStringProperty(
                    GlobalProperties.PropertiesString.FPS_VALUE,
                    "30"
                )
            )
            val customBitrate: Boolean = this.appSettings!!.getBooleanProperty(
                GlobalProperties.PropertiesBoolean.CUSTOM_BITRATE,
                false
            )
            val bitrateValue: Int = Integer.parseInt(
                this.appSettings!!.getStringProperty(
                    GlobalProperties.PropertiesString.BITRATE_VALUE,
                    "0"
                )
            )
            val customKeyframe: Boolean = this.appSettings!!.getBooleanProperty(
                GlobalProperties.PropertiesBoolean.CUSTOM_KEYFRAME,
                false
            )
            val keyframeValue: Int = Integer.parseInt(
                this.appSettings!!.getStringProperty(
                    GlobalProperties.PropertiesString.KEYFRAME_VALUE,
                    "1"
                )
            )
            val useCropArea: Boolean = this.appSettings!!.getBooleanProperty(
                GlobalProperties.PropertiesBoolean.CROP_SCREEN,
                false
            )
            val smoothCrop: Boolean = this.appSettings!!.getBooleanProperty(
                GlobalProperties.PropertiesBoolean.SMOOTH_CROP,
                false
            )

            var cropAreaWidth: Int = 0
            var cropAreaHeight: Int = 0
            var cropAreaX: Int = 0
            var cropAreaY: Int = 0

            if (horizontal) {
                cropAreaWidth = this.appSettings!!.getIntProperty(
                    GlobalProperties.PropertiesInt.CROP_AREA_WIDTH_HORIZONTAL,
                    width
                )
                cropAreaHeight = this.appSettings!!.getIntProperty(
                    GlobalProperties.PropertiesInt.CROP_AREA_HEIGHT_HORIZONTAL,
                    height
                )
                cropAreaX =
                    this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.CROP_AREA_X_HORIZONTAL, 0)
                cropAreaY =
                    this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.CROP_AREA_Y_HORIZONTAL, 0)
            } else {
                cropAreaWidth = this.appSettings!!.getIntProperty(
                    GlobalProperties.PropertiesInt.CROP_AREA_WIDTH_VERTICAL,
                    width
                )
                cropAreaHeight = this.appSettings!!.getIntProperty(
                    GlobalProperties.PropertiesInt.CROP_AREA_HEIGHT_VERTICAL,
                    height
                )
                cropAreaX =
                    this.appSettings!!.getIntProperty(
                        GlobalProperties.PropertiesInt.CROP_AREA_X_VERTICAL,
                        0
                    )
                cropAreaY =
                    this.appSettings!!.getIntProperty(
                        GlobalProperties.PropertiesInt.CROP_AREA_Y_VERTICAL,
                        0
                    )
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val mediaRecorder = MediaRecorder()
                this.recordingMediaRecorder = mediaRecorder
                mediaRecorder.setOnErrorListener { mediaRecorder, what, extra ->
                    this@ScreenRecorder.recordingError()
                }
                try {
                    if (this.recordMicrophone) {
                        this.recordingMediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
                        this.recordingMediaRecorder!!.setAudioEncodingBitRate(this.customSampleRate * 32 * 2)
                        this.recordingMediaRecorder!!.setAudioSamplingRate(this.customSampleRate)
                        this.recordingMediaRecorder!!.setAudioChannels(this.customChannelsCount)
                    }
                    if (!this.recordOnlyAudio) {
                        this.recordingMediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    }
                    this.recordingMediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    this.recordingMediaRecorder!!.setOutputFile(this.recordFileFullPath.toString())
                    if (!this.recordOnlyAudio) {
                        this.recordingMediaRecorder!!.setVideoSize((width * scaleRatio).toInt(), (height * scaleRatio).toInt())
                        this.recordingMediaRecorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    }
                    if (this.recordMicrophone) {
                        this.recordingMediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    }
                    if (!this.recordOnlyAudio) {
                        if (customFps) {
                            refreshRate = fpsValue
                        }
                        var bitrate: Int = (refreshRate * BPP * width * height).toInt()
                        if (customQuality) {
                            bitrate = (bitrate * qualityScale).toInt()
                        }
                        if (customBitrate) {
                            bitrate = bitrateValue
                        }
                        this.recordingMediaRecorder!!.setVideoEncodingBitRate(bitrate)
                        this.recordingMediaRecorder!!.setVideoFrameRate(refreshRate)
                    }
                    this.recordingMediaRecorder!!.prepare()
                } catch (_: IOException) {
                    recordingError()
                }
                try {
                    this.recordingMediaRecorder!!.start()
                } catch (_: IllegalStateException) {
                    recordingError()
                }
                if (!this.recordOnlyAudio) {
                    this.recordingVirtualDisplay!!.surface = this.recordingMediaRecorder!!.surface
                }
            } else {
                if (!enableStream || streamSave) {
                    try {
                        val parcelFileDescriptorOpenFileDescriptor: ParcelFileDescriptor =
                            contentResolver.openFileDescriptor(this.recordFilePath!!, "rw")!!
                        this.recordingOpenFileDescriptor = parcelFileDescriptorOpenFileDescriptor
                        this.recordingFileDescriptor =
                            parcelFileDescriptorOpenFileDescriptor.fileDescriptor
                    } catch (_: Exception) {
                        recordingError()
                    }
                }

                val callRecordingEnabled = (useShizuku && useShizukuPhoneCallRecording)

                val playbackRecorder = PlaybackRecorder(
                    applicationContext,
                    this.recordOnlyAudio,
                    this.recordingVirtualDisplay,
                    this.recordingFileDescriptor,
                    this.recordingMediaProjection,
                    this.enableStream,
                    this.streamURL,
                    this.streamKey,
                    this.streamSave,
                    width,
                    height,
                    forceOrientation,
                    forceRotation,
                    scaleRatio,
                    this.display!!.rotation,
                    refreshRate,
                    this.recordMicrophone,
                    this.recordPlayback,
                    callRecordingEnabled,
                    shizukuRecordService,
                    shizukuPhoneCallAudioSource!!,
                    drawOverlay,
                    customQuality,
                    qualityScale,
                    customFps,
                    fpsValue,
                    customBitrate,
                    bitrateValue,
                    customKeyframe,
                    keyframeValue,
                    useCustomFormat,
                    format,
                    useCustomCodec,
                    codec,
                    useCustomAudioFormat,
                    audioFormat,
                    useCustomAudioCodec,
                    audioCodec,
                    this.customSampleRate,
                    this.customChannelsCount,
                    useCropArea,
                    smoothCrop,
                    cropAreaWidth,
                    cropAreaHeight,
                    cropAreaX,
                    cropAreaY,
                    this.mediaAudioSource,
                    this.gameAudioSource,
                    this.unknownAudioSource
                )

                this.recorderPlayback = playbackRecorder
                this.recorderPlayback?.recordingCallback = RecordingFinishedCallback()
                playbackRecorder.start()
                startFloatingControls()
            }
            this.isActive = true
        }
    }

    private fun getRecordingNotification(): NotificationCompat.Builder {
        var iconStop: IconCompat = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_stop_color_action))
        if (getModeNight()) {
            iconStop = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_stop_color_action_dark))
        }
        val stopIntent: Intent = Intent(this, ScreenRecorder::class.java)
        stopIntent.setAction(ACTION_STOP)

        val notificationStopBuilder: NotificationCompat.Action.Builder = NotificationCompat.Action.Builder(iconStop, getString(R.string.notifications_stop), PendingIntent.getService(this, 0, stopIntent, this.intentFlag))
        var iconPause: IconCompat = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_pause_color_action))
        if (getModeNight()) {
            iconPause = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_pause_color_action_dark))
        }

        val pauseIntent: Intent = Intent(this, ScreenRecorder::class.java)
        pauseIntent.setAction(ACTION_PAUSE)
        val pauseAction: NotificationCompat.Action.Builder = NotificationCompat.Action.Builder(iconPause, getString(R.string.notifications_pause), PendingIntent.getService(this, 0, pauseIntent, this.intentFlag))

        var recordingStartedBuilder: NotificationCompat.Builder = NotificationCompat.Builder(this, NOTIFICATIONS_RECORDING_CHANNEL)
        if (this.recordOnlyAudio) {
            if (enableStream) {
                recordingStartedBuilder =
                    recordingStartedBuilder.setContentTitle(getString(R.string.streaming_audio_started_title))
                        .setContentText(getString(R.string.streaming_audio_started_text))
                        .setTicker(getString(R.string.streaming_audio_started_text))
            } else {
                recordingStartedBuilder =
                    recordingStartedBuilder.setContentTitle(getString(R.string.recording_audio_started_title))
                        .setContentText(getString(R.string.recording_audio_started_text))
                        .setTicker(getString(R.string.recording_audio_started_text))
            }
        } else {
            if (enableStream) {
                recordingStartedBuilder =
                    recordingStartedBuilder.setContentTitle(getString(R.string.streaming_started_title))
                        .setContentText(getString(R.string.streaming_started_text))
                        .setTicker(getString(R.string.streaming_started_text))
            } else {
                recordingStartedBuilder =
                    recordingStartedBuilder.setContentTitle(getString(R.string.recording_started_title))
                        .setContentText(getString(R.string.recording_started_text))
                        .setTicker(getString(R.string.recording_started_text))
            }
        }
        var iconRecord = Icon.createWithBitmap(getBitmapDescriptor(R.drawable.icon_record_color_action_normal))
        if (getModeNight()) {
            iconRecord = Icon.createWithBitmap(getBitmapDescriptor(R.drawable.icon_record_color_action_normal_dark))
        }

        recordingStartedBuilder = recordingStartedBuilder
            .setSmallIcon(R.drawable.icon_record_status)
            .setLargeIcon(iconRecord)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis() - (SystemClock.elapsedRealtime() - this.timeStart))
            .setOngoing(true)
            .addAction(notificationStopBuilder.build())
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !enableStream) {
            recordingStartedBuilder.addAction(pauseAction.build())
        }

        return recordingStartedBuilder
    }

    private fun getScreenRotatedNotification(): NotificationCompat.Builder {
        var screenRotatedBuilder: NotificationCompat.Builder = NotificationCompat.Builder(this, NOTIFICATIONS_RECORDING_CHANNEL)

        screenRotatedBuilder =
            screenRotatedBuilder.setContentTitle(getString(R.string.recording_rotated_title))
                .setContentText(getString(R.string.recording_rotated_text))

        var iconRotated = Icon.createWithBitmap(getBitmapDescriptor(R.drawable.icon_notification_rotate))
        if (getModeNight()) {
            iconRotated = Icon.createWithBitmap(getBitmapDescriptor(R.drawable.icon_notification_rotate_dark))
        }

        screenRotatedBuilder = screenRotatedBuilder
            .setSmallIcon(R.drawable.icon_rotate_status)
            .setLargeIcon(iconRotated)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        return screenRotatedBuilder
    }

    private fun getSoundSwitchNotification(): NotificationCompat.Builder {
        var iconAudio: IconCompat = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_notification_audio))
        if (getModeNight()) {
            iconAudio = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_notification_audio_dark))
        }
        val mutedAudio = audioMuted()
        if (mutedAudio) {
            iconAudio = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_notification_audio_disabled))
            if (getModeNight()) {
                iconAudio = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_notification_audio_disabled_dark))
            }
        }

        val switchAudioIntent: Intent = Intent(this, ScreenRecorder::class.java)
        switchAudioIntent.setAction(ACTION_DISABLE_AUDIO)
        if (mutedAudio) {
            switchAudioIntent.setAction(ACTION_ENABLE_AUDIO)
        }
        var switchAudioActionName = getString(R.string.mute_audio)
        if (mutedAudio) {
            switchAudioActionName = getString(R.string.unmute_audio)
        }
        val switchAudioAction: NotificationCompat.Action.Builder = NotificationCompat.Action.Builder(iconAudio, switchAudioActionName, PendingIntent.getService(this, 0, switchAudioIntent, this.intentFlag))


        var iconMicrophone: IconCompat = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_notification_mic))
        if (getModeNight()) {
            iconMicrophone = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_notification_mic_dark))
        }
        val mutedMicrophone = micMuted()
        if (mutedMicrophone) {
            iconMicrophone = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_notification_mic_disabled))
            if (getModeNight()) {
                iconMicrophone = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_notification_mic_disabled_dark))
            }
        }
        val switchMicrophoneIntent: Intent = Intent(this, ScreenRecorder::class.java)
        switchMicrophoneIntent.setAction(ACTION_DISABLE_MIC)
        if (mutedMicrophone) {
            switchMicrophoneIntent.setAction(ACTION_ENABLE_MIC)
        }
        var switchMicrophoneActionName = getString(R.string.mute_microphone)
        if (mutedMicrophone) {
            switchMicrophoneActionName = getString(R.string.unmute_microphone)
        }
        val switchMicrophoneAction: NotificationCompat.Action.Builder = NotificationCompat.Action.Builder(iconMicrophone, switchMicrophoneActionName, PendingIntent.getService(this, 0, switchMicrophoneIntent, this.intentFlag))


        var iconShizukuPhoneCall: IconCompat = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_notification_phonecall))
        if (getModeNight()) {
            iconShizukuPhoneCall = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_notification_phonecall_dark))
        }
        val mutedShizukuPhoneCall = shizukuPhoneCallMuted()
        if (mutedShizukuPhoneCall) {
            iconShizukuPhoneCall = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_notification_phonecall_disabled))
            if (getModeNight()) {
                iconShizukuPhoneCall = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_notification_phonecall_disabled_dark))
            }
        }

        val switchShizukuPhoneCallIntent: Intent = Intent(this, ScreenRecorder::class.java)
        switchShizukuPhoneCallIntent.setAction(ACTION_DISABLE_SHIZUKU_PHONE_CALL)
        if (mutedShizukuPhoneCall) {
            switchShizukuPhoneCallIntent.setAction(ACTION_ENABLE_SHIZUKU_PHONE_CALL)
        }
        var switchShizukuPhoneCallActionName = getString(R.string.mute_phonecall)
        if (mutedShizukuPhoneCall) {
            switchShizukuPhoneCallActionName = getString(R.string.unmute_phonecall)
        }
        val switchShizukuPhoneCallAction: NotificationCompat.Action.Builder = NotificationCompat.Action.Builder(iconShizukuPhoneCall, switchShizukuPhoneCallActionName, PendingIntent.getService(this, 0, switchShizukuPhoneCallIntent, this.intentFlag))


        var recordingSoundControlBuilder: NotificationCompat.Builder = NotificationCompat.Builder(this, NOTIFICATIONS_RECORDING_CHANNEL)

        recordingSoundControlBuilder =
            recordingSoundControlBuilder.setContentTitle(getString(R.string.sound_control_title))
                .setContentText(getString(R.string.sound_control_description))

        var iconSound = Icon.createWithBitmap(getBitmapDescriptor(R.drawable.icon_record_sound_control))
        if (getModeNight()) {
            iconSound = Icon.createWithBitmap(getBitmapDescriptor(R.drawable.icon_record_sound_control_dark))
        }

        recordingSoundControlBuilder = recordingSoundControlBuilder
            .setSmallIcon(R.drawable.icon_record_sound_control_status)
            .setLargeIcon(iconSound)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (recordMicrophone) {
            recordingSoundControlBuilder = recordingSoundControlBuilder.addAction(switchMicrophoneAction.build())
        }

        if (recordPlayback) {
            recordingSoundControlBuilder = recordingSoundControlBuilder.addAction(switchAudioAction.build())
        }

        if (useShizukuPhoneCallRecording) {
            recordingSoundControlBuilder = recordingSoundControlBuilder.addAction(switchShizukuPhoneCallAction.build())
        }

        return recordingSoundControlBuilder
    }

    fun startOnPanel() {
        this.showFloatingControls = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.FLOATING_CONTROLS, false) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Settings.canDrawOverlays(this)
        if (this.showFloatingControls && !isRestarting) {
            val floatingControlsIntent = Intent(this, FloatingControls::class.java)
            floatingControlsIntent.setAction(FloatingControls.ACTION_PRERECORD_PANEL)
            startService(floatingControlsIntent)
            panelBinder?.setPreRecord()
        }

        val preRecordNotificationBuilder = getPreRecordNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceStartFlag = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if ((recordMicrophone || recordPlayback) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                serviceStartFlag = serviceStartFlag or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (drawOverlay && hasCamera && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                serviceStartFlag = serviceStartFlag or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            startForeground(NotificationID.NOTIFICATION_RECORDING_ID.ordinal, preRecordNotificationBuilder.build(), serviceStartFlag)
        } else {
            startForeground(NotificationID.NOTIFICATION_RECORDING_ID.ordinal, preRecordNotificationBuilder.build())
        }
        this.activityBinder?.preRecordingStart()
    }

    fun timerStart() {
        this.timerRunning = true
        this.timerValue = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.TIMER_SECONDS, 10).toLong() * 1000
        this.timerEndsAt = System.currentTimeMillis() + this.timerValue
        this.timerEndsAtRealtime = SystemClock.elapsedRealtime() + this.timerValue
        val timerNotificationBuilder = getTimerNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceStartFlag = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if ((recordMicrophone || recordPlayback) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                serviceStartFlag = serviceStartFlag or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (drawOverlay && hasCamera && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                serviceStartFlag = serviceStartFlag or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            startForeground(NotificationID.NOTIFICATION_RECORDING_ID.ordinal, timerNotificationBuilder.build(), serviceStartFlag)
        } else {
            startForeground(NotificationID.NOTIFICATION_RECORDING_ID.ordinal, timerNotificationBuilder.build())
        }

        timerStartRecording.purge()
        timerStartRecording = Timer()
        timerStartRecording.schedule(object : TimerTask() {
            override fun run() {
                this@ScreenRecorder.screenRecordingStart()
            }
        }, this.timerValue)

        activityBinder!!.timerStart(this.timerEndsAtRealtime)
    }

    fun abortPreRecord() {
        if (this.recordingNotificationManager != null) {
            this.recordingNotificationManager!!.cancel(NotificationID.NOTIFICATION_RECORDING_ID.ordinal)
        }
        stopForeground(true)
        this@ScreenRecorder.runningService = false
        this.activityBinder?.recordingReset()
        startedFromPanel = false
        panelBinder?.setStop()
    }

    fun abortTimer() {
        this@ScreenRecorder.timerStartRecording.cancel()
        if (this.recordingNotificationManager != null) {
            this.recordingNotificationManager!!.cancel(NotificationID.NOTIFICATION_RECORDING_ID.ordinal)
        }
        stopForeground(true)
        this@ScreenRecorder.timerRunning = false
        this@ScreenRecorder.runningService = false
        this.activityBinder?.recordingReset()
    }

    private fun getPreRecordNotification(): NotificationCompat.Builder {
        var startIcon: IconCompat = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_record_continue_color_action))
        if (getModeNight()) {
            startIcon = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_record_continue_color_action_dark))
        }
        val startIntent = Intent(this, ScreenRecorder::class.java)
        startIntent.setAction(ACTION_START)
        val resumeNotificationAction: NotificationCompat.Action.Builder = NotificationCompat.Action.Builder(startIcon, getString(R.string.capture_start), PendingIntent.getService(this, 0, startIntent, this.intentFlag))


        var iconStop: IconCompat = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_stop_color_action))
        if (getModeNight()) {
            iconStop = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_stop_color_action_dark))
        }
        val stopIntent: Intent = Intent(this, ScreenRecorder::class.java)
        stopIntent.setAction(ACTION_STOP)

        val notificationStopBuilder: NotificationCompat.Action.Builder = NotificationCompat.Action.Builder(iconStop, getString(R.string.capture_abort), PendingIntent.getService(this, 0, stopIntent, this.intentFlag))

        var preRecordingBuilder: NotificationCompat.Builder = NotificationCompat.Builder(this, NOTIFICATIONS_RECORDING_CHANNEL)
        preRecordingBuilder =
            preRecordingBuilder.setContentTitle(getString(R.string.capture_prepared_title))
                .setContentText(getString(R.string.capture_prepared_description))

        var iconPreRecord = Icon.createWithBitmap(getBitmapDescriptor(R.drawable.icon_record_continue_color_action))
        if (getModeNight()) {
            iconPreRecord = Icon.createWithBitmap(getBitmapDescriptor(R.drawable.icon_record_continue_color_action_dark))
        }

        preRecordingBuilder = preRecordingBuilder
            .setSmallIcon(R.drawable.icon_record_status)
            .setLargeIcon(iconPreRecord)
            .setOngoing(true)
            .addAction(resumeNotificationAction.build())
            .addAction(notificationStopBuilder.build())
            .setPriority(NotificationCompat.PRIORITY_LOW)

        return preRecordingBuilder
    }

    private fun getTimerNotification(): NotificationCompat.Builder {
        var iconStop: IconCompat = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_stop_color_action))
        if (getModeNight()) {
            iconStop = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_stop_color_action_dark))
        }
        val stopIntent: Intent = Intent(this, ScreenRecorder::class.java)
        stopIntent.setAction(ACTION_STOP)

        val notificationStopBuilder: NotificationCompat.Action.Builder = NotificationCompat.Action.Builder(iconStop, getString(R.string.timer_abort), PendingIntent.getService(this, 0, stopIntent, this.intentFlag))

        var timerStartedBuilder: NotificationCompat.Builder = NotificationCompat.Builder(this, NOTIFICATIONS_RECORDING_CHANNEL)
        timerStartedBuilder =
            timerStartedBuilder.setContentTitle(getString(R.string.timer_wait_title))
                .setContentText(getString(R.string.timer_wait_description))
                .setTicker(getString(R.string.timer_wait_description))

        var iconTimer = Icon.createWithBitmap(getBitmapDescriptor(R.drawable.icon_timer_color_action_normal))
        if (getModeNight()) {
            iconTimer = Icon.createWithBitmap(getBitmapDescriptor(R.drawable.icon_timer_color_action_dark))
        }

        timerStartedBuilder = timerStartedBuilder
            .setSmallIcon(R.drawable.icon_timer_status)
            .setLargeIcon(iconTimer)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(this.timerEndsAt)
            .setOngoing(true)
            .addAction(notificationStopBuilder.build())
            .setPriority(NotificationCompat.PRIORITY_LOW)

        return timerStartedBuilder
    }

    fun screenRecordingStop()  {
        this.isActive = false
        this.timeStart = 0L
        this.timeRecorded = 0L
        this.isPaused = false
        this.isStopped = true
        if (!isRestarting) {
            this.runningService = false
            tileBinder?.recordingState(false)
        }

        if (!this.errorDir && this.activityBinder != null) {
            if (!isRestarting) {
                this.activityBinder!!.recordingStop(true)
            }
        }
        if (this.panelBinder != null && this.showFloatingControls && !isRestarting) {
            this.panelBinder?.setStop()
        }

        if (!this.recordOnlyAudio && !isRestarting) {
            this.recordingVirtualDisplay!!.release()
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (this.recordingMediaRecorder != null) {
                try {
                    this.recordingMediaRecorder?.stop()
                    this.recordingMediaRecorder?.reset()
                    this.recordingMediaRecorder?.release()
                } catch (_: RuntimeException) {
                    Toast.makeText(this, R.string.error_recorder_failed, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            if (this.recorderPlayback != null) {
                this.recorderPlayback?.quit()
                if (!isRestarting) {
                    this.recordingVirtualDisplay?.release()
                }
                if (!enableStream || (enableStream && streamSave)) {
                    try {
                        this.recordingOpenFileDescriptor!!.close()
                    } catch (_: IOException) {
                        Toast.makeText(this, R.string.error_recorder_failed, Toast.LENGTH_SHORT)
                            .show()
                    }
                }
                this.recorderPlayback = null
            }
        }
        this.finishedFile = this.recordFile
        this.finishedFileDocument = this.recordFilePath
        this.finishedFullFileDocument = this.recordFileFullPath
        this.finishedDocumentMime = this.recordFileMime
        this.finishedFileIntent = Intent(Intent.ACTION_VIEW)
        this.finishedFileIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        this.finishedFileIntent!!.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && this.finishedFile != null) {
            this.finishedFileIntent!!.setDataAndType(FileProvider.getUriForFile(applicationContext, MainActivity.appName + ".DocProvider", this.finishedFile), this.finishedDocumentMime)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && this.finishedFileDocument != null) {
            this.finishedFileIntent!!.setDataAndType(this.finishedFileDocument, this.finishedDocumentMime)
        }
        if (!isRestarting) {
            if (enableSoundControlsNotification) {
                removeSoundControlsNotification()
            }
            if (this.recordingNotificationManager != null) {
                this.recordingNotificationManager!!.cancel(NotificationID.NOTIFICATION_RECORDING_ROTATED_ID.ordinal)
            }
        }
        val activity: PendingIntent = PendingIntent.getActivity(this, 0, this.finishedFileIntent, this.intentFlag)
        val recordingDeleteIntent = Intent(this, ScreenRecorder::class.java)
        recordingDeleteIntent.setAction(ACTION_ACTIVITY_DELETE_FINISHED_FILE)
        var iconRecordDelete: IconCompat = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_record_delete_color_action))
        if (getModeNight()) {
            iconRecordDelete = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_record_delete_color_action_dark))
        }
        val notificationDeleteBuilder: NotificationCompat.Action.Builder = NotificationCompat.Action.Builder(iconRecordDelete, getString(R.string.notifications_delete), PendingIntent.getService(this, 0, recordingDeleteIntent, this.intentFlag))
        var iconRecordShare: IconCompat = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_record_share_color_action))
        if (getModeNight()) {
            iconRecordShare = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_record_share_color_action_dark))
        }
        this.shareFinishedFileIntent = Intent(Intent.ACTION_SEND)
        if (this.finishedFullFileDocument != null) {
            this.shareFinishedFileIntent!!.setType(this.finishedDocumentMime)
            this.shareFinishedFileIntent!!.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            this.shareFinishedFileIntent!!.putExtra(Intent.EXTRA_STREAM, this.recordFilePath)
            this.shareFinishedFileIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val notificationShareBuilder: NotificationCompat.Action.Builder = NotificationCompat.Action.Builder(iconRecordShare, getString(R.string.notifications_share), PendingIntent.getActivity(this, 0, this.shareFinishedFileIntent, this.intentFlag))
        if (this.recordFileFullPath != null) {
            MediaScannerConnection.scanFile(this, arrayOf(this.recordFileFullPath.toString()), null, null)
        }
        var finishedRecordingBuilder: NotificationCompat.Builder = NotificationCompat.Builder(this, NOTIFICATIONS_RECORDING_CHANNEL)
        if (this.recordOnlyAudio) {
            if (enableStream) {
                if (streamSave) {
                    finishedRecordingBuilder =
                        finishedRecordingBuilder.setContentTitle(getString(R.string.streaming_audio_finished_saved_title))
                            .setContentText(getString(R.string.streaming_audio_finished_saved_text))
                } else {
                    finishedRecordingBuilder =
                        finishedRecordingBuilder.setContentTitle(getString(R.string.streaming_audio_finished_title))
                            .setContentText(getString(R.string.streaming_audio_finished_text))
                }
            } else {
                finishedRecordingBuilder =
                    finishedRecordingBuilder.setContentTitle(getString(R.string.recording_audio_finished_title))
                        .setContentText(getString(R.string.recording_audio_finished_text))
            }
        } else {
            if (enableStream) {
                if (streamSave) {
                    finishedRecordingBuilder =
                        finishedRecordingBuilder.setContentTitle(getString(R.string.streaming_finished_saved_title))
                            .setContentText(getString(R.string.streaming_finished_saved_text))
                } else {
                    finishedRecordingBuilder =
                        finishedRecordingBuilder.setContentTitle(getString(R.string.streaming_finished_title))
                            .setContentText(getString(R.string.streaming_finished_text))
                }
            } else {
                finishedRecordingBuilder =
                    finishedRecordingBuilder.setContentTitle(getString(R.string.recording_finished_title))
                        .setContentText(getString(R.string.recording_finished_text))
            }
        }
        var finishedIcon: Icon = Icon.createWithBitmap(getBitmapDescriptor(R.drawable.icon_record_finished_color_action_normal))
        if (getModeNight()) {
            finishedIcon = Icon.createWithBitmap(getBitmapDescriptor(R.drawable.icon_record_finished_color_action_normal_dark))
        }
        val notificationDelete: NotificationCompat.Builder = finishedRecordingBuilder.setContentIntent(activity).setSmallIcon(R.drawable.icon_record_finished_status).setLargeIcon(finishedIcon).addAction(notificationShareBuilder.build()).addAction(notificationDeleteBuilder.build()).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_LOW)
        val notificationStreamFinished: NotificationCompat.Builder = finishedRecordingBuilder.setContentIntent(activity).setSmallIcon(R.drawable.icon_record_finished_status).setLargeIcon(finishedIcon).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_LOW)
        if (!isRestarting) {
            if (!this.dontNotifyOnFinish && (!enableStream || streamSave)) {
                this.recordingNotificationManager!!.notify(
                    NotificationID.NOTIFICATION_RECORDING_FINISHED_ID.ordinal,
                    notificationDelete.build()
                )
            } else {
                if (enableStream) {
                    this.recordingNotificationManager!!.notify(
                        NotificationID.NOTIFICATION_RECORDING_FINISHED_ID.ordinal,
                        notificationStreamFinished.build()
                    )
                }
            }
        } else {
            if (!dontNotifyOnRotate) {
                val rotationNotification = getScreenRotatedNotification()
                this.recordingNotificationManager!!.notify(
                    NotificationID.NOTIFICATION_RECORDING_ROTATED_ID.ordinal,
                    rotationNotification.build()
                )
            }
        }
        if (!isRestarting) {
            if (this.recordingMediaProjection != null) {
                this.recordingMediaProjection?.stop()
                this.recordingMediaProjection = null
            }
            stopForeground(true)
        }
        this.errorDir = false
    }

    fun screenRecordingPause() {
        if (this.isPaused) {
            return
        }
        this.isPaused = true
        this.timeRecorded = this.timeRecorded + (SystemClock.elapsedRealtime() - this.timeStart)
        this.timeStart = 0L
        if (this.activityBinder != null) {
            this.activityBinder?.recordingPause(this.timeRecorded, true)
        }
        if (this.panelBinder != null && this.showFloatingControls) {
            this.panelBinder?.setPause(this.timeRecorded)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            this.recordingMediaRecorder!!.pause()
        } else {
            this.recorderPlayback!!.pause()
        }
        var stopIcon: IconCompat = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_stop_continue_color_action))
        if (getModeNight()) {
            stopIcon = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_stop_continue_color_action_dark))
        }
        val intent = Intent(this, ScreenRecorder::class.java)
        intent.setAction(ACTION_STOP)
        val stopNotificationAction: NotificationCompat.Action.Builder = NotificationCompat.Action.Builder(stopIcon, getString(R.string.notifications_stop), PendingIntent.getService(this, 0, intent, this.intentFlag))
        var continueIcon: IconCompat = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_record_continue_color_action))
        if (getModeNight()) {
            continueIcon = IconCompat.createWithBitmap(getBitmapDescriptor(R.drawable.icon_record_continue_color_action_dark))
        }
        val continueIntent = Intent(this, ScreenRecorder::class.java)
        continueIntent.setAction(ACTION_CONTINUE)
        val resumeNotificationAction: NotificationCompat.Action.Builder = NotificationCompat.Action.Builder(continueIcon, getString(R.string.notifications_resume), PendingIntent.getService(this, 0, continueIntent, this.intentFlag))
        var pauseNotificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(this, NOTIFICATIONS_RECORDING_CHANNEL)
        if (this.recordOnlyAudio) {
            pauseNotificationBuilder = pauseNotificationBuilder.setContentTitle(getString(R.string.recording_audio_paused_title)).setContentText(getString(R.string.recording_audio_paused_text))
        } else {
            pauseNotificationBuilder = pauseNotificationBuilder.setContentTitle(getString(R.string.recording_paused_title)).setContentText(getString(R.string.recording_paused_text))
        }
        var iconPause = Icon.createWithBitmap(getBitmapDescriptor(R.drawable.icon_pause_color_action_normal))
        if (getModeNight()) {
            iconPause = Icon.createWithBitmap(getBitmapDescriptor(R.drawable.icon_pause_color_action_normal_dark))
        }
        pauseNotificationBuilder = pauseNotificationBuilder.setSmallIcon(R.drawable.icon_pause_status).setLargeIcon(iconPause).setOngoing(true).addAction(stopNotificationAction.build()).addAction(resumeNotificationAction.build()).setPriority(NotificationCompat.PRIORITY_LOW)
        this.recordingNotificationManager!!.notify(NotificationID.NOTIFICATION_RECORDING_ID.ordinal, pauseNotificationBuilder.build())
    }

    fun screenRecordingResume() {
        this.isPaused = false
        this.timeStart = SystemClock.elapsedRealtime() - this.timeRecorded
        this.timeRecorded = 0L
        if (this.activityBinder != null) {
            this.activityBinder?.recordingResume(this.timeStart)
        }
        if (this.panelBinder != null && this.showFloatingControls) {
            this.panelBinder?.setResume(this.timeStart)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            this.recordingMediaRecorder?.resume()
        } else {
            this.recorderPlayback?.resume()
        }

        val recordingStartedBuilder = getRecordingNotification()
        this.recordingNotificationManager!!.notify(NotificationID.NOTIFICATION_RECORDING_ID.ordinal, recordingStartedBuilder.build())
    }

    fun screenRecordingShare() {
        startActivity(this.shareFinishedFileIntent)
    }

    fun screenRecordingDelete() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                this.finishedFile!!.delete()
            } else {
                DocumentsContract.deleteDocument(this.contentResolver, this.finishedFileDocument!!)
            }
        } catch (exc: Exception) {}

        if (this.recordingNotificationManager != null) {
            this.recordingNotificationManager!!.cancel(NotificationID.NOTIFICATION_RECORDING_FINISHED_ID.ordinal)
        }
        this.screenRecordingReset()
    }

    fun screenRecordingOpen() {
        startActivity(this.finishedFileIntent)
    }

    fun screenRecordingReset() {
        this.isStopped = false
        if (this.activityBinder != null) {
            this.activityBinder?.recordingReset()
        }
        stopSelf()
    }
}
