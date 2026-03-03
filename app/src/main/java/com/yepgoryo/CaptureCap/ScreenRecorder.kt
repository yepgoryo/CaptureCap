package com.yepgoryo.CaptureCap

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioManager
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
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.IconCompat

import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.sqrt

class ScreenRecorder : Service() {

    companion object {
        const val ACTION_START: String = MainActivity.appName + ".START_RECORDING"
        const val ACTION_START_NOVIDEO: String = MainActivity.appName + ".START_RECORDING_NOVIDEO"
        const val ACTION_START_BACKGROUND: String = MainActivity.appName + ".START_BACKGROUND_RECORDING"
        const val ACTION_PAUSE: String = MainActivity.appName + ".PAUSE_RECORDING"
        const val ACTION_CONTINUE: String = MainActivity.appName + ".CONTINUE_RECORDING"
        const val ACTION_STOP: String = MainActivity.appName + ".STOP_RECORDING"
        const val ACTION_ACTIVITY_CONNECT: String = MainActivity.appName + ".ACTIVITY_CONNECT"
        const val ACTION_ACTIVITY_DISCONNECT: String = MainActivity.appName + ".ACTIVITY_DISCONNECT"
        const val ACTION_ACTIVITY_DELETE_FINISHED_FILE: String = MainActivity.appName + ".ACTIVITY_DELETE_FINISHED_FILE"
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
    private var recordingFilePath: String = ""
    private var recordingMediaProjection: MediaProjection? = null
    private var recordingMediaRecorder: MediaRecorder? = null
    private var recordingNotificationManager: NotificationManagerCompat? = null
    private var recordingOpenFileDescriptor: ParcelFileDescriptor? = null
    private var recordingVirtualDisplay: VirtualDisplay? = null
    private var screenHeightNormal: Int = 0
    private var screenWidthNormal: Int = 0
    private var screenWindowHeight: Int = 0
    private var screenWindowWidth: Int = 0
    private var sensor: SensorManager? = null
    private var windowManager: WindowManager? = null
    private val NOTIFICATIONS_RECORDING_CHANNEL: String = "notifications"
    var runningService: Boolean = false
    private var recordingBinder: IBinder = RecordingBinder()
    private var recordingTileBinder: IBinder = RecordingTileBinder()
    private var shakeAcceleration: Float = 10.0f
    private var currentShakeAcceleration: Float = 9.80665f
    private var lastShakeAcceleration: Float = 9.80665f
    private var timeStart: Long = 0
    private var timeRecorded: Long = 0
    private var recordMicrophone: Boolean = false
    private var recordPlayback: Boolean = false
    private var isPaused: Boolean = false
    private var isStopped: Boolean = false
    private var showFloatingControls: Boolean = false
    private var recordOnlyAudio: Boolean = false
    private var isActive: Boolean = false
    private var dontNotifyOnFinish: Boolean = false
    private var mediaAudioSource: Boolean = true
    private var gameAudioSource: Boolean = false
    private var unknownAudioSource: Boolean = false
    private var minimizeOnStart: Boolean = false
    private var errorDir: Boolean = false
    private var finishedFileIntent: Intent? = null
    private var shareFinishedFileIntent: Intent? = null
    private var activityBinder: MainActivity.ActivityBinder? = null
    private var tileBinder: QuickTile.TileBinder? = null
    private var panelBinder: FloatingControls.PanelBinder? = null
    
    private var backgroundRecordingActive: Boolean = false
    private var partialWakeLock: android.os.PowerManager.WakeLock? = null
    private var overlayBlackView: android.view.View? = null

    private var sensorListener: SensorEventListener = object: SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

        @Throws(IllegalStateException::class, Resources.NotFoundException::class, IOException::class, NumberFormatException::class)
        override fun onSensorChanged(sensorEvent: SensorEvent)  {
            var onShake: GlobalProperties.OnShakeProperty = GlobalProperties(this@ScreenRecorder.baseContext).getOnShake()
            if (sensorEvent.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                if (this@ScreenRecorder.appSettings != null && this@ScreenRecorder.isActive && onShake != GlobalProperties.OnShakeProperty.DO_NOTHING) {
                    var sensorGx: Float = sensorEvent.values[0]
                    var sensorGy: Float = sensorEvent.values[1]
                    var sensorGz: Float = sensorEvent.values[2]
                    this@ScreenRecorder.lastShakeAcceleration = this@ScreenRecorder.currentShakeAcceleration
                    this@ScreenRecorder.currentShakeAcceleration = sqrt(((sensorGx * sensorGx) + (sensorGy * sensorGy) + (sensorGz * sensorGz)).toDouble()).toFloat()
                    this@ScreenRecorder.shakeAcceleration = (this@ScreenRecorder.shakeAcceleration * 0.9f) + (this@ScreenRecorder.currentShakeAcceleration - this@ScreenRecorder.lastShakeAcceleration)
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

    private var mPanelConnection: ServiceConnection = object: ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            this@ScreenRecorder.panelBinder = iBinder as FloatingControls.PanelBinder
            this@ScreenRecorder.panelBinder!!.setConnectPanel(RecordingPanelBinder())
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            this@ScreenRecorder.panelBinder!!.setDisconnectPanel()
            this@ScreenRecorder.panelBinder = null
        }
    }

    private enum class NotificationID {
        __,
        NOTIFICATION_RECORDING_ID,
        NOTIFICATION_RECORDING_FINISHED_ID
    }

    inner class RecordingBinder : Binder() {
        fun isStarted(): Boolean {
            return this@ScreenRecorder.runningService
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

        fun setPreStart(resultCode: Int, intent: Intent, width: Int, height: Int) {
            this@ScreenRecorder.intentResult = resultCode
            this@ScreenRecorder.intentData = intent
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
            this@ScreenRecorder.sensor!!.registerListener(this@ScreenRecorder.sensorListener, this@ScreenRecorder.sensor!!.getDefaultSensor(1), 2)
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
    }

    override fun onDestroy() {
        super.onDestroy()
        this.sensor?.unregisterListener(this.sensorListener)
    }

    override fun onBind(intent: Intent): IBinder {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && intent.action == QuickTile.ACTION_CONNECT_TILE) {
            return this.recordingTileBinder
        }
        return this.recordingBinder
    }

    override fun onCreate() {
        super.onCreate()
        this.display = (baseContext.getSystemService("display") as DisplayManager).getDisplay(0)
        val sensorManager: SensorManager = applicationContext.getSystemService("sensor") as SensorManager
        this.sensor = sensorManager
        sensorManager.registerListener(this.sensorListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 2)
        if (this.panelBinder == null) {
            val intent = Intent(this, FloatingControls::class.java)
            intent.setAction(FloatingControls.ACTION_RECORD_PANEL)
            bindService(intent, this.mPanelConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            if (intent.action == ACTION_START) {
                this.recordOnlyAudio = false
                this.backgroundRecordingActive = false
                actionStart()
            } else if (intent.action == ACTION_START_NOVIDEO) {
                this.recordOnlyAudio = true
                this.backgroundRecordingActive = false
                actionStart()
            } else if (intent.action == ACTION_START_BACKGROUND) {
                this.recordOnlyAudio = false
                this.backgroundRecordingActive = true
                actionStart()
            } else if (intent.action == ACTION_STOP) {
                screenRecordingStop()
            } else if (intent.action == ACTION_PAUSE) {
                screenRecordingPause()
            } else if (intent.action == ACTION_CONTINUE) {
                screenRecordingResume()
            } else if (intent.action == ACTION_ACTIVITY_DELETE_FINISHED_FILE) {
                screenRecordingDelete()
            }
        }
        return START_STICKY
    }

    fun actionStart() {
        val displayMetrics = DisplayMetrics()
        this.display!!.getRealMetrics(displayMetrics)
        this.orientationOnStart = this.display!!.rotation
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
        this.intentFlag = Intent.FLAG_ACTIVITY_MULTIPLE_TASK
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
                } catch (exc: NumberFormatException) {
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
            this.recordingNotificationManager!!.createNotificationChannel(NotificationChannelCompat.Builder(NOTIFICATIONS_RECORDING_CHANNEL, NotificationManager.IMPORTANCE_HIGH).setName(getString(R.string.notifications_channel)).setLightsEnabled(true).setLightColor(android.graphics.Color.RED).setShowBadge(true).setVibrationEnabled(true).build())
        }
        this.runningService = true
        if (this.tileBinder != null) {
            this.tileBinder!!.recordingState(true)
        }
        screenRecordingStart()
    }

    fun actionConnect(activityBinder: MainActivity.ActivityBinder) {
        this.activityBinder = activityBinder
        if (this.runningService) {
            if (!this.isPaused) {
                if (activityBinder != null) {
                    activityBinder.recordingStart()
                }
            } else {
                if (this.isPaused && activityBinder != null) {
                    activityBinder.recordingPause(this.timeRecorded)
                }
            }
        } else {
            if (this.isStopped && activityBinder != null) {
                activityBinder.recordingStop()
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

    fun screenRecordingStart() {
        this.isStopped = false
        if (this.minimizeOnStart) {
            val homeIntent = Intent(Intent.ACTION_MAIN)
            homeIntent.addCategory(Intent.CATEGORY_HOME)
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(homeIntent)
        }
        this.showFloatingControls = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.FLOATING_CONTROLS, false) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Settings.canDrawOverlays(this)
        if (this.showFloatingControls) {
            val floatingControlsIntent = Intent(this, FloatingControls::class.java)
            floatingControlsIntent.setAction(FloatingControls.ACTION_RECORD_PANEL)
            startService(floatingControlsIntent)
        }
        if (this.backgroundRecordingActive) {
            this.recordMicrophone = false
            this.recordPlayback = true
            
            // Acquire partial wakelock
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            this.partialWakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "CaptureCap:BgRecordingWakelock")
            this.partialWakeLock?.acquire()

            // Dim screen fully using overlay
            val wManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            params.dimAmount = 1.0f
            params.screenBrightness = 0.0f
            
            this.overlayBlackView = android.view.View(this).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
            }
            wManager.addView(this.overlayBlackView, params)
        } else {
            this.recordMicrophone = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.CHECK_SOUND_MIC, false)
            this.recordPlayback = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.CHECK_SOUND_PLAYBACK, false)
        }

        val recordingDate: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Calendar.getInstance().getTime())
        var recordingFileName: String = "ScreenRecording_$recordingDate"

        var folderPath: String = this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.FOLDER_PATH, "")
        if (this.recordOnlyAudio) {
            folderPath = this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.FOLDER_AUDIO_PATH, "")
        }

        var fileExtension: String
        var fileMimeType: String
        if (this.recordOnlyAudio) {
            recordingFileName = "AudioRecording_$recordingDate"
            fileMimeType = "audio/mp4"
            fileExtension = ".m4a"
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

        var file: File?
        var fullFilePathCreateDocument: Uri?
        var fullFilePathRenameDocument: Uri?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                fullFilePathCreateDocument = DocumentsContract.createDocument(contentResolver, documentParentPath, fileMimeType, recordingFileName)
                if (!fullFilePathCreateDocument.toString().endsWith(".m4a") && this.recordOnlyAudio) {
                    try {
                        fullFilePathRenameDocument = DocumentsContract.renameDocument(contentResolver, fullFilePathCreateDocument!!, "$recordingFileName.m4a")
                    } catch (exc: Exception) {
                        fullFilePathRenameDocument = null
                    }
                    if (fullFilePathRenameDocument == null) {
                        fullFilePathRenameDocument = Uri.parse("$fullFilePathCreateDocument.m4a")
                    }
                    fullFilePathCreateDocument = fullFilePathRenameDocument
                }
                file = null
            } catch (exc: Exception) {
                Log.e("ScreenRecorder", "Invalid recording path: $documentParentPath")
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
                Log.v("ScreenRecorder", "File path: " + fullFilePath.toString())
                file = File(fullFilePath.toString())
                file.createNewFile()
                fullFilePathCreateDocument = null
            } catch (exc: Exception) {
                file = null
                fullFilePathCreateDocument = null
            }
        }
        if ((file == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) || (fullFilePathCreateDocument == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)) {
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
    
            val iconStop: IconCompat = IconCompat.createWithResource(this, R.drawable.icon_stop_color_action)
    
            val stopIntent: Intent = Intent(this, ScreenRecorder::class.java)
            stopIntent.setAction(ACTION_STOP)
    
            val notificationStopBuilder: NotificationCompat.Action.Builder = NotificationCompat.Action.Builder(iconStop, getString(R.string.notifications_stop), PendingIntent.getService(this, 0, stopIntent, this.intentFlag))
            val iconPause: IconCompat = IconCompat.createWithResource(this, R.drawable.icon_pause_color_action)
            val pauseIntent: Intent = Intent(this, ScreenRecorder::class.java)
            pauseIntent.setAction(ACTION_PAUSE)
            val pauseAction: NotificationCompat.Action.Builder = NotificationCompat.Action.Builder(iconPause, getString(R.string.notifications_pause), PendingIntent.getService(this, 0, pauseIntent, this.intentFlag))
            var recordingStartedBuilder: NotificationCompat.Builder = NotificationCompat.Builder(this, NOTIFICATIONS_RECORDING_CHANNEL)
            if (this.recordOnlyAudio) {
                recordingStartedBuilder = recordingStartedBuilder.setContentTitle(getString(R.string.recording_audio_started_title)).setContentText(getString(R.string.recording_audio_started_text)).setTicker(getString(R.string.recording_audio_started_text))
            } else {
                recordingStartedBuilder = recordingStartedBuilder.setContentTitle(getString(R.string.recording_started_title)).setContentText(getString(R.string.recording_started_text)).setTicker(getString(R.string.recording_started_text))
            }
            recordingStartedBuilder = recordingStartedBuilder.setSmallIcon(R.drawable.icon_record_status).setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.icon_record_color_action_normal)).setUsesChronometer(true).setWhen(System.currentTimeMillis() - (SystemClock.elapsedRealtime() - this.timeStart)).setOngoing(true).addAction(notificationStopBuilder.build()).setPriority(NotificationCompat.PRIORITY_LOW)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                recordingStartedBuilder.addAction(pauseAction.build())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NotificationID.NOTIFICATION_RECORDING_ID.ordinal, recordingStartedBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NotificationID.NOTIFICATION_RECORDING_ID.ordinal, recordingStartedBuilder.build())
            }
            if (this.activityBinder != null) {
                this.activityBinder?.recordingStart()
            }
    
            var width: Int
            var height: Int
    
            if (this.orientationOnStart == Surface.ROTATION_270 || this.orientationOnStart == Surface.ROTATION_90) {
                width = this.screenHeightNormal
                height = this.screenWidthNormal
            } else {
                width = this.screenWidthNormal
                height = this.screenHeightNormal
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
                val screenHeight: Int = if (height > width) {width} else {height}
                var screenScale = 0.0f
                if (resolution == GlobalProperties.ResolutionProperty._2160P_ && screenHeight >= 2160) {
                    screenScale = 2160.0f
                } else if (resolution == GlobalProperties.ResolutionProperty._1080P_ && screenHeight >= 1080) {
                    screenScale = 1080.0f
                } else if (resolution == GlobalProperties.ResolutionProperty._720P_ && screenHeight >= 720) {
                    screenScale = 720.0f
                } else if (resolution != GlobalProperties.ResolutionProperty._480P_ || screenHeight < 480) {
                    if (resolution == GlobalProperties.ResolutionProperty._360P_ && screenHeight >= 360) {
                        screenScale = 360.0f
                    }
                    scaleRatio = 1.0f
                } else {
                    screenScale = 480.0f
                }
                scaleRatio = screenScale / screenHeight
            }
    
            val mediaProjectionManager: MediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
            val callback: MediaProjection.Callback = object: MediaProjection.Callback() {
                override fun onStop() {
                    if (this@ScreenRecorder.isActive) {
                        this@ScreenRecorder.recordingError()
                    }
                }
            }
    
            if (this.recordOnlyAudio && (!this.recordPlayback || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)) {
                this.recordingMediaProjection = null
            } else {
                val mediaProjection: MediaProjection? = mediaProjectionManager.getMediaProjection(this.intentResult, this.intentData!!)
                this.recordingMediaProjection = mediaProjection
                mediaProjection!!.registerCallback(callback, null)
            }
    
            if (!this.recordOnlyAudio) {
                this.recordingVirtualDisplay = this.recordingMediaProjection!!.createVirtualDisplay("CaptureCap", width, height, screenDensity.toInt(), DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, null, null, null)
            }
    
            var refreshRate: Int = this.display!!.refreshRate.toInt()
            val customQuality: Boolean = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.CUSTOM_QUALITY, false)
            val qualityScale: Float = (this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.QUALITY_SCALE, 9) + 1) * 0.1f
            val customFps: Boolean = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.CUSTOM_FPS, false)
            val fpsValue: Int = Integer.parseInt(this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.FPS_VALUE, "30"))
            val customBitrate: Boolean = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.CUSTOM_BITRATE, false)
            val bitrateValue: Int = Integer.parseInt(this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.BITRATE_VALUE, "0"))
            val codec: String = this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.CODEC_VALUE, resources.getString(R.string.codec_option_auto_value))
            val audioCodec: String = this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.AUDIO_CODEC_VALUE, resources.getString(R.string.audio_codec_option_auto_value))
    
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
                } catch (exc: IOException) {
                    recordingError()
                }
                try {
                    this.recordingMediaRecorder!!.start()
                } catch (exc: IllegalStateException) {
                    recordingError()
                }
                if (!this.recordOnlyAudio) {
                    this.recordingVirtualDisplay!!.surface = this.recordingMediaRecorder!!.surface
                }
            } else {
                try {
                    val parcelFileDescriptorOpenFileDescriptor: ParcelFileDescriptor = contentResolver.openFileDescriptor(this.recordFilePath!!, "rw")!!
                    this.recordingOpenFileDescriptor = parcelFileDescriptorOpenFileDescriptor
                    this.recordingFileDescriptor = parcelFileDescriptorOpenFileDescriptor.fileDescriptor
                } catch (exc: Exception) {
                    recordingError()
                }
                val playbackRecorder = PlaybackRecorder(applicationContext, this.recordOnlyAudio, this.recordingVirtualDisplay, this.recordingFileDescriptor!!, this.recordingMediaProjection, width, height, scaleRatio, refreshRate, this.recordMicrophone, this.recordPlayback, customQuality, qualityScale, customFps, fpsValue, customBitrate, bitrateValue, !codec.contentEquals(resources.getString(R.string.codec_option_auto_value)), codec, !audioCodec.contentEquals(resources.getString(R.string.audio_codec_option_auto_value)), audioCodec, this.customSampleRate, this.customChannelsCount, this.mediaAudioSource, this.gameAudioSource, this.unknownAudioSource)
                this.recorderPlayback = playbackRecorder
                playbackRecorder.start()
            }
            this.isActive = true
        }
    }

    fun screenRecordingStop()  {
        this.isActive = false
        this.timeStart = 0L
        this.timeRecorded = 0L
        this.isPaused = false
        this.isStopped = true
        this.runningService = false
        tileBinder?.recordingState(false)
        if (!this.errorDir && this.activityBinder != null) {
            this.activityBinder!!.recordingStop()
        }
        if (this.panelBinder != null && this.showFloatingControls) {
            this.panelBinder?.setStop()
        }
        
        if (this.partialWakeLock != null && this.partialWakeLock!!.isHeld) {
            this.partialWakeLock!!.release()
            this.partialWakeLock = null
        }
        if (this.overlayBlackView != null) {
            val wManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wManager.removeView(this.overlayBlackView)
            this.overlayBlackView = null
        }
        this.backgroundRecordingActive = false

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (this.recordingMediaRecorder != null) {
                try {
                    this.recordingMediaRecorder?.stop()
                    this.recordingMediaRecorder?.reset()
                    this.recordingMediaRecorder?.release()
                    if (!this.recordOnlyAudio) {
                        this.recordingVirtualDisplay!!.release()
                    }
                } catch (exc: RuntimeException) {
                    Toast.makeText(this, R.string.error_recorder_failed, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            if (this.recorderPlayback != null) {
                this.recorderPlayback?.quit()
                try {
                    this.recordingOpenFileDescriptor!!.close()
                } catch (exc: IOException) {
                    Toast.makeText(this, R.string.error_recorder_failed, Toast.LENGTH_SHORT).show()
                }
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
        val activity: PendingIntent = PendingIntent.getActivity(this, 0, this.finishedFileIntent, this.intentFlag)
        val recordingDeleteIntent = Intent(this, ScreenRecorder::class.java)
        recordingDeleteIntent.setAction(ScreenRecorder.ACTION_ACTIVITY_DELETE_FINISHED_FILE)
        val notificationDeleteBuilder: NotificationCompat.Action.Builder = NotificationCompat.Action.Builder(IconCompat.createWithResource(this, R.drawable.icon_record_delete_color_action), getString(R.string.notifications_delete), PendingIntent.getService(this, 0, recordingDeleteIntent, this.intentFlag))
        val iconRecordShare: IconCompat = IconCompat.createWithResource(this, R.drawable.icon_record_share_color_action)
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
            finishedRecordingBuilder = finishedRecordingBuilder.setContentTitle(getString(R.string.recording_audio_finished_title)).setContentText(getString(R.string.recording_audio_finished_text))
        } else {
            finishedRecordingBuilder = finishedRecordingBuilder.setContentTitle(getString(R.string.recording_finished_title)).setContentText(getString(R.string.recording_finished_text))
        }
        val notificationDelete: NotificationCompat.Builder = finishedRecordingBuilder.setContentIntent(activity).setSmallIcon(R.drawable.icon_record_finished_status).setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.icon_record_finished_color_action_normal)).addAction(notificationShareBuilder.build()).addAction(notificationDeleteBuilder.build()).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_LOW)
        if (!this.dontNotifyOnFinish) {
            this.recordingNotificationManager!!.notify(NotificationID.NOTIFICATION_RECORDING_FINISHED_ID.ordinal, notificationDelete.build())
        }
        if (this.recordingMediaProjection != null) {
            this.recordingMediaProjection?.stop()
            this.recordingMediaProjection = null
        }
        stopForeground(true)
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
            this.activityBinder?.recordingPause(this.timeRecorded)
        }
        if (this.panelBinder != null && this.showFloatingControls) {
            this.panelBinder?.setPause(this.timeRecorded)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            this.recordingMediaRecorder!!.pause()
        } else {
            this.recorderPlayback!!.pause()
        }
        val stopIcon: IconCompat = IconCompat.createWithResource(this, R.drawable.icon_stop_continue_color_action)
        val intent = Intent(this, ScreenRecorder::class.java)
        intent.setAction(ACTION_STOP)
        val stopNotificationAction: NotificationCompat.Action.Builder = NotificationCompat.Action.Builder(stopIcon, getString(R.string.notifications_stop), PendingIntent.getService(this, 0, intent, this.intentFlag))
        val continueIcon: IconCompat = IconCompat.createWithResource(this, R.drawable.icon_record_continue_color_action)
        val continueIntent = Intent(this, ScreenRecorder::class.java)
        continueIntent.setAction(ACTION_CONTINUE)
        val resumeNotificationAction: NotificationCompat.Action.Builder = NotificationCompat.Action.Builder(continueIcon, getString(R.string.notifications_resume), PendingIntent.getService(this, 0, continueIntent, this.intentFlag))
        var pauseNotificationBuilder: NotificationCompat.Builder = NotificationCompat.Builder(this, NOTIFICATIONS_RECORDING_CHANNEL)
        if (this.recordOnlyAudio) {
            pauseNotificationBuilder = pauseNotificationBuilder.setContentTitle(getString(R.string.recording_audio_paused_title)).setContentText(getString(R.string.recording_audio_paused_text))
        } else {
            pauseNotificationBuilder = pauseNotificationBuilder.setContentTitle(getString(R.string.recording_paused_title)).setContentText(getString(R.string.recording_paused_text))
        }
        this.recordingNotificationManager!!.notify(NotificationID.NOTIFICATION_RECORDING_ID.ordinal, pauseNotificationBuilder.setSmallIcon(R.drawable.icon_pause_status).setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.icon_pause_color_action_normal)).setOngoing(true).addAction(stopNotificationAction.build()).addAction(resumeNotificationAction.build()).setPriority(NotificationCompat.PRIORITY_LOW).build())
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
        val stopIcon: IconCompat = IconCompat.createWithResource(this, R.drawable.icon_stop_color_action)
        val intent = Intent(this, ScreenRecorder::class.java)
        intent.setAction(ACTION_STOP)
        val builder: NotificationCompat.Action.Builder = NotificationCompat.Action.Builder(stopIcon, getString(R.string.notifications_stop), PendingIntent.getService(this, 0, intent, this.intentFlag))
        val pauseIcon: IconCompat = IconCompat.createWithResource(this, R.drawable.icon_pause_color_action)
        val pauseIntent = Intent(this, ScreenRecorder::class.java)
        pauseIntent.setAction(ACTION_PAUSE)
        this.recordingNotificationManager!!.notify(NotificationID.NOTIFICATION_RECORDING_ID.ordinal, NotificationCompat.Builder(this, NOTIFICATIONS_RECORDING_CHANNEL).setContentTitle(getString(R.string.recording_started_title)).setContentText(getString(R.string.recording_started_text)).setTicker(getString(R.string.recording_started_text)).setSmallIcon(R.drawable.icon_record_status).setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.icon_record_color_action_normal)).setUsesChronometer(true).setWhen(System.currentTimeMillis() - (SystemClock.elapsedRealtime() - this.timeStart)).setOngoing(true).addAction(builder.build()).addAction(NotificationCompat.Action.Builder(pauseIcon, getString(R.string.notifications_pause), PendingIntent.getService(this, 0, pauseIntent, this.intentFlag)).build()).setPriority(NotificationCompat.PRIORITY_LOW).build())
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
