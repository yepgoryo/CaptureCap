package com.yepgoryo.CaptureCap

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.widget.TooltipCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import java.util.ArrayList

class RecordButton(context: Context, imageButton: ImageButton) {
    private var appSettings: GlobalProperties? = null
    private var buttonUse: ImageButton? = null
    private var currentButtonState: ButtonState? = null
    private var mainButtonLayers: LayerDrawable? = null
    private var recordingBackgroundHover: AnimatedVectorDrawableCompat? = null
    private var recordingBackgroundHoverReleased: AnimatedVectorDrawableCompat? = null
    private var recordingBackgroundNormal: VectorDrawableCompat? = null
    private var recordingBackgroundTransition: AnimatedVectorDrawableCompat? = null
    private var recordingBackgroundTransitionBack: AnimatedVectorDrawableCompat? = null
    private var recordingStartAudioBodyAppear: AnimatedVectorDrawableCompat? = null
    private var recordingStartAudioBodyDisappear: AnimatedVectorDrawableCompat? = null
    private var recordingStartAudioBodyWorking: AnimatedVectorDrawableCompat? = null
    private var recordingStartAudioHeadphones: VectorDrawableCompat? = null
    private var recordingStartAudioHeadphonesAppear: AnimatedVectorDrawableCompat? = null
    private var recordingStartAudioHeadphonesDisappear: AnimatedVectorDrawableCompat? = null
    private var recordingStartAudioMicrophone: VectorDrawableCompat? = null
    private var recordingStartAudioMicrophoneAppear: AnimatedVectorDrawableCompat? = null
    private var recordingStartAudioMicrophoneDisappear: AnimatedVectorDrawableCompat? = null
    private var recordingStartAudioTapeAppear: AnimatedVectorDrawableCompat? = null
    private var recordingStartAudioTapeDisappear: AnimatedVectorDrawableCompat? = null
    private var recordingStartAudioTapeWorking: AnimatedVectorDrawableCompat? = null
    private var recordingStartButtonHover: AnimatedVectorDrawableCompat? = null
    private var recordingStartButtonHoverReleased: AnimatedVectorDrawableCompat? = null
    private var recordingStartButtonNormal: VectorDrawableCompat? = null
    private var recordingStartButtonTransitionBack: AnimatedVectorDrawableCompat? = null
    private var recordingStartButtonTransitionToRecording: AnimatedVectorDrawableCompat? = null
    private var recordingStartCameraBodyAppear: AnimatedVectorDrawableCompat? = null
    private var recordingStartCameraBodyDisappear: AnimatedVectorDrawableCompat? = null
    private var recordingStartCameraBodyWorking: AnimatedVectorDrawableCompat? = null
    private var recordingStartCameraHeadphones: VectorDrawableCompat? = null
    private var recordingStartCameraHeadphonesAppear: AnimatedVectorDrawableCompat? = null
    private var recordingStartCameraHeadphonesDisappear: AnimatedVectorDrawableCompat? = null
    private var recordingStartCameraLegs: VectorDrawableCompat? = null
    private var recordingStartCameraLegsAppear: AnimatedVectorDrawableCompat? = null
    private var recordingStartCameraLegsDisappear: AnimatedVectorDrawableCompat? = null
    private var recordingStartCameraMicrophone: VectorDrawableCompat? = null
    private var recordingStartCameraMicrophoneAppear: AnimatedVectorDrawableCompat? = null
    private var recordingStartCameraMicrophoneDisappear: AnimatedVectorDrawableCompat? = null
    private var recordingStatusIconPause: AnimatedVectorDrawableCompat? = null
    private var recordingStopAudioReelsFirstAppear: AnimatedVectorDrawableCompat? = null
    private var recordingStopAudioReelsFirstDisappear: AnimatedVectorDrawableCompat? = null
    private var recordingStopAudioReelsSecondAppear: AnimatedVectorDrawableCompat? = null
    private var recordingStopAudioReelsSecondDisappear: AnimatedVectorDrawableCompat? = null
    private var recordingStopCameraReelsFirstAppear: AnimatedVectorDrawableCompat? = null
    private var recordingStopCameraReelsFirstDisappear: AnimatedVectorDrawableCompat? = null
    private var recordingStopCameraReelsSecondAppear: AnimatedVectorDrawableCompat? = null
    private var recordingStopCameraReelsSecondDisappear: AnimatedVectorDrawableCompat? = null
    private var useContext: Context = context
    private var recordButtonLocked: Boolean = false
    private var recordButtonPressed: Boolean = false
    private var recordMicrophone: Boolean = false
    private var recordPlayback: Boolean = false
    private var recordOnlyAudio: Boolean = false
    private var nextButtonState: ButtonState? = null

    enum class ButtonState {
        BEFORE_RECORDING_NORMAL,
        BEFORE_RECORDING_HOVER,
        BEFORE_RECORDING_HOVER_RELEASED,
        TRANSITION_TO_RECORDING,
        START_RECORDING,
        CONTINUE_RECORDING,
        WHILE_RECORDING_NORMAL,
        WHILE_RECORDING_HOVER,
        WHILE_RECORDING_HOVER_RELEASED,
        TRANSITION_TO_RECORDING_PAUSE,
        WHILE_PAUSE_NORMAL,
        WHILE_PAUSE_HOVER,
        WHILE_PAUSE_HOVER_RELEASED,
        TRANSITION_FROM_PAUSE,
        TRANSITION_TO_RECORDING_END,
        END_SHOW_REELS,
        ENDED_RECORDING_NORMAL,
        ENDED_RECORDING_HOVER,
        ENDED_RECORDING_HOVER_RELEASED,
        TRANSITION_TO_RESTART
    }

    enum class DarkenState {
        TO_DARKEN,
        SET_DARKEN,
        UNDARKEN_RECORD,
        UNDARKEN_END
    }

