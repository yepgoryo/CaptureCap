package com.yepgoryo.CaptureCap

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.View
import android.view.Window
import android.widget.Chronometer
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat

class MainActivity : AppCompatActivity() {

    companion object {
        const val appName: String = "com.yepgoryo.CaptureCap"
        const val ACTION_ACTIVITY_START_RECORDING: String = "$appName.ACTIVITY_START_RECORDING"
    }

    private var activityProjectionManager: MediaProjectionManager? = null
    private var appSettings: GlobalProperties? = null
    var audioPlaybackUnavailable: TextView? = null
    private var dialog: AlertDialog? = null
    private var display: Display? = null
    var finishedPanel: LinearLayout? = null
    var mainRecordingButton: RecordButton? = null
    var modesPanel: LinearLayout? = null
    var optionsPanel: LinearLayout? = null
    var recordAudioSetting: ImageButton? = null
    var recordDelete: ImageButton? = null
    private var recordDeleteIcon: VectorDrawableCompat? = null
    var recordInfo: ImageButton? = null
    private var recordInfoIcon: VectorDrawableCompat? = null
    var recordMicrophoneSetting: ImageButton? = null
    private var recordMicrophoneState: VectorDrawableCompat? = null
    private var recordMicrophoneStateDisabled: VectorDrawableCompat? = null
    private var recordModeChosen: Boolean = false
    var recordOpen: ImageButton? = null
    private var recordOpenIcon: VectorDrawableCompat? = null
    private var recordPlaybackState: VectorDrawableCompat? = null
    private var recordPlaybackStateDisabled: VectorDrawableCompat? = null
    var recordScreenSetting: ImageButton? = null
    private var recordScreenState: VectorDrawableCompat? = null
    private var recordScreenStateDisabled: VectorDrawableCompat? = null
    var recordSettings: ImageButton? = null
    private var recordSettingsIcon: VectorDrawableCompat? = null
    var recordShare: ImageButton? = null
    private var recordShareIcon: VectorDrawableCompat? = null
    var recordStop: ImageButton? = null
    private var recordStopIcon: VectorDrawableCompat? = null
    private var recordingBinder: ScreenRecorder.RecordingBinder? = null
    private var serviceIntent: Intent? = null
    var startRecordingButton: ImageButton? = null
    var timeCounter: Chronometer? = null
    var timerPanel: LinearLayout? = null
    private var recordingState: ActionState = ActionState.RECORDING_STOPPED
    private var screenRecorderStarted: Boolean = false
    private var stateActivated: Boolean = false
    private var serviceToRecording: Boolean = false
    private var isRecording: Boolean = false
    private var recordMicrophone: Boolean = false
    private var recordPlayback: Boolean = false
    private var recordOnlyAudio: Boolean = false
    private var stateToRestore: Boolean = false

