/*
 * This code has been co-authored by an AI.
 * Model name: Qwen 3 Coder Next
 */

package com.yepgoryo.CaptureCap

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.os.Handler
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.net.Uri
import android.os.IBinder
import android.provider.DocumentsContract
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

import kotlinx.coroutines.*
import kotlin.math.*

class RecordingCropScreen : AppCompatActivity() {
    private lateinit var cropBar: RecordingCropBar

    private lateinit var playerView: PlayerView
    private lateinit var exoPlayer: ExoPlayer

    private lateinit var btnPlay: ImageButton
    private lateinit var btnPause: ImageButton
    private lateinit var btnStop: ImageButton

    private lateinit var btnCrop: Button

    private lateinit var tvStatus: TextView

    private var currentStartMs: Long = 0L
    private var currentEndMs: Long = 0L
    private var hasReachedEndOfClip = false

    private var firstInit = true

    private var restoreSelection = false

    private var isAudio = false

    private var serviceIntent: Intent? = null

    private lateinit var recordingUri: Uri

    private var zoomInBtn: ImageButton? = null
    private var zoomOutBtn: ImageButton? = null
    private var zoomScrollToSelection: ImageButton? = null

    private var cropperBinder: ScreenRecorder.VideoCropperBinder? = null

    companion object {
        const val ACTION_CONNECT_CROP: String = MainActivity.appName + ".ACTION_CONNECT_CROP"

        private const val KEY_SELECTION_START = "selection_start"
        private const val KEY_SELECTION_END = "selection_end"
        private const val KEY_RESTORE_SELECTION = "restore_selection"
    }

