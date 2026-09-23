package com.yepgoryo.CaptureCap

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import androidx.core.animation.doOnEnd
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.core.net.toUri
import androidx.media3.common.Player

import java.util.concurrent.atomic.AtomicBoolean

class RecordStatusVideoAnimations(
    private val context: Context,
    private val playerUse: PlayerView,
    private val playerPreviewUse: ImageView,
    private val scrollView: ScrollView,
    private val container: FrameLayout,
    micEnabled: Boolean,
    playbackEnabled: Boolean,
    shizukuPhoneCallEnabled: Boolean,
    onlyAudio: Boolean,
) {

    private val TAG = "RecordButtonVideoAnimations"
    private var appSettings: GlobalProperties? = null
    private var currentRecordStatus: RecordStatus? = null

    private var recordingInProgressVideoMicAudioPreview: Drawable? = null
    private var recordingInProgressVideoMicAudio: MediaItem? = null
    private var recordingInProgressVideoNoMicAudioPreview: Drawable? = null
    private var recordingInProgressVideoNoMicAudio: MediaItem? = null
    private var recordingInProgressVideoMicNoAudioPreview: Drawable? = null
    private var recordingInProgressVideoMicNoAudio: MediaItem? = null
    private var recordingInProgressVideoNoMicNoAudioPreview: Drawable? = null
    private var recordingInProgressVideoNoMicNoAudio: MediaItem? = null

    private var recordingInProgressAudioMicAudioPreview: Drawable? = null
    private var recordingInProgressAudioMicAudio: MediaItem? = null
    private var recordingInProgressAudioNoMicAudioPreview: Drawable? = null
    private var recordingInProgressAudioNoMicAudio: MediaItem? = null
    private var recordingInProgressAudioMicNoAudioPreview: Drawable? = null
    private var recordingInProgressAudioMicNoAudio: MediaItem? = null

    private var recordingFinishedAudioPreview: Drawable? = null
    private var recordingFinishedAudioStaticPreview: Drawable? = null
    private var recordingFinishedAudio: MediaItem? = null
    private var recordingFinishedVideoPreview: Drawable? = null
    private var recordingFinishedVideoStaticPreview: Drawable? = null
    private var recordingFinishedVideo: MediaItem? = null
    private var useContext: Context = context
    private var recordMicrophone: Boolean = false
    private var recordPlayback: Boolean = false
    private var recordShizukuPhoneCall: Boolean = false
    private var recordOnlyAudio: Boolean = false
    private var nextRecordStatus: RecordStatus? = null
    private var isReady: AtomicBoolean = AtomicBoolean(false)
    private var useAnimate: AtomicBoolean = AtomicBoolean(true)
    private var fadingEnded: AtomicBoolean = AtomicBoolean(true)
    private var isLooping: AtomicBoolean = AtomicBoolean(false)
    private var disableAnimations: Boolean = false
    var recordingAnimationPlayer: ExoPlayer? = null

    enum class RecordStatus {
        START_RECORDING,
        WHILE_RECORDING_NORMAL,
        TRANSITION_TO_RECORDING_PAUSE,
        WHILE_PAUSE_NORMAL,
        TRANSITION_FROM_PAUSE,
        TRANSITION_TO_RECORDING_END,
        END_SHOW_REELS,
        ENDED_RECORDING_NORMAL,
        TRANSITION_TO_RESTART,
    }

    private fun playerSetAnimation(item: MediaItem, repeat: Boolean, autoplay: Boolean = false) {
        isLooping.set(false)
        if (disableAnimations) {
            return
        }
        recordingAnimationPlayer!!.stop()
        if (repeat) {
            isLooping.set(true)
            recordingAnimationPlayer!!.repeatMode = ExoPlayer.REPEAT_MODE_ONE
        } else {
            recordingAnimationPlayer!!.repeatMode = ExoPlayer.REPEAT_MODE_OFF
        }
        recordingAnimationPlayer!!.playWhenReady = autoplay
        recordingAnimationPlayer!!.setMediaItems(listOf(item), 0, 0)
        isReady.set(false)
        recordingAnimationPlayer!!.prepare()
    }

    private fun updateAnimationResources() {
        disableAnimations = appSettings!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.DISABLE_ANIMATIONS, false)
        var darkTheme: GlobalProperties.DarkThemeProperty = appSettings!!.getDarkTheme(true)
        if (((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES && darkTheme == GlobalProperties.DarkThemeProperty.AUTOMATIC) || darkTheme == GlobalProperties.DarkThemeProperty.DARK) {
            recordingInProgressVideoMicAudioPreview = context.getDrawable(R.drawable.icon_recording_in_progress_video_mic_audio_dark_preview_rendered)
            recordingInProgressVideoMicAudio = MediaItem.fromUri("asset:///animations/recording_in_progress_video_mic_audio_dark.mp4".toUri())
            recordingInProgressVideoNoMicAudioPreview = context.getDrawable(R.drawable.icon_recording_in_progress_video_nomic_audio_dark_preview_rendered)
            recordingInProgressVideoNoMicAudio = MediaItem.fromUri("asset:///animations/recording_in_progress_video_nomic_audio_dark.mp4".toUri())
            recordingInProgressVideoMicNoAudioPreview = context.getDrawable(R.drawable.icon_recording_in_progress_video_mic_noaudio_dark_preview_rendered)
            recordingInProgressVideoMicNoAudio = MediaItem.fromUri("asset:///animations/recording_in_progress_video_mic_noaudio_dark.mp4".toUri())
            recordingInProgressVideoNoMicNoAudioPreview = context.getDrawable(R.drawable.icon_recording_in_progress_video_nomic_noaudio_dark_preview_rendered)
            recordingInProgressVideoNoMicNoAudio = MediaItem.fromUri("asset:///animations/recording_in_progress_video_nomic_noaudio_dark.mp4".toUri())

            recordingInProgressAudioMicAudioPreview = context.getDrawable(R.drawable.icon_recording_in_progress_audio_mic_audio_dark_preview_rendered)
            recordingInProgressAudioMicAudio = MediaItem.fromUri("asset:///animations/recording_in_progress_audio_mic_audio_dark.mp4".toUri())
            recordingInProgressAudioNoMicAudioPreview = context.getDrawable(R.drawable.icon_recording_in_progress_audio_nomic_audio_dark_preview_rendered)
            recordingInProgressAudioNoMicAudio = MediaItem.fromUri("asset:///animations/recording_in_progress_audio_nomic_audio_dark.mp4".toUri())
            recordingInProgressAudioMicNoAudioPreview = context.getDrawable(R.drawable.icon_recording_in_progress_audio_mic_noaudio_dark_preview_rendered)
            recordingInProgressAudioMicNoAudio = MediaItem.fromUri("asset:///animations/recording_in_progress_audio_mic_noaudio_dark.mp4".toUri())
            recordingFinishedAudioStaticPreview =
                context.getDrawable(R.drawable.icon_recording_finished_reels_audio_dark_static_preview_rendered)
            recordingFinishedVideoStaticPreview =
                context.getDrawable(R.drawable.icon_recording_finished_reels_video_dark_static_preview_rendered)

            if (disableAnimations) {
                recordingFinishedAudioPreview =
                    context.getDrawable(R.drawable.icon_recording_finished_reels_audio_dark_static_preview_rendered)
            } else {
                recordingFinishedAudioPreview =
                    context.getDrawable(R.drawable.icon_recording_finished_reels_audio_dark_preview_rendered)
            }
            recordingFinishedAudio = MediaItem.fromUri("asset:///animations/recording_finished_audio_dark.mp4".toUri())
            if (disableAnimations) {
                recordingFinishedVideoPreview =
                    context.getDrawable(R.drawable.icon_recording_finished_reels_video_dark_static_preview_rendered)
            } else {
                recordingFinishedVideoPreview =
                    context.getDrawable(R.drawable.icon_recording_finished_reels_video_dark_preview_rendered)
            }
            recordingFinishedVideo = MediaItem.fromUri("asset:///animations/recording_finished_video_dark.mp4".toUri())
        } else {
            recordingInProgressVideoMicAudioPreview = context.getDrawable(R.drawable.icon_recording_in_progress_video_mic_audio_preview_rendered)
            recordingInProgressVideoMicAudio = MediaItem.fromUri("asset:///animations/recording_in_progress_video_mic_audio.mp4".toUri())
            recordingInProgressVideoNoMicAudioPreview = context.getDrawable(R.drawable.icon_recording_in_progress_video_nomic_audio_preview_rendered)
            recordingInProgressVideoNoMicAudio = MediaItem.fromUri("asset:///animations/recording_in_progress_video_nomic_audio.mp4".toUri())
            recordingInProgressVideoMicNoAudioPreview = context.getDrawable(R.drawable.icon_recording_in_progress_video_mic_noaudio_preview_rendered)
            recordingInProgressVideoMicNoAudio = MediaItem.fromUri("asset:///animations/recording_in_progress_video_mic_noaudio.mp4".toUri())
            recordingInProgressVideoNoMicNoAudioPreview = context.getDrawable(R.drawable.icon_recording_in_progress_video_nomic_noaudio_preview_rendered)
            recordingInProgressVideoNoMicNoAudio = MediaItem.fromUri("asset:///animations/recording_in_progress_video_nomic_noaudio.mp4".toUri())

            recordingInProgressAudioMicAudioPreview = context.getDrawable(R.drawable.icon_recording_in_progress_audio_mic_audio_preview_rendered)
            recordingInProgressAudioMicAudio = MediaItem.fromUri("asset:///animations/recording_in_progress_audio_mic_audio.mp4".toUri())
            recordingInProgressAudioNoMicAudioPreview = context.getDrawable(R.drawable.icon_recording_in_progress_audio_nomic_audio_preview_rendered)
            recordingInProgressAudioNoMicAudio = MediaItem.fromUri("asset:///animations/recording_in_progress_audio_nomic_audio.mp4".toUri())
            recordingInProgressAudioMicNoAudioPreview = context.getDrawable(R.drawable.icon_recording_in_progress_audio_mic_noaudio_preview_rendered)
            recordingInProgressAudioMicNoAudio = MediaItem.fromUri("asset:///animations/recording_in_progress_audio_mic_noaudio.mp4".toUri())
            recordingFinishedAudioStaticPreview =
                context.getDrawable(R.drawable.icon_recording_finished_reels_audio_static_preview_rendered)

            recordingFinishedVideoStaticPreview =
                context.getDrawable(R.drawable.icon_recording_finished_reels_video_static_preview_rendered)

            if (disableAnimations) {
                recordingFinishedAudioPreview =
                    context.getDrawable(R.drawable.icon_recording_finished_reels_audio_static_preview_rendered)
            } else {
                recordingFinishedAudioPreview =
                    context.getDrawable(R.drawable.icon_recording_finished_reels_audio_preview_rendered)
            }
            recordingFinishedAudio = MediaItem.fromUri("asset:///animations/recording_finished_audio.mp4".toUri())
            if (disableAnimations) {
                recordingFinishedVideoPreview =
                    context.getDrawable(R.drawable.icon_recording_finished_reels_video_static_preview_rendered)
            } else {
                recordingFinishedVideoPreview =
                    context.getDrawable(R.drawable.icon_recording_finished_reels_video_preview_rendered)
            }
            recordingFinishedVideo = MediaItem.fromUri("asset:///animations/recording_finished_video.mp4".toUri())
        }
    }

    init {
        this.useContext = context
        this.appSettings = GlobalProperties(this.useContext)
        recordMicrophone = micEnabled
        recordPlayback = playbackEnabled
        recordShizukuPhoneCall = shizukuPhoneCallEnabled
        recordOnlyAudio = onlyAudio
        updateAnimationResources()

        recordingAnimationPlayer = ExoPlayer.Builder(context).build().also { exoPlayer ->
            playerUse.player = exoPlayer

            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        ExoPlayer.STATE_IDLE -> {}
                        ExoPlayer.STATE_BUFFERING -> {
                            scrollView.post {
                                isReady.set(false)
                            }
                        }
                        ExoPlayer.STATE_READY -> {
                            scrollView.post {
                                if (fadingEnded.get() && useAnimate.get()) {
                                    recordingAnimationPlayer!!.play()
                                    playerUse.visibility = View.VISIBLE
                                    playerPreviewUse.visibility = View.INVISIBLE
                                } else {
                                    isReady.set(true)
                                }
                            }
                        }
                        ExoPlayer.STATE_ENDED -> {
                            scrollView.post {
                                if (!isLooping.getAndSet(false) &&
                                    currentRecordStatus == RecordStatus.END_SHOW_REELS) {
                                    setRecordStatusState(RecordStatus.ENDED_RECORDING_NORMAL)
                                }
                            }
                        }
                    }
                }
            })
        }
    }

    fun innerPlayer(): PlayerView {
        return this.playerUse
    }

    fun innerPreview(): ImageView {
        return this.playerPreviewUse
    }

    fun updateConditions(microphone: Boolean, playback: Boolean, phoneCall: Boolean, onlyAudio: Boolean) {
        this.recordMicrophone = microphone
        this.recordPlayback = playback
        this.recordOnlyAudio = onlyAudio
        this.recordShizukuPhoneCall = phoneCall
        updateAnimationResources()
    }

    fun recordIconStart() {
        if (disableAnimations) {
            return
        }
        if (isReady.getAndSet(false)) {
            scrollView.post {
                recordingAnimationPlayer!!.play()
                playerUse.visibility = View.VISIBLE
                playerPreviewUse.visibility = View.INVISIBLE
            }
        }
    }

    fun recordIconFadeIn(startAnimation: Boolean = true, fromTransparency: Float = 0.0f, toTransparency: Float = 1.0f) {
        playerUse.visibility = View.INVISIBLE
        playerPreviewUse.visibility = View.VISIBLE
        if (disableAnimations) {
            this.container.alpha = toTransparency
            return
        }
        val animateRecordingIconAlpha: ObjectAnimator = ObjectAnimator.ofFloat(this.container, "alpha", fromTransparency, toTransparency)
        animateRecordingIconAlpha.setDuration(400L)

        animateRecordingIconAlpha.doOnEnd {
            fadingEnded.set(true)
            if (startAnimation) {
                recordIconStart()
            }
        }
        fadingEnded.set(false)
        animateRecordingIconAlpha.start()
    }

    fun recordIconFadeOut(nextState: RecordStatus, toTransparency: Float = 0.0f) {
        playerUse.visibility = View.INVISIBLE
        playerPreviewUse.visibility = View.VISIBLE

        if (disableAnimations) {
            this.container.alpha = toTransparency
            setRecordStatusState(nextState)
            return
        }
        val animateRecordingIconAlpha: ObjectAnimator = ObjectAnimator.ofFloat(this.container, "alpha", 1.0f, toTransparency)
        animateRecordingIconAlpha.setDuration(400L)
        animateRecordingIconAlpha.doOnEnd {
            fadingEnded.set(true)
            setRecordStatusState(nextState)
        }
        fadingEnded.set(false)
        animateRecordingIconAlpha.start()
    }

    fun setRecordStatusState(statusRecordState: RecordStatus) {
        this.currentRecordStatus = statusRecordState
        this.nextRecordStatus = null
        when (statusRecordState) {
            RecordStatus.START_RECORDING -> {
                Log.d(TAG, "Start recording called!")

                useAnimate.set(true)
                if (recordOnlyAudio) {
                    if ((this.recordPlayback && this.recordMicrophone) || recordShizukuPhoneCall) {
                        playerPreviewUse.setImageDrawable(recordingInProgressAudioMicAudioPreview)
                        playerSetAnimation(recordingInProgressAudioMicAudio!!, true)
                    } else if (!this.recordMicrophone) {
                        playerPreviewUse.setImageDrawable(recordingInProgressAudioNoMicAudioPreview)
                        playerSetAnimation(recordingInProgressAudioNoMicAudio!!, true)
                    } else if (!this.recordPlayback) {
                        playerPreviewUse.setImageDrawable(recordingInProgressAudioMicNoAudioPreview)
                        playerSetAnimation(recordingInProgressAudioMicNoAudio!!, true)
                    }
                } else {
                    if ((this.recordPlayback && this.recordMicrophone) || recordShizukuPhoneCall) {
                        playerPreviewUse.setImageDrawable(recordingInProgressVideoMicAudioPreview)
                        playerSetAnimation(recordingInProgressVideoMicAudio!!, true)
                    } else if (!this.recordPlayback && !this.recordMicrophone) {
                        playerPreviewUse.setImageDrawable(recordingInProgressVideoNoMicNoAudioPreview)
                        playerSetAnimation(recordingInProgressVideoNoMicNoAudio!!, true)
                    } else if (!this.recordMicrophone) {
                        playerPreviewUse.setImageDrawable(recordingInProgressVideoNoMicAudioPreview)
                        playerSetAnimation(recordingInProgressVideoNoMicAudio!!, true)
                    } else if (!this.recordPlayback) {
                        playerPreviewUse.setImageDrawable(recordingInProgressVideoMicNoAudioPreview)
                        playerSetAnimation(recordingInProgressVideoMicNoAudio!!, true)
                    }
                }
                recordIconFadeIn()
            }
            RecordStatus.WHILE_RECORDING_NORMAL -> {
                Log.d(TAG, "While recording normal called!")

                useAnimate.set(true)
                if (recordOnlyAudio) {
                    if ((this.recordPlayback && this.recordMicrophone) || recordShizukuPhoneCall) {
                        playerPreviewUse.setImageDrawable(recordingInProgressAudioMicAudioPreview)
                        playerSetAnimation(recordingInProgressAudioMicAudio!!, true)
                    } else if (!this.recordMicrophone) {
                        playerPreviewUse.setImageDrawable(recordingInProgressAudioNoMicAudioPreview)
                        playerSetAnimation(recordingInProgressAudioNoMicAudio!!, true)
                    } else if (!this.recordPlayback) {
                        playerPreviewUse.setImageDrawable(recordingInProgressAudioMicNoAudioPreview)
                        playerSetAnimation(recordingInProgressAudioMicNoAudio!!, true)
                    }
                } else {
                    if ((this.recordPlayback && this.recordMicrophone) || recordShizukuPhoneCall) {
                        playerPreviewUse.setImageDrawable(recordingInProgressVideoMicAudioPreview)
                        playerSetAnimation(recordingInProgressVideoMicAudio!!, true)
                    } else if (!this.recordPlayback && !this.recordMicrophone) {
                        playerPreviewUse.setImageDrawable(recordingInProgressVideoNoMicNoAudioPreview)
                        playerSetAnimation(recordingInProgressVideoNoMicNoAudio!!, true)
                    } else if (!this.recordMicrophone) {
                        playerPreviewUse.setImageDrawable(recordingInProgressVideoNoMicAudioPreview)
                        playerSetAnimation(recordingInProgressVideoNoMicAudio!!, true)
                    } else if (!this.recordPlayback) {
                        playerPreviewUse.setImageDrawable(recordingInProgressVideoMicNoAudioPreview)
                        playerSetAnimation(recordingInProgressVideoMicNoAudio!!, true)
                    }
                }
                recordIconStart()
            }
            RecordStatus.TRANSITION_TO_RECORDING_PAUSE -> {
                Log.d(TAG, "Transition to recording pause called!")

                recordIconFadeOut(RecordStatus.WHILE_PAUSE_NORMAL, 0.5f)
            }
            RecordStatus.WHILE_PAUSE_NORMAL -> {
                Log.d(TAG, "While pause normal called!")

                useAnimate.set(false)
                if (recordOnlyAudio) {
                    if ((this.recordPlayback && this.recordMicrophone) || recordShizukuPhoneCall) {
                        playerPreviewUse.setImageDrawable(recordingInProgressAudioMicAudioPreview)
                        playerSetAnimation(recordingInProgressAudioMicAudio!!, true)
                    } else if (!this.recordMicrophone) {
                        playerPreviewUse.setImageDrawable(recordingInProgressAudioNoMicAudioPreview)
                        playerSetAnimation(recordingInProgressAudioNoMicAudio!!, true)
                    } else if (!this.recordPlayback) {
                        playerPreviewUse.setImageDrawable(recordingInProgressAudioMicNoAudioPreview)
                        playerSetAnimation(recordingInProgressAudioMicNoAudio!!, true)
                    }
                } else {
                    if ((this.recordPlayback && this.recordMicrophone) || recordShizukuPhoneCall) {
                        playerPreviewUse.setImageDrawable(recordingInProgressVideoMicAudioPreview)
                        playerSetAnimation(recordingInProgressVideoMicAudio!!, true)
                    } else if (!this.recordPlayback && !this.recordMicrophone) {
                        playerPreviewUse.setImageDrawable(recordingInProgressVideoNoMicNoAudioPreview)
                        playerSetAnimation(recordingInProgressVideoNoMicNoAudio!!, true)
                    } else if (!this.recordMicrophone) {
                        playerPreviewUse.setImageDrawable(recordingInProgressVideoNoMicAudioPreview)
                        playerSetAnimation(recordingInProgressVideoNoMicAudio!!, true)
                    } else if (!this.recordPlayback) {
                        playerPreviewUse.setImageDrawable(recordingInProgressVideoMicNoAudioPreview)
                        playerSetAnimation(recordingInProgressVideoMicNoAudio!!, true)
                    }
                }
                playerPreviewUse.visibility = View.VISIBLE
                playerUse.visibility = View.INVISIBLE
                container.alpha = 0.5f
            }
            RecordStatus.TRANSITION_FROM_PAUSE -> {
                Log.d(TAG, "Transition from pause called!")

                useAnimate.set(true)
                if (recordOnlyAudio) {
                    if ((this.recordPlayback && this.recordMicrophone) || recordShizukuPhoneCall) {
                        playerPreviewUse.setImageDrawable(recordingInProgressAudioMicAudioPreview)
                        playerSetAnimation(recordingInProgressAudioMicAudio!!, true)
                    } else if (!this.recordMicrophone) {
                        playerPreviewUse.setImageDrawable(recordingInProgressAudioNoMicAudioPreview)
                        playerSetAnimation(recordingInProgressAudioNoMicAudio!!, true)
                    } else if (!this.recordPlayback) {
                        playerPreviewUse.setImageDrawable(recordingInProgressAudioMicNoAudioPreview)
                        playerSetAnimation(recordingInProgressAudioMicNoAudio!!, true)
                    }
                } else {
                    if ((this.recordPlayback && this.recordMicrophone) || recordShizukuPhoneCall) {
                        playerPreviewUse.setImageDrawable(recordingInProgressVideoMicAudioPreview)
                        playerSetAnimation(recordingInProgressVideoMicAudio!!, true)
                    } else if (!this.recordPlayback && !this.recordMicrophone) {
                        playerPreviewUse.setImageDrawable(recordingInProgressVideoNoMicNoAudioPreview)
                        playerSetAnimation(recordingInProgressVideoNoMicNoAudio!!, true)
                    } else if (!this.recordMicrophone) {
                        playerPreviewUse.setImageDrawable(recordingInProgressVideoNoMicAudioPreview)
                        playerSetAnimation(recordingInProgressVideoNoMicAudio!!, true)
                    } else if (!this.recordPlayback) {
                        playerPreviewUse.setImageDrawable(recordingInProgressVideoMicNoAudioPreview)
                        playerSetAnimation(recordingInProgressVideoMicNoAudio!!, true)
                    }
                }
                recordIconFadeIn(fromTransparency = 0.5f)
            }
            RecordStatus.TRANSITION_TO_RECORDING_END -> {
                Log.d(TAG, "Recording end transition")

                useAnimate.set(true)
                recordIconFadeOut(RecordStatus.END_SHOW_REELS)
            }
            RecordStatus.END_SHOW_REELS -> {
                Log.d(TAG, "Recording end show reels")

                useAnimate.set(true)
                if (recordOnlyAudio) {
                    playerPreviewUse.setImageDrawable(recordingFinishedAudioPreview)
                    playerSetAnimation(recordingFinishedAudio!!, false)
                } else {
                    playerPreviewUse.setImageDrawable(recordingFinishedVideoPreview)
                    playerSetAnimation(recordingFinishedVideo!!, false)
                }
                recordIconFadeIn()
            }
            RecordStatus.ENDED_RECORDING_NORMAL -> {
                Log.d(TAG, "Recording ended normal")

                useAnimate.set(false)
                if (recordOnlyAudio) {
                    playerPreviewUse.setImageDrawable(recordingFinishedAudioStaticPreview)
                } else {
                    playerPreviewUse.setImageDrawable(recordingFinishedVideoStaticPreview)
                }
                playerUse.visibility = View.INVISIBLE
                playerPreviewUse.visibility = View.VISIBLE
            }
            RecordStatus.TRANSITION_TO_RESTART -> {
                Log.d(TAG, "Transition to restart called!")

                useAnimate.set(true)
                recordIconFadeOut(RecordStatus.START_RECORDING)
            }
            else -> {}
        }
    }
}