    init {
        this.useContext = context
        this.buttonUse = imageButton
        var globalProperties = GlobalProperties(this.useContext)
        this.appSettings = globalProperties
        var darkTheme: GlobalProperties.DarkThemeProperty = globalProperties.getDarkTheme(true)
        if (((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES && darkTheme == GlobalProperties.DarkThemeProperty.AUTOMATIC) || darkTheme == GlobalProperties.DarkThemeProperty.DARK) {
            this.recordingStartButtonNormal = VectorDrawableCompat.create(this.useContext.resources, R.drawable.icon_recording_stopped_dark, null)
            this.recordingStartButtonHover = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_button_pressed_dark)
            this.recordingStartButtonHoverReleased = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_button_released_dark)
            this.recordingStartButtonTransitionToRecording = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_button_activate_dark)
            this.recordingBackgroundNormal = VectorDrawableCompat.create(this.useContext.resources, R.drawable.icon_recording_in_progress_background_dark, null)
            this.recordingBackgroundHover = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_background_pressed_dark)
            this.recordingBackgroundHoverReleased = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_background_released_dark)
            this.recordingBackgroundTransition = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_background_transition_dark)
            this.recordingBackgroundTransitionBack = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_background_transition_back_dark)
            this.recordingStartCameraLegs = VectorDrawableCompat.create(this.useContext.resources, R.drawable.icon_recording_in_progress_camlegs_dark, null)
            this.recordingStartCameraMicrophone = VectorDrawableCompat.create(this.useContext.resources, R.drawable.icon_recording_in_progress_with_mic_dark, null)
            this.recordingStartCameraHeadphones = VectorDrawableCompat.create(this.useContext.resources, R.drawable.icon_recording_in_progress_with_headphones_dark, null)
            this.recordingStartCameraLegsAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_camera_legs_appear_dark)
            this.recordingStartCameraLegsDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_camera_legs_disappear_dark)
            this.recordingStartCameraMicrophoneAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_camera_microphone_appear_dark)
            this.recordingStartCameraMicrophoneDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_camera_microphone_disappear_dark)
            this.recordingStartCameraBodyWorking = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_camera_body_working_dark)
            this.recordingStartCameraBodyAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_camera_body_appear_dark)
            this.recordingStartCameraBodyDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_camera_body_disappear_dark)
            this.recordingStartCameraHeadphonesAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_camera_headphones_appear_dark)
            this.recordingStartCameraHeadphonesDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_camera_headphones_disappear_dark)
            this.recordingStopCameraReelsFirstAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_stop_camera_reels_first_appear_dark)
            this.recordingStopCameraReelsFirstDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_stop_camera_reels_first_disappear_dark)
            this.recordingStopCameraReelsSecondAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_stop_camera_reels_second_appear_dark)
            this.recordingStopCameraReelsSecondDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_stop_camera_reels_second_disappear_dark)
            this.recordingStartAudioHeadphones = VectorDrawableCompat.create(this.useContext.resources, R.drawable.icon_recording_audio_in_progress_with_headphones_dark, null)
            this.recordingStartAudioMicrophone = VectorDrawableCompat.create(this.useContext.resources, R.drawable.icon_recording_audio_in_progress_with_mic_dark, null)
            this.recordingStartAudioBodyWorking = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_audio_body_working_dark)
            this.recordingStartAudioBodyAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_audio_body_appear_dark)
            this.recordingStartAudioBodyDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_audio_body_disappear_dark)
            this.recordingStartAudioTapeWorking = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_audio_tape_working_dark)
            this.recordingStartAudioTapeAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_audio_tape_appear_dark)
            this.recordingStartAudioTapeDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_audio_tape_disappear_dark)
            this.recordingStartAudioHeadphonesAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_audio_headphones_appear_dark)
            this.recordingStartAudioHeadphonesDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_audio_headphones_disappear_dark)
            this.recordingStartAudioMicrophoneAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_audio_microphone_appear_dark)
            this.recordingStartAudioMicrophoneDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_audio_microphone_disappear_dark)
            this.recordingStopAudioReelsFirstAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_stop_audio_reels_first_appear_dark)
            this.recordingStopAudioReelsFirstDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_stop_audio_reels_first_disappear_dark)
            this.recordingStopAudioReelsSecondAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_stop_audio_reels_second_appear_dark)
            this.recordingStopAudioReelsSecondDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_stop_audio_reels_second_disappear_dark)
            this.recordingStartButtonTransitionBack = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_button_restore_dark)
        } else {
            this.recordingStartButtonNormal = VectorDrawableCompat.create(this.useContext.resources, R.drawable.icon_recording_stopped, null)
            this.recordingStartButtonHover = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_button_pressed)
            this.recordingStartButtonHoverReleased = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_button_released)
            this.recordingStartButtonTransitionToRecording = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_button_activate)
            this.recordingBackgroundNormal = VectorDrawableCompat.create(this.useContext.resources, R.drawable.icon_recording_in_progress_background, null)
            this.recordingBackgroundHover = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_background_pressed)
            this.recordingBackgroundHoverReleased = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_background_released)
            this.recordingBackgroundTransition = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_background_transition)
            this.recordingBackgroundTransitionBack = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_background_transition_back)
            this.recordingStartCameraLegs = VectorDrawableCompat.create(this.useContext.resources, R.drawable.icon_recording_in_progress_camlegs, null)
            this.recordingStartCameraMicrophone = VectorDrawableCompat.create(this.useContext.resources, R.drawable.icon_recording_in_progress_with_mic, null)
            this.recordingStartCameraHeadphones = VectorDrawableCompat.create(this.useContext.resources, R.drawable.icon_recording_in_progress_with_headphones, null)
            this.recordingStartCameraLegsAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_camera_legs_appear)
            this.recordingStartCameraLegsDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_camera_legs_disappear)
            this.recordingStartCameraMicrophoneAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_camera_microphone_appear)
            this.recordingStartCameraMicrophoneDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_camera_microphone_disappear)
            this.recordingStartCameraBodyWorking = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_camera_body_working)
            this.recordingStartCameraBodyAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_camera_body_appear)
            this.recordingStartCameraBodyDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_camera_body_disappear)
            this.recordingStartCameraHeadphonesAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_camera_headphones_appear)
            this.recordingStartCameraHeadphonesDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_camera_headphones_disappear)
            this.recordingStopCameraReelsFirstAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_stop_camera_reels_first_appear)
            this.recordingStopCameraReelsFirstDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_stop_camera_reels_first_disappear)
            this.recordingStopCameraReelsSecondAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_stop_camera_reels_second_appear)
            this.recordingStopCameraReelsSecondDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_stop_camera_reels_second_disappear)
            this.recordingStartAudioHeadphones = VectorDrawableCompat.create(this.useContext.resources, R.drawable.icon_recording_audio_in_progress_with_headphones, null)
            this.recordingStartAudioMicrophone = VectorDrawableCompat.create(this.useContext.resources, R.drawable.icon_recording_audio_in_progress_with_mic, null)
            this.recordingStartAudioBodyWorking = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_audio_body_working)
            this.recordingStartAudioBodyAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_audio_body_appear)
            this.recordingStartAudioBodyDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_audio_body_disappear)
            this.recordingStartAudioTapeWorking = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_audio_tape_working)
            this.recordingStartAudioTapeAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_audio_tape_appear)
            this.recordingStartAudioTapeDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_audio_tape_disappear)
            this.recordingStartAudioHeadphonesAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_audio_headphones_appear)
            this.recordingStartAudioHeadphonesDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_audio_headphones_disappear)
            this.recordingStartAudioMicrophoneAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_audio_microphone_appear)
            this.recordingStartAudioMicrophoneDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_start_audio_microphone_disappear)
            this.recordingStopAudioReelsFirstAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_stop_audio_reels_first_appear)
            this.recordingStopAudioReelsFirstDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_stop_audio_reels_first_disappear)
            this.recordingStopAudioReelsSecondAppear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_stop_audio_reels_second_appear)
            this.recordingStopAudioReelsSecondDisappear = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_stop_audio_reels_second_disappear)
            this.recordingStartButtonTransitionBack = AnimatedVectorDrawableCompat.create(this.useContext, R.drawable.anim_recording_button_restore)
        }
        this.recordingStartButtonHover!!.registerAnimationCallback(object: Animatable2Compat.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable) {
                if (!this@RecordButton.recordButtonPressed && this@RecordButton.currentButtonState == ButtonState.BEFORE_RECORDING_HOVER) {
                    if (this@RecordButton.nextButtonState != null) {
                        this@RecordButton.setButtonState(this@RecordButton.nextButtonState!!)
                    } else {
                        this@RecordButton.setButtonState(ButtonState.BEFORE_RECORDING_HOVER_RELEASED)
                    }
                }
            }
        })
        this.recordingStartButtonHoverReleased!!.registerAnimationCallback(object: Animatable2Compat.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable) {
                if (this@RecordButton.currentButtonState == ButtonState.BEFORE_RECORDING_HOVER_RELEASED) {
                    this@RecordButton.setButtonState(ButtonState.BEFORE_RECORDING_NORMAL)
                }
            }
        })
        this.recordingStartButtonTransitionToRecording!!.registerAnimationCallback(object: Animatable2Compat.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable) {
                if (this@RecordButton.currentButtonState == ButtonState.TRANSITION_TO_RECORDING) {
                    this@RecordButton.setButtonState(ButtonState.START_RECORDING)
                }
            }
        })
        this.recordingBackgroundHover!!.registerAnimationCallback(object: Animatable2Compat.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable) {
                if (!this@RecordButton.recordButtonPressed) {
                    if (this@RecordButton.nextButtonState == null) {
                        if (this@RecordButton.currentButtonState != ButtonState.WHILE_RECORDING_HOVER) {
                            if (this@RecordButton.currentButtonState != ButtonState.WHILE_PAUSE_HOVER) {
                                if (this@RecordButton.currentButtonState == ButtonState.ENDED_RECORDING_HOVER) {
                                    this@RecordButton.setButtonState(ButtonState.ENDED_RECORDING_HOVER_RELEASED)
                                }
                            } else {
                                this@RecordButton.setButtonState(ButtonState.WHILE_PAUSE_HOVER_RELEASED)
                            }
                        } else {
                            this@RecordButton.setButtonState(ButtonState.WHILE_RECORDING_HOVER_RELEASED)
                        }
                    } else {
                        this@RecordButton.setButtonState(this@RecordButton.nextButtonState!!)
                    }
                }
            }
        })
        this.recordingBackgroundHoverReleased!!.registerAnimationCallback(object: Animatable2Compat.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable) {
                if (this@RecordButton.currentButtonState != ButtonState.WHILE_RECORDING_HOVER_RELEASED) {
                    if (this@RecordButton.currentButtonState != ButtonState.WHILE_PAUSE_HOVER_RELEASED) {
                        if (this@RecordButton.currentButtonState == ButtonState.ENDED_RECORDING_HOVER_RELEASED) {
                            this@RecordButton.setButtonState(ButtonState.ENDED_RECORDING_NORMAL)
                        }
                    } else {
                        this@RecordButton.setButtonState(ButtonState.WHILE_PAUSE_NORMAL)
                    }
                } else {
                    this@RecordButton.setButtonState(ButtonState.WHILE_RECORDING_NORMAL)
                }
            }
        })
        this.recordingStartCameraLegsAppear!!.registerAnimationCallback(object: Animatable2Compat.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable) {
                if (this@RecordButton.currentButtonState == ButtonState.START_RECORDING) {
                    this@RecordButton.setButtonState(ButtonState.WHILE_RECORDING_NORMAL)
                }
            }
        })
        this.recordingStartAudioBodyAppear!!.registerAnimationCallback(object: Animatable2Compat.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable) {
                if (this@RecordButton.currentButtonState == ButtonState.START_RECORDING) {
                    this@RecordButton.setButtonState(ButtonState.WHILE_RECORDING_NORMAL)
                }
            }
        })
        this.recordingStartAudioTapeDisappear!!.registerAnimationCallback(object: Animatable2Compat.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable) {
                if (this@RecordButton.currentButtonState == ButtonState.TRANSITION_TO_RECORDING_END) {
                    this@RecordButton.setButtonState(ButtonState.END_SHOW_REELS)
                }
            }
        })
        this.recordingStartCameraBodyDisappear!!.registerAnimationCallback(object: Animatable2Compat.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable) {
                if (this@RecordButton.currentButtonState == ButtonState.TRANSITION_TO_RECORDING_END) {
                    this@RecordButton.setButtonState(ButtonState.END_SHOW_REELS)
                }
            }
        })
        this.recordingStopCameraReelsFirstAppear!!.registerAnimationCallback(object: Animatable2Compat.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable) {
                if (this@RecordButton.currentButtonState == ButtonState.END_SHOW_REELS) {
                    this@RecordButton.setButtonState(ButtonState.ENDED_RECORDING_NORMAL)
                }
            }
        })
        this.recordingStopAudioReelsFirstAppear!!.registerAnimationCallback(object: Animatable2Compat.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable) {
                if (this@RecordButton.currentButtonState == ButtonState.END_SHOW_REELS) {
                    this@RecordButton.setButtonState(ButtonState.ENDED_RECORDING_NORMAL)
                }
            }
        })
        this.recordingStartButtonTransitionBack!!.registerAnimationCallback(object: Animatable2Compat.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable) {
                if (this@RecordButton.currentButtonState == ButtonState.TRANSITION_TO_RESTART) {
                    this@RecordButton.setButtonState(ButtonState.BEFORE_RECORDING_NORMAL)
                }
            }
        })

        var buttonLayers = LayerDrawable(arrayOf<Drawable>(
            this.recordingStartButtonNormal!!,
            this.recordingStartButtonHover!!,
            this.recordingStartButtonHoverReleased!!,
            this.recordingStartButtonTransitionToRecording!!,
            this.recordingBackgroundNormal!!,
            this.recordingBackgroundHover!!,
            this.recordingBackgroundHoverReleased!!,
            this.recordingBackgroundTransition!!,
            this.recordingBackgroundTransitionBack!!,
            this.recordingStartCameraLegs!!,
            this.recordingStartCameraLegsAppear!!,
            this.recordingStartCameraLegsDisappear!!,
            this.recordingStartCameraMicrophone!!,
            this.recordingStartCameraMicrophoneAppear!!,
            this.recordingStartCameraMicrophoneDisappear!!,
            this.recordingStartCameraBodyWorking!!,
            this.recordingStartCameraBodyAppear!!,
            this.recordingStartCameraBodyDisappear!!,
            this.recordingStartCameraHeadphones!!,
            this.recordingStartCameraHeadphonesAppear!!,
            this.recordingStartCameraHeadphonesDisappear!!,
            this.recordingStopCameraReelsFirstAppear!!,
            this.recordingStopCameraReelsFirstDisappear!!,
            this.recordingStopCameraReelsSecondAppear!!,
            this.recordingStopCameraReelsSecondDisappear!!,
            this.recordingStartAudioBodyWorking!!,
            this.recordingStartAudioBodyAppear!!,
            this.recordingStartAudioBodyDisappear!!,
            this.recordingStartAudioTapeWorking!!,
            this.recordingStartAudioTapeAppear!!,
            this.recordingStartAudioTapeDisappear!!,
            this.recordingStartAudioHeadphones!!,
            this.recordingStartAudioHeadphonesAppear!!,
            this.recordingStartAudioHeadphonesDisappear!!,
            this.recordingStartAudioMicrophone!!,
            this.recordingStartAudioMicrophoneAppear!!,
            this.recordingStartAudioMicrophoneDisappear!!,
            this.recordingStopAudioReelsFirstAppear!!,
            this.recordingStopAudioReelsFirstDisappear!!,
            this.recordingStopAudioReelsSecondAppear!!,
            this.recordingStopAudioReelsSecondDisappear!!,
            this.recordingStartButtonTransitionBack!!))

        this.mainButtonLayers = buttonLayers
        this.buttonUse!!.background = buttonLayers
        this.buttonUse!!.setOnTouchListener(object: View.OnTouchListener {
            override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
                var action: Int = motionEvent.action

                if (action != MotionEvent.ACTION_DOWN) {
                    if (action != MotionEvent.ACTION_UP) {
                        if (action == MotionEvent.ACTION_MOVE && this@RecordButton.recordButtonPressed) {
                            var motionEventCoords: Array<Int> = arrayOf(motionEvent.rawX.toInt(), motionEvent.rawY.toInt())
                            var locationOnScreen: IntArray? = IntArray(2)
                            view.getLocationOnScreen(locationOnScreen)
                            var viewCoords: Array<Int> = arrayOf(view.width.toInt(), view.height.toInt())
                            if (locationOnScreen!!.get(0) > motionEventCoords[0] || (locationOnScreen!!.get(0) + viewCoords[0]) < motionEventCoords[0] || locationOnScreen!!.get(1) > motionEventCoords[1] || (locationOnScreen!!.get(1) + viewCoords[1]) < motionEventCoords[1]) {
                                this@RecordButton.recordButtonPressed = false
                                this@RecordButton.recordButtonLocked = true
                                if (this@RecordButton.currentButtonState != ButtonState.BEFORE_RECORDING_HOVER || this@RecordButton.recordingStartButtonHover!!.isRunning()) {
                                    if (this@RecordButton.currentButtonState != ButtonState.WHILE_RECORDING_HOVER || this@RecordButton.recordingBackgroundHover!!.isRunning()) {
                                        if (this@RecordButton.currentButtonState != ButtonState.WHILE_PAUSE_HOVER || this@RecordButton.recordingBackgroundHover!!.isRunning()) {
                                            if (this@RecordButton.currentButtonState == ButtonState.ENDED_RECORDING_HOVER && !this@RecordButton.recordingBackgroundHover!!.isRunning()) {
                                                this@RecordButton.setButtonState(ButtonState.ENDED_RECORDING_HOVER_RELEASED)
                                            }
                                        } else {
                                            this@RecordButton.setButtonState(ButtonState.WHILE_PAUSE_HOVER_RELEASED)
                                        }
                                    } else {
                                        this@RecordButton.setButtonState(ButtonState.WHILE_RECORDING_HOVER_RELEASED)
                                    }
                                } else {
                                    this@RecordButton.setButtonState(ButtonState.BEFORE_RECORDING_HOVER_RELEASED)
                                }
                            }
                        }
                    } else {
                        this@RecordButton.recordButtonPressed = false
                    }
                } else {
                    if (this@RecordButton.recordButtonLocked) {
                        return true
                    }
                    this@RecordButton.recordButtonPressed = true
                    if (this@RecordButton.currentButtonState != ButtonState.BEFORE_RECORDING_NORMAL) {
                        if (this@RecordButton.currentButtonState != ButtonState.WHILE_RECORDING_NORMAL) {
                            if (this@RecordButton.currentButtonState != ButtonState.WHILE_PAUSE_NORMAL) {
                                if (this@RecordButton.currentButtonState == ButtonState.ENDED_RECORDING_NORMAL) {
                                    this@RecordButton.setButtonState(ButtonState.ENDED_RECORDING_HOVER)
                                }
                            } else {
                                this@RecordButton.setButtonState(ButtonState.WHILE_PAUSE_HOVER)
                            }
                        } else {
                            this@RecordButton.setButtonState(ButtonState.WHILE_RECORDING_HOVER)
                        }
                    } else {
                        this@RecordButton.setButtonState(ButtonState.BEFORE_RECORDING_HOVER)
                    }
                }
                return false
            }
        })
        setButtonState(ButtonState.BEFORE_RECORDING_NORMAL)
    }

    fun innerButton(): ImageButton {
        return this.buttonUse!!
    }

    fun updateConditions(microphone: Boolean, playback: Boolean, onlyAudio: Boolean) {
        this.recordMicrophone = microphone
        this.recordPlayback = playback
        this.recordOnlyAudio = onlyAudio
    }

    fun getLockButton(): Boolean {
        return this.recordButtonLocked
    }

    fun setLockButton(locked: Boolean) {
        this.recordButtonLocked = locked
    }

    fun releaseFocus() {
        if (!this.recordingStartButtonHover!!.isRunning()) {
            when (this.currentButtonState) {
                ButtonState.BEFORE_RECORDING_HOVER -> {
                    setButtonState(ButtonState.BEFORE_RECORDING_HOVER_RELEASED)
                }
                ButtonState.WHILE_RECORDING_HOVER -> {
                    setButtonState(ButtonState.WHILE_RECORDING_HOVER_RELEASED)
                }
                ButtonState.WHILE_PAUSE_HOVER -> {
                    setButtonState(ButtonState.WHILE_PAUSE_HOVER_RELEASED)
                }
                ButtonState.ENDED_RECORDING_HOVER -> {
                    setButtonState(ButtonState.ENDED_RECORDING_HOVER_RELEASED)
                }
                else -> {}
            }
        }
    }

    fun hoverInProgress(): Boolean {
        when (this.currentButtonState) {
            ButtonState.BEFORE_RECORDING_HOVER -> {
                return this.recordingStartButtonHover!!.isRunning()
            }
            ButtonState.WHILE_RECORDING_HOVER -> {
                return this.recordingStartButtonHover!!.isRunning()
            }
            ButtonState.WHILE_PAUSE_HOVER -> {
                return this.recordingStartButtonHover!!.isRunning()
            }
            else -> {
                return false
            }
        }
    }

    private fun darkenLayers(layers: ArrayList<Drawable>, fromDark: Boolean, darkenState: DarkenState, buttonState: ButtonState) {
        if (darkenState == DarkenState.SET_DARKEN) {
            var i = 0
            while (i < layers.size) {
                layers.get(i).setColorFilter(PorterDuffColorFilter(Color.argb((80.0f).toInt(), 0, 0, 0), PorterDuff.Mode.SRC_ATOP))
                i+=1
            }
        } else {
            var darkenAnimator: ValueAnimator = ObjectAnimator.ofFloat(0.0f, 0.8f)
            if (fromDark) {
                darkenAnimator = ObjectAnimator.ofFloat(0.8f, 0.0f)
            }
            darkenAnimator.addUpdateListener(object: ValueAnimator.AnimatorUpdateListener {
                override fun onAnimationUpdate(valueAnimator: ValueAnimator) {
                    val opacityScale: Float = (valueAnimator.getAnimatedValue() as Float)
                    var i: Int = 0
                    while (i < layers.size) {
                        if (opacityScale == 0.0f) {
                            (layers.get(i) as Drawable).setColorFilter(null)
                        } else {
                            (layers.get(i) as Drawable).setColorFilter(PorterDuffColorFilter(Color.argb((100.0f * opacityScale).toInt(), 0, 0, 0), PorterDuff.Mode.SRC_ATOP))
                        }
                        i += 1
                    }
                    if ((opacityScale != 0.0f && fromDark) || this@RecordButton.currentButtonState == buttonState) {
                        if (darkenState == DarkenState.TO_DARKEN) {
                            this@RecordButton.setButtonState(ButtonState.WHILE_PAUSE_NORMAL)
                        } else if (darkenState == DarkenState.UNDARKEN_RECORD) {
                            this@RecordButton.setButtonState(ButtonState.WHILE_RECORDING_NORMAL)
                        } else if (darkenState == DarkenState.UNDARKEN_END) {
                            this@RecordButton.setButtonState(ButtonState.TRANSITION_TO_RECORDING_END)
                        }
                    }
                }
            })
            darkenAnimator.setDuration(ItemTouchHelper.Callback.DEFAULT_DRAG_ANIMATION_DURATION.toLong())
            darkenAnimator.start()
        }
    }

    private fun getButtonDarkenLayers(): ArrayList<Drawable> {
        val arrayList: ArrayList<Drawable> = ArrayList()
        if (this.recordMicrophone && !this.recordOnlyAudio) {
            arrayList.add(this.recordingStartCameraMicrophone!!)
        }
        if (this.recordPlayback && !this.recordOnlyAudio) {
            arrayList.add(this.recordingStartCameraHeadphones!!)
        }
        if (this.recordOnlyAudio) {
            arrayList.add(this.recordingStartAudioBodyWorking!!)
            arrayList.add(this.recordingStartAudioTapeWorking!!)
        } else {
            arrayList.add(this.recordingStartCameraLegs!!)
            arrayList.add(this.recordingStartCameraBodyWorking!!)
        }
        if (this.recordPlayback && this.recordOnlyAudio) {
            arrayList.add(this.recordingStartAudioHeadphones!!)
        }
        if (this.recordMicrophone && this.recordOnlyAudio) {
            arrayList.add(this.recordingStartAudioMicrophone!!)
        }
        return arrayList
    }

    private fun buttonResetDarkMask() {
        this.recordingStartCameraLegs!!.setColorFilter(null)
        this.recordingStartCameraMicrophone!!.setColorFilter(null)
        this.recordingStartCameraBodyWorking!!.setColorFilter(null)
        this.recordingStartCameraHeadphones!!.setColorFilter(null)
        this.recordingStartAudioHeadphones!!.setColorFilter(null)
        this.recordingStartAudioBodyWorking!!.setColorFilter(null)
        this.recordingStartAudioTapeWorking!!.setColorFilter(null)
        this.recordingStartAudioMicrophone!!.setColorFilter(null)
    }

    private fun itemsResetVisible() {
        this.recordingStartButtonNormal!!.setAlpha(0)
        this.recordingStartButtonHover!!.setAlpha(0)
        this.recordingStartButtonHoverReleased!!.setAlpha(0)
        this.recordingStartButtonTransitionToRecording!!.setAlpha(0)
        this.recordingBackgroundNormal!!.setAlpha(0)
        this.recordingBackgroundHover!!.setAlpha(0)
        this.recordingBackgroundHoverReleased!!.setAlpha(0)
        this.recordingBackgroundTransition!!.setAlpha(0)
        this.recordingBackgroundTransitionBack!!.setAlpha(0)
        this.recordingStartCameraLegs!!.setAlpha(0)
        this.recordingStartCameraLegsAppear!!.setAlpha(0)
        this.recordingStartCameraLegsDisappear!!.setAlpha(0)
        this.recordingStartCameraMicrophone!!.setAlpha(0)
        this.recordingStartCameraMicrophoneAppear!!.setAlpha(0)
        this.recordingStartCameraMicrophoneDisappear!!.setAlpha(0)
        this.recordingStartCameraBodyWorking!!.setAlpha(0)
        this.recordingStartCameraBodyAppear!!.setAlpha(0)
        this.recordingStartCameraBodyDisappear!!.setAlpha(0)
        this.recordingStartCameraHeadphones!!.setAlpha(0)
        this.recordingStartCameraHeadphonesAppear!!.setAlpha(0)
        this.recordingStartCameraHeadphonesDisappear!!.setAlpha(0)
        this.recordingStopCameraReelsFirstAppear!!.setAlpha(0)
        this.recordingStopCameraReelsFirstDisappear!!.setAlpha(0)
        this.recordingStopCameraReelsSecondAppear!!.setAlpha(0)
        this.recordingStopCameraReelsSecondDisappear!!.setAlpha(0)
        this.recordingStartAudioBodyWorking!!.setAlpha(0)
        this.recordingStartAudioBodyAppear!!.setAlpha(0)
        this.recordingStartAudioBodyDisappear!!.setAlpha(0)
        this.recordingStartAudioTapeWorking!!.setAlpha(0)
        this.recordingStartAudioTapeAppear!!.setAlpha(0)
        this.recordingStartAudioTapeDisappear!!.setAlpha(0)
        this.recordingStartAudioHeadphones!!.setAlpha(0)
        this.recordingStartAudioHeadphonesAppear!!.setAlpha(0)
        this.recordingStartAudioHeadphonesDisappear!!.setAlpha(0)
        this.recordingStartAudioMicrophone!!.setAlpha(0)
        this.recordingStartAudioMicrophoneAppear!!.setAlpha(0)
        this.recordingStartAudioMicrophoneDisappear!!.setAlpha(0)
        this.recordingStopAudioReelsFirstAppear!!.setAlpha(0)
        this.recordingStopAudioReelsFirstDisappear!!.setAlpha(0)
        this.recordingStopAudioReelsSecondAppear!!.setAlpha(0)
        this.recordingStopAudioReelsSecondDisappear!!.setAlpha(0)
        this.recordingStartButtonTransitionBack!!.setAlpha(0)
    }

    private fun itemsVisibleRecording() {
        if (!this.recordOnlyAudio) {
            this.recordingStartCameraLegs!!.setAlpha(255)
        }
        if (this.recordMicrophone && !this.recordOnlyAudio) {
            this.recordingStartCameraMicrophone!!.setAlpha(255)
        }
        if (!this.recordOnlyAudio) {
            this.recordingStartCameraBodyWorking!!.setAlpha(255)
            this.recordingStartCameraBodyWorking!!.start()
        }
        if (this.recordPlayback && !this.recordOnlyAudio) {
            this.recordingStartCameraHeadphones!!.setAlpha(255)
        }
        if (this.recordOnlyAudio) {
            this.recordingStartAudioBodyWorking!!.setAlpha(255)
            this.recordingStartAudioBodyWorking!!.start()
        }
        if (this.recordOnlyAudio) {
            this.recordingStartAudioTapeWorking!!.setAlpha(255)
            this.recordingStartAudioTapeWorking!!.start()
        }
        if (this.recordPlayback && this.recordOnlyAudio) {
            this.recordingStartAudioHeadphones!!.setAlpha(255)
        }
        if (this.recordMicrophone && this.recordOnlyAudio) {
            this.recordingStartAudioMicrophone!!.setAlpha(255)
        }
    }

    private fun itemsRecordingStopAnimation() {
        if (this.recordOnlyAudio) {
            this.recordingStartAudioBodyWorking!!.stop()
            this.recordingStartAudioTapeWorking!!.stop()
        } else {
            this.recordingStartCameraBodyWorking!!.stop()
        }
    }

    private fun itemsVisibleEndedRecording() {
        if (this.recordOnlyAudio) {
            this.recordingStopAudioReelsFirstAppear!!.setAlpha(255)
            this.recordingStopAudioReelsSecondAppear!!.setAlpha(255)
        } else {
            this.recordingStopCameraReelsFirstAppear!!.setAlpha(255)
            this.recordingStopCameraReelsSecondAppear!!.setAlpha(255)
        }
    }

    private fun setButtonDescription(stringId: Int) {
        this.buttonUse!!.setContentDescription(this.useContext.resources.getString(stringId))
        TooltipCompat.setTooltipText(this.buttonUse!!, this.useContext.resources.getString(stringId))
    }

    fun setButtonState(buttonState: ButtonState) {
        this.currentButtonState = buttonState
        this.nextButtonState = null
        itemsResetVisible()
        when (buttonState) {
            ButtonState.BEFORE_RECORDING_HOVER -> {
                this.recordingStartButtonHover!!.setAlpha(255)
                this.recordingStartButtonHover!!.start()
            }
            ButtonState.WHILE_RECORDING_HOVER -> {
                itemsVisibleRecording()
                this.recordingBackgroundHover!!.setAlpha(255)
                this.recordingBackgroundHover!!.start()
            }
            ButtonState.WHILE_PAUSE_HOVER -> {
                itemsVisibleRecording()
                this.recordingBackgroundHover!!.setAlpha(255)
                this.recordingBackgroundHover!!.start()
                itemsRecordingStopAnimation()
            }
            ButtonState.ENDED_RECORDING_HOVER -> {
                itemsVisibleEndedRecording()
                this.recordingBackgroundHover!!.setAlpha(255)
                this.recordingBackgroundHover!!.start()
            }
            ButtonState.BEFORE_RECORDING_NORMAL -> {
                this.recordButtonLocked = false
                setButtonDescription(R.string.record_start)
                this.recordingStartButtonNormal!!.setAlpha(255)
            }
            ButtonState.BEFORE_RECORDING_HOVER_RELEASED -> {
                this.recordingStartButtonHoverReleased!!.setAlpha(255)
                this.recordingStartButtonHoverReleased!!.start()
            }
            ButtonState.TRANSITION_TO_RECORDING -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setButtonDescription(R.string.record_pause)
                } else {
                    setButtonDescription(R.string.record_stop)
                }
                this.recordingStartButtonTransitionToRecording!!.setAlpha(255)
                this.recordingStartButtonTransitionToRecording!!.start()
            }
            ButtonState.START_RECORDING -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setButtonDescription(R.string.record_pause)
                } else {
                    setButtonDescription(R.string.record_stop)
                }
                this.recordingBackgroundNormal!!.setAlpha(255)
                if (this.recordOnlyAudio) {
                    this.recordingStartAudioBodyAppear!!.setAlpha(255)
                    this.recordingStartAudioTapeAppear!!.setAlpha(255)
                    this.recordingStartAudioBodyAppear!!.start()
                    this.recordingStartAudioTapeAppear!!.start()
                    if (this.recordMicrophone) {
                        this.recordingStartAudioMicrophoneAppear!!.setAlpha(255)
                        this.recordingStartAudioMicrophoneAppear!!.start()
                    }
                    if (this.recordPlayback) {
                        this.recordingStartAudioHeadphonesAppear!!.setAlpha(255)
                        this.recordingStartAudioHeadphonesAppear!!.start()
                    }
                } else {
                    this.recordingStartCameraLegsAppear!!.setAlpha(255)
                    this.recordingStartCameraBodyAppear!!.setAlpha(255)
                    this.recordingStartCameraLegsAppear!!.start()
                    this.recordingStartCameraBodyAppear!!.start()
                    if (this.recordMicrophone) {
                        this.recordingStartCameraMicrophoneAppear!!.setAlpha(255)
                        this.recordingStartCameraMicrophoneAppear!!.start()
                    }
                    if (this.recordPlayback) {
                        this.recordingStartCameraHeadphonesAppear!!.setAlpha(255)
                        this.recordingStartCameraHeadphonesAppear!!.start()
                    }
                }
            }
            ButtonState.WHILE_RECORDING_NORMAL -> {
                itemsVisibleRecording()
                this.recordButtonLocked = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setButtonDescription(R.string.record_pause)
                } else {
                    setButtonDescription(R.string.record_stop)
                }
                this.recordingBackgroundNormal!!.setAlpha(255)
            }
            ButtonState.WHILE_RECORDING_HOVER_RELEASED -> {
                itemsVisibleRecording()
                this.recordingBackgroundHoverReleased!!.setAlpha(255)
                this.recordingBackgroundHoverReleased!!.start()
            }
            ButtonState.TRANSITION_TO_RECORDING_PAUSE -> {
                itemsVisibleRecording()
                setButtonDescription(R.string.record_resume)
                this.recordingBackgroundHoverReleased!!.setAlpha(255)
                this.recordingBackgroundHoverReleased!!.start()
                itemsRecordingStopAnimation()
                darkenLayers(getButtonDarkenLayers(), false, DarkenState.TO_DARKEN, buttonState)
            }
            ButtonState.WHILE_PAUSE_NORMAL -> {
                this.recordButtonLocked = false
                itemsVisibleRecording()
                setButtonDescription(R.string.record_resume)
                this.recordingBackgroundNormal!!.setAlpha(255)
                itemsRecordingStopAnimation()
                darkenLayers(getButtonDarkenLayers(), false, DarkenState.SET_DARKEN, buttonState)
            }
            ButtonState.WHILE_PAUSE_HOVER_RELEASED -> {
                itemsVisibleRecording()
                this.recordingBackgroundHoverReleased!!.setAlpha(255)
                this.recordingBackgroundHoverReleased!!.start()
                itemsRecordingStopAnimation()
            }
            ButtonState.TRANSITION_FROM_PAUSE -> {
                itemsVisibleRecording()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setButtonDescription(R.string.record_pause)
                } else {
                    setButtonDescription(R.string.record_stop)
                }
                this.recordingBackgroundHoverReleased!!.setAlpha(255)
                this.recordingBackgroundHoverReleased!!.start()
                itemsRecordingStopAnimation()
                darkenLayers(getButtonDarkenLayers(), true, DarkenState.UNDARKEN_RECORD, buttonState)
            }
            ButtonState.TRANSITION_TO_RECORDING_END -> {
                itemsRecordingStopAnimation()
                setButtonDescription(R.string.recording_finished_title)
                this.recordingBackgroundNormal!!.setAlpha(255)
                if (this.recordOnlyAudio) {
                    this.recordingStartAudioBodyDisappear!!.setAlpha(255)
                    this.recordingStartAudioTapeDisappear!!.setAlpha(255)
                    this.recordingStartAudioBodyDisappear!!.start()
                    this.recordingStartAudioTapeDisappear!!.start()
                    if (this.recordPlayback) {
                        this.recordingStartAudioHeadphonesDisappear!!.setAlpha(255)
                        this.recordingStartAudioHeadphonesDisappear!!.start()
                    }
                    if (this.recordMicrophone) {
                        this.recordingStartAudioMicrophoneDisappear!!.setAlpha(255)
                        this.recordingStartAudioMicrophoneDisappear!!.start()
                    }
                } else {
                    this.recordingStartCameraBodyDisappear!!.setAlpha(255)
                    this.recordingStartCameraLegsDisappear!!.setAlpha(255)
                    this.recordingStartCameraBodyDisappear!!.start()
                    this.recordingStartCameraLegsDisappear!!.start()
                    if (this.recordMicrophone) {
                        this.recordingStartCameraMicrophoneDisappear!!.setAlpha(255)
                        this.recordingStartCameraMicrophoneDisappear!!.start()
                    }
                    if (this.recordPlayback) {
                        this.recordingStartCameraHeadphonesDisappear!!.setAlpha(255)
                        this.recordingStartCameraHeadphonesDisappear!!.start()
                    }
                }
            }
            ButtonState.END_SHOW_REELS -> {
                buttonResetDarkMask()
                this.recordingBackgroundNormal!!.setAlpha(255)
                if (!this.recordOnlyAudio) {
                    this.recordingStopCameraReelsFirstAppear!!.setAlpha(255)
                    this.recordingStopCameraReelsFirstAppear!!.start()
                    this.recordingStopCameraReelsSecondAppear!!.setAlpha(255)
                    this.recordingStopCameraReelsSecondAppear!!.start()
                }
                if (this.recordOnlyAudio) {
                    this.recordingStopAudioReelsFirstAppear!!.setAlpha(255)
                    this.recordingStopAudioReelsFirstAppear!!.start()
                    this.recordingStopAudioReelsSecondAppear!!.setAlpha(255)
                    this.recordingStopAudioReelsSecondAppear!!.start()
                }
            }
            ButtonState.ENDED_RECORDING_NORMAL -> {
                this.recordButtonLocked = false
                itemsVisibleEndedRecording()
                setButtonDescription(R.string.recording_finished_title)
                buttonResetDarkMask()
                this.recordingBackgroundNormal!!.setAlpha(255)
            }
            ButtonState.ENDED_RECORDING_HOVER_RELEASED -> {
                itemsVisibleEndedRecording()
                this.recordingBackgroundHoverReleased!!.setAlpha(255)
                this.recordingBackgroundHoverReleased!!.start()
            }
            ButtonState.TRANSITION_TO_RESTART -> {
                setButtonDescription(R.string.record_start)
                this.recordingStartButtonTransitionBack!!.setAlpha(255)
                this.recordingStartButtonTransitionBack!!.start()
            }
            else -> {}
        }
    }

    fun transitionToButtonState(buttonState: ButtonState) {
        if (!hoverInProgress()) {
            setButtonState(buttonState)
        } else {
            this.nextButtonState = buttonState
        }
    }
}