    private val mConnection: ServiceConnection = object: ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            cropperBinder = iBinder as ScreenRecorder.VideoCropperBinder?
            cropperBinder?.let {
                recordingUri = it.getFilePath()
            }
            if (recordingUri.toString().endsWith(".m4a")) {
                isAudio = true
            }
            playVideo(recordingUri)
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            this@RecordingCropScreen.cropperBinder = null
        }
    }

    fun doBindService() {
        val intent = Intent(this, ScreenRecorder::class.java)
        this.serviceIntent = intent
        intent.setAction(ACTION_CONNECT_CROP)
        bindService(intent, this.mConnection, 1)
    }

    fun doUnbindService() {
        if (cropperBinder != null) {
            unbindService(this.mConnection)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            restoreSelection = savedInstanceState.getBoolean(KEY_RESTORE_SELECTION)
            currentStartMs = savedInstanceState.getLong(KEY_SELECTION_START)
            currentEndMs = savedInstanceState.getLong(KEY_SELECTION_END)
        }

        var globalProperties = GlobalProperties(baseContext)
        val appSettings = globalProperties

        var statusBarHeight = 0
        val resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId)
        }

        val darkTheme: GlobalProperties.DarkThemeProperty = globalProperties.getDarkTheme(false)
        if (appSettings.getDarkTheme(true) != appSettings.getDarkTheme(false)) {
            appSettings.setDarkTheme(true, darkTheme)
        }

        super.onCreate(savedInstanceState)

        setContentView(R.layout.video_crop_screen)

        val statusbarlayout = findViewById<LinearLayout?>(R.id.statusbar)!!

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

        cropBar = findViewById(R.id.zoomableSeekbar)

        zoomInBtn = findViewById(R.id.zoomInBtn)
        zoomOutBtn = findViewById(R.id.zoomOutBtn)
        zoomScrollToSelection = findViewById(R.id.zoomScrollSelection)

        zoomInBtn?.setOnClickListener { cropBar.zoomIn() }
        zoomOutBtn?.setOnClickListener { cropBar.zoomOut() }
        zoomScrollToSelection?.setOnClickListener { cropBar.fitSelectionToView() }

        TooltipCompat.setTooltipText(zoomInBtn!!, getResources().getString(R.string.crop_zoom_in))
        TooltipCompat.setTooltipText(zoomOutBtn!!, getResources().getString(R.string.crop_zoom_out))
        TooltipCompat.setTooltipText(zoomScrollToSelection!!, getResources().getString(R.string.crop_selection))

        playerView = findViewById(R.id.player_view)
        btnPlay = findViewById(R.id.btnPlay)
        btnPause = findViewById(R.id.btnPause)
        btnStop = findViewById(R.id.btnStop)
        btnCrop = findViewById(R.id.cropVideo)
        tvStatus = findViewById(R.id.tvStatus)

        val layoutParams: LinearLayout.LayoutParams = playerView.layoutParams as LinearLayout.LayoutParams

        val recordPanel: LinearLayout = findViewById(R.id.crop_screen)
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            layoutParams.setMargins(0, 0, 0, 0)
            playerView.setLayoutParams(layoutParams)
            recordPanel.orientation = LinearLayout.HORIZONTAL
        } else {
            val marginInDp = (48 * baseContext.resources.displayMetrics.density).toInt()
            layoutParams.setMargins(0, marginInDp, 0, marginInDp)
            playerView.setLayoutParams(layoutParams)
            recordPanel.orientation = LinearLayout.VERTICAL
        }

        btnPlay.let { TooltipCompat.setTooltipText(it, getResources().getString(R.string.crop_play)) }
        btnPause.let { TooltipCompat.setTooltipText(it, getResources().getString(R.string.crop_pause)) }
        btnStop.let { TooltipCompat.setTooltipText(it, getResources().getString(R.string.crop_stop)) }


        exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.addListener(playerListener)
        playerView.player = exoPlayer

        btnPlay.setOnClickListener { playCurrentClip() }
        btnPause.setOnClickListener { exoPlayer.pause() }
        btnStop.setOnClickListener {
            cropBar.exitCursorMode()
            stopPlayback()
        }

        btnCrop.setOnClickListener {
            btnCrop.isEnabled = false

            val recordingDate: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Calendar.getInstance().getTime())
            var recordingFileName: String = "ScreenRecording_Edit_$recordingDate"

            var folderPath: String = appSettings.getStringProperty(GlobalProperties.PropertiesString.FOLDER_PATH, "")

            var fileMimeType: String

            fileMimeType = "video/mp4"

            if (isAudio) {
                recordingFileName = "AudioRecording_Edit_$recordingDate"
                fileMimeType = "audio/mp4"
            }

            val documentPath: String = Regex("^content://[^/]*/tree/").replaceFirst(folderPath, "")
            val documentParentPath: Uri = Uri.parse("$folderPath/document/$documentPath")

            var fullFilePathCreateDocument: Uri?
            var fullFilePathRenameDocument: Uri?

            fullFilePathCreateDocument = DocumentsContract.createDocument(contentResolver, documentParentPath, fileMimeType, recordingFileName)

            if (!fullFilePathCreateDocument.toString().endsWith(".m4a") && isAudio) {
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

            var recordingTrimmer = RecordingTrimmer(baseContext)

            val scope = CoroutineScope(Dispatchers.IO)

            btnCrop.setText(R.string.crop_progress)

            Thread {
                scope.launch {
                    if (isAudio) {
                        recordingTrimmer.trimAudio(
                            recordingUri,
                            fullFilePathCreateDocument!!,
                            currentStartMs,
                            currentEndMs
                        )
                    } else {
                        recordingTrimmer.trimVideo(
                            recordingUri,
                            fullFilePathCreateDocument!!,
                            currentStartMs,
                            currentEndMs
                        )
                    }

                    runOnUiThread({
                        Toast.makeText(baseContext, R.string.crop_done, Toast.LENGTH_SHORT).show()
                        btnCrop.setText(R.string.crop_do_save)
                        btnCrop.isEnabled = true
                    })
                }
            }.start()
        }

        positionChecker.run()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_SELECTION_START, currentStartMs)
        outState.putLong(KEY_SELECTION_END, currentEndMs)
        outState.putBoolean(KEY_RESTORE_SELECTION, true)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        var statusBarHeight = 0
        val resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId)
        }

        val statusbarlayout = findViewById<LinearLayout?>(R.id.statusbar)!!

        val statusbarlayoutparams: LinearLayout.LayoutParams = statusbarlayout.layoutParams as LinearLayout.LayoutParams
        statusbarlayoutparams.height = statusBarHeight
        statusbarlayout.setLayoutParams(statusbarlayoutparams)

        val player: View = findViewById(R.id.player_view)
        val layoutParams: LinearLayout.LayoutParams =
            player.layoutParams as LinearLayout.LayoutParams

        val recordPanel: LinearLayout = findViewById(R.id.crop_screen)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            layoutParams.setMargins(0, 0, 0, 0)
            player.setLayoutParams(layoutParams)
            recordPanel.orientation = LinearLayout.HORIZONTAL
        } else {
            val marginInDp = (48 * baseContext.resources.displayMetrics.density).toInt()
            layoutParams.setMargins(0, marginInDp, 0, marginInDp)
            player.setLayoutParams(layoutParams)
            recordPanel.orientation = LinearLayout.VERTICAL
        }
    }

    private fun playVideo(uri: Uri) {
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    private fun playCurrentClip() {
        hasReachedEndOfClip = false

        if (!cropBar.isCursorMode) {
            exoPlayer.seekTo(currentStartMs)

            cropBar.enterCursorMode(currentStartMs.toFloat())
            cropBar.setCursorPosition(currentStartMs.toFloat())
        }

        exoPlayer.play()
    }

    private fun stopPlayback() {
        hasReachedEndOfClip = true
        exoPlayer.pause()
    }

    private fun formatTime(value: Long): String {
        val totalMs = value
        val absMs = abs(totalMs)
        val totalSeconds = absMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millis = absMs % 1000
        return String.format("%d:%02d.%03d", minutes, seconds, millis)
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                ExoPlayer.STATE_READY -> {
                    Log.d("PlayerListener", "ExoPlayer is ready, duration is ${exoPlayer.duration}")
                    val dur = exoPlayer.duration
                    if (dur > 0) {
                        if (firstInit) {
                            Log.d("PlayerListener", "Duration: ${dur.toFloat()}")
                            cropBar.setTimelineRange(0f, dur.toFloat())
                            if (!restoreSelection) {
                                currentEndMs = dur
                            }
                        }

                        cropBar.setSelectionRange(currentStartMs.toFloat(), currentEndMs.coerceAtMost(dur).toFloat())
                        firstInit = false
                    }
                }
            }
        }
    }

    private val positionCheckerHandler = Handler()

    private val positionChecker = object : Runnable {
        var lastMinSel = currentStartMs.toFloat()
        var lastMaxSel = currentEndMs.toFloat()
        var lastCursorPos = 0f
        override fun run() {
            if (!firstInit) {
                val selection = cropBar.getSelectionRange()
                var newMinSel = selection.first
                if (newMinSel != lastMinSel) {
                    if (exoPlayer.duration > 0) {
                        val safeProgress = newMinSel.toLong().coerceIn(0L, exoPlayer.duration)

                        exoPlayer.seekTo(safeProgress)
                        currentStartMs = safeProgress
                        lastMinSel = newMinSel
                        tvStatus.text =
                            "${formatTime(currentStartMs)} - ${formatTime(currentEndMs)}"
                    }
                }

                var newMaxSel = selection.second
                if (newMaxSel != lastMaxSel) {
                    if (exoPlayer.duration > 0) {
                        val safeProgress = newMaxSel.toLong().coerceIn(0L, exoPlayer.duration)

                        exoPlayer.seekTo(safeProgress)
                        currentEndMs = safeProgress
                        lastMaxSel = newMaxSel
                        tvStatus.text = "${formatTime(currentStartMs)} - ${formatTime(currentEndMs)}"
                        Log.d("PositionChecker", "New Max Selection: $newMaxSel")
                    }
                }

                if (!exoPlayer.isPlaying) {
                    if (exoPlayer.duration > 0) {
                        var newCursorPos = cropBar.getCursorPosition()
                        if (newCursorPos != lastCursorPos) {
                            if (exoPlayer.duration > 0) {
                                exoPlayer.seekTo(newCursorPos.toLong())
                                lastCursorPos = newCursorPos
                            }
                        }
                    }
                }

                if (exoPlayer.isPlaying && !hasReachedEndOfClip) {
                    val pos = exoPlayer.currentPosition
                    cropBar.setCursorPosition(pos.toFloat())
                    Log.d(
                        "PositionChecker",
                        "Playing, currentPosition: ${exoPlayer.currentPosition}, endMS: $currentEndMs"
                    )
                    if (pos >= currentEndMs) {
                        Log.d(
                            "PositionChecker",
                            "Reached end, currentPosition: ${exoPlayer.currentPosition}, endMS: $currentEndMs"
                        )
                        stopPlayback()
                    }
                }
            }

            positionCheckerHandler.postDelayed(this, 100)
        }
    }

    override fun onStart() {
        super.onStart()
        doBindService()
    }

    override fun onStop() {
        super.onStop()
        exoPlayer.pause()
    }

    override fun onDestroy() {
        exoPlayer.release()
        doUnbindService()
        super.onDestroy()
    }
}