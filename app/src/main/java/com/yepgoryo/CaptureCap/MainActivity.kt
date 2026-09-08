package com.yepgoryo.CaptureCap

import android.Manifest
import android.animation.ObjectAnimator
import android.content.ComponentName
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
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.Chronometer
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.RelativeLayout.LayoutParams
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.TooltipCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.flexbox.FlexboxLayout
import rikka.shizuku.Shizuku

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
    var postRecordingPanel: LinearLayout? = null
    var mainRecordingButton: RecordButton? = null
    var captureStartButton: Button? = null
    var recordOptionsPanel: LinearLayout? = null
    var recordControls: FlexboxLayout? = null
    var recordControlMuteMic: Button? = null
    var recordControlMuteAudio: Button? = null
    var recordControlMuteShizukuPhoneCall: Button? = null
    var recordControlUnmuteMic: Button? = null
    var recordControlUnmuteAudio: Button? = null
    var recordControlUnmuteShizukuPhoneCall: Button? = null
    var recordControlAdjustVolume: Button? = null
    var recordControlPause: Button? = null
    var recordControlResume: Button? = null
    var recordControlStop: Button? = null
    var optionsPanel: FrameLayout? = null


    var recordInfo: ImageButton? = null
    private var recordModeChosen: Boolean = false
    var recordSettings: ImageButton? = null

    var postRecordShare: Button? = null
    var postRecordDelete: Button? = null
    var postRecordOpen: Button? = null
    var postRecordCrop: Button? = null
    var postRecordBack: Button? = null


    private var recordingBinder: ScreenRecorder.RecordingBinder? = null
    private var serviceIntent: Intent? = null
    var timeCounter: Chronometer? = null
    var timerCountdown: Chronometer? = null
    var timerControls: LinearLayout? = null
    var preRecordControls: LinearLayout? = null
    var timerStop: Button? = null
    var preRecordStart: Button? = null
    var preRecordAbort: Button? = null
    var recordStatusMessage: TextView? = null
    var timerPanel: LinearLayout? = null
    var captureOptionStream: ImageView? = null
    var captureOptionRecord: ImageView? = null
    var captureOptionScreen: ImageView? = null
    var captureOptionMicrophone: ImageView? = null
    var captureOptionPhonecall: ImageView? = null
    var captureOptionAudio: ImageView? = null
    var captureModeMenu: LinearLayout? = null
    var recordOptionScreen: RecordSettingButton? = null
    var recordOptionSound: RecordSettingButton? = null
    var recordOptionMicrophone: RecordSettingButton? = null
    var recordOptionPhoneCall: RecordSettingButton? = null
    private var recordingState: ActionState = ActionState.RECORDING_STOPPED
    private var screenRecorderStarted: Boolean = false
    private var stateActivated: Boolean = false
    private var serviceToRecording: Boolean = false
    private var recordMicrophone: Boolean = false
    private var recordPlayback: Boolean = false
    private var recordStream: Boolean = false
    private var saveStreamToFile: Boolean = false
    private var recordOnlyAudio: Boolean = false
    private var enableShizuku: Boolean = false
    private var shizukuRecordPhoneCall: Boolean = false

    data class StreamCredentialsData(
        val url: String,
        val key: String,
        val tofile: Boolean
    )

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
        if (intent.action == ACTION_ACTIVITY_START_RECORDING && this.stateActivated) {
            this@MainActivity.finish()
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

    private val shizukuPermissionListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            if (requestCode == RecordingPermissionRequest.REQUEST_SHIZUKU.ordinal) {
                Shizuku.removeRequestPermissionResultListener(this)

                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                } else {
                    Toast.makeText(this@MainActivity, R.string.error_shizuku_required, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    enum class ActionState {
        RECORDING_STOPPED,
        RECORDING_IN_PROGRESS,
        RECORDING_PAUSED,
        RECORDING_ENDED
    }

    enum class RecordingPermissionRequest {
        REQUEST_MICROPHONE,
        REQUEST_MICROPHONE_PLAYBACK,
        REQUEST_MICROPHONE_RECORD,
        REQUEST_CAMERA,
        REQUEST_STORAGE,
        REQUEST_STORAGE_AUDIO,
        REQUEST_MODE_CHANGE,
        REQUEST_POST_NOTIFICATIONS,
        REQUEST_SHIZUKU,
    }

    fun showCounter(starting: Boolean, buttonState: RecordButton.ButtonState) {
        if (starting) {
            this.timeCounter!!.scaleX = 0.0f
            this.timeCounter!!.scaleY = 0.0f
            timeCounter!!.visibility = View.VISIBLE
            this@MainActivity.mainRecordingButton!!.setButtonState(buttonState)
            val animateCounterX: ObjectAnimator = ObjectAnimator.ofFloat(this.timeCounter, "scaleX", 0.0f, 1.0f)
            val animateCounterY: ObjectAnimator = ObjectAnimator.ofFloat(this.timeCounter, "scaleY", 0.0f, 1.0f)
            animateCounterX.setDuration(400L)
            animateCounterY.setDuration(400L)
            animateCounterX.start()
            animateCounterY.start()
        } else {
            this.timeCounter!!.scaleX = 1.0f
            this.timeCounter!!.scaleY = 1.0f
            timeCounter!!.visibility = View.VISIBLE
            val animateCounterX: ObjectAnimator = ObjectAnimator.ofFloat(this.timeCounter, "scaleX", 1.0f, 0.0f)
            val animateCounterY: ObjectAnimator = ObjectAnimator.ofFloat(this.timeCounter, "scaleY", 1.0f, 0.0f)
            animateCounterX.addUpdateListener { valueAnimator ->
                if ((valueAnimator.getAnimatedValue() as Float) == 0.0f) {
                    timeCounter!!.visibility = View.GONE
                    this@MainActivity.mainRecordingButton!!.transitionToButtonState(buttonState)
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

    private fun updateCaptureOptionsIcons() {
        captureOptionStream?.isVisible = this.recordStream
        captureOptionRecord?.isVisible = !this.recordStream
        captureOptionScreen?.isVisible = !this.recordOnlyAudio
        captureOptionAudio?.isVisible = this.recordPlayback
        captureOptionMicrophone?.isVisible = this.recordMicrophone
        captureOptionPhonecall?.isVisible = (this.shizukuRecordPhoneCall && this.enableShizuku)
    }

    private fun updateRecordModeData() {
        this.recordMicrophone = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.CHECK_SOUND_MIC, false)
        this.recordPlayback = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.CHECK_SOUND_PLAYBACK, false)
        this.recordStream = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.CHECK_STREAM, false)
        this.saveStreamToFile = appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.STREAM_SAVE_TO_FILE, false)
        if (this.recordPlayback && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            this.recordPlayback = false
            this.recordStream = false
        }
        this.recordOnlyAudio = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.RECORD_MODE, false)
        this.enableShizuku = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.SHIZUKU_ENABLE, false)
        this.shizukuRecordPhoneCall = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.SHIZUKU_RECORD_PHONECALL, false)
        if (this.recordOnlyAudio && !this.recordPlayback && !this.recordMicrophone) {
            this.recordOnlyAudio = false
        }
    }

    inner class ActivityBinder : Binder() {
        fun preRecordingStart() {
            runOnUiThread {
                recordStatusMessage!!.visibility = View.GONE
                recordOptionsFullPanel!!.visibility = View.GONE
                this@MainActivity.optionsPanel!!.visibility = View.GONE
                captureStartButton!!.isVisible = false
                mainRecordingButton!!.innerButton().isVisible = false
                recordOptionsFullPanel!!.isVisible = false
                recordControls!!.visibility = View.GONE

                preRecordControls!!.visibility = View.VISIBLE
            }
        }

        fun timerStart(timerEnd: Long) {
            runOnUiThread {
                recordStatusMessage!!.visibility = View.GONE
                recordOptionsFullPanel!!.visibility = View.GONE
                this@MainActivity.optionsPanel!!.visibility = View.GONE
                captureStartButton!!.isVisible = false
                mainRecordingButton!!.innerButton().isVisible = false
                recordOptionsFullPanel!!.isVisible = false
                recordControls!!.visibility = View.GONE

                timerControls!!.visibility = View.VISIBLE
                timerCountdown!!.stop()
                timerCountdown!!.setBase(timerEnd)
                timerCountdown!!.start()
            }
        }

        fun recordingStart(stateToRestore: Boolean) {
            runOnUiThread {
                timerControls!!.visibility = View.GONE
                preRecordControls!!.visibility = View.GONE
                timerCountdown!!.stop()
                this@MainActivity.timeCounter!!.stop()
                this@MainActivity.timeCounter!!.setBase(this@MainActivity.recordingBinder!!.getTimeStart())
                this@MainActivity.timeCounter!!.start()
                this@MainActivity.audioPlaybackUnavailable!!.visibility = View.GONE
                recordControls!!.visibility = View.VISIBLE
                if (recordStream) {
                    recordControlPause!!.isVisible = false
                    recordControlResume!!.isVisible = false
                } else {
                    recordControlPause!!.isVisible = true
                    recordControlResume!!.isVisible = false
                }
                recordStatusMessage!!.visibility = View.VISIBLE
                recordOptionsFullPanel!!.visibility = View.GONE
                this@MainActivity.optionsPanel!!.visibility = View.GONE
                this@MainActivity.recordingState = ActionState.RECORDING_IN_PROGRESS
                captureStartButton!!.isVisible = false
                mainRecordingButton!!.innerButton().isVisible = true
                recordOptionsFullPanel!!.isVisible = false
                if (this@MainActivity.recordingBinder!!.recordMic() || this@MainActivity.recordingBinder!!.recordAudio()) {
                    recordControlAdjustVolume!!.visibility = View.VISIBLE
                } else {
                    recordControlAdjustVolume!!.visibility = View.GONE
                }
                if (this@MainActivity.recordingBinder!!.recordMic() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (this@MainActivity.recordingBinder!!.micMuted()) {
                        recordControlMuteMic!!.visibility = View.GONE
                        recordControlUnmuteMic!!.visibility = View.VISIBLE
                    } else {
                        recordControlMuteMic!!.visibility = View.VISIBLE
                        recordControlUnmuteMic!!.visibility = View.GONE
                    }
                } else {
                    recordControlMuteMic!!.visibility = View.GONE
                    recordControlUnmuteMic!!.visibility = View.GONE
                }
                if (this@MainActivity.recordingBinder!!.recordAudio() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (this@MainActivity.recordingBinder!!.audioMuted()) {
                        recordControlMuteAudio!!.visibility = View.GONE
                        recordControlUnmuteAudio!!.visibility = View.VISIBLE
                    } else {
                        recordControlMuteAudio!!.visibility = View.VISIBLE
                        recordControlUnmuteAudio!!.visibility = View.GONE
                    }
                } else {
                    recordControlMuteAudio!!.visibility = View.GONE
                    recordControlUnmuteAudio!!.visibility = View.GONE
                }
                if (this@MainActivity.recordingBinder!!.recordShizukuPhoneCall() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (this@MainActivity.recordingBinder!!.shizukuPhoneCallMuted()) {
                        recordControlMuteShizukuPhoneCall!!.visibility = View.GONE
                        recordControlUnmuteShizukuPhoneCall!!.visibility = View.VISIBLE
                    } else {
                        recordControlMuteShizukuPhoneCall!!.visibility = View.VISIBLE
                        recordControlUnmuteShizukuPhoneCall!!.visibility = View.GONE
                    }
                } else {
                    recordControlMuteShizukuPhoneCall!!.visibility = View.GONE
                    recordControlUnmuteShizukuPhoneCall!!.visibility = View.GONE
                }
                if (recordStream) {
                    if (recordOnlyAudio) {
                        recordStatusMessage!!.setText(R.string.streaming_audio_started_text)
                    } else {
                        recordStatusMessage!!.setText(R.string.streaming_started_text)
                    }
                } else {
                    if (recordOnlyAudio) {
                        recordStatusMessage!!.setText(R.string.recording_audio_started_text)
                    } else {
                        recordStatusMessage!!.setText(R.string.recording_started_text)
                    }
                }
                if (stateToRestore) {
                    this@MainActivity.showCounter(
                        true,
                        RecordButton.ButtonState.WHILE_RECORDING_NORMAL
                    )
                } else {
                    this@MainActivity.timeCounter!!.scaleX = 1.0f
                    this@MainActivity.timeCounter!!.scaleY = 1.0f
                    timeCounter!!.visibility = View.VISIBLE
                    this@MainActivity.mainRecordingButton!!.transitionToButtonState(RecordButton.ButtonState.WHILE_RECORDING_NORMAL)
                }
            }
        }

        fun recordingStop(stateToRestore: Boolean) {
            runOnUiThread {
                timerControls!!.visibility = View.GONE
                preRecordControls!!.visibility = View.GONE
                timerCountdown!!.stop()
                this@MainActivity.timeCounter!!.stop()
                this@MainActivity.timeCounter!!.setBase(SystemClock.elapsedRealtime())
                this@MainActivity.audioPlaybackUnavailable!!.visibility = View.GONE
                if (recordStream) {
                    if (recordOnlyAudio) {
                        recordStatusMessage!!.setText(R.string.streaming_audio_finished_text)
                    } else {
                        recordStatusMessage!!.setText(R.string.streaming_finished_text)
                    }
                } else {
                    if (recordOnlyAudio) {
                        recordStatusMessage!!.setText(R.string.recording_audio_finished_text)
                    } else {
                        recordStatusMessage!!.setText(R.string.recording_finished_text)
                    }
                }
                recordControls!!.visibility = View.GONE
                recordOptionsFullPanel!!.visibility = View.GONE
                this@MainActivity.optionsPanel!!.visibility = View.GONE
                postRecordingPanel!!.visibility = View.VISIBLE
                captureStartButton!!.visibility = View.GONE
                mainRecordingButton!!.innerButton().visibility = View.VISIBLE
                recordStatusMessage!!.visibility = View.VISIBLE
                if (recordStream && !saveStreamToFile) {
                    postRecordingPanel!!.visibility = View.GONE
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    this@MainActivity.audioPlaybackUnavailable!!.visibility = View.VISIBLE
                }
                this@MainActivity.recordingState = ActionState.RECORDING_ENDED
                if (stateToRestore) {
                    this@MainActivity.showCounter(
                        false,
                        RecordButton.ButtonState.TRANSITION_TO_RECORDING_END
                    )
                } else {
                    timeCounter!!.visibility = View.GONE
                    this@MainActivity.mainRecordingButton!!.transitionToButtonState(RecordButton.ButtonState.ENDED_RECORDING_NORMAL)
                    this@MainActivity.recordingState = ActionState.RECORDING_ENDED
                }
            }
        }

        fun recordingPause(j: Long, stateToRestore: Boolean) {
            runOnUiThread {
                timerControls!!.visibility = View.GONE
                preRecordControls!!.visibility = View.GONE
                timerCountdown!!.stop()
                this@MainActivity.timeCounter!!.setBase(SystemClock.elapsedRealtime() - j)
                this@MainActivity.timeCounter!!.stop()
                recordOptionsFullPanel!!.visibility = View.GONE
                this@MainActivity.optionsPanel!!.visibility = View.GONE
                this@MainActivity.timeCounter!!.scaleX = 1.0f
                this@MainActivity.timeCounter!!.scaleY = 1.0f
                timeCounter!!.visibility = View.VISIBLE
                captureStartButton!!.visibility = View.GONE
                mainRecordingButton!!.innerButton().visibility = View.VISIBLE
                recordStatusMessage!!.visibility = View.VISIBLE
                recordControls!!.visibility = View.VISIBLE
                recordControlMuteMic!!.visibility = View.GONE
                recordControlMuteAudio!!.visibility = View.GONE
                recordControlMuteShizukuPhoneCall!!.visibility = View.GONE
                recordControlUnmuteMic!!.visibility = View.GONE
                recordControlUnmuteAudio!!.visibility = View.GONE
                recordControlUnmuteShizukuPhoneCall!!.visibility = View.GONE
                recordControlAdjustVolume!!.visibility = View.GONE
                recordControlPause!!.visibility = View.GONE
                recordControlResume!!.visibility = View.VISIBLE
                this@MainActivity.recordingState = ActionState.RECORDING_PAUSED
                if (!recordStream) {
                    if (recordOnlyAudio) {
                        recordStatusMessage!!.setText(R.string.recording_audio_paused_text)
                    } else {
                        recordStatusMessage!!.setText(R.string.recording_paused_text)
                    }
                }
                if (stateToRestore) {
                    this@MainActivity.mainRecordingButton!!.transitionToButtonState(RecordButton.ButtonState.TRANSITION_TO_RECORDING_PAUSE)
                } else {
                    this@MainActivity.mainRecordingButton!!.transitionToButtonState(RecordButton.ButtonState.WHILE_PAUSE_NORMAL)
                    this@MainActivity.recordingState = ActionState.RECORDING_PAUSED
                }
            }
        }

        fun recordingResume(time: Long) {
            runOnUiThread {
                timerControls!!.visibility = View.GONE
                preRecordControls!!.visibility = View.GONE
                timerCountdown!!.stop()
                this@MainActivity.timeCounter!!.setBase(time)
                this@MainActivity.timeCounter!!.start()

                if (recordStream) {
                    if (recordOnlyAudio) {
                        recordStatusMessage!!.setText(R.string.streaming_audio_started_text)
                    } else {
                        recordStatusMessage!!.setText(R.string.streaming_started_text)
                    }
                } else {
                    if (recordOnlyAudio) {
                        recordStatusMessage!!.setText(R.string.recording_audio_started_text)
                    } else {
                        recordStatusMessage!!.setText(R.string.recording_started_text)
                    }
                }
                if (this@MainActivity.recordingBinder!!.recordMic() || this@MainActivity.recordingBinder!!.recordAudio()) {
                    recordControlAdjustVolume!!.visibility = View.VISIBLE
                } else {
                    recordControlAdjustVolume!!.visibility = View.GONE
                }
                if (this@MainActivity.recordingBinder!!.recordMic() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (this@MainActivity.recordingBinder!!.micMuted()) {
                        recordControlMuteMic!!.visibility = View.GONE
                        recordControlUnmuteMic!!.visibility = View.VISIBLE
                    } else {
                        recordControlMuteMic!!.visibility = View.VISIBLE
                        recordControlUnmuteMic!!.visibility = View.GONE
                    }
                } else {
                    recordControlMuteMic!!.visibility = View.GONE
                    recordControlUnmuteMic!!.visibility = View.GONE
                }
                if (this@MainActivity.recordingBinder!!.recordAudio() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (this@MainActivity.recordingBinder!!.audioMuted()) {
                        recordControlMuteAudio!!.visibility = View.GONE
                        recordControlUnmuteAudio!!.visibility = View.VISIBLE
                    } else {
                        recordControlMuteAudio!!.visibility = View.VISIBLE
                        recordControlUnmuteAudio!!.visibility = View.GONE
                    }
                } else {
                    recordControlMuteAudio!!.visibility = View.GONE
                    recordControlUnmuteAudio!!.visibility = View.GONE
                }
                if (this@MainActivity.recordingBinder!!.recordShizukuPhoneCall() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (this@MainActivity.recordingBinder!!.shizukuPhoneCallMuted()) {
                        recordControlMuteShizukuPhoneCall!!.visibility = View.GONE
                        recordControlUnmuteShizukuPhoneCall!!.visibility = View.VISIBLE
                    } else {
                        recordControlMuteShizukuPhoneCall!!.visibility = View.VISIBLE
                        recordControlUnmuteShizukuPhoneCall!!.visibility = View.GONE
                    }
                } else {
                    recordControlMuteShizukuPhoneCall!!.visibility = View.GONE
                    recordControlUnmuteShizukuPhoneCall!!.visibility = View.GONE
                }
                recordControlPause!!.visibility = View.VISIBLE
                recordControlResume!!.visibility = View.GONE
                this@MainActivity.mainRecordingButton!!.transitionToButtonState(RecordButton.ButtonState.TRANSITION_FROM_PAUSE)
                this@MainActivity.recordingState = ActionState.RECORDING_IN_PROGRESS
            }
        }

        fun recordingReset() {
            timerControls!!.visibility = View.GONE
            preRecordControls!!.visibility = View.GONE
            timerCountdown!!.stop()
            postRecordingPanel!!.visibility = View.GONE
            this@MainActivity.optionsPanel!!.visibility = View.VISIBLE
            recordStatusMessage!!.visibility = View.GONE
            mainRecordingButton!!.innerButton().isVisible = false
            captureStartButton!!.isVisible = true
            recordOptionsFullPanel!!.isVisible = true
            this@MainActivity.mainRecordingButton!!.transitionToButtonState(RecordButton.ButtonState.TRANSITION_TO_RESTART)
            postRecordingPanel!!.visibility = View.GONE
            this@MainActivity.optionsPanel!!.visibility = View.VISIBLE
            this@MainActivity.recordingState = ActionState.RECORDING_STOPPED
        }

        fun resetDir(audio: Boolean) {
            this@MainActivity.resetFolder(audio)
        }

        fun updateSoundSwitchButtons() {
            runOnUiThread {
                if (this@MainActivity.recordingBinder!!.recordMic() || this@MainActivity.recordingBinder!!.recordAudio()) {
                    recordControlAdjustVolume!!.visibility = View.VISIBLE
                } else {
                    recordControlAdjustVolume!!.visibility = View.GONE
                }
                if (this@MainActivity.recordingBinder!!.recordMic() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (this@MainActivity.recordingBinder!!.micMuted()) {
                        recordControlMuteMic!!.visibility = View.GONE
                        recordControlUnmuteMic!!.visibility = View.VISIBLE
                    } else {
                        recordControlMuteMic!!.visibility = View.VISIBLE
                        recordControlUnmuteMic!!.visibility = View.GONE
                    }
                } else {
                    recordControlMuteMic!!.visibility = View.GONE
                    recordControlUnmuteMic!!.visibility = View.GONE
                }

                if (this@MainActivity.recordingBinder!!.recordAudio() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (this@MainActivity.recordingBinder!!.audioMuted()) {
                        recordControlMuteAudio!!.visibility = View.GONE
                        recordControlUnmuteAudio!!.visibility = View.VISIBLE
                    } else {
                        recordControlMuteAudio!!.visibility = View.VISIBLE
                        recordControlUnmuteAudio!!.visibility = View.GONE
                    }
                } else {
                    recordControlMuteAudio!!.visibility = View.GONE
                    recordControlUnmuteAudio!!.visibility = View.GONE
                }
                if (this@MainActivity.recordingBinder!!.recordShizukuPhoneCall() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (this@MainActivity.recordingBinder!!.shizukuPhoneCallMuted()) {
                        recordControlMuteShizukuPhoneCall!!.visibility = View.GONE
                        recordControlUnmuteShizukuPhoneCall!!.visibility = View.VISIBLE
                    } else {
                        recordControlMuteShizukuPhoneCall!!.visibility = View.VISIBLE
                        recordControlUnmuteShizukuPhoneCall!!.visibility = View.GONE
                    }
                } else {
                    recordControlMuteShizukuPhoneCall!!.visibility = View.GONE
                    recordControlUnmuteShizukuPhoneCall!!.visibility = View.GONE
                }
            }
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

    var recordOptionsButtonIcons: RelativeLayout? = null
    var recordOptionsButtonText: TextView? = null
    var recordOptionsButton: RecordOptionsButton? = null
    var recordOptionsFullPanel: LinearLayout? = null
    var captureOptionsPanel: LinearLayout? = null

    private var recordOptionsOpen = false
    private var menuHiddenTopMargin = 0

    private fun toggleMenu() {
        if (recordOptionsOpen) {
            closeMenu()
        } else {
            openMenu()
        }
    }

    /*
     * This function has been co-authored by an AI.
     * Model name: Qwen 3 Coder Next
     */
    private fun openMenu() {
        val display = (baseContext.getSystemService("display") as DisplayManager).getDisplay(0)
        if (display.rotation == Surface.ROTATION_270 || display.rotation == Surface.ROTATION_90) {
            captureStartButton!!.isVisible = false
        }
        recordOptionsOpen = true
        captureOptionsPanel!!.translationY = -captureOptionsPanel!!.measuredHeight.toFloat()
        captureOptionsPanel!!.animate().translationY(0F).setDuration(300L).withStartAction {
            recordOptionsButton!!.setupBackgroundOpened()
            captureOptionsPanel!!.isVisible = true
        }.start()
    }

    /*
     * This function has been co-authored by an AI.
     * Model name: Qwen 3 Coder Next
     */
    private fun closeMenu() {
        recordOptionsOpen = false
        menuHiddenTopMargin = -captureOptionsPanel!!.height
        recordOptionsButton!!.setupBackground()
        captureOptionsPanel!!.animate().translationY(menuHiddenTopMargin.toFloat()).setDuration(300L).withEndAction {
            captureOptionsPanel!!.isVisible = false
            captureStartButton!!.isVisible = true
        }.start()
    }

    override fun onCreate(bundle: Bundle?) {
        val splashScreen = installSplashScreen()
        val display: Display =
            (baseContext.getSystemService("display") as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
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
        when (darkTheme) {
            GlobalProperties.DarkThemeProperty.DARK -> {
                if ((getResources().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                }
            }
            GlobalProperties.DarkThemeProperty.LIGHT -> {
                if ((getResources().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_NO) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
        super.onCreate(bundle)

        setContentView(R.layout.main)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            findViewById<LinearLayout>(R.id.statusbar).visibility = View.GONE
        }

        var statusBarHeight = 0
        var resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId)
        }

        val statusbarlayout = findViewById<LinearLayout?>(R.id.statusbar)
        val statusbarlayoutparams: FrameLayout.LayoutParams = statusbarlayout.layoutParams as FrameLayout.LayoutParams
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

        val recordPanel: LinearLayout = findViewById(R.id.mainlayout)
        if ((rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) && displayMetrics.widthPixels > displayMetrics.heightPixels) {
            recordPanel.orientation = LinearLayout.HORIZONTAL
        } else {
            recordPanel.orientation = LinearLayout.VERTICAL
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
        } else {
        }
        this.mainRecordingButton = RecordButton(baseContext, findViewById<ImageButton>(R.id.recordingmainbutton)!!)
        this.captureStartButton = findViewById<Button>(R.id.capture_start_button)
        this.captureStartButton!!.setOnClickListener(object: View.OnClickListener {
            override fun onClick(view: View) {
                if (this@MainActivity.recordingState == ActionState.RECORDING_ENDED) {
                    this@MainActivity.recordingBinder!!.recordingReset()
                } else if (this@MainActivity.recordingState == ActionState.RECORDING_PAUSED) {
                    this@MainActivity.mainRecordingButton!!.transitionToButtonState(RecordButton.ButtonState.TRANSITION_FROM_PAUSE)
                    this@MainActivity.recordingBinder!!.recordingResume()
                } else if (this@MainActivity.recordingState != ActionState.RECORDING_STOPPED) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !recordStream) {
                        this@MainActivity.recordingBinder!!.recordingPause()
                    } else {
                        this@MainActivity.recordingBinder!!.stopService()
                    }
                } else {
                    this@MainActivity.recordingStart()
                }
            }
        })
        updateRecordButtonConditions()
        this.mainRecordingButton!!.innerButton().setOnClickListener(object: View.OnClickListener {
            override fun onClick(view: View) {
                if (!this@MainActivity.mainRecordingButton!!.getLockButton()) {
                    this@MainActivity.mainRecordingButton!!.setLockButton(true)

                    if (this@MainActivity.recordingState == ActionState.RECORDING_ENDED) {
                        this@MainActivity.recordingBinder!!.recordingReset()
                    } else if (this@MainActivity.recordingState == ActionState.RECORDING_PAUSED) {
                        this@MainActivity.mainRecordingButton!!.transitionToButtonState(RecordButton.ButtonState.TRANSITION_FROM_PAUSE)
                        this@MainActivity.recordingBinder!!.recordingResume()
                    } else if (this@MainActivity.recordingState != ActionState.RECORDING_STOPPED) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !recordStream) {
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
        findViewById<LinearLayout>(R.id.mainlayout).setOnClickListener { this@MainActivity.mainRecordingButton!!.releaseFocus() }
        this.recordInfo = findViewById<ImageButton>(R.id.openinfo)!!
        this.recordSettings = findViewById<ImageButton>(R.id.opensettings)!!
        postRecordShare = findViewById<Button>(R.id.post_record_controls_share)
        postRecordDelete = findViewById<Button>(R.id.post_record_controls_delete)
        postRecordOpen = findViewById<Button>(R.id.post_record_controls_open)
        postRecordCrop = findViewById<Button>(R.id.post_record_controls_crop)
        postRecordBack = findViewById<Button>(R.id.post_record_controls_back)

        captureOptionStream = findViewById<ImageView>(R.id.record_options_button_icon_stream)!!
        captureOptionRecord = findViewById<ImageView>(R.id.record_options_button_icon_record)!!
        captureOptionScreen = findViewById<ImageView>(R.id.record_options_button_icon_screen)!!
        captureOptionAudio = findViewById<ImageView>(R.id.record_options_button_icon_audio_playback)!!
        captureOptionMicrophone = findViewById<ImageView>(R.id.record_options_button_icon_microphone)!!
        captureOptionPhonecall = findViewById<ImageView>(R.id.record_options_button_icon_phonecall)!!

        updateCaptureOptionsIcons()

        recordOptionsButtonIcons = findViewById<RelativeLayout>(R.id.record_options_button_icons)!!
        recordOptionsButtonText = findViewById<TextView>(R.id.record_options_button_text)!!
        recordOptionsButton = findViewById<RecordOptionsButton>(R.id.record_options_button)!!
        recordOptionsFullPanel = findViewById<LinearLayout>(R.id.record_options)!!
        captureOptionsPanel = findViewById<LinearLayout>(R.id.capture_options)!!

        captureOptionsPanel = findViewById(R.id.capture_options)

        captureOptionsPanel!!.post {
            captureOptionsPanel!!.measure(0, 0)
            menuHiddenTopMargin = -captureOptionsPanel!!.height
            captureOptionsPanel!!.translationY = menuHiddenTopMargin.toFloat()
            captureOptionsPanel!!.isVisible = recordOptionsOpen
        }


        captureOptionsPanel = findViewById<LinearLayout>(R.id.capture_options)


        captureOptionsPanel!!.isVisible = recordOptionsOpen

        recordOptionsButton!!.setOnClickListener {
            updateCaptureOptionsIcons()
            toggleMenu()

            recordOptionsButtonIcons!!.isVisible = !recordOptionsOpen

            if (recordOptionsOpen) {
                val params: LayoutParams = recordOptionsButtonText!!.layoutParams!! as LayoutParams

                params.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                params.addRule(RelativeLayout.CENTER_IN_PARENT)

                recordOptionsButtonText!!.layoutParams = params
            } else {
                val params: LayoutParams = recordOptionsButtonText!!.layoutParams!! as LayoutParams

                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                params.removeRule(RelativeLayout.CENTER_IN_PARENT)

                recordOptionsButtonText!!.layoutParams = params
            }
        }

        captureModeMenu = findViewById<LinearLayout>(R.id.button_mode_menu)
        val recordBtn = findViewById<CaptureSwitchModeButton>(R.id.button_mode_record)
        val streamBtn = findViewById<CaptureSwitchModeButton>(R.id.button_mode_stream)

        if (this@MainActivity.recordStream) {
            streamBtn.isSelected = true
        } else {
            recordBtn.isSelected = true
        }


        recordBtn.setOnClickListener {
            if (streamBtn.isSelected) {
                streamBtn.isSelected = false
            }
            recordBtn.isSelected = true
            this@MainActivity.recordStream = false
            this@MainActivity.appSettings?.setBooleanProperty(GlobalProperties.PropertiesBoolean.CHECK_STREAM, false)
        }

        streamBtn.setOnClickListener {
            showStreamCredentialsDialog { data ->
                if (data != null) {
                    if (!data.url.isEmpty()) {
                        if (recordBtn.isSelected) {
                            recordBtn.isSelected = false
                        }
                        streamBtn.isSelected = true

                        this@MainActivity.recordStream = true
                        this@MainActivity.appSettings?.setBooleanProperty(GlobalProperties.PropertiesBoolean.CHECK_STREAM, true)
                    }
                }
            }
        }
        recordOptionsPanel = findViewById<LinearLayout>(R.id.record_options_panel)

        recordControls = findViewById<FlexboxLayout>(R.id.record_controls)
        recordControlMuteMic = findViewById<Button>(R.id.record_controls_mute_microphone)
        recordControlMuteAudio = findViewById<Button>(R.id.record_controls_mute_audio)
        recordControlMuteShizukuPhoneCall = findViewById<Button>(R.id.record_controls_mute_phonecall)
        recordControlUnmuteMic = findViewById<Button>(R.id.record_controls_unmute_microphone)
        recordControlUnmuteAudio = findViewById<Button>(R.id.record_controls_unmute_audio)
        recordControlUnmuteShizukuPhoneCall = findViewById<Button>(R.id.record_controls_unmute_phonecall)
        recordControlAdjustVolume = findViewById<Button>(R.id.record_controls_adjust_volume)
        recordControlPause = findViewById<Button>(R.id.record_controls_pause)
        recordControlResume = findViewById<Button>(R.id.record_controls_resume)
        recordControlStop = findViewById<Button>(R.id.record_controls_stop)

        if (recordStream) {
            recordControlPause!!.isVisible = false
            recordControlResume!!.isVisible = false
        }

        recordControlMuteMic!!.setOnClickListener {
            if (this@MainActivity.recordingBinder!!.recordMic()) {
                if (this@MainActivity.recordingBinder!!.micMuted()) {
                    this@MainActivity.recordingBinder!!.unmuteMic()
                    recordControlMuteMic!!.visibility = View.VISIBLE
                    recordControlUnmuteMic!!.visibility = View.GONE
                } else {
                    this@MainActivity.recordingBinder!!.muteMic()
                    recordControlMuteMic!!.visibility = View.GONE
                    recordControlUnmuteMic!!.visibility = View.VISIBLE
                }
            } else {
                recordControlMuteMic!!.visibility = View.GONE
                recordControlUnmuteMic!!.visibility = View.GONE
            }
        }

        recordControlMuteAudio!!.setOnClickListener {
            if (this@MainActivity.recordingBinder!!.recordAudio()) {
                if (this@MainActivity.recordingBinder!!.audioMuted()) {
                    this@MainActivity.recordingBinder!!.unmuteAudio()
                    recordControlMuteAudio!!.visibility = View.VISIBLE
                    recordControlUnmuteAudio!!.visibility = View.GONE
                } else {
                    this@MainActivity.recordingBinder!!.muteAudio()
                    recordControlMuteAudio!!.visibility = View.GONE
                    recordControlUnmuteAudio!!.visibility = View.VISIBLE
                }
            } else {
                recordControlMuteAudio!!.visibility = View.GONE
                recordControlUnmuteAudio!!.visibility = View.GONE
            }
        }

        recordControlMuteShizukuPhoneCall!!.setOnClickListener {
            if (this@MainActivity.recordingBinder!!.recordShizukuPhoneCall()) {
                if (this@MainActivity.recordingBinder!!.shizukuPhoneCallMuted()) {
                    this@MainActivity.recordingBinder!!.unmuteShizukuPhoneCall()
                    recordControlMuteShizukuPhoneCall!!.visibility = View.VISIBLE
                    recordControlUnmuteShizukuPhoneCall!!.visibility = View.GONE
                } else {
                    this@MainActivity.recordingBinder!!.muteShizukuPhoneCall()
                    recordControlMuteShizukuPhoneCall!!.visibility = View.GONE
                    recordControlUnmuteShizukuPhoneCall!!.visibility = View.VISIBLE
                }
            } else {
                recordControlMuteShizukuPhoneCall!!.visibility = View.GONE
                recordControlUnmuteShizukuPhoneCall!!.visibility = View.GONE
            }
        }

        recordControlUnmuteMic!!.setOnClickListener {
            if (this@MainActivity.recordingBinder!!.recordMic()) {
                if (this@MainActivity.recordingBinder!!.micMuted()) {
                    this@MainActivity.recordingBinder!!.unmuteMic()
                    recordControlMuteMic!!.visibility = View.VISIBLE
                    recordControlUnmuteMic!!.visibility = View.GONE
                } else {
                    this@MainActivity.recordingBinder!!.muteMic()
                    recordControlMuteMic!!.visibility = View.GONE
                    recordControlUnmuteMic!!.visibility = View.VISIBLE
                }
            } else {
                recordControlMuteMic!!.visibility = View.GONE
                recordControlUnmuteMic!!.visibility = View.GONE
            }
        }

        recordControlUnmuteAudio!!.setOnClickListener {
            if (this@MainActivity.recordingBinder!!.recordAudio()) {
                if (this@MainActivity.recordingBinder!!.audioMuted()) {
                    this@MainActivity.recordingBinder!!.unmuteAudio()
                    recordControlMuteAudio!!.visibility = View.VISIBLE
                    recordControlUnmuteAudio!!.visibility = View.GONE
                } else {
                    this@MainActivity.recordingBinder!!.muteAudio()
                    recordControlMuteAudio!!.visibility = View.GONE
                    recordControlUnmuteAudio!!.visibility = View.VISIBLE
                }
            } else {
                recordControlMuteAudio!!.visibility = View.GONE
                recordControlUnmuteAudio!!.visibility = View.GONE
            }
        }

        recordControlUnmuteShizukuPhoneCall!!.setOnClickListener {
            if (this@MainActivity.recordingBinder!!.recordShizukuPhoneCall()) {
                if (this@MainActivity.recordingBinder!!.shizukuPhoneCallMuted()) {
                    this@MainActivity.recordingBinder!!.unmuteShizukuPhoneCall()
                    recordControlMuteShizukuPhoneCall!!.visibility = View.VISIBLE
                    recordControlUnmuteShizukuPhoneCall!!.visibility = View.GONE
                } else {
                    this@MainActivity.recordingBinder!!.muteShizukuPhoneCall()
                    recordControlMuteShizukuPhoneCall!!.visibility = View.GONE
                    recordControlUnmuteShizukuPhoneCall!!.visibility = View.VISIBLE
                }
            } else {
                recordControlMuteShizukuPhoneCall!!.visibility = View.GONE
                recordControlUnmuteShizukuPhoneCall!!.visibility = View.GONE
            }
        }

        recordControlAdjustVolume!!.setOnClickListener {
            showAudioVolumeDialog()
        }

        recordControlPause!!.setOnClickListener {
            this@MainActivity.recordingBinder!!.recordingPause()
            recordControlResume!!.isVisible = true
            recordControlPause!!.isVisible = false
        }

        recordControlResume!!.setOnClickListener {
            this@MainActivity.recordingBinder!!.recordingResume()
            recordControlResume!!.isVisible = false
            recordControlPause!!.isVisible = true
        }

        recordControlStop!!.setOnClickListener {
            if (!recordStream) {
                recordControlPause!!.isVisible = true
            }
            recordControlResume!!.isVisible = false
            this@MainActivity.mainRecordingButton!!.releaseFocus()
            this@MainActivity.recordingBinder!!.stopService()
        }

        recordOptionScreen = findViewById<RecordSettingButton>(R.id.record_option_screen)
        recordOptionSound = findViewById<RecordSettingButton>(R.id.record_option_audio)
        recordOptionMicrophone = findViewById<RecordSettingButton>(R.id.record_option_microphone)
        recordOptionPhoneCall = findViewById<RecordSettingButton>(R.id.record_option_phonecall)

        recordOptionScreen!!.setSwitchChecked(!recordOnlyAudio)
        recordOptionSound!!.setSwitchChecked(recordPlayback)
        recordOptionMicrophone!!.setSwitchChecked(recordMicrophone)
        recordOptionPhoneCall!!.setSwitchChecked(shizukuRecordPhoneCall)

        recordOptionScreen!!.setOnToggleListener(object: RecordSettingButton.OnToggleListener {
            override fun onToggle(isChecked: Boolean) {
                this@MainActivity.mainRecordingButton!!.releaseFocus()

                if (!isChecked && !((this@MainActivity.shizukuRecordPhoneCall && this@MainActivity.enableShizuku) || this@MainActivity.recordMicrophone || (this@MainActivity.recordPlayback || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q))) {
                    recordOptionScreen!!.setSwitchChecked(true)
                } else {
                    if (this@MainActivity.recordOnlyAudio) {
                        this@MainActivity.recordOnlyAudio = false
                        this@MainActivity.setRecordMode(false)
                    } else if (!this@MainActivity.recordOnlyAudio && (this@MainActivity.recordMicrophone || this@MainActivity.recordPlayback || (this@MainActivity.shizukuRecordPhoneCall && this@MainActivity.enableShizuku))) {
                        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && this@MainActivity.checkSelfPermission(
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED) || Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                        ) {
                            this@MainActivity.recordOnlyAudio = true
                            this@MainActivity.setRecordMode(true)
                        } else {
                            this@MainActivity.recordOnlyAudio = false
                            this@MainActivity.requestPermissions(
                                arrayOf(Manifest.permission.RECORD_AUDIO),
                                RecordingPermissionRequest.REQUEST_MODE_CHANGE.ordinal
                            )
                        }
                    }
                }
                this@MainActivity.updateRecordButtonConditions()
            }
        })

        recordOptionMicrophone!!.setOnToggleListener(object: RecordSettingButton.OnToggleListener {
            override fun onToggle(isChecked: Boolean) {
                this@MainActivity.mainRecordingButton!!.releaseFocus()

                if (!isChecked && this@MainActivity.recordOnlyAudio && !((this@MainActivity.shizukuRecordPhoneCall && this@MainActivity.enableShizuku) || (this@MainActivity.recordPlayback && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q))) {
                    recordOptionMicrophone!!.setSwitchChecked(true)
                } else {
                    if (((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && this@MainActivity.checkSelfPermission(
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED) || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) || this@MainActivity.recordMicrophone
                    ) {
                        if (!this@MainActivity.recordOnlyAudio || this@MainActivity.recordPlayback || (this@MainActivity.shizukuRecordPhoneCall && this@MainActivity.enableShizuku)) {
                            this@MainActivity.recordMicrophone = !this@MainActivity.recordMicrophone
                            this@MainActivity.appSettings!!.setBooleanProperty(
                                GlobalProperties.PropertiesBoolean.CHECK_SOUND_MIC,
                                this@MainActivity.recordMicrophone
                            )
                        }
                    } else {
                        this@MainActivity.recordMicrophone = false
                        this@MainActivity.requestPermissions(
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            RecordingPermissionRequest.REQUEST_MICROPHONE.ordinal
                        )
                    }
                }
                this@MainActivity.updateRecordButtonConditions()
            }
        })

        recordOptionSound!!.setOnToggleListener(object: RecordSettingButton.OnToggleListener {
            override fun onToggle(isChecked: Boolean) {
                this@MainActivity.mainRecordingButton!!.releaseFocus()

                if (!isChecked && this@MainActivity.recordOnlyAudio && !(this@MainActivity.recordMicrophone || (this@MainActivity.shizukuRecordPhoneCall && this@MainActivity.enableShizuku))) {
                    recordOptionSound!!.setSwitchChecked(true)
                } else {
                    if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && this@MainActivity.checkSelfPermission(
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED) || this@MainActivity.recordPlayback
                    ) {
                        if (!this@MainActivity.recordOnlyAudio || this@MainActivity.recordMicrophone || (this@MainActivity.shizukuRecordPhoneCall && this@MainActivity.enableShizuku)) {
                            this@MainActivity.recordPlayback = !this@MainActivity.recordPlayback
                            this@MainActivity.appSettings!!.setBooleanProperty(
                                GlobalProperties.PropertiesBoolean.CHECK_SOUND_PLAYBACK,
                                this@MainActivity.recordPlayback
                            )
                        }
                    } else {
                        this@MainActivity.recordPlayback = false
                        this@MainActivity.requestPermissions(
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            RecordingPermissionRequest.REQUEST_MICROPHONE_PLAYBACK.ordinal
                        )
                    }
                }
                this@MainActivity.updateRecordButtonConditions()
            }
        })

        recordOptionPhoneCall!!.setOnToggleListener(object: RecordSettingButton.OnToggleListener {
            @RequiresApi(Build.VERSION_CODES.R)
            override fun onToggle(isChecked: Boolean) {
                this@MainActivity.mainRecordingButton!!.releaseFocus()

                var hasShizukuPermission = false
                if (ShizukuConnectionHelper.shizukuAvailable()) {
                    if (ShizukuConnectionHelper.hasShizukuPermission(this@MainActivity)) {
                        hasShizukuPermission = true
                    }
                }

                if (!isChecked && this@MainActivity.recordOnlyAudio && !(this@MainActivity.recordPlayback || this@MainActivity.recordMicrophone)) {
                    recordOptionPhoneCall!!.setSwitchChecked(true)
                } else {
                    if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasShizukuPermission) || (this@MainActivity.shizukuRecordPhoneCall && this@MainActivity.enableShizuku)) {
                        if (!this@MainActivity.recordOnlyAudio || this@MainActivity.recordPlayback || this@MainActivity.recordMicrophone) {
                            this@MainActivity.shizukuRecordPhoneCall = !this@MainActivity.shizukuRecordPhoneCall
                            this@MainActivity.appSettings!!.setBooleanProperty(
                                GlobalProperties.PropertiesBoolean.SHIZUKU_RECORD_PHONECALL,
                                this@MainActivity.shizukuRecordPhoneCall
                            )
                        }
                    } else {
                        this@MainActivity.shizukuRecordPhoneCall = false
                        if (!hasShizukuPermission && ShizukuConnectionHelper.shizukuAvailable()) {
                            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
                            Shizuku.requestPermission(RecordingPermissionRequest.REQUEST_SHIZUKU.ordinal)
                        } else {
                            recordOptionPhoneCall!!.setSwitchChecked(false)
                        }
                    }
                }
                this@MainActivity.updateRecordButtonConditions()
            }
        })

        this.recordInfo!!.let { TooltipCompat.setTooltipText(it, getResources().getString(R.string.info_title)) }
        this.recordSettings!!.let { TooltipCompat.setTooltipText(it, getResources().getString(R.string.settings_title)) }

        postRecordingPanel = findViewById<LinearLayout>(R.id.post_record_controls)
        this.optionsPanel = findViewById<FrameLayout>(R.id.optionspanel)!!
        this.timeCounter = findViewById<Chronometer>(R.id.timerrecord)!!
        recordStatusMessage = findViewById<TextView>(R.id.record_status_message)!!
        this.audioPlaybackUnavailable = findViewById<TextView>(R.id.audioplaybackunavailable)!!

        this.timerCountdown = findViewById<Chronometer>(R.id.timercountdown)!!
        this.timerControls = findViewById<LinearLayout>(R.id.timer_controls)!!
        this.timerStop = findViewById<Button>(R.id.timer_stop)!!

        this.preRecordControls = findViewById<LinearLayout>(R.id.prerecord_controls)!!
        this.preRecordStart = findViewById<Button>(R.id.prerecord_start)!!
        this.preRecordAbort = findViewById<Button>(R.id.prerecord_abort)!!

        preRecordStart!!.setOnClickListener {
            recordingBinder!!.startFromPreRecord()
        }

        preRecordAbort!!.setOnClickListener {
            recordingBinder!!.abortPreRecord()
        }

        resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0) {
            val optionslayoutparams: FrameLayout.LayoutParams = optionsPanel?.layoutParams as FrameLayout.LayoutParams

            val display = (baseContext.getSystemService("display") as DisplayManager).getDisplay(0)
            if (display.rotation == Surface.ROTATION_270) {
                optionslayoutparams.bottomMargin = 0
                optionslayoutparams.rightMargin = statusBarHeight            } else if (display.rotation == Surface.ROTATION_90) {
                optionslayoutparams.bottomMargin = 0
                optionslayoutparams.rightMargin = resources.getDimensionPixelSize(resourceId)
            } else {
                optionslayoutparams.bottomMargin = resources.getDimensionPixelSize(resourceId)
                optionslayoutparams.rightMargin = 0
            }
            optionsPanel?.setLayoutParams(optionslayoutparams)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            this.audioPlaybackUnavailable!!.visibility = View.VISIBLE
            captureModeMenu!!.visibility = View.GONE
            recordOptionSound!!.visibility = View.GONE
            postRecordCrop!!.visibility = View.GONE
        }

        setRecordMode(this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.RECORD_MODE, false))
        this.activityProjectionManager = getSystemService("media_projection") as MediaProjectionManager

        timerStop!!.setOnClickListener {
            recordingBinder!!.abortTimer()
        }

        postRecordCrop!!.setOnClickListener(object: View.OnClickListener {
            override fun onClick(v: View?) {
                this@MainActivity.startActivity(Intent(this@MainActivity, RecordingCropScreen::class.java))
            }
        })

        postRecordBack!!.setOnClickListener(object: View.OnClickListener {
            override fun onClick(v: View?) {
                this@MainActivity.recordingBinder!!.recordingReset()
            }
        })

        postRecordShare!!.setOnClickListener {
            this@MainActivity.mainRecordingButton!!.releaseFocus()
            this@MainActivity.recordingBinder!!.recordingShare()
        }

        postRecordDelete!!.setOnClickListener {
            this@MainActivity.mainRecordingButton!!.releaseFocus()
            this@MainActivity.recordingBinder!!.recordingDelete()
        }

        postRecordOpen!!.setOnClickListener {
            this@MainActivity.mainRecordingButton!!.releaseFocus()
            this@MainActivity.recordingBinder!!.recordingOpen()
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

    fun showAudioVolumeDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_audio_volume, null)

        var audioVolumeScale = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.AUDIO_VOLUME, 100)
        var micVolumeScale = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.MICROPHONE_VOLUME, 100)
        var shizukuPhoneCallVolumeScale = this.appSettings!!.getIntProperty(GlobalProperties.PropertiesInt.SHIZUKU_PHONE_CALL_VOLUME, 100)

        val audioPanel: LinearLayout = dialogView.findViewById(R.id.audio_volume_panel)
        val audioVolume: TextView = dialogView.findViewById(R.id.audio_volume_value)
        val audioSeekBar: SeekBar = dialogView.findViewById(R.id.audio_volume_seek)
        audioSeekBar.progress = audioVolumeScale
        audioVolume.text = audioVolumeScale.toString()
        audioSeekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                audioVolumeScale = progress
                audioVolume.text = audioVolumeScale.toString()
            }
        })

        if (recordPlayback) {
            audioPanel.visibility = View.VISIBLE
        } else {
            audioPanel.visibility = View.GONE
        }

        val micPanel: LinearLayout = dialogView.findViewById(R.id.mic_volume_panel)
        val micVolume: TextView = dialogView.findViewById(R.id.microphone_volume_value)
        val micSeekBar: SeekBar = dialogView.findViewById(R.id.microphone_volume_seek)
        micSeekBar.progress = micVolumeScale
        micVolume.text = micVolumeScale.toString()
        micSeekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                micVolumeScale = progress
                micVolume.text = micVolumeScale.toString()
            }
        })

        if (recordMicrophone) {
            micPanel.visibility = View.VISIBLE
        } else {
            micPanel.visibility = View.GONE
        }

        val shizukuPhoneCallPanel: LinearLayout = dialogView.findViewById(R.id.phonecall_volume_panel)
        val shizukuPhoneCallVolume: TextView = dialogView.findViewById(R.id.phonecall_volume_value)
        val shizukuPhoneCallSeekBar: SeekBar = dialogView.findViewById(R.id.phonecall_volume_seek)
        shizukuPhoneCallSeekBar.progress = shizukuPhoneCallVolumeScale
        shizukuPhoneCallVolume.text = shizukuPhoneCallVolumeScale.toString()
        shizukuPhoneCallSeekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                shizukuPhoneCallVolumeScale = progress
                shizukuPhoneCallVolume.text = shizukuPhoneCallVolumeScale.toString()
            }
        })

        if (enableShizuku && shizukuRecordPhoneCall) {
            shizukuPhoneCallPanel.visibility = View.VISIBLE
        } else {
            shizukuPhoneCallPanel.visibility = View.GONE
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.audio_volume_option)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                this.appSettings?.setIntProperty(GlobalProperties.PropertiesInt.AUDIO_VOLUME, audioVolumeScale)
                this.appSettings?.setIntProperty(GlobalProperties.PropertiesInt.MICROPHONE_VOLUME, micVolumeScale)
                this.appSettings?.setIntProperty(GlobalProperties.PropertiesInt.SHIZUKU_PHONE_CALL_VOLUME, shizukuPhoneCallVolumeScale)

                if (this.recordingBinder != null) {
                    this.recordingBinder!!.setAudioVolume(audioVolumeScale)
                    this.recordingBinder!!.setMicVolume(micVolumeScale)
                    this.recordingBinder!!.setShizukuPhoneCallVolume(micVolumeScale)
                }
            }
            .setNegativeButton(R.string.dialog_cancel) { dialog, _ ->
                dialog.cancel()
            }
            .create()

        dialog.show()
    }

    /*
     * This function has been co-authored by an AI.
     * Model name: Qwen 3 Coder Next
     */
    fun showStreamCredentialsDialog(onDismiss: (StreamCredentialsData?) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_stream, null)

        val streamKey = dialogView.findViewById<EditText>(R.id.streamkey)
        val alsoRecord = dialogView.findViewById<CheckBox>(R.id.alsorecord)

        val streamUrl = dialogView.findViewById<EditText>(R.id.streamurl).apply {
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) {
                    streamKey.requestFocus()
                    true
                } else {
                    false
                }
            }
        }

        streamUrl.setText(appSettings!!.getStringProperty(GlobalProperties.PropertiesString.STREAM_URL, ""))
        streamKey.setText(appSettings!!.getPrivateStringProperty(GlobalProperties.PropertiesString.STREAM_KEY, ""))
        alsoRecord.isChecked = appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.STREAM_SAVE_TO_FILE, false)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.stream_enter_credentials)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val url = streamUrl.text?.toString().orEmpty().trim()
                val key = streamKey.text?.toString().orEmpty().trim()
                val recordtofile = alsoRecord.isChecked

                if (url.isEmpty()) {
                    onDismiss(null)
                } else {
                    appSettings!!.setStringProperty(GlobalProperties.PropertiesString.STREAM_URL, url)
                    appSettings!!.setPrivateStringProperty(GlobalProperties.PropertiesString.STREAM_KEY, key)
                    appSettings!!.setBooleanProperty(GlobalProperties.PropertiesBoolean.STREAM_SAVE_TO_FILE, recordtofile)
                    saveStreamToFile = recordtofile
                    onDismiss(StreamCredentialsData(url, key, recordtofile))
                }
            }
            .setNegativeButton(R.string.dialog_cancel) { dialog, _ ->
                dialog.cancel()
                onDismiss(null)
            }
            .setOnCancelListener { onDismiss(null) }
            .create()

        dialog.show()

        streamUrl.post {
            streamUrl.requestFocus()
        }
    }

    fun setRecordMode(audioOnly: Boolean) {
        this.appSettings!!.setBooleanProperty(GlobalProperties.PropertiesBoolean.RECORD_MODE, audioOnly)
        this.recordModeChosen = audioOnly
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        var statusBarHeight = 0
        var resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId)
        }

        val statusbarlayout = findViewById<LinearLayout?>(R.id.statusbar)!!

        val statusbarlayoutparams: FrameLayout.LayoutParams = statusbarlayout.layoutParams as FrameLayout.LayoutParams
        statusbarlayoutparams.height = statusBarHeight
        statusbarlayout.setLayoutParams(statusbarlayoutparams)

        val recordPanel: LinearLayout = findViewById(R.id.mainlayout)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            recordPanel.orientation = LinearLayout.HORIZONTAL
        } else {
            recordPanel.orientation = LinearLayout.VERTICAL
        }

        val optionslayoutparams: FrameLayout.LayoutParams = optionsPanel?.layoutParams as FrameLayout.LayoutParams

        resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")

        val display = (baseContext.getSystemService("display") as DisplayManager).getDisplay(0)

        if (recordOptionsOpen) {
            if (display.rotation == Surface.ROTATION_270 || display.rotation == Surface.ROTATION_90) {
                captureStartButton!!.visibility = View.GONE
            } else {
                captureStartButton!!.visibility = View.VISIBLE
            }
        }

        if (display.rotation == Surface.ROTATION_270) {
            optionslayoutparams.bottomMargin = 0
            optionslayoutparams.rightMargin = statusBarHeight
        } else if (display.rotation == Surface.ROTATION_90) {
            optionslayoutparams.bottomMargin = 0
            optionslayoutparams.rightMargin = resources.getDimensionPixelSize(resourceId)
        } else {
            optionslayoutparams.bottomMargin = resources.getDimensionPixelSize(resourceId)
            optionslayoutparams.rightMargin = 0
        }
        optionsPanel?.setLayoutParams(optionslayoutparams)
    }

    override fun onResume() {
        super.onResume()
        updateRecordModeData()
        updateCaptureOptionsIcons()
        if (!enableShizuku || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            recordOptionPhoneCall!!.visibility = View.GONE
        } else {
            recordOptionPhoneCall!!.visibility = View.VISIBLE
        }
    }

    override fun onStart() {
        super.onStart()
        if (this.recordingBinder == null) {
            doBindService()
        }
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

            var isHorizontal = false
            val rotation: Int = display!!.rotation
            if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                isHorizontal = true
            }

            val drawOverlay: Boolean = this.appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.DRAW_OVERLAY, false)

            if (shizukuRecordPhoneCall && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (ShizukuConnectionHelper.shizukuAvailable()) {
                    if (!ShizukuConnectionHelper.hasShizukuPermission(this)) {
                        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
                        Shizuku.requestPermission(RecordingPermissionRequest.REQUEST_SHIZUKU.ordinal)
                        return
                    }
                } else {
                    Toast.makeText(this, R.string.shizuku_waiting, Toast.LENGTH_LONG).show()
                    return
                }
            }
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && VideoOverlay.getCameraItem(baseContext, isHorizontal) != null && drawOverlay) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), RecordingPermissionRequest.REQUEST_CAMERA.ordinal)
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
            RecordingPermissionRequest.REQUEST_CAMERA -> {
                if (grantResults[0] == 0) {
                } else {
                    Toast.makeText(this, R.string.error_camera_required, Toast.LENGTH_SHORT).show()
                }
            }
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
            else -> {}
        }
    }
}