    private var mConnection: ServiceConnection = object: ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            this@MainActivity.recordingBinder = iBinder as ScreenRecorder.RecordingBinder
            var mainActivity: MainActivity = this@MainActivity
            mainActivity.recordingBinder!!.let { mainActivity.screenRecorderStarted = it.isStarted() }
            this@MainActivity.recordingBinder!!.setConnect(this@MainActivity.ActivityBinder())
            if (this@MainActivity.serviceToRecording) {
                this@MainActivity.serviceToRecording = false
                this@MainActivity.recordingStart()
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            this@MainActivity.recordingBinder!!.setDisconnect()
            this@MainActivity.screenRecorderStarted = false
        }
    }
    private var requestRecordingPermission: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == -1 && this@MainActivity.recordingBinder != null) {
            this@MainActivity.doStartService(result.resultCode, result.data!!)
        }
    }
    private var requestFolderPermission: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        this@MainActivity.requestFolder(result.resultCode, result.data!!.data!!, false, false)
    }
    private var requestAudioFolderPermission: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        this@MainActivity.requestFolder(result.resultCode, result.data!!.data!!, false, true)
    }
    private var requestFolderPermissionAndProceed: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        this@MainActivity.requestFolder(result.resultCode, result.data!!.data!!, true, false)
    }
    private var requestAudioFolderPermissionAndProceed: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        this@MainActivity.requestFolder(result.resultCode, result.data!!.data!!, true, true)
    }

    enum class ActionState {
        RECORDING_STOPPED,
        RECORDING_IN_PROGRESS,
        RECORDING_PAUSED,
        RECORDING_ENDED
    }

    private enum class RecordingPermissionRequest {
        REQUEST_MICROPHONE,
        REQUEST_MICROPHONE_PLAYBACK,
        REQUEST_MICROPHONE_RECORD,
        REQUEST_STORAGE,
        REQUEST_STORAGE_AUDIO,
        REQUEST_MODE_CHANGE,
        REQUEST_POST_NOTIFICATIONS
    }

    fun showCounter(starting: Boolean, buttonState: RecordButton.ButtonState) {
        if (starting) {
            this.timeCounter!!.scaleX = 0.0f
            this.timeCounter!!.scaleY = 0.0f
            this.timerPanel!!.visibility = View.VISIBLE
            val animateCounterX: ObjectAnimator = ObjectAnimator.ofFloat(this.timeCounter, "scaleX", 0.0f, 1.0f)
            val animateCounterY: ObjectAnimator = ObjectAnimator.ofFloat(this.timeCounter, "scaleY", 0.0f, 1.0f)
            animateCounterX.addUpdateListener(object: ValueAnimator.AnimatorUpdateListener {
                override fun onAnimationUpdate(valueAnimator: ValueAnimator) {
                    if ((valueAnimator.getAnimatedValue() as Float) == 1.0f && buttonState != null) {
                        this@MainActivity.mainRecordingButton!!.transitionToButtonState(buttonState)
                    }
                }
            })
            animateCounterX.setDuration(400L)
            animateCounterY.setDuration(400L)
            animateCounterX.start()
            animateCounterY.start()
        } else {
            this.timeCounter!!.scaleX = 1.0f
            this.timeCounter!!.scaleY = 1.0f
            this.timerPanel!!.visibility = View.VISIBLE
            val animateCounterX: ObjectAnimator = ObjectAnimator.ofFloat(this.timeCounter, "scaleX", 1.0f, 0.0f)
            val animateCounterY: ObjectAnimator = ObjectAnimator.ofFloat(this.timeCounter, "scaleY", 1.0f, 0.0f)
            animateCounterX.addUpdateListener { valueAnimator ->
                if ((valueAnimator.getAnimatedValue() as Float) == 0.0f) {
                    this@MainActivity.timerPanel!!.visibility = View.GONE
                    if (buttonState != null) {
                        this@MainActivity.mainRecordingButton!!.transitionToButtonState(buttonState)
                    }
                }
            }
            animateCounterX.setDuration(400L)
            animateCounterY.setDuration(400L)
            animateCounterX.start()
            animateCounterY.start()
        }
    }

    fun updateRecordButtonConditions() {
        if (this.mainRecordingButton != null) {
            this.mainRecordingButton?.updateConditions(this.recordMicrophone, this.recordPlayback, this.recordOnlyAudio)
        }
    }

    private fun updateRecordModeData() {
        this.recordMicrophone = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.CHECK_SOUND_MIC, false)
        this.recordPlayback = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.CHECK_SOUND_PLAYBACK, false)
        if (this.recordPlayback && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            this.recordPlayback = false
        }
        this.recordOnlyAudio = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.RECORD_MODE, false)
        if (this.recordOnlyAudio && !this.recordPlayback && !this.recordMicrophone) {
            this.recordOnlyAudio = false
        }
    }

    inner class ActivityBinder : Binder() {
        fun recordingStart() {
            this@MainActivity.timeCounter!!.stop()
            this@MainActivity.timeCounter!!.setBase(this@MainActivity.recordingBinder!!.getTimeStart())
            this@MainActivity.timeCounter!!.start()
            this@MainActivity.audioPlaybackUnavailable!!.visibility = View.GONE
            this@MainActivity.modesPanel!!.visibility = View.GONE
            this@MainActivity.optionsPanel!!.visibility = View.GONE
            this@MainActivity.recordingState = ActionState.RECORDING_IN_PROGRESS
            if (this@MainActivity.stateToRestore) {
                this@MainActivity.showCounter(true, RecordButton.ButtonState.TRANSITION_TO_RECORDING)
            } else {
                this@MainActivity.timeCounter!!.scaleX = 1.0f
                this@MainActivity.timeCounter!!.scaleY = 1.0f
                this@MainActivity.timerPanel!!.visibility = View.VISIBLE
                this@MainActivity.mainRecordingButton!!.transitionToButtonState(RecordButton.ButtonState.WHILE_RECORDING_NORMAL)
            }
        }

        fun recordingStop() {
            this@MainActivity.timeCounter!!.stop()
            this@MainActivity.timeCounter!!.setBase(SystemClock.elapsedRealtime())
            this@MainActivity.audioPlaybackUnavailable!!.visibility = View.GONE
            this@MainActivity.modesPanel!!.visibility = View.GONE
            this@MainActivity.optionsPanel!!.visibility = View.GONE
            this@MainActivity.finishedPanel!!.visibility = View.VISIBLE
            this@MainActivity.recordStop!!.setVisibility(View.GONE)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                this@MainActivity.audioPlaybackUnavailable!!.visibility = View.VISIBLE
            }
            this@MainActivity.recordingState = ActionState.RECORDING_ENDED
            if (this@MainActivity.stateToRestore) {
                this@MainActivity.showCounter(false, RecordButton.ButtonState.TRANSITION_TO_RECORDING_END)
            } else {
                this@MainActivity.timerPanel!!.visibility = View.GONE
                this@MainActivity.mainRecordingButton!!.transitionToButtonState(RecordButton.ButtonState.ENDED_RECORDING_NORMAL)
                this@MainActivity.recordingState = ActionState.RECORDING_ENDED
            }
        }

        fun recordingPause(j: Long) {
            this@MainActivity.timeCounter!!.setBase(SystemClock.elapsedRealtime() - j)
            this@MainActivity.timeCounter!!.stop()
            this@MainActivity.modesPanel!!.visibility = View.GONE
            this@MainActivity.optionsPanel!!.visibility = View.GONE
            this@MainActivity.timeCounter!!.scaleX = 1.0f
            this@MainActivity.timeCounter!!.scaleY = 1.0f
            this@MainActivity.timerPanel!!.visibility = View.VISIBLE
            this@MainActivity.recordStop!!.setVisibility(View.VISIBLE)
            this@MainActivity.recordingState = ActionState.RECORDING_PAUSED
            if (this@MainActivity.stateToRestore) {
                this@MainActivity.mainRecordingButton!!.transitionToButtonState(RecordButton.ButtonState.TRANSITION_TO_RECORDING_PAUSE)
            } else {
                this@MainActivity.mainRecordingButton!!.transitionToButtonState(RecordButton.ButtonState.WHILE_PAUSE_NORMAL)
                this@MainActivity.recordingState = ActionState.RECORDING_PAUSED
            }
        }

        fun recordingResume(time: Long) {
            this@MainActivity.timeCounter!!.setBase(time)
            this@MainActivity.timeCounter!!.start()
            this@MainActivity.recordStop!!.setVisibility(View.GONE)

            this@MainActivity.mainRecordingButton!!.transitionToButtonState(RecordButton.ButtonState.TRANSITION_FROM_PAUSE)
            this@MainActivity.recordingState = ActionState.RECORDING_IN_PROGRESS
        }

        fun recordingReset() {
            this@MainActivity.finishedPanel!!.visibility = View.GONE
            this@MainActivity.optionsPanel!!.visibility = View.VISIBLE
            this@MainActivity.modesPanel!!.visibility = View.VISIBLE
            this@MainActivity.mainRecordingButton!!.transitionToButtonState(RecordButton.ButtonState.TRANSITION_TO_RESTART)
            this@MainActivity.finishedPanel!!.visibility = View.GONE
            this@MainActivity.optionsPanel!!.visibility = View.VISIBLE
            this@MainActivity.modesPanel!!.visibility = View.VISIBLE
            this@MainActivity.recordingState = ActionState.RECORDING_STOPPED
        }

        fun resetDir(audio: Boolean) {
            this@MainActivity.resetFolder(audio)
        }
    }

    fun doStartService(resultCode: Int, intent: Intent?) {
        val display: Display = (baseContext.getSystemService("display") as DisplayManager).getDisplay(0)
        this.display = display
        val rotation: Int = display.rotation
        val rect = Rect()
        window.decorView.getWindowVisibleDisplayFrame(rect)
        window.decorView.getWindowVisibleDisplayFrame(Rect())
        var iWidth: Int = rect.width()
        var iHeight: Int = rect.height()
        if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
            iWidth = iHeight
            iHeight = iWidth
        }
        if (intent != null) {
            this.recordingBinder!!.setPreStart(resultCode, intent!!, iWidth, iHeight)
        }
        updateRecordModeData()
        updateRecordButtonConditions()
        if (!this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.RECORD_MODE, false)) {
            this.serviceIntent!!.setAction(ScreenRecorder.ACTION_START)
        } else {
            this.serviceIntent!!.setAction(ScreenRecorder.ACTION_START_NOVIDEO)
        }
        startService(this.serviceIntent)
    }

    fun doBindService() {
        val intent = Intent(this, ScreenRecorder::class.java)
        this.serviceIntent = intent
        bindService(intent, this.mConnection, 1)
    }

    fun doUnbindService() {
        if (this.recordingBinder != null) {
            unbindService(this.mConnection)
        }
    }

    protected override fun onDestroy() {
        super.onDestroy()
        doUnbindService()
    }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        val display: Display = (baseContext.getSystemService("display") as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        this.display = display
        val rotation: Int = display.rotation
        val displayMetrics = DisplayMetrics()
        this.display!!.getRealMetrics(displayMetrics)
        val globalProperties = GlobalProperties(baseContext)
        this.appSettings = globalProperties
        val darkTheme: GlobalProperties.DarkThemeProperty = globalProperties.getDarkTheme(false)
        if (this.appSettings!!.getDarkTheme(true) != this.appSettings!!.getDarkTheme(false)) {
            this.appSettings!!.setDarkTheme(true, darkTheme)
        }
        if (((getResources().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES && darkTheme == GlobalProperties.DarkThemeProperty.AUTOMATIC) || darkTheme == GlobalProperties.DarkThemeProperty.DARK) {
            setTheme(androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar)
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        setContentView(R.layout.main)

        if (((getResources().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES && darkTheme == GlobalProperties.DarkThemeProperty.AUTOMATIC) || darkTheme == GlobalProperties.DarkThemeProperty.DARK) {
            findViewById<LinearLayout>(R.id.statusbar).setBackgroundColor(getColor(R.color.statusbar_dark))
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            findViewById<LinearLayout>(R.id.statusbar).visibility = View.GONE
        }

        var statusBarHeight = 0
        val resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId)
        }

        val statusbarlayout = findViewById<LinearLayout?>(R.id.statusbar)
        val statusbarlayoutparams: LinearLayout.LayoutParams = statusbarlayout.layoutParams as LinearLayout.LayoutParams
        statusbarlayoutparams.height = statusBarHeight
        statusbarlayout.setLayoutParams(statusbarlayoutparams)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainscroll)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )
            v.updatePadding(
                left = bars.left,
                top = bars.top-statusBarHeight,
                right = bars.right,
                bottom = bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }

        val recordPanel: View = findViewById(R.id.recordpanel)
        val layoutParams: RelativeLayout.LayoutParams =
            recordPanel.layoutParams as RelativeLayout.LayoutParams
        if ((rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) && displayMetrics.widthPixels > displayMetrics.heightPixels) {
            layoutParams.addRule(RelativeLayout.RIGHT_OF, R.id.recordingmainbutton)
            recordPanel.setLayoutParams(layoutParams)
        } else {
            layoutParams.addRule(RelativeLayout.BELOW, R.id.recordingmainbutton)
            recordPanel.setLayoutParams(layoutParams)
        }
        if (this.appSettings!!.getFloatingControlsSize() == GlobalProperties.FloatingControlsSizeProperty.LITTLE) {
            this.appSettings!!.setFloatingControlsSize(GlobalProperties.FloatingControlsSizeProperty.TINY)
            this.appSettings!!.setBooleanProperty(GlobalProperties.PropertiesBoolean.PANEL_POSITION_HORIZONTAL_HIDDEN_TINY, this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.PANEL_POSITION_HORIZONTAL_HIDDEN_LITTLE, false))
            this.appSettings!!.setBooleanProperty(GlobalProperties.PropertiesBoolean.PANEL_POSITION_VERTICAL_HIDDEN_TINY, this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.PANEL_POSITION_VERTICAL_HIDDEN_LITTLE, false))
            this.appSettings!!.setIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_HORIZONTAL_X_TINY, this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_HORIZONTAL_X_LITTLE, 0))
            this.appSettings!!.setIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_HORIZONTAL_Y_TINY, this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_HORIZONTAL_Y_LITTLE, 0))
            this.appSettings!!.setIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_VERTICAL_X_TINY, this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_VERTICAL_X_LITTLE, 0))
            this.appSettings!!.setIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_VERTICAL_Y_TINY, this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.PANEL_POSITION_VERTICAL_Y_LITTLE, 0))
        }
        if (this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.CHECK_SOUND_PLAYBACK, false) && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            this.appSettings!!.setBooleanProperty(GlobalProperties.PropertiesBoolean.CHECK_SOUND_PLAYBACK, false)
        }
        if (this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.FLOATING_CONTROLS, false) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !Settings.canDrawOverlays(this)) {
            this.appSettings!!.setBooleanProperty(GlobalProperties.PropertiesBoolean.FLOATING_CONTROLS, false)
        }
        updateRecordModeData()
        val darkTheme2: GlobalProperties.DarkThemeProperty = this.appSettings!!.getDarkTheme(true)
        if (((getResources().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES && darkTheme == GlobalProperties.DarkThemeProperty.AUTOMATIC) || darkTheme2 == GlobalProperties.DarkThemeProperty.DARK) {
            this.recordScreenState = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_screen_dark, null)
            this.recordScreenStateDisabled = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_screen_disabled_dark, null)
            this.recordMicrophoneState = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_mic_dark, null)
            this.recordMicrophoneStateDisabled = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_mic_disabled_dark, null)
            this.recordPlaybackState = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_audio_dark, null)
            this.recordPlaybackStateDisabled = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_audio_disabled_dark, null)
            this.recordInfoIcon = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_info_dark, null)
            this.recordSettingsIcon = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_settings_dark, null)
            this.recordShareIcon = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_share_dark, null)
            this.recordDeleteIcon = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_delete_dark, null)
            this.recordOpenIcon = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_play_dark, null)
            this.recordStopIcon = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_stop_dark, null)
        } else {
            this.recordScreenState = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_screen, null)
            this.recordScreenStateDisabled = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_screen_disabled, null)
            this.recordMicrophoneState = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_mic, null)
            this.recordMicrophoneStateDisabled = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_mic_disabled, null)
            this.recordPlaybackState = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_audio, null)
            this.recordPlaybackStateDisabled = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_audio_disabled, null)
            this.recordInfoIcon = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_info, null)
            this.recordSettingsIcon = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_settings, null)
            this.recordShareIcon = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_share, null)
            this.recordDeleteIcon = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_delete, null)
            this.recordOpenIcon = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_play, null)
            this.recordStopIcon = VectorDrawableCompat.create(getResources(), R.drawable.icon_record_stop, null)
        }
        this.mainRecordingButton = RecordButton(baseContext, findViewById<ImageButton>(R.id.recordingmainbutton)!!)
        updateRecordButtonConditions()
        this.mainRecordingButton!!.innerButton().setOnClickListener(object: View.OnClickListener {
            override fun onClick(view: View) {
                if (!this@MainActivity.mainRecordingButton!!.getLockButton()) {
                    this@MainActivity.mainRecordingButton!!.setLockButton(true)
                    this@MainActivity.stateToRestore = true
    
                    if (this@MainActivity.recordingState == ActionState.RECORDING_ENDED) {
                        this@MainActivity.recordingBinder!!.recordingReset()
                    } else if (this@MainActivity.recordingState == ActionState.RECORDING_PAUSED) {
                        this@MainActivity.mainRecordingButton!!.transitionToButtonState(RecordButton.ButtonState.TRANSITION_FROM_PAUSE)
                        this@MainActivity.recordingBinder!!.recordingResume()
                    } else if (this@MainActivity.recordingState != ActionState.RECORDING_STOPPED) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            this@MainActivity.recordingBinder!!.recordingPause()
                        } else {
                            this@MainActivity.recordingBinder!!.stopService()
                        }
                    } else {
                        this@MainActivity.recordingStart()
                    }
                }
            }
        })
        findViewById<RelativeLayout>(R.id.mainlayout).setOnClickListener { this@MainActivity.mainRecordingButton!!.releaseFocus() }
        this.recordScreenSetting = findViewById<ImageButton>(R.id.recordscreen)!!
        this.recordMicrophoneSetting = findViewById<ImageButton>(R.id.recordmicrohone)!!
        this.recordAudioSetting = findViewById<ImageButton>(R.id.recordaudio)!!
        this.recordInfo = findViewById<ImageButton>(R.id.openinfo)!!
        this.recordSettings = findViewById<ImageButton>(R.id.opensettings)!!
        this.recordShare = findViewById<ImageButton>(R.id.sharerecord)!!
        this.recordDelete = findViewById<ImageButton>(R.id.deleterecord)!!
        this.recordOpen = findViewById<ImageButton>(R.id.openrecord)!!
        this.recordStop = findViewById<ImageButton>(R.id.recordstop)!!
        this.recordScreenSetting!!.setImageDrawable(this.recordScreenStateDisabled)
        this.recordMicrophoneSetting!!.setImageDrawable(this.recordMicrophoneStateDisabled)
        this.recordAudioSetting!!.setImageDrawable(this.recordPlaybackStateDisabled)
        this.recordInfo!!.setImageDrawable(this.recordInfoIcon)
        this.recordSettings!!.setImageDrawable(this.recordSettingsIcon)
        this.recordShare!!.setImageDrawable(this.recordShareIcon)
        this.recordDelete!!.setImageDrawable(this.recordDeleteIcon)
        this.recordOpen!!.setImageDrawable(this.recordOpenIcon)
        this.recordStop!!.setImageDrawable(this.recordStopIcon)
        this.recordScreenSetting!!.setContentDescription(getResources().getString(R.string.setting_record_screen) + ": " + getResources().getString(R.string.option_deactivated))
        this.recordMicrophoneSetting!!.setContentDescription(getResources().getString(R.string.setting_audio_record_microphone_sound) + ": " + getResources().getString(R.string.option_deactivated))
        this.recordAudioSetting!!.setContentDescription(getResources().getString(R.string.setting_audio_record_playback_sound) + ": " + getResources().getString(R.string.option_deactivated))

        this.recordScreenSetting!!.let { TooltipCompat.setTooltipText(it, getResources().getString(R.string.setting_record_screen)) }
        this.recordMicrophoneSetting!!.let { TooltipCompat.setTooltipText(it, getResources().getString(R.string.setting_audio_record_microphone_sound)) }
        this.recordAudioSetting!!.let { TooltipCompat.setTooltipText(it, getResources().getString(R.string.setting_audio_record_playback_sound)) }
        this.recordInfo!!.let { TooltipCompat.setTooltipText(it, getResources().getString(R.string.info_title)) }
        this.recordSettings!!.let { TooltipCompat.setTooltipText(it, getResources().getString(R.string.settings_title)) }
        this.recordShare!!.let { TooltipCompat.setTooltipText(it, getResources().getString(R.string.record_share)) }
        this.recordDelete!!.let { TooltipCompat.setTooltipText(it, getResources().getString(R.string.record_delete)) }
        this.recordOpen!!.let { TooltipCompat.setTooltipText(it, getResources().getString(R.string.record_open)) }
        this.recordStop!!.let { TooltipCompat.setTooltipText(it, getResources().getString(R.string.record_stop)) }

        this.timerPanel = findViewById<LinearLayout>(R.id.recordtimerpanel)!!
        this.modesPanel = findViewById<LinearLayout>(R.id.recordmodepanel)!!
        this.finishedPanel = findViewById<LinearLayout>(R.id.recordfinishedpanel)!!
        this.optionsPanel = findViewById<LinearLayout>(R.id.optionspanel)!!
        this.timeCounter = findViewById<Chronometer>(R.id.timerrecord)!!
        this.audioPlaybackUnavailable = findViewById<TextView>(R.id.audioplaybackunavailable)!!

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            this.audioPlaybackUnavailable!!.visibility = View.VISIBLE
            this.recordAudioSetting!!.setVisibility(View.GONE)
        }
        if (!this.recordOnlyAudio) {
            this.recordScreenSetting!!.setImageDrawable(this.recordScreenState)
            this.recordScreenSetting!!.setContentDescription(getResources().getString(R.string.setting_record_screen) + ": " + getResources().getString(R.string.option_activated))
        }
        if (this.recordMicrophone) {
            this.recordMicrophoneSetting!!.setImageDrawable(this.recordMicrophoneState)
            this.recordMicrophoneSetting!!.setContentDescription(getResources().getString(R.string.setting_audio_record_microphone_sound) + ": " + getResources().getString(R.string.option_activated))
        }
        if (this.recordPlayback) {
            this.recordAudioSetting!!.setImageDrawable(this.recordPlaybackState)
            this.recordAudioSetting!!.setContentDescription(getResources().getString(R.string.setting_audio_record_playback_sound) + ": " + getResources().getString(R.string.option_activated))
        }
        setRecordMode(this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.RECORD_MODE, false))
        this.activityProjectionManager = getSystemService("media_projection") as MediaProjectionManager

        this.recordScreenSetting!!.setOnClickListener {
            this@MainActivity.mainRecordingButton!!.releaseFocus()

            if (this@MainActivity.recordOnlyAudio) {
                this@MainActivity.recordScreenSetting!!.setImageDrawable(this@MainActivity.recordScreenState)
                this@MainActivity.recordOnlyAudio = false
                this@MainActivity.recordScreenSetting!!.setContentDescription(this@MainActivity.getResources().getString(R.string.setting_record_screen) + ": " + this@MainActivity.getResources().getString(R.string.option_activated))
                this@MainActivity.setRecordMode(false)
            } else if (!this@MainActivity.recordOnlyAudio && (this@MainActivity.recordMicrophone || this@MainActivity.recordPlayback)) {
                if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && this@MainActivity.checkSelfPermission(
                        Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) || Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                ) {
                    this@MainActivity.recordOnlyAudio = true
                    this@MainActivity.recordScreenSetting!!.setImageDrawable(this@MainActivity.recordScreenStateDisabled)
                    this@MainActivity.recordScreenSetting!!.setContentDescription(this@MainActivity.getResources().getString(R.string.setting_record_screen) + ": " + this@MainActivity.getResources().getString(R.string.option_deactivated))
                    this@MainActivity.setRecordMode(true)
                } else {
                    this@MainActivity.recordOnlyAudio = false
                    this@MainActivity.recordScreenSetting!!.setImageDrawable(this@MainActivity.recordScreenState)
                    this@MainActivity.recordScreenSetting!!.setContentDescription(this@MainActivity.getResources().getString(R.string.setting_record_screen) + ": " + this@MainActivity.getResources().getString(R.string.option_activated))
                    this@MainActivity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),RecordingPermissionRequest.REQUEST_MODE_CHANGE.ordinal)
                }
            }
            this@MainActivity.updateRecordButtonConditions()
        }

        this.recordMicrophoneSetting!!.setOnClickListener {
            this@MainActivity.mainRecordingButton!!.releaseFocus()

            if (((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && this@MainActivity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) || this@MainActivity.recordMicrophone) {
                if (!this@MainActivity.recordOnlyAudio || this@MainActivity.recordPlayback) {
                    this@MainActivity.recordMicrophone = !this@MainActivity.recordMicrophone
                    this@MainActivity.appSettings!!.setBooleanProperty(
                        GlobalProperties.PropertiesBoolean.CHECK_SOUND_MIC,
                        this@MainActivity.recordMicrophone
                    )
                    if (this@MainActivity.recordMicrophone) {
                        this@MainActivity.recordMicrophoneSetting!!.setImageDrawable(this@MainActivity.recordMicrophoneState)
                        this@MainActivity.recordMicrophoneSetting!!.setContentDescription(
                            this@MainActivity.getResources()
                                .getString(R.string.setting_audio_record_microphone_sound) + ": " + this@MainActivity.getResources()
                                .getString(R.string.option_activated)
                        )
                    } else {
                        this@MainActivity.recordMicrophoneSetting!!.setImageDrawable(this@MainActivity.recordMicrophoneStateDisabled)
                        this@MainActivity.recordMicrophoneSetting!!.setContentDescription(
                            this@MainActivity.getResources()
                                .getString(R.string.setting_audio_record_microphone_sound) + ": " + this@MainActivity.getResources()
                                .getString(R.string.option_deactivated)
                        )
                    }
                }
            } else {
                this@MainActivity.recordMicrophone = false
                this@MainActivity.recordMicrophoneSetting!!.setImageDrawable(this@MainActivity.recordMicrophoneStateDisabled)
                this@MainActivity.recordMicrophoneSetting!!.setContentDescription(this@MainActivity.getResources().getString(R.string.setting_audio_record_microphone_sound) + ": " + this@MainActivity.getResources().getString(R.string.option_deactivated))
                this@MainActivity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RecordingPermissionRequest.REQUEST_MICROPHONE.ordinal)
            }
            this@MainActivity.updateRecordButtonConditions()
        }

        this.recordAudioSetting!!.setOnClickListener {
            this@MainActivity.mainRecordingButton!!.releaseFocus()

            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && this@MainActivity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) || this@MainActivity.recordPlayback
            ) {
                if (!this@MainActivity.recordOnlyAudio || this@MainActivity.recordMicrophone) {
                    this@MainActivity.recordPlayback = !this@MainActivity.recordPlayback
                    this@MainActivity.appSettings!!.setBooleanProperty(
                        GlobalProperties.PropertiesBoolean.CHECK_SOUND_PLAYBACK,
                        this@MainActivity.recordPlayback
                    )
                    if (this@MainActivity.recordPlayback) {
                        this@MainActivity.recordAudioSetting!!.setImageDrawable(this@MainActivity.recordPlaybackState)
                        this@MainActivity.recordAudioSetting!!.setContentDescription(
                            this@MainActivity.getResources()
                                .getString(R.string.setting_audio_record_playback_sound) + ": " + this@MainActivity.getResources()
                                .getString(R.string.option_activated)
                        )
                    } else {
                        this@MainActivity.recordAudioSetting!!.setImageDrawable(this@MainActivity.recordPlaybackStateDisabled)
                        this@MainActivity.recordAudioSetting!!.setContentDescription(
                            this@MainActivity.getResources()
                                .getString(R.string.setting_audio_record_playback_sound) + ": " + this@MainActivity.getResources()
                                .getString(R.string.option_deactivated)
                        )
                    }
                }
            } else {
                this@MainActivity.recordPlayback = false
                this@MainActivity.recordAudioSetting!!.setImageDrawable(this@MainActivity.recordPlaybackStateDisabled)
                this@MainActivity.recordAudioSetting!!.setContentDescription(this@MainActivity.getResources().getString(R.string.setting_audio_record_playback_sound) + ": " + this@MainActivity.getResources().getString(R.string.option_deactivated))
                this@MainActivity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),RecordingPermissionRequest.REQUEST_MICROPHONE_PLAYBACK.ordinal)
            }
            this@MainActivity.updateRecordButtonConditions()
        }

        this.recordShare!!.setOnClickListener {
            this@MainActivity.mainRecordingButton!!.releaseFocus()
            this@MainActivity.recordingBinder!!.recordingShare()
        }

        this.recordDelete!!.setOnClickListener {
            this@MainActivity.mainRecordingButton!!.releaseFocus()
            this@MainActivity.recordingBinder!!.recordingDelete()
        }

        this.recordOpen!!.setOnClickListener {
            this@MainActivity.mainRecordingButton!!.releaseFocus()
            this@MainActivity.recordingBinder!!.recordingOpen()
        }

        this.recordStop!!.setOnClickListener {
            this@MainActivity.mainRecordingButton!!.releaseFocus()
            this@MainActivity.recordingBinder!!.stopService()
        }

        this.recordInfo!!.setOnClickListener {
            this@MainActivity.mainRecordingButton!!.releaseFocus()
            this@MainActivity.startActivity(Intent(this@MainActivity, AppInfo::class.java))
        }

        this.recordSettings!!.setOnClickListener {
            this@MainActivity.mainRecordingButton!!.releaseFocus()
            this@MainActivity.startActivity(Intent(this@MainActivity, SettingsPanel::class.java))
        }
    }

    fun setRecordMode(audioOnly: Boolean) {
        this.appSettings!!.setBooleanProperty(GlobalProperties.PropertiesBoolean.RECORD_MODE, audioOnly)
        this.recordModeChosen = audioOnly
    }

    override fun onStart() {
        super.onStart()
        doBindService()
        if (intent.action == ACTION_ACTIVITY_START_RECORDING && !this.stateActivated) {
            this.stateActivated = true
            recordingStart()
        }
    }

    fun checkDirRecord(isAudio: Boolean) {
        var folderPath: String = this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.FOLDER_PATH, "NULL") ?: "NULL"
        if (isAudio) {
            folderPath = this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.FOLDER_AUDIO_PATH, "NULL") ?: "NULL"
        }
        if (folderPath.contentEquals("NULL")) {
            chooseDir(true, isAudio)
        } else {
            proceedRecording()
        }
    }

    fun recordingStart() {
        if (this.recordingBinder == null) {
            this.serviceToRecording = true
            doBindService()
        } else {
            if (this.recordingBinder!!.isStarted()) {
                return
            }
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), RecordingPermissionRequest.REQUEST_POST_NOTIFICATIONS.ordinal)
                return
            }
            if (((this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.CHECK_SOUND_MIC, false)) || (this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.CHECK_SOUND_PLAYBACK, false) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RecordingPermissionRequest.REQUEST_MICROPHONE_RECORD.ordinal)
                return
            }
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
                if (!this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.RECORD_MODE, false)) {
                    requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), RecordingPermissionRequest.REQUEST_STORAGE.ordinal)
                    return
                } else {
                    requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), RecordingPermissionRequest.REQUEST_STORAGE_AUDIO.ordinal)
                    return
                }
            }
            if (this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.FLOATING_CONTROLS, false) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !Settings.canDrawOverlays(this)) {
                this.appSettings!!.setBooleanProperty(GlobalProperties.PropertiesBoolean.FLOATING_CONTROLS, false)
                requestOverlayDisplayPermission()
            } else {
                checkDirRecord(this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.RECORD_MODE, false))
            }
        }
    }

    fun requestFolder(resultCode: Int, uri: Uri, toRecording: Boolean, isAudio: Boolean) {
        if (resultCode == -1) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            if (isAudio) {
                this.appSettings!!.setStringProperty(GlobalProperties.PropertiesString.FOLDER_AUDIO_PATH, uri.toString())
            } else {
                this.appSettings!!.setStringProperty(GlobalProperties.PropertiesString.FOLDER_PATH, uri.toString())
            }
            if (toRecording) {
                proceedRecording()
                return
            }
        } else {
            if (isAudio) {
                if (this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.FOLDER_AUDIO_PATH, "NULL").contentEquals("NULL")) {
                    Toast.makeText(this, R.string.error_storage_select_folder, Toast.LENGTH_SHORT).show()
                }
            } else if (this.appSettings!!.getStringProperty(GlobalProperties.PropertiesString.FOLDER_PATH, "NULL").contentEquals("NULL")) {
                Toast.makeText(this, R.string.error_storage_select_folder, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun proceedRecording() {
        if (this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.RECORD_MODE, false) && ((!this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.CHECK_SOUND_PLAYBACK, false) || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) && this.recordingBinder != null)) {
            doStartService(0, null)
        } else {
            this.requestRecordingPermission.launch(this.activityProjectionManager!!.createScreenCaptureIntent())
        }
    }

    fun resetFolder(isAudio: Boolean) {
        if (isAudio) {
            this.appSettings!!.setStringProperty(GlobalProperties.PropertiesString.FOLDER_AUDIO_PATH, "")
        } else {
            this.appSettings!!.setStringProperty(GlobalProperties.PropertiesString.FOLDER_PATH, "")
        }
        Toast.makeText(this, R.string.error_invalid_folder, Toast.LENGTH_SHORT).show()
        chooseDir(true, isAudio)
    }

    fun chooseDir(proceed: Boolean, isAudio: Boolean) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        if (proceed) {
            if (isAudio) {
                this.requestAudioFolderPermissionAndProceed.launch(intent)
                return
            } else {
                this.requestFolderPermissionAndProceed.launch(intent)
                return
            }
        }
        if (isAudio) {
            this.requestAudioFolderPermission.launch(intent)
        } else {
            this.requestFolderPermission.launch(intent)
        }
    }

    private fun requestOverlayDisplayPermission() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setCancelable(true)
        builder.setTitle(R.string.overlay_notice_title)
        builder.setMessage(R.string.overlay_notice_description)
        builder.setPositiveButton(R.string.overlay_notice_button) { dialogInterface, i ->
            this@MainActivity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$appName")))
        }
        this.dialog = builder.create()
        this.dialog?.show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (RecordingPermissionRequest.entries[requestCode]) {
            RecordingPermissionRequest.REQUEST_MICROPHONE -> {
                if (grantResults[0] == 0) {
                    this.appSettings!!.setBooleanProperty(GlobalProperties.PropertiesBoolean.CHECK_SOUND_MIC, true)
                } else {
                    Toast.makeText(this, R.string.error_audio_required, Toast.LENGTH_SHORT).show()
                }
            }
            RecordingPermissionRequest.REQUEST_MICROPHONE_PLAYBACK -> {
                if (grantResults[0] == 0) {
                    this.appSettings!!.setBooleanProperty(GlobalProperties.PropertiesBoolean.CHECK_SOUND_PLAYBACK, true)
                } else {
                    Toast.makeText(this, R.string.error_audio_required, Toast.LENGTH_SHORT).show()
                }
            }
            RecordingPermissionRequest.REQUEST_MICROPHONE_RECORD -> {
                if (grantResults[0] == 0) {
                    checkDirRecord(this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.RECORD_MODE, false))
                } else {
                    Toast.makeText(this, R.string.error_audio_required, Toast.LENGTH_SHORT).show()
                }
            }
            RecordingPermissionRequest.REQUEST_STORAGE -> {
                if (grantResults[0] == 0) {
                    checkDirRecord(false)
                } else {
                    Toast.makeText(this, R.string.error_storage_required, Toast.LENGTH_SHORT).show()
                }
            }
            RecordingPermissionRequest.REQUEST_STORAGE_AUDIO -> {
                if (grantResults[0] == 0) {
                    checkDirRecord(true)
                } else {
                    Toast.makeText(this, R.string.error_storage_required, Toast.LENGTH_SHORT).show()
                }
            }
            RecordingPermissionRequest.REQUEST_MODE_CHANGE -> {
                if (grantResults[0] == 0) {
                    setRecordMode(true)
                } else {
                    Toast.makeText(this, R.string.error_audio_required, Toast.LENGTH_SHORT).show()
                }
            }
            RecordingPermissionRequest.REQUEST_POST_NOTIFICATIONS -> {
                if (grantResults[0] == 0) {
                } else {
                    Toast.makeText(this, R.string.error_notifications_required, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
